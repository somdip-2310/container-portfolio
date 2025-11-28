package dev.somdip.containerplatform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.somdip.containerplatform.model.LinkedRepository;
import dev.somdip.containerplatform.repository.LinkedRepositoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class GitHubWebhookService {
    private static final Logger log = LoggerFactory.getLogger(GitHubWebhookService.class);

    private final LinkedRepositoryRepository repoRepository;
    private final GitHubBuildService buildService;
    private final ObjectMapper objectMapper;

    public GitHubWebhookService(LinkedRepositoryRepository repoRepository,
                                GitHubBuildService buildService) {
        this.repoRepository = repoRepository;
        this.buildService = buildService;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Handle push event - trigger deployment
     */
    public void handlePushEvent(String payload) {
        try {
            JsonNode json = objectMapper.readTree(payload);

            String repoFullName = json.get("repository").get("full_name").asText();
            String ref = json.get("ref").asText();  // refs/heads/main
            String branch = ref.replace("refs/heads/", "");
            String commitSha = json.get("after").asText();

            // Skip if this is a delete event (all zeros)
            if (commitSha.matches("^0+$")) {
                log.debug("Skipping delete event for {}", repoFullName);
                return;
            }

            JsonNode headCommit = json.get("head_commit");
            String commitMessage = headCommit != null ? headCommit.get("message").asText() : "";
            String pusherName = json.get("pusher").get("name").asText();

            log.info("Push event: repo={}, branch={}, commit={}",
                repoFullName, branch, commitSha.substring(0, 7));

            // Find linked repository
            Optional<LinkedRepository> linkedRepo = repoRepository.findByRepoFullName(repoFullName);

            if (linkedRepo.isEmpty()) {
                log.debug("No linked repository found for: {}", repoFullName);
                return;
            }

            LinkedRepository repo = linkedRepo.get();

            // Check if this is the deploy branch
            if (!branch.equals(repo.getDeployBranch())) {
                log.debug("Push to non-deploy branch: {} (deploy branch: {})",
                    branch, repo.getDeployBranch());
                return;
            }

            // Check if auto-deploy is enabled
            if (repo.getAutoDeploy() == null || !repo.getAutoDeploy()) {
                log.debug("Auto-deploy disabled for: {}", repoFullName);
                return;
            }

            // Trigger build and deployment
            log.info("Triggering deployment for {} from commit {}",
                repoFullName, commitSha.substring(0, 7));

            buildService.triggerBuild(repo, commitSha, commitMessage, pusherName);

        } catch (Exception e) {
            log.error("Error handling push event", e);
            throw new RuntimeException("Failed to handle push event", e);
        }
    }

    /**
     * Handle pull request event (for preview deployments - optional)
     */
    public void handlePullRequestEvent(String payload) {
        try {
            JsonNode json = objectMapper.readTree(payload);

            String action = json.get("action").asText();
            int prNumber = json.get("number").asInt();
            String repoFullName = json.get("repository").get("full_name").asText();

            log.info("PR event: action={}, pr={}, repo={}", action, prNumber, repoFullName);

            // Optional: Implement preview deployments for PRs
            // For now, just log the event
            switch (action) {
                case "opened":
                case "synchronize":
                    log.info("PR #{} {} - could trigger preview deployment", prNumber, action);
                    break;
                case "closed":
                    log.info("PR #{} closed - could cleanup preview deployment", prNumber);
                    break;
            }

        } catch (Exception e) {
            log.error("Error handling PR event", e);
        }
    }

    /**
     * Validate webhook secret per repository
     */
    public boolean validateWebhookSecret(String repoFullName, String payload, String signature) {
        Optional<LinkedRepository> repo = repoRepository.findByRepoFullName(repoFullName);
        if (repo.isEmpty()) {
            return false;
        }

        String secret = repo.get().getWebhookSecret();
        if (secret == null || secret.isEmpty()) {
            return false;
        }

        // Validate HMAC-SHA256 signature
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            javax.crypto.spec.SecretKeySpec secretKeySpec = new javax.crypto.spec.SecretKeySpec(
                secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);

            byte[] hash = mac.doFinal(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            String expectedSignature = "sha256=" + sb.toString();

            return expectedSignature.equals(signature);
        } catch (Exception e) {
            log.error("Signature validation failed", e);
            return false;
        }
    }
}
