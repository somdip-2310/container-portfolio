package dev.somdip.containerplatform.controller;

import dev.somdip.containerplatform.dto.github.*;
import dev.somdip.containerplatform.model.LinkedRepository;
import dev.somdip.containerplatform.security.CustomUserDetails;
import dev.somdip.containerplatform.service.GitHubApiService;
import dev.somdip.containerplatform.service.GitHubBuildService;
import dev.somdip.containerplatform.service.GitHubOAuthService;
import dev.somdip.containerplatform.service.GitHubRepositoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/github")
public class GitHubRepositoryController {
    private static final Logger log = LoggerFactory.getLogger(GitHubRepositoryController.class);

    private final GitHubApiService apiService;
    private final GitHubOAuthService oAuthService;
    private final GitHubRepositoryService repositoryService;
    private final GitHubBuildService buildService;

    public GitHubRepositoryController(GitHubApiService apiService,
                                       GitHubOAuthService oAuthService,
                                       GitHubRepositoryService repositoryService,
                                       GitHubBuildService buildService) {
        this.apiService = apiService;
        this.oAuthService = oAuthService;
        this.repositoryService = repositoryService;
        this.buildService = buildService;
    }

    /**
     * List user's GitHub repositories
     * GET /api/github/repos?page=1&perPage=20
     */
    @GetMapping("/repos")
    public ResponseEntity<List<GitHubRepoDTO>> listRepositories(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int perPage,
            Authentication authentication) {

        String userId = getUserId(authentication);

        if (!oAuthService.isConnected(userId)) {
            return ResponseEntity.status(401).build();
        }

        List<GitHubRepoDTO> repos = apiService.listRepositories(userId, page, perPage);
        return ResponseEntity.ok(repos);
    }

    /**
     * Search repositories
     * GET /api/github/repos/search?q=query
     */
    @GetMapping("/repos/search")
    public ResponseEntity<List<GitHubRepoDTO>> searchRepositories(
            @RequestParam String q,
            Authentication authentication) {

        String userId = getUserId(authentication);

        if (!oAuthService.isConnected(userId)) {
            return ResponseEntity.status(401).build();
        }

        List<GitHubRepoDTO> repos = apiService.searchRepositories(userId, q);
        return ResponseEntity.ok(repos);
    }

    /**
     * Get repository details
     * GET /api/github/repos/{owner}/{repo}
     */
    @GetMapping("/repos/{owner}/{repo}")
    public ResponseEntity<GitHubRepoDTO> getRepository(
            @PathVariable String owner,
            @PathVariable String repo,
            Authentication authentication) {

        String userId = getUserId(authentication);

        if (!oAuthService.isConnected(userId)) {
            return ResponseEntity.status(401).build();
        }

        GitHubRepoDTO repoInfo = apiService.getRepository(userId, owner, repo);
        return ResponseEntity.ok(repoInfo);
    }

    /**
     * List branches for a repository
     * GET /api/github/repos/{owner}/{repo}/branches
     */
    @GetMapping("/repos/{owner}/{repo}/branches")
    public ResponseEntity<List<GitHubBranchDTO>> listBranches(
            @PathVariable String owner,
            @PathVariable String repo,
            Authentication authentication) {

        String userId = getUserId(authentication);

        if (!oAuthService.isConnected(userId)) {
            return ResponseEntity.status(401).build();
        }

        List<GitHubBranchDTO> branches = apiService.listBranches(userId, owner, repo);
        return ResponseEntity.ok(branches);
    }

    /**
     * Check if repository has Dockerfile
     * GET /api/github/repos/{owner}/{repo}/has-dockerfile?path=Dockerfile
     */
    @GetMapping("/repos/{owner}/{repo}/has-dockerfile")
    public ResponseEntity<Map<String, Boolean>> hasDockerfile(
            @PathVariable String owner,
            @PathVariable String repo,
            @RequestParam(defaultValue = "Dockerfile") String path,
            Authentication authentication) {

        String userId = getUserId(authentication);

        if (!oAuthService.isConnected(userId)) {
            return ResponseEntity.status(401).build();
        }

        boolean hasDockerfile = apiService.hasDockerfile(userId, owner, repo, path);
        return ResponseEntity.ok(Map.of("hasDockerfile", hasDockerfile));
    }

