package dev.somdip.containerplatform.service;

import dev.somdip.containerplatform.model.Container;
import dev.somdip.containerplatform.model.Deployment;
import dev.somdip.containerplatform.model.LinkedRepository;
import dev.somdip.containerplatform.repository.ContainerRepository;
import dev.somdip.containerplatform.repository.DeploymentRepository;
import dev.somdip.containerplatform.repository.LinkedRepositoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * Service for managing GitHub repository builds.
 * Delegates async build execution to AsyncBuildExecutor to properly
 * leverage Spring's @Async proxy mechanism.
 */
@Service
public class GitHubBuildService {
    private static final Logger log = LoggerFactory.getLogger(GitHubBuildService.class);

    private final ContainerRepository containerRepository;
    private final DeploymentRepository deploymentRepository;
    private final LinkedRepositoryRepository linkedRepoRepository;
    private final AsyncBuildExecutor asyncBuildExecutor;

    public GitHubBuildService(ContainerRepository containerRepository,
                              DeploymentRepository deploymentRepository,
                              LinkedRepositoryRepository linkedRepoRepository,
                              AsyncBuildExecutor asyncBuildExecutor) {
        this.containerRepository = containerRepository;
        this.deploymentRepository = deploymentRepository;
        this.linkedRepoRepository = linkedRepoRepository;
        this.asyncBuildExecutor = asyncBuildExecutor;
    }

    /**
     * Trigger build from GitHub webhook push.
     * Creates deployment record and delegates to AsyncBuildExecutor.
     */
    public void triggerBuild(LinkedRepository linkedRepo, String commitSha,
                             String commitMessage, String pusherName) {
        log.info("Triggering build for repo: {}, commit: {}",
            linkedRepo.getRepoFullName(), commitSha.substring(0, Math.min(7, commitSha.length())));

        // Get container
        Container container = containerRepository.findById(linkedRepo.getContainerId())
            .orElseThrow(() -> new IllegalStateException("Container not found: " + linkedRepo.getContainerId()));

        // Create deployment record
        Deployment deployment = createDeployment(container, linkedRepo, commitSha, commitMessage, pusherName);

        // Delegate to AsyncBuildExecutor - this properly runs async via Spring proxy
        asyncBuildExecutor.executeBuildFromWebhook(
            linkedRepo.getRepoLinkId(),
            deployment.getDeploymentId(),
            commitSha,
            commitMessage,
            pusherName
        );

        log.info("Build triggered asynchronously for deployment: {}", deployment.getDeploymentId());
    }

    /**
     * Manual trigger build for a linked repository.
     * Returns immediately with deployment ID for frontend to track progress.
     */
    public Deployment triggerManualBuild(String repoLinkId, String userId) {
        LinkedRepository linkedRepo = linkedRepoRepository.findById(repoLinkId)
            .orElseThrow(() -> new IllegalStateException("Linked repository not found"));

        if (!linkedRepo.getUserId().equals(userId)) {
            throw new IllegalStateException("Unauthorized access to repository");
        }

        // Get latest commit from the deploy branch
        // For now, use a placeholder - in real implementation, fetch from GitHub API
        String commitSha = "manual-" + UUID.randomUUID().toString().substring(0, 8);

        // Get container to create deployment record first
        Container container = containerRepository.findById(linkedRepo.getContainerId())
            .orElseThrow(() -> new IllegalStateException("Container not found"));

        // Create deployment before triggering async build so we can return deploymentId
        Deployment deployment = createDeployment(container, linkedRepo, commitSha, "Manual deployment triggered", userId);

        // Delegate to AsyncBuildExecutor - this properly runs async via Spring proxy
        // The method returns immediately, build runs in background
        asyncBuildExecutor.executeBuildAsync(
            linkedRepo.getRepoLinkId(),
            deployment.getDeploymentId(),
            commitSha,
            "Manual deployment triggered",
            userId
        );

        log.info("Manual build triggered asynchronously for deployment: {}", deployment.getDeploymentId());

        // Return immediately with deployment info - frontend can poll for status
        return deployment;
    }

    private Deployment createDeployment(Container container, LinkedRepository linkedRepo,
                                         String commitSha, String commitMessage, String triggeredBy) {
        Deployment deployment = new Deployment();
        deployment.setDeploymentId(UUID.randomUUID().toString());
        deployment.setContainerId(container.getContainerId());
        deployment.setContainerName(container.getName());
        deployment.setUserId(linkedRepo.getUserId());
        deployment.setStatus(Deployment.DeploymentStatus.PENDING);
        deployment.setType(Deployment.DeploymentType.UPDATE);
        deployment.setInitiatedBy(triggeredBy);
        deployment.setCreatedAt(Instant.now());
        deployment.setPreviousImage(container.getImage());

        // Store GitHub-specific info in metadata
        Map<String, String> metadata = new HashMap<>();
        metadata.put("trigger", "GITHUB_PUSH");
        metadata.put("commitSha", commitSha);
        if (commitMessage != null) {
            metadata.put("commitMessage", commitMessage.substring(0, Math.min(200, commitMessage.length())));
        }
        deployment.setMetadata(metadata);

        return deploymentRepository.save(deployment);
    }
}
