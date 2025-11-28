package dev.somdip.containerplatform.service;

import dev.somdip.containerplatform.model.Container;
import dev.somdip.containerplatform.model.Deployment;
import dev.somdip.containerplatform.model.LinkedRepository;
import dev.somdip.containerplatform.repository.ContainerRepository;
import dev.somdip.containerplatform.repository.DeploymentRepository;
import dev.somdip.containerplatform.repository.LinkedRepositoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.codebuild.CodeBuildClient;
import software.amazon.awssdk.services.codebuild.model.*;

import java.time.Instant;
import java.util.*;

/**
 * Separate service for async build execution.
 * This fixes the Spring @Async self-invocation issue where calling
 * @Async methods from within the same class bypasses the proxy.
 */
@Service
public class AsyncBuildExecutor {
    private static final Logger log = LoggerFactory.getLogger(AsyncBuildExecutor.class);

    private final CodeBuildClient codeBuildClient;
    private final ContainerRepository containerRepository;
    private final DeploymentRepository deploymentRepository;
    private final LinkedRepositoryRepository linkedRepoRepository;
    private final EcsService ecsService;
    private final GitHubOAuthService oAuthService;
    private final DeploymentLogStreamService logStreamService;

    @Value("${aws.codebuild.project-name:github-container-build}")
    private String codeBuildProjectName;

    @Value("${aws.ecr.registry}")
    private String ecrRegistry;

    @Value("${aws.ecr.repository-prefix:somdip}")
    private String ecrRepositoryPrefix;

    public AsyncBuildExecutor(CodeBuildClient codeBuildClient,
                              ContainerRepository containerRepository,
                              DeploymentRepository deploymentRepository,
                              LinkedRepositoryRepository linkedRepoRepository,
                              EcsService ecsService,
                              GitHubOAuthService oAuthService,
                              DeploymentLogStreamService logStreamService) {
        this.codeBuildClient = codeBuildClient;
        this.containerRepository = containerRepository;
        this.deploymentRepository = deploymentRepository;
        this.linkedRepoRepository = linkedRepoRepository;
        this.ecsService = ecsService;
        this.oAuthService = oAuthService;
        this.logStreamService = logStreamService;
    }

    /**
     * Execute build asynchronously.
     * This method is called from GitHubBuildService through Spring's proxy,
     * ensuring @Async works correctly.
     */
    @Async
    public void executeBuildAsync(String repoLinkId, String deploymentId, String commitSha,
                                   String commitMessage, String triggeredBy) {
        log.info("Starting async build execution for deployment: {}", deploymentId);

        LinkedRepository linkedRepo = linkedRepoRepository.findById(repoLinkId)
            .orElseThrow(() -> new IllegalStateException("Linked repository not found: " + repoLinkId));

        Deployment deployment = deploymentRepository.findById(deploymentId)
            .orElseThrow(() -> new IllegalStateException("Deployment not found: " + deploymentId));

        Container container = containerRepository.findById(linkedRepo.getContainerId())
            .orElseThrow(() -> new IllegalStateException("Container not found: " + linkedRepo.getContainerId()));

        // Publish initial step
        logStreamService.publishStep(deploymentId, "INITIALIZING", "IN_PROGRESS",
            "Initializing build for " + linkedRepo.getRepoFullName());

        try {
            // Get GitHub access token for cloning private repos
            logStreamService.publishStep(deploymentId, "AUTHENTICATING", "IN_PROGRESS",
                "Authenticating with GitHub...");

            String accessToken = oAuthService.getAccessToken(linkedRepo.getUserId());

            logStreamService.publishStep(deploymentId, "AUTHENTICATING", "COMPLETED",
                "GitHub authentication successful");

            // Build environment variables
            List<EnvironmentVariable> envVars = buildEnvironmentVariables(
                linkedRepo, container, commitSha, accessToken);

            // Start CodeBuild
            logStreamService.publishStep(deploymentId, "STARTING_BUILD", "IN_PROGRESS",
                "Starting AWS CodeBuild job...");

            StartBuildRequest buildRequest = StartBuildRequest.builder()
                .projectName(codeBuildProjectName)
                .environmentVariablesOverride(envVars)
                .build();

            StartBuildResponse buildResponse = codeBuildClient.startBuild(buildRequest);
            String buildId = buildResponse.build().id();

            log.info("CodeBuild started: buildId={}", buildId);

            logStreamService.publishStep(deploymentId, "STARTING_BUILD", "COMPLETED",
                "CodeBuild job started successfully");

            // Update deployment with build ID (stored in metadata)
            Map<String, String> metadata = deployment.getMetadata();
            if (metadata == null) {
                metadata = new HashMap<>();
            }
            metadata.put("buildId", buildId);
            deployment.setMetadata(metadata);
            deployment.setStatus(Deployment.DeploymentStatus.IN_PROGRESS);
            deployment.setStartedAt(Instant.now());
            deploymentRepository.save(deployment);

            // Update linked repo with last deployed info
            linkedRepo.setLastDeployedAt(Instant.now());
            linkedRepo.setLastDeployedCommit(commitSha);
            linkedRepoRepository.save(linkedRepo);

            // Poll for build completion
            pollBuildStatus(buildId, deployment, container, linkedRepo);

        } catch (Exception e) {
            log.error("Build failed for {}", linkedRepo.getRepoFullName(), e);

            logStreamService.publishStep(deploymentId, "ERROR", "FAILED",
                "Build failed: " + e.getMessage());
            logStreamService.publishStatus(deploymentId, Deployment.DeploymentStatus.FAILED,
                e.getMessage());

            deployment.setStatus(Deployment.DeploymentStatus.FAILED);
            deployment.setErrorMessage(e.getMessage());
            deployment.setCompletedAt(Instant.now());
            deploymentRepository.save(deployment);

            linkedRepo.setLastError(e.getMessage());
            linkedRepoRepository.save(linkedRepo);
        }
    }

