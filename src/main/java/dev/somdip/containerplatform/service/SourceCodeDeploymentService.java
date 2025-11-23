package dev.somdip.containerplatform.service;

import dev.somdip.containerplatform.model.Container;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class SourceCodeDeploymentService {
    private static final Logger log = LoggerFactory.getLogger(SourceCodeDeploymentService.class);

    private final S3Client s3Client;
    private final ProjectAnalyzer projectAnalyzer;
    private final DockerfileGenerator dockerfileGenerator;
    private final ContainerService containerService;

    @Value("${aws.s3.bucket}")
    private String s3Bucket;

    @Value("${aws.region}")
    private String awsRegion;

    public SourceCodeDeploymentService(S3Client s3Client,
                                      ProjectAnalyzer projectAnalyzer,
                                      DockerfileGenerator dockerfileGenerator,
                                      ContainerService containerService) {
        this.s3Client = s3Client;
        this.projectAnalyzer = projectAnalyzer;
        this.dockerfileGenerator = dockerfileGenerator;
        this.containerService = containerService;
    }

    public static class DeploymentResult {
        private String projectId;
        private ProjectAnalyzer.ProjectType projectType;
        private String dockerfileContent;
        private String s3Key;
        private Container container;
        private boolean dockerfileGenerated;

        public String getProjectId() {
            return projectId;
        }

        public void setProjectId(String projectId) {
            this.projectId = projectId;
        }

        public ProjectAnalyzer.ProjectType getProjectType() {
            return projectType;
        }

        public void setProjectType(ProjectAnalyzer.ProjectType projectType) {
            this.projectType = projectType;
        }

        public String getDockerfileContent() {
            return dockerfileContent;
        }

        public void setDockerfileContent(String dockerfileContent) {
            this.dockerfileContent = dockerfileContent;
        }

        public String getS3Key() {
            return s3Key;
        }

        public void setS3Key(String s3Key) {
            this.s3Key = s3Key;
        }

        public Container getContainer() {
            return container;
        }

        public void setContainer(Container container) {
            this.container = container;
        }

        public boolean isDockerfileGenerated() {
            return dockerfileGenerated;
        }

        public void setDockerfileGenerated(boolean dockerfileGenerated) {
            this.dockerfileGenerated = dockerfileGenerated;
        }
    }

    public DeploymentResult deployFromSource(MultipartFile file, String containerName, String userId)
            throws IOException {
        log.info("Starting source code deployment for user: {}, container: {}", userId, containerName);

        // Create temp directory
        Path tempDir = Files.createTempDirectory("deploy-" + UUID.randomUUID());
        try {
            // Extract ZIP file
            Path projectPath = extractZipFile(file, tempDir);
            log.info("Extracted project to: {}", projectPath);

            // Analyze project
            ProjectAnalyzer.ProjectInfo projectInfo = projectAnalyzer.analyzeProject(projectPath);
            log.info("Detected project type: {}", projectInfo.getType().getDisplayName());

            DeploymentResult result = new DeploymentResult();
            result.setProjectId(UUID.randomUUID().toString());
            result.setProjectType(projectInfo.getType());

            // Check if Dockerfile exists, if not generate one
            Path existingDockerfile = projectPath.resolve("Dockerfile");
            if (Files.exists(existingDockerfile)) {
                log.info("Found existing Dockerfile - replacing Docker Hub base images with ECR");
                String originalDockerfile = Files.readString(existingDockerfile);
                String modifiedDockerfile = dockerfileGenerator.replaceBaseImages(originalDockerfile);

                // Write the modified Dockerfile back
                dockerfileGenerator.writeDockerfile(projectPath, modifiedDockerfile);

                result.setDockerfileContent(modifiedDockerfile);
                result.setDockerfileGenerated(false);
                log.info("Dockerfile updated with ECR base images");
            } else {
                log.info("Generating Dockerfile for project type: {}", projectInfo.getType());
                String dockerfileContent = dockerfileGenerator.generateDockerfile(projectInfo);
                dockerfileGenerator.writeDockerfile(projectPath, dockerfileContent);
                result.setDockerfileContent(dockerfileContent);
                result.setDockerfileGenerated(true);
            }

            // Upload to S3
            String s3Key = uploadToS3(projectPath, userId, result.getProjectId());
            result.setS3Key(s3Key);
            log.info("Uploaded project to S3: {}", s3Key);

            return result;

        } finally {
            // Cleanup temp directory
            deleteDirectory(tempDir);
        }
    }

    private Path extractZipFile(MultipartFile file, Path tempDir) throws IOException {
        Path zipPath = tempDir.resolve("upload.zip");
        file.transferTo(zipPath.toFile());

        Path extractPath = tempDir.resolve("project");
        Files.createDirectories(extractPath);

        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipPath))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path entryPath = extractPath.resolve(entry.getName());

                // Security check - prevent zip slip
                if (!entryPath.normalize().startsWith(extractPath.normalize())) {
                    throw new IOException("Invalid ZIP entry: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    Files.createDirectories(entryPath.getParent());
                    Files.copy(zis, entryPath, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }

        // Find the root project directory (might be nested)
        Path rootProjectPath = findProjectRoot(extractPath);
        return rootProjectPath != null ? rootProjectPath : extractPath;
    }

    private Path findProjectRoot(Path extractPath) throws IOException {
        // Check if current path is project root
        if (isProjectRoot(extractPath)) {
            return extractPath;
        }

        // Check one level deep
        try (var paths = Files.list(extractPath)) {
            var subDirs = paths.filter(Files::isDirectory).toList();
            if (subDirs.size() == 1) {
                Path potentialRoot = subDirs.get(0);
                if (isProjectRoot(potentialRoot)) {
                    return potentialRoot;
                }
            }
        }

        return extractPath;
    }

    private boolean isProjectRoot(Path path) {
        return Files.exists(path.resolve("package.json")) ||
               Files.exists(path.resolve("requirements.txt")) ||
               Files.exists(path.resolve("pom.xml")) ||
               Files.exists(path.resolve("build.gradle")) ||
               Files.exists(path.resolve("go.mod")) ||
               Files.exists(path.resolve("composer.json")) ||
               Files.exists(path.resolve("Gemfile")) ||
               Files.exists(path.resolve("Dockerfile"));
    }

    private String uploadToS3(Path projectPath, String userId, String projectId) throws IOException {
        // Create a zip of the project directory
        Path zipPath = Files.createTempFile("project-", ".zip");
        try {
            zipDirectory(projectPath, zipPath);

            String s3Key = String.format("deployments/%s/%s/source.zip", userId, projectId);

            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(s3Bucket)
                    .key(s3Key)
                    .build();

            s3Client.putObject(putRequest, RequestBody.fromFile(zipPath));

            return s3Key;
        } finally {
            Files.deleteIfExists(zipPath);
        }
    }

    private void zipDirectory(Path sourceDir, Path zipPath) throws IOException {
        try (var zipOutputStream = new java.util.zip.ZipOutputStream(Files.newOutputStream(zipPath))) {
            Files.walk(sourceDir)
                    .filter(path -> !Files.isDirectory(path))
                    .forEach(path -> {
                        try {
                            String entryName = sourceDir.relativize(path).toString();
                            zipOutputStream.putNextEntry(new ZipEntry(entryName));
                            Files.copy(path, zipOutputStream);
                            zipOutputStream.closeEntry();
                        } catch (IOException e) {
                            throw new RuntimeException("Error zipping file: " + path, e);
                        }
                    });
        }
    }

    private void deleteDirectory(Path directory) {
        try {
            if (Files.exists(directory)) {
                Files.walk(directory)
                        .sorted((p1, p2) -> -p1.compareTo(p2)) // Reverse order to delete files before directories
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                log.warn("Failed to delete: {}", path, e);
                            }
                        });
            }
        } catch (IOException e) {
            log.warn("Failed to delete directory: {}", directory, e);
        }
    }
}
