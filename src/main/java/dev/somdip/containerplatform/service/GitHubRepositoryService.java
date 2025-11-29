package dev.somdip.containerplatform.service;

import dev.somdip.containerplatform.dto.github.GitHubRepoDTO;
import dev.somdip.containerplatform.dto.github.LinkRepoRequest;
import dev.somdip.containerplatform.model.Container;
import dev.somdip.containerplatform.model.LinkedRepository;
import dev.somdip.containerplatform.repository.ContainerRepository;
import dev.somdip.containerplatform.repository.LinkedRepositoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class GitHubRepositoryService {
    private static final Logger log = LoggerFactory.getLogger(GitHubRepositoryService.class);

    private final GitHubApiService gitHubApiService;
    private final GitHubOAuthService oAuthService;
    private final LinkedRepositoryRepository linkedRepoRepository;
    private final ContainerRepository containerRepository;

    public GitHubRepositoryService(GitHubApiService gitHubApiService,
                                    GitHubOAuthService oAuthService,
                                    LinkedRepositoryRepository linkedRepoRepository,
                                    ContainerRepository containerRepository) {
        this.gitHubApiService = gitHubApiService;
        this.oAuthService = oAuthService;
        this.linkedRepoRepository = linkedRepoRepository;
        this.containerRepository = containerRepository;
    }

    /**
     * Link a GitHub repository to a container
     */
    public LinkedRepository linkRepository(String userId, LinkRepoRequest request) {
        // Verify GitHub is connected
        if (!oAuthService.isConnected(userId)) {
            throw new IllegalStateException("GitHub not connected. Please connect your GitHub account first.");
        }

        // Verify container exists and belongs to user
        Container container = containerRepository.findById(request.getContainerId())
            .orElseThrow(() -> new IllegalArgumentException("Container not found"));

        if (!container.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Container does not belong to user");
        }

        // Check if container already has a linked repo
        Optional<LinkedRepository> existing = linkedRepoRepository.findByContainerId(request.getContainerId());
        if (existing.isPresent()) {
            throw new IllegalStateException("Container already has a linked repository. Unlink it first.");
        }

        // Parse repo full name
        String[] parts = request.getRepoFullName().split("/");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid repository format. Expected: owner/repo");
        }
        String owner = parts[0];
        String repoName = parts[1];

        // Verify user has access to the repository
        GitHubRepoDTO repoInfo;
        try {
            repoInfo = gitHubApiService.getRepository(userId, owner, repoName);
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot access repository. Make sure you have permission.");
        }

        // Generate webhook secret
        String webhookSecret = generateWebhookSecret();

        // Create linked repository record
        LinkedRepository linkedRepo = new LinkedRepository();
        linkedRepo.setRepoLinkId(UUID.randomUUID().toString());
        linkedRepo.setUserId(userId);
        linkedRepo.setContainerId(request.getContainerId());
        linkedRepo.setConnectionId(oAuthService.getConnection(userId)
            .map(c -> c.getConnectionId()).orElse(null));

        linkedRepo.setRepoId(repoInfo.getId());
        linkedRepo.setRepoFullName(repoInfo.getFullName());
        linkedRepo.setRepoOwner(owner);
        linkedRepo.setRepoName(repoName);
        linkedRepo.setDefaultBranch(repoInfo.getDefaultBranch());
        linkedRepo.setDeployBranch(request.getDeployBranch() != null ?
            request.getDeployBranch() : repoInfo.getDefaultBranch());
        linkedRepo.setIsPrivate(repoInfo.getIsPrivate());
        linkedRepo.setRepoUrl(repoInfo.getHtmlUrl());
        linkedRepo.setCloneUrl(repoInfo.getCloneUrl());

        linkedRepo.setRootDirectory(request.getRootDirectory() != null ?
            request.getRootDirectory() : "/");
        linkedRepo.setDockerfilePath(request.getDockerfilePath() != null ?
            request.getDockerfilePath() : "Dockerfile");
        linkedRepo.setAutoDeploy(request.getAutoDeploy() != null ?
            request.getAutoDeploy() : true);
        linkedRepo.setBuildEnvVars(request.getBuildEnvVars());

        linkedRepo.setWebhookSecret(webhookSecret);
        linkedRepo.setWebhookStatus(LinkedRepository.WebhookStatus.PENDING);
        linkedRepo.setStatus(LinkedRepository.LinkStatus.PENDING_WEBHOOK);
        linkedRepo.setCreatedAt(Instant.now());
        linkedRepo.setUpdatedAt(Instant.now());

        // Save first to get the ID
        linkedRepoRepository.save(linkedRepo);

        // Try to create webhook
        try {
            String webhookId = gitHubApiService.createWebhook(userId, owner, repoName, webhookSecret);
            linkedRepo.setWebhookId(webhookId);
            linkedRepo.setWebhookStatus(LinkedRepository.WebhookStatus.ACTIVE);
            linkedRepo.setStatus(LinkedRepository.LinkStatus.ACTIVE);
            log.info("Webhook created for {}: {}", repoInfo.getFullName(), webhookId);
        } catch (Exception e) {
            log.warn("Failed to create webhook for {}: {}", repoInfo.getFullName(), e.getMessage());
            linkedRepo.setWebhookStatus(LinkedRepository.WebhookStatus.FAILED);
            linkedRepo.setStatus(LinkedRepository.LinkStatus.WEBHOOK_ERROR);
            linkedRepo.setLastError("Failed to create webhook: " + e.getMessage());
        }

        linkedRepoRepository.save(linkedRepo);

        log.info("Linked repository {} to container {} for user {}",
            repoInfo.getFullName(), request.getContainerId(), userId);

        return linkedRepo;
    }

    /**
     * Unlink a repository from a container
     */
    public void unlinkRepository(String userId, String repoLinkId) {
        LinkedRepository linkedRepo = linkedRepoRepository.findById(repoLinkId)
            .orElseThrow(() -> new IllegalArgumentException("Linked repository not found"));

        if (!linkedRepo.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Linked repository does not belong to user");
        }

        // Try to delete webhook
        if (linkedRepo.getWebhookId() != null) {
            try {
                gitHubApiService.deleteWebhook(userId,
                    linkedRepo.getRepoOwner(),
                    linkedRepo.getRepoName(),
                    linkedRepo.getWebhookId());
                log.info("Deleted webhook {} from {}", linkedRepo.getWebhookId(), linkedRepo.getRepoFullName());
            } catch (Exception e) {
                log.warn("Failed to delete webhook: {}", e.getMessage());
            }
        }

        // Delete linked repo record
        linkedRepoRepository.delete(repoLinkId);

        log.info("Unlinked repository {} from container {}",
            linkedRepo.getRepoFullName(), linkedRepo.getContainerId());
    }

    /**
     * Get linked repository for a container
     */
    public Optional<LinkedRepository> getLinkedRepository(String containerId) {
        return linkedRepoRepository.findByContainerId(containerId);
    }

    /**
     * Get linked repository by repoLinkId
     */
    public Optional<LinkedRepository> getLinkedRepositoryById(String repoLinkId) {
        return linkedRepoRepository.findById(repoLinkId);
    }

    /**
     * Get all linked repositories for a user
     */
    public List<LinkedRepository> getUserLinkedRepositories(String userId) {
        return linkedRepoRepository.findByUserId(userId);
    }

    /**
     * Update linked repository settings
     */
    public LinkedRepository updateSettings(String userId, String repoLinkId,
                                            String deployBranch, Boolean autoDeploy,
                                            String rootDirectory, String dockerfilePath) {
        LinkedRepository linkedRepo = linkedRepoRepository.findById(repoLinkId)
            .orElseThrow(() -> new IllegalArgumentException("Linked repository not found"));

        if (!linkedRepo.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Linked repository does not belong to user");
        }

        if (deployBranch != null) {
            linkedRepo.setDeployBranch(deployBranch);
        }
        if (autoDeploy != null) {
            linkedRepo.setAutoDeploy(autoDeploy);
        }
        if (rootDirectory != null) {
            linkedRepo.setRootDirectory(rootDirectory);
        }
        if (dockerfilePath != null) {
            linkedRepo.setDockerfilePath(dockerfilePath);
        }

        linkedRepo.setUpdatedAt(Instant.now());
        linkedRepoRepository.save(linkedRepo);

        return linkedRepo;
    }

    /**
     * Retry webhook creation for a linked repository
     */
    public LinkedRepository retryWebhook(String userId, String repoLinkId) {
        LinkedRepository linkedRepo = linkedRepoRepository.findById(repoLinkId)
            .orElseThrow(() -> new IllegalArgumentException("Linked repository not found"));

        if (!linkedRepo.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Linked repository does not belong to user");
        }

        // Generate new webhook secret
        String webhookSecret = generateWebhookSecret();
        linkedRepo.setWebhookSecret(webhookSecret);

        try {
            String webhookId = gitHubApiService.createWebhook(userId,
                linkedRepo.getRepoOwner(),
                linkedRepo.getRepoName(),
                webhookSecret);

            linkedRepo.setWebhookId(webhookId);
            linkedRepo.setWebhookStatus(LinkedRepository.WebhookStatus.ACTIVE);
            linkedRepo.setStatus(LinkedRepository.LinkStatus.ACTIVE);
            linkedRepo.setLastError(null);

            log.info("Webhook created for {}: {}", linkedRepo.getRepoFullName(), webhookId);

        } catch (Exception e) {
            log.warn("Failed to create webhook for {}: {}", linkedRepo.getRepoFullName(), e.getMessage());
            linkedRepo.setWebhookStatus(LinkedRepository.WebhookStatus.FAILED);
            linkedRepo.setStatus(LinkedRepository.LinkStatus.WEBHOOK_ERROR);
            linkedRepo.setLastError("Failed to create webhook: " + e.getMessage());
        }

        linkedRepo.setUpdatedAt(Instant.now());
        linkedRepoRepository.save(linkedRepo);

        return linkedRepo;
    }

    private String generateWebhookSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
