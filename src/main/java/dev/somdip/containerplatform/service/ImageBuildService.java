package dev.somdip.containerplatform.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.ecr.EcrClient;
import software.amazon.awssdk.services.ecr.model.*;
import software.amazon.awssdk.services.codebuild.CodeBuildClient;
import software.amazon.awssdk.services.codebuild.model.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Service for building Docker images from source code.
 * This service can use either AWS CodeBuild or local Docker builds.
 */
@Service
public class ImageBuildService {
    private static final Logger log = LoggerFactory.getLogger(ImageBuildService.class);

    private final EcrClient ecrClient;
    private final CodeBuildClient codeBuildClient;

    @Value("${aws.region}")
    private String awsRegion;

    @Value("${aws.account-id}")
    private String awsAccountId;

    @Value("${aws.ecr.repository-prefix:somdip}")
    private String ecrRepositoryPrefix;

    public ImageBuildService(EcrClient ecrClient, CodeBuildClient codeBuildClient) {
        this.ecrClient = ecrClient;
        this.codeBuildClient = codeBuildClient;
    }

    public static class BuildResult {
        private String buildId;
        private String repositoryUri;
        private String imageTag;
        private BuildStatus status;
        private String message;

        public String getBuildId() {
            return buildId;
        }

        public void setBuildId(String buildId) {
            this.buildId = buildId;
        }

        public String getRepositoryUri() {
            return repositoryUri;
        }

        public void setRepositoryUri(String repositoryUri) {
            this.repositoryUri = repositoryUri;
        }

        public String getImageTag() {
            return imageTag;
        }

        public void setImageTag(String imageTag) {
            this.imageTag = imageTag;
        }

        public BuildStatus getStatus() {
            return status;
        }

        public void setStatus(BuildStatus status) {
            this.status = status;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }

    public enum BuildStatus {
        PENDING, IN_PROGRESS, SUCCEEDED, FAILED, STOPPED
    }

    /**
     * Create or get ECR repository for the container
     */
    public String ensureEcrRepository(String containerName) {
        String repositoryName = ecrRepositoryPrefix + "/" + containerName.toLowerCase();

        try {
            // Check if repository exists
            DescribeRepositoriesRequest describeRequest = DescribeRepositoriesRequest.builder()
                    .repositoryNames(repositoryName)
                    .build();

            DescribeRepositoriesResponse response = ecrClient.describeRepositories(describeRequest);

            if (!response.repositories().isEmpty()) {
                String uri = response.repositories().get(0).repositoryUri();
                log.info("ECR repository already exists: {}", uri);
                return uri;
            }
        } catch (RepositoryNotFoundException e) {
            // Repository doesn't exist, create it
            log.info("Creating new ECR repository: {}", repositoryName);
        }

        // Create repository
        CreateRepositoryRequest createRequest = CreateRepositoryRequest.builder()
                .repositoryName(repositoryName)
                .imageScanningConfiguration(ImageScanningConfiguration.builder()
                        .scanOnPush(true)
                        .build())
                .imageTagMutability(ImageTagMutability.MUTABLE)
                .build();

        CreateRepositoryResponse createResponse = ecrClient.createRepository(createRequest);
        String uri = createResponse.repository().repositoryUri();
        log.info("Created ECR repository: {}", uri);

        return uri;
    }