    /**
     * Execute build asynchronously from GitHub webhook push.
     */
    @Async
    public void executeBuildFromWebhook(String repoLinkId, String deploymentId, String commitSha,
                                         String commitMessage, String pusherName) {
        executeBuildAsync(repoLinkId, deploymentId, commitSha, commitMessage, pusherName);
    }

    private List<EnvironmentVariable> buildEnvironmentVariables(LinkedRepository linkedRepo,
                                                                  Container container,
                                                                  String commitSha,
                                                                  String accessToken) {
        List<EnvironmentVariable> envVars = new ArrayList<>();

        // Repository info
        envVars.add(EnvironmentVariable.builder()
            .name("GITHUB_REPO")
            .value(linkedRepo.getRepoFullName())
            .type(EnvironmentVariableType.PLAINTEXT)
            .build());

        envVars.add(EnvironmentVariable.builder()
            .name("GITHUB_BRANCH")
            .value(linkedRepo.getDeployBranch())
            .type(EnvironmentVariableType.PLAINTEXT)
            .build());

        envVars.add(EnvironmentVariable.builder()
            .name("COMMIT_SHA")
            .value(commitSha)
            .type(EnvironmentVariableType.PLAINTEXT)
            .build());

        // GitHub token for private repos
        envVars.add(EnvironmentVariable.builder()
            .name("GITHUB_TOKEN")
            .value(accessToken)
            .type(EnvironmentVariableType.PLAINTEXT)
            .build());

        // ECR info
        String imageTag = commitSha.substring(0, Math.min(7, commitSha.length()));
        String ecrRepo = ecrRepositoryPrefix + "/user-" + container.getUserId();
        String fullImageUri = ecrRegistry + "/" + ecrRepo + ":" + imageTag;

        envVars.add(EnvironmentVariable.builder()
            .name("ECR_REGISTRY")
            .value(ecrRegistry)
            .type(EnvironmentVariableType.PLAINTEXT)
            .build());

        envVars.add(EnvironmentVariable.builder()
            .name("ECR_REPOSITORY")
            .value(ecrRepo)
            .type(EnvironmentVariableType.PLAINTEXT)
            .build());

        envVars.add(EnvironmentVariable.builder()
            .name("IMAGE_TAG")
            .value(imageTag)
            .type(EnvironmentVariableType.PLAINTEXT)
            .build());

        envVars.add(EnvironmentVariable.builder()
            .name("IMAGE_URI")
            .value(fullImageUri)
            .type(EnvironmentVariableType.PLAINTEXT)
            .build());

        // Build configuration
        String rootDir = linkedRepo.getRootDirectory();
        if (rootDir != null && !rootDir.isEmpty() && !rootDir.equals("/")) {
            envVars.add(EnvironmentVariable.builder()
                .name("ROOT_DIRECTORY")
                .value(rootDir)
                .type(EnvironmentVariableType.PLAINTEXT)
                .build());
        }

        String dockerfilePath = linkedRepo.getDockerfilePath();
        if (dockerfilePath != null && !dockerfilePath.isEmpty()) {
            envVars.add(EnvironmentVariable.builder()
                .name("DOCKERFILE_PATH")
                .value(dockerfilePath)
                .type(EnvironmentVariableType.PLAINTEXT)
                .build());
        }

        // Add build env vars from linked repo config
        Map<String, String> buildEnvVars = linkedRepo.getBuildEnvVars();
        if (buildEnvVars != null) {
            for (Map.Entry<String, String> entry : buildEnvVars.entrySet()) {
                envVars.add(EnvironmentVariable.builder()
                    .name(entry.getKey())
                    .value(entry.getValue())
                    .type(EnvironmentVariableType.PLAINTEXT)
                    .build());
            }
        }

        return envVars;
    }