    /**
     * Link a repository to a container
     * POST /api/github/link
     */
    @PostMapping("/link")
    public ResponseEntity<?> linkRepository(
            @RequestBody LinkRepoRequest request,
            Authentication authentication) {

        String userId = getUserId(authentication);

        try {
            LinkedRepository linked = repositoryService.linkRepository(userId, request);

            // Trigger initial build and get deployment ID
            Map<String, Object> response = new HashMap<>();
            response.put("linked", linked);
            response.put("repoLinkId", linked.getRepoLinkId());

            try {
                var deployment = buildService.triggerManualBuild(linked.getRepoLinkId(), userId);
                if (deployment != null) {
                    response.put("deploymentId", deployment.getDeploymentId());
                    response.put("buildTriggered", true);
                }
            } catch (Exception e) {
                log.warn("Failed to trigger initial build: {}", e.getMessage());
                response.put("buildTriggered", false);
                response.put("buildError", e.getMessage());
            }

            return ResponseEntity.ok(response);
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error linking repository: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "An unexpected error occurred: " + e.getMessage()));
        }
    }

    /**
     * Unlink a repository from a container
     * DELETE /api/github/link/{repoLinkId}
     */
    @DeleteMapping("/link/{repoLinkId}")
    public ResponseEntity<?> unlinkRepository(
            @PathVariable String repoLinkId,
            Authentication authentication) {

        String userId = getUserId(authentication);

        try {
            repositoryService.unlinkRepository(userId, repoLinkId);
            return ResponseEntity.ok(Map.of("status", "unlinked"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get linked repository for a container
     * GET /api/github/link/container/{containerId}
     */
    @GetMapping("/link/container/{containerId}")
    public ResponseEntity<?> getLinkedRepository(
            @PathVariable String containerId,
            Authentication authentication) {

        return repositoryService.getLinkedRepository(containerId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get all linked repositories for user
     * GET /api/github/links
     */
    @GetMapping("/links")
    public ResponseEntity<List<LinkedRepository>> getUserLinkedRepositories(
            Authentication authentication) {

        String userId = getUserId(authentication);
        List<LinkedRepository> linked = repositoryService.getUserLinkedRepositories(userId);
        return ResponseEntity.ok(linked);
    }

    /**
     * Update linked repository settings
     * PATCH /api/github/link/{repoLinkId}
     */
    @PatchMapping("/link/{repoLinkId}")
    public ResponseEntity<?> updateSettings(
            @PathVariable String repoLinkId,
            @RequestBody Map<String, Object> updates,
            Authentication authentication) {

        String userId = getUserId(authentication);

        try {
            LinkedRepository updated = repositoryService.updateSettings(
                userId,
                repoLinkId,
                (String) updates.get("deployBranch"),
                (Boolean) updates.get("autoDeploy"),
                (String) updates.get("rootDirectory"),
                (String) updates.get("dockerfilePath")
            );
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Retry webhook creation
     * POST /api/github/link/{repoLinkId}/retry-webhook
     */
    @PostMapping("/link/{repoLinkId}/retry-webhook")
    public ResponseEntity<?> retryWebhook(
            @PathVariable String repoLinkId,
            Authentication authentication) {

        String userId = getUserId(authentication);

        try {
            LinkedRepository updated = repositoryService.retryWebhook(userId, repoLinkId);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Trigger manual deployment
     * POST /api/github/link/{repoLinkId}/deploy
     */
    @PostMapping("/link/{repoLinkId}/deploy")
    public ResponseEntity<?> triggerDeployment(
            @PathVariable String repoLinkId,
            Authentication authentication) {

        String userId = getUserId(authentication);

        try {
            var deployment = buildService.triggerManualBuild(repoLinkId, userId);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "triggered");
            if (deployment != null) {
                response.put("deploymentId", deployment.getDeploymentId());
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        }
    }

    private String getUserId(Authentication authentication) {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        return userDetails.getUserId();
    }
}
