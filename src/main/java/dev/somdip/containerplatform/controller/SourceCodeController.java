package dev.somdip.containerplatform.controller;

import dev.somdip.containerplatform.dto.container.CreateContainerRequest;
import dev.somdip.containerplatform.dto.container.DeployContainerResponse;
import dev.somdip.containerplatform.model.Container;
import dev.somdip.containerplatform.service.ContainerService;
import dev.somdip.containerplatform.service.ProjectAnalyzer;
import dev.somdip.containerplatform.service.SourceCodeDeploymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/source")
@CrossOrigin(origins = "*", maxAge = 3600)
public class SourceCodeController {
    private static final Logger log = LoggerFactory.getLogger(SourceCodeController.class);

    private final SourceCodeDeploymentService sourceCodeDeploymentService;
    private final ContainerService containerService;

    public SourceCodeController(SourceCodeDeploymentService sourceCodeDeploymentService,
                               ContainerService containerService) {
        this.sourceCodeDeploymentService = sourceCodeDeploymentService;
        this.containerService = containerService;
    }

    @PostMapping("/deploy")
    public ResponseEntity<?> deployFromSource(
            @RequestParam("file") MultipartFile file,
            @RequestParam("containerName") String containerName,
            @RequestParam(value = "cpu", defaultValue = "256") Integer cpu,
            @RequestParam(value = "memory", defaultValue = "512") Integer memory,
            @RequestParam(value = "subdomain", required = false) String subdomain,
            Authentication authentication) {

        try {
            String userId = authentication.getName();
            log.info("Deploying from source for user: {}, container: {}", userId, containerName);

            // Validate file
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "File is empty"));
            }

            // Only accept ZIP files
            String filename = file.getOriginalFilename();
            if (filename == null || !filename.toLowerCase().endsWith(".zip")) {
                return ResponseEntity.badRequest().body(Map.of("error", "Only ZIP files are accepted"));
            }

            // Deploy from source
            SourceCodeDeploymentService.DeploymentResult result =
                    sourceCodeDeploymentService.deployFromSource(file, containerName, userId);

            log.info("Project analyzed - Type: {}, Dockerfile generated: {}",
                    result.getProjectType().getDisplayName(),
                    result.isDockerfileGenerated());

            // Create response with project info
            Map<String, Object> response = new HashMap<>();
            response.put("projectId", result.getProjectId());
            response.put("projectType", result.getProjectType().name());
            response.put("projectTypeDisplay", result.getProjectType().getDisplayName());
            response.put("dockerfileGenerated", result.isDockerfileGenerated());
            response.put("s3Key", result.getS3Key());
            response.put("message", "Project analyzed successfully. Building Docker image...");

            // Note: Actual container creation and image building would happen via CodeBuild
            // This is a placeholder for the next steps
            response.put("status", "ANALYZING_COMPLETE");
            response.put("nextStep", "IMAGE_BUILD_PENDING");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error deploying from source", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Deployment failed: " + e.getMessage()));
        }
    }

    @PostMapping("/analyze")
    public ResponseEntity<?> analyzeProject(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {

        try {
            String userId = authentication.getName();
            log.info("Analyzing project for user: {}", userId);

            // Validate file
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "File is empty"));
            }

            String filename = file.getOriginalFilename();
            if (filename == null || !filename.toLowerCase().endsWith(".zip")) {
                return ResponseEntity.badRequest().body(Map.of("error", "Only ZIP files are accepted"));
            }

            // Analyze without deploying
            SourceCodeDeploymentService.DeploymentResult result =
                    sourceCodeDeploymentService.deployFromSource(file, "temp-analysis", userId);

            Map<String, Object> response = new HashMap<>();
            response.put("projectType", result.getProjectType().name());
            response.put("projectTypeDisplay", result.getProjectType().getDisplayName());
            response.put("dockerfileExists", !result.isDockerfileGenerated());
            response.put("generatedDockerfile", result.getDockerfileContent());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error analyzing project", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Analysis failed: " + e.getMessage()));
        }
    }

    @GetMapping("/supported-types")
    public ResponseEntity<?> getSupportedProjectTypes() {
        Map<String, String> supportedTypes = new HashMap<>();

        for (ProjectAnalyzer.ProjectType type : ProjectAnalyzer.ProjectType.values()) {
            if (type != ProjectAnalyzer.ProjectType.UNKNOWN) {
                supportedTypes.put(type.name(), type.getDisplayName());
            }
        }

        return ResponseEntity.ok(Map.of(
                "supportedTypes", supportedTypes,
                "message", "These project types are automatically detected and configured"
        ));
    }
}