    /**
     * Trigger CodeBuild project to build Docker image from S3 source
     */
    public BuildResult triggerCodeBuild(String projectName, String s3SourceKey,
                                       String containerName, String imageTag) {
        BuildResult result = new BuildResult();
        result.setImageTag(imageTag);

        try {
            // Ensure ECR repository exists
            String repositoryUri = ensureEcrRepository(containerName);
            result.setRepositoryUri(repositoryUri);

            // Start build
            Map<String, String> environmentVariables = new HashMap<>();
            environmentVariables.put("IMAGE_REPO_NAME", repositoryUri);
            environmentVariables.put("IMAGE_TAG", imageTag);
            environmentVariables.put("AWS_DEFAULT_REGION", awsRegion);
            environmentVariables.put("AWS_ACCOUNT_ID", awsAccountId);

            StartBuildRequest buildRequest = StartBuildRequest.builder()
                    .projectName(projectName)
                    .sourceLocationOverride("s3://" + s3SourceKey)
                    .environmentVariablesOverride(
                            environmentVariables.entrySet().stream()
                                    .map(e -> EnvironmentVariable.builder()
                                            .name(e.getKey())
                                            .value(e.getValue())
                                            .type(EnvironmentVariableType.PLAINTEXT)
                                            .build())
                                    .toList()
                    )
                    .build();

            StartBuildResponse buildResponse = codeBuildClient.startBuild(buildRequest);
            Build build = buildResponse.build();

            result.setBuildId(build.id());
            result.setStatus(convertBuildStatus(build.buildStatus()));
            result.setMessage("Build started successfully");

            log.info("CodeBuild started - Build ID: {}, Status: {}", build.id(), build.buildStatus());

        } catch (Exception e) {
            log.error("Failed to trigger CodeBuild", e);
            result.setStatus(BuildStatus.FAILED);
            result.setMessage("Failed to start build: " + e.getMessage());
        }

        return result;
    }

    /**
     * Check build status
     */
    public BuildResult checkBuildStatus(String buildId) {
        BuildResult result = new BuildResult();
        result.setBuildId(buildId);

        try {
            BatchGetBuildsRequest request = BatchGetBuildsRequest.builder()
                    .ids(buildId)
                    .build();

            BatchGetBuildsResponse response = codeBuildClient.batchGetBuilds(request);

            if (!response.builds().isEmpty()) {
                Build build = response.builds().get(0);
                result.setStatus(convertBuildStatus(build.buildStatus()));

                if (build.buildStatus() == software.amazon.awssdk.services.codebuild.model.StatusType.SUCCEEDED) {
                    result.setMessage("Build completed successfully");
                } else if (build.buildStatus() == software.amazon.awssdk.services.codebuild.model.StatusType.FAILED) {
                    result.setMessage("Build failed: " + build.buildComplete());
                } else {
                    result.setMessage("Build in progress");
                }
            }
        } catch (Exception e) {
            log.error("Failed to check build status", e);
            result.setStatus(BuildStatus.FAILED);
            result.setMessage("Failed to check status: " + e.getMessage());
        }

        return result;
    }

    /**
     * Get full image URI for deployment
     */
    public String getImageUri(String containerName, String imageTag) {
        String repositoryName = ecrRepositoryPrefix + "/" + containerName.toLowerCase();
        return String.format("%s.dkr.ecr.%s.amazonaws.com/%s:%s",
                awsAccountId, awsRegion, repositoryName, imageTag);
    }

    private BuildStatus convertBuildStatus(software.amazon.awssdk.services.codebuild.model.StatusType status) {
        return switch (status) {
            case IN_PROGRESS -> BuildStatus.IN_PROGRESS;
            case SUCCEEDED -> BuildStatus.SUCCEEDED;
            case FAILED, FAULT, TIMED_OUT -> BuildStatus.FAILED;
            case STOPPED -> BuildStatus.STOPPED;
            default -> BuildStatus.PENDING;
        };
    }

    /**
     * Delete ECR repository
     */
    public void deleteEcrRepository(String containerName) {
        String repositoryName = ecrRepositoryPrefix + "/" + containerName.toLowerCase();

        try {
            DeleteRepositoryRequest deleteRequest = DeleteRepositoryRequest.builder()
                    .repositoryName(repositoryName)
                    .force(true) // Force delete even if it contains images
                    .build();

            ecrClient.deleteRepository(deleteRequest);
            log.info("Deleted ECR repository: {}", repositoryName);
        } catch (RepositoryNotFoundException e) {
            log.warn("Repository not found for deletion: {}", repositoryName);
        } catch (Exception e) {
            log.error("Failed to delete ECR repository: {}", repositoryName, e);
        }
    }
}