    private void pollBuildStatus(String buildId, Deployment deployment,
                                  Container container, LinkedRepository linkedRepo) {
        int maxAttempts = 60; // 10 minutes max
        int attempt = 0;
        String lastPhase = "";
        String lastStepName = "";

        while (attempt < maxAttempts) {
            try {
                Thread.sleep(10000); // Poll every 10 seconds

                BatchGetBuildsRequest request = BatchGetBuildsRequest.builder()
                    .ids(buildId)
                    .build();

                BatchGetBuildsResponse response = codeBuildClient.batchGetBuilds(request);

                if (response.builds().isEmpty()) {
                    log.error("Build not found: {}", buildId);
                    break;
                }

                Build build = response.builds().get(0);
                StatusType status = build.buildStatus();
                String currentPhase = build.currentPhase();

                // Publish phase changes
                if (currentPhase != null && !currentPhase.equals(lastPhase)) {
                    // Mark the previous step as COMPLETED before starting new one
                    if (!lastStepName.isEmpty()) {
                        logStreamService.publishStep(deployment.getDeploymentId(), lastStepName, "COMPLETED",
                            getPhaseCompletedMessage(lastPhase));
                    }

                    lastPhase = currentPhase;
                    lastStepName = mapPhaseToStep(currentPhase);
                    String message = getPhaseMessage(currentPhase);
                    logStreamService.publishStep(deployment.getDeploymentId(), lastStepName, "IN_PROGRESS", message);
                }

                log.debug("Build {} status: {}, phase: {}", buildId, status, currentPhase);

                if (status == StatusType.SUCCEEDED) {
                    log.info("Build {} succeeded", buildId);
                    // Mark the last step as completed
                    if (!lastStepName.isEmpty()) {
                        logStreamService.publishStep(deployment.getDeploymentId(), lastStepName, "COMPLETED",
                            getPhaseCompletedMessage(lastPhase));
                    }
                    logStreamService.publishStep(deployment.getDeploymentId(), "BUILD_COMPLETE", "COMPLETED",
                        "Build completed successfully!");
                    handleBuildSuccess(deployment, container, linkedRepo);
                    return;
                } else if (status == StatusType.FAILED || status == StatusType.FAULT ||
                           status == StatusType.STOPPED || status == StatusType.TIMED_OUT) {
                    log.error("Build {} failed with status: {}", buildId, status);
                    // Mark the last step as failed
                    if (!lastStepName.isEmpty()) {
                        logStreamService.publishStep(deployment.getDeploymentId(), lastStepName, "FAILED",
                            "Failed at: " + lastPhase);
                    }
                    logStreamService.publishStep(deployment.getDeploymentId(), "BUILD_FAILED", "FAILED",
                        "Build failed: " + status);
                    handleBuildFailure(deployment, linkedRepo, "Build failed: " + status);
                    return;
                }

                // Still in progress
                attempt++;

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logStreamService.publishStep(deployment.getDeploymentId(), "ERROR", "FAILED",
                    "Build was interrupted");
                handleBuildFailure(deployment, linkedRepo, "Build interrupted");
                return;
            } catch (Exception e) {
                log.error("Error polling build status", e);
                attempt++;
            }
        }

        logStreamService.publishStep(deployment.getDeploymentId(), "TIMEOUT", "FAILED",
            "Build timed out after 10 minutes");
        handleBuildFailure(deployment, linkedRepo, "Build timed out");
    }

    private String getPhaseCompletedMessage(String phase) {
        if (phase == null) return "Step completed";
        switch (phase.toUpperCase()) {
            case "SUBMITTED":
                return "Build submitted";
            case "QUEUED":
                return "Build environment ready";
            case "PROVISIONING":
                return "Build environment provisioned";
            case "DOWNLOAD_SOURCE":
                return "Repository cloned successfully";
            case "INSTALL":
                return "Dependencies installed";
            case "PRE_BUILD":
                return "Pre-build commands completed";
            case "BUILD":
                return "Docker image built successfully";
            case "POST_BUILD":
                return "Post-build commands completed";
            case "UPLOAD_ARTIFACTS":
                return "Image pushed to registry";
            case "FINALIZING":
                return "Build finalized";
            default:
                return "Step completed: " + phase;
        }
    }

