package dev.somdip.containerplatform.controller;

import dev.somdip.containerplatform.dto.container.CreateContainerRequest;
import dev.somdip.containerplatform.dto.container.DeployContainerResponse;
import dev.somdip.containerplatform.model.Container;
import dev.somdip.containerplatform.model.SourceDeployment;
import dev.somdip.containerplatform.model.User;
import dev.somdip.containerplatform.security.CustomUserDetails;
import dev.somdip.containerplatform.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/source")
@CrossOrigin(origins = "*", maxAge = 3600)
public class SourceCodeController {
    private static final Logger log = LoggerFactory.getLogger(SourceCodeController.class);

    private final SourceCodeDeploymentService sourceCodeDeploymentService;
    private final ContainerService containerService;
    private final SourceCodeBuildService buildService;
    private final SourceDeploymentTrackingService deploymentTrackingService;
    private final UsageTrackingService usageTrackingService;
    private final UserService userService;

    public SourceCodeController(SourceCodeDeploymentService sourceCodeDeploymentService,
                               ContainerService containerService,
                               SourceCodeBuildService buildService,
                               SourceDeploymentTrackingService deploymentTrackingService,
                               UsageTrackingService usageTrackingService,
                               UserService userService) {
        this.sourceCodeDeploymentService = sourceCodeDeploymentService;
        this.containerService = containerService;
        this.buildService = buildService;
        this.deploymentTrackingService = deploymentTrackingService;
        this.usageTrackingService = usageTrackingService;
        this.userService = userService;
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
            // Get the actual userId from CustomUserDetails
            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
            String userId = userDetails.getUserId();
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

            // Step 1: Analyze and upload source code to S3
            log.info("Step 1: Starting source code analysis for user: {}, container: {}", userId, containerName);
            SourceCodeDeploymentService.DeploymentResult result =
                    sourceCodeDeploymentService.deployFromSource(file, containerName, userId);

            log.info("Project analyzed - Type: {}, Dockerfile generated: {}",
                    result.getProjectType().getDisplayName(),
                    result.isDockerfileGenerated());

            // Step 2: Create deployment tracking record
            log.info("Step 2: Creating deployment tracking record");
            SourceDeployment deployment = deploymentTrackingService.createDeployment(
                userId, containerName, result.getProjectId(), result.getS3Key());
            log.info("Created deployment with ID: {}", deployment.getDeploymentId());

            // Step 3: Start CodeBuild job asynchronously
            CompletableFuture.runAsync(() -> {
                try {
                    log.info("Starting CodeBuild for deployment: {}", deployment.getDeploymentId());

                    SourceCodeBuildService.BuildResult buildResult = buildService.startBuild(
                        result.getProjectId(), userId, containerName, result.getS3Key());

                    // Update deployment with build info
                    deploymentTrackingService.updateBuildStarted(
                        deployment.getDeploymentId(),
                        buildResult.getBuildId(),
                        buildResult.getImageUri());

                    // Poll build status and update deployment
                    pollBuildStatus(deployment.getDeploymentId(), buildResult.getBuildId(),
                                  buildResult.getImageUri(), containerName, userId, cpu, memory);

                } catch (Exception e) {
                    log.error("Error in async build process", e);
                    deploymentTrackingService.markFailed(deployment.getDeploymentId(), e.getMessage());
                }
            });

            // Create response with deployment info
            Map<String, Object> response = new HashMap<>();
            response.put("deploymentId", deployment.getDeploymentId());
            response.put("projectId", result.getProjectId());
            response.put("projectType", result.getProjectType().name());
            response.put("projectTypeDisplay", result.getProjectType().getDisplayName());
            response.put("dockerfileGenerated", result.isDockerfileGenerated());
            response.put("message", "Project analyzed successfully. Building Docker image...");
            response.put("status", "ANALYZING");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error deploying from source", e);
            String errorMessage = e.getMessage();
            if (errorMessage == null || errorMessage.isEmpty()) {
                errorMessage = e.getClass().getSimpleName() + ": " + (e.getCause() != null ? e.getCause().getMessage() : "Unknown error");
            }
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Deployment failed: " + errorMessage));
        }
    }

    /**
     * Poll build status and create container when build completes
     */
    private void pollBuildStatus(String deploymentId, String buildId, String imageUri,
                                String containerName, String userId, Integer cpu, Integer memory) {
        int maxAttempts = 120; // 10 minutes max (poll every 5 seconds)
        int attempt = 0;

        while (attempt < maxAttempts) {
            try {
                Thread.sleep(5000); // Wait 5 seconds between polls

                SourceCodeBuildService.BuildStatus status = buildService.getBuildStatus(buildId);

                log.debug("Build status for {}: {} - {}", deploymentId, status.getStatus(), status.getPhase());

                // Update deployment status
                deploymentTrackingService.updateBuildStatus(deploymentId, status.getStatus(), status.getPhase());

                // Check if build completed
                if ("BUILD_COMPLETED".equals(status.getStatus())) {
                    log.info("Build completed successfully for deployment: {}", deploymentId);

                    // Use the full ECR image URI (e.g., 257394460825.dkr.ecr.us-east-1.amazonaws.com/somdip-app-nginx:latest)
                    // Extract repository and tag from full URI
                    String imageWithoutTag = imageUri.substring(0, imageUri.lastIndexOf(':'));
                    String imageTag = imageUri.substring(imageUri.lastIndexOf(':') + 1);

                    // For ECR images, we need the full registry path
                    String image = imageWithoutTag;

                    log.info("Using ECR image: {}:{}", image, imageTag);

                    // Create container (port will be auto-detected based on image type)
                    Container container = containerService.createContainer(
                        userId, containerName, image, imageTag, null);

                    deploymentTrackingService.updateContainerCreated(deploymentId, container.getContainerId());

                    // Check usage limits before deployment
                    User user = userService.findById(userId).orElseThrow();
                    if (!usageTrackingService.canStartContainer(user)) {
                        log.error("User {} cannot deploy container - FREE tier limit exceeded", userId);
                        deploymentTrackingService.markFailed(deploymentId,
                            "Cannot deploy: FREE tier limit exceeded. Please upgrade your plan or wait for hours to become available.");
                        break;
                    }

                    // Deploy the container
                    containerService.deployContainer(container.getContainerId());

                    // Mark deployment as completed
                    deploymentTrackingService.markCompleted(deploymentId);

                    log.info("Deployment completed successfully: {}", deploymentId);
                    break;

                } else if ("BUILD_FAILED".equals(status.getStatus())) {
                    String errorMsg = "Build failed: " + status.getPhase();
                    log.error("Build failed for deployment {}: {}", deploymentId, errorMsg);
                    deploymentTrackingService.markFailed(deploymentId, errorMsg);
                    break;
                }

                attempt++;

            } catch (InterruptedException e) {
                log.error("Build polling interrupted for deployment: {}", deploymentId);
                Thread.currentThread().interrupt();
                deploymentTrackingService.markFailed(deploymentId, "Build polling interrupted");
                break;
            } catch (Exception e) {
                log.error("Error polling build status for deployment: {}", deploymentId, e);
                deploymentTrackingService.markFailed(deploymentId, "Error: " + e.getMessage());
                break;
            }
        }

        if (attempt >= maxAttempts) {
            log.error("Build timeout for deployment: {}", deploymentId);
            deploymentTrackingService.markFailed(deploymentId, "Build timeout after 10 minutes");
        }
    }

    @PostMapping("/analyze")
    public ResponseEntity<?> analyzeProject(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {

        try {
            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
            String userId = userDetails.getUserId();
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

    /**
     * Get deployment status - used by frontend to poll progress
     */
    @GetMapping("/status/{deploymentId}")
    public ResponseEntity<?> getDeploymentStatus(
            @PathVariable String deploymentId,
            Authentication authentication) {
        try {
            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
            String userId = userDetails.getUserId();

            SourceDeployment deployment = deploymentTrackingService.getDeployment(deploymentId);

            if (deployment == null) {
                return ResponseEntity.notFound().build();
            }

            // Verify ownership
            if (!deployment.getUserId().equals(userId)) {
                return ResponseEntity.status(403).build();
            }

            Map<String, Object> response = new HashMap<>();
            response.put("deploymentId", deployment.getDeploymentId());
            response.put("status", deployment.getStatus().name());
            response.put("phase", deployment.getCurrentPhase());
            response.put("containerId", deployment.getContainerId());
            response.put("error", deployment.getErrorMessage());
            response.put("createdAt", deployment.getCreatedAt());
            response.put("updatedAt", deployment.getUpdatedAt());
            response.put("completedAt", deployment.getCompletedAt());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error getting deployment status: {}", deploymentId, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to get status: " + e.getMessage()));
        }
    }
}