    private String mapPhaseToStep(String phase) {
        if (phase == null) return "INITIALIZING";
        switch (phase.toUpperCase()) {
            case "SUBMITTED":
            case "QUEUED":
                return "QUEUED";
            case "PROVISIONING":
                return "PROVISIONING";
            case "DOWNLOAD_SOURCE":
                return "CLONING";
            case "INSTALL":
                return "INSTALLING";
            case "PRE_BUILD":
                return "PRE_BUILD";
            case "BUILD":
                return "BUILDING";
            case "POST_BUILD":
                return "POST_BUILD";
            case "UPLOAD_ARTIFACTS":
                return "PUSHING_IMAGE";
            case "FINALIZING":
                return "FINALIZING";
            default:
                return phase;
        }
    }

    private String getPhaseMessage(String phase) {
        if (phase == null) return "Starting build...";
        switch (phase.toUpperCase()) {
            case "SUBMITTED":
                return "Build submitted to queue...";
            case "QUEUED":
                return "Waiting for build environment...";
            case "PROVISIONING":
                return "Provisioning build environment...";
            case "DOWNLOAD_SOURCE":
                return "Cloning repository from GitHub...";
            case "INSTALL":
                return "Installing dependencies...";
            case "PRE_BUILD":
                return "Running pre-build commands...";
            case "BUILD":
                return "Building Docker image...";
            case "POST_BUILD":
                return "Running post-build commands...";
            case "UPLOAD_ARTIFACTS":
                return "Pushing image to container registry...";
            case "FINALIZING":
                return "Finalizing build...";
            default:
                return "Processing: " + phase;
        }
    }

    private void handleBuildSuccess(Deployment deployment, Container container,
                                     LinkedRepository linkedRepo) {
        try {
            // Get commit SHA from metadata
            Map<String, String> metadata = deployment.getMetadata();
            String commitSha = metadata != null ? metadata.get("commitSha") : "unknown";

            // Get the built image URI - set image and imageTag separately for ECS compatibility
            String imageTag = commitSha.substring(0, Math.min(7, commitSha.length()));
            String ecrRepo = ecrRepositoryPrefix + "/user-" + container.getUserId();
            String imageBase = ecrRegistry + "/" + ecrRepo;
            String imageUri = imageBase + ":" + imageTag;

            // Update container with image (without tag) and imageTag separately
            container.setImage(imageBase);
            container.setImageTag(imageTag);
            containerRepository.save(container);

            // Deploy to ECS
            logStreamService.publishStep(deployment.getDeploymentId(), "DEPLOYING", "IN_PROGRESS",
                "Deploying to ECS cluster...");

            ecsService.deployContainer(container, container.getUserId());

            // Update container status to RUNNING after successful deployment
            container.setStatus(Container.ContainerStatus.RUNNING);
            containerRepository.save(container);
            log.info("Container {} status updated to RUNNING", container.getContainerId());

            logStreamService.publishStep(deployment.getDeploymentId(), "DEPLOYING", "COMPLETED",
                "Deployed to ECS successfully!");

            // Update deployment as successful
            deployment.setStatus(Deployment.DeploymentStatus.COMPLETED);
            deployment.setCompletedAt(Instant.now());
            deployment.setNewImage(imageUri);
            if (deployment.getStartedAt() != null) {
                deployment.setDurationMillis(Instant.now().toEpochMilli() - deployment.getStartedAt().toEpochMilli());
            }
            deploymentRepository.save(deployment);

            // Publish completion
            logStreamService.publishStatus(deployment.getDeploymentId(), Deployment.DeploymentStatus.COMPLETED,
                "Deployment completed successfully!");

            // Clear any previous error
            linkedRepo.setLastError(null);
            linkedRepoRepository.save(linkedRepo);

            log.info("Deployment successful for container: {}", container.getContainerId());

        } catch (Exception e) {
            log.error("Deployment failed after successful build", e);
            logStreamService.publishStep(deployment.getDeploymentId(), "DEPLOY_FAILED", "FAILED",
                "Deployment failed: " + e.getMessage());
            handleBuildFailure(deployment, linkedRepo, "Deployment failed: " + e.getMessage());
        }
    }

    private void handleBuildFailure(Deployment deployment, LinkedRepository linkedRepo, String error) {
        deployment.setStatus(Deployment.DeploymentStatus.FAILED);
        deployment.setErrorMessage(error);
        deployment.setCompletedAt(Instant.now());
        deploymentRepository.save(deployment);

        linkedRepo.setLastError(error);
        linkedRepoRepository.save(linkedRepo);

        logStreamService.publishStatus(deployment.getDeploymentId(), Deployment.DeploymentStatus.FAILED, error);
    }
}
