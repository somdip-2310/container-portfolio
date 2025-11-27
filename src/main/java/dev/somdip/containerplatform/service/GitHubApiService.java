package dev.somdip.containerplatform.service;

import dev.somdip.containerplatform.config.GitHubConfig;
import dev.somdip.containerplatform.dto.github.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class GitHubApiService {
    private static final Logger log = LoggerFactory.getLogger(GitHubApiService.class);
    private static final String GITHUB_API = "https://api.github.com";

    private final GitHubOAuthService oAuthService;
    private final GitHubConfig gitHubConfig;
    private final RestTemplate restTemplate;

    public GitHubApiService(GitHubOAuthService oAuthService, GitHubConfig gitHubConfig) {
        this.oAuthService = oAuthService;
        this.gitHubConfig = gitHubConfig;
        this.restTemplate = new RestTemplate();
    }

    /**
     * List user's repositories (including org repos they have access to)
     */
    public List<GitHubRepoDTO> listRepositories(String userId, int page, int perPage) {
        String accessToken = oAuthService.getAccessToken(userId);

        HttpHeaders headers = createHeaders(accessToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        // Get user's repos + repos from orgs
        String url = GITHUB_API + "/user/repos?type=all&sort=updated&direction=desc" +
                     "&page=" + page + "&per_page=" + perPage;

        ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
            url,
            HttpMethod.GET,
            request,
            new ParameterizedTypeReference<List<Map<String, Object>>>() {}
        );

        List<GitHubRepoDTO> repos = new ArrayList<>();
        if (response.getBody() != null) {
            for (Map<String, Object> repo : response.getBody()) {
                repos.add(mapToRepoDTO(repo));
            }
        }

        return repos;
    }

    /**
     * Search repositories
     */
    @SuppressWarnings("unchecked")
    public List<GitHubRepoDTO> searchRepositories(String userId, String query) {
        String accessToken = oAuthService.getAccessToken(userId);

        HttpHeaders headers = createHeaders(accessToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        // Search in user's accessible repos
        String url = GITHUB_API + "/search/repositories?q=" +
            query + "+in:name&sort=updated&per_page=20";

        ResponseEntity<Map> response = restTemplate.exchange(
            url,
            HttpMethod.GET,
            request,
            Map.class
        );

        List<GitHubRepoDTO> repos = new ArrayList<>();
        if (response.getBody() != null) {
            List<Map<String, Object>> items = (List<Map<String, Object>>) response.getBody().get("items");
            if (items != null) {
                for (Map<String, Object> repo : items) {
                    repos.add(mapToRepoDTO(repo));
                }
            }
        }

        return repos;
    }

    /**
     * Get single repository details
     */
    @SuppressWarnings("unchecked")
    public GitHubRepoDTO getRepository(String userId, String owner, String repo) {
        String accessToken = oAuthService.getAccessToken(userId);

        HttpHeaders headers = createHeaders(accessToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        String url = GITHUB_API + "/repos/" + owner + "/" + repo;

        ResponseEntity<Map> response = restTemplate.exchange(
            url,
            HttpMethod.GET,
            request,
            Map.class
        );

        return mapToRepoDTO(response.getBody());
    }

    /**
     * List branches for a repository
     */
    @SuppressWarnings("unchecked")
    public List<GitHubBranchDTO> listBranches(String userId, String owner, String repo) {
        String accessToken = oAuthService.getAccessToken(userId);

        HttpHeaders headers = createHeaders(accessToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        String url = GITHUB_API + "/repos/" + owner + "/" + repo + "/branches";

        ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
            url,
            HttpMethod.GET,
            request,
            new ParameterizedTypeReference<List<Map<String, Object>>>() {}
        );

        List<GitHubBranchDTO> branches = new ArrayList<>();
        if (response.getBody() != null) {
            for (Map<String, Object> branch : response.getBody()) {
                GitHubBranchDTO dto = new GitHubBranchDTO();
                dto.setName((String) branch.get("name"));
                dto.setIsProtected((Boolean) branch.get("protected"));

                Map<String, Object> commit = (Map<String, Object>) branch.get("commit");
                if (commit != null) {
                    dto.setCommitSha((String) commit.get("sha"));
                }
                branches.add(dto);
            }
        }

        return branches;
    }

    /**
     * Check if repository has a file at given path
     */
    public boolean hasFile(String userId, String owner, String repo, String path) {
        String accessToken = oAuthService.getAccessToken(userId);

        HttpHeaders headers = createHeaders(accessToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        String url = GITHUB_API + "/repos/" + owner + "/" + repo + "/contents/" + path;

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                request,
                Map.class
            );
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Check if repository has Dockerfile
     */
    public boolean hasDockerfile(String userId, String owner, String repo, String dockerfilePath) {
        String path = dockerfilePath != null ? dockerfilePath : "Dockerfile";
        return hasFile(userId, owner, repo, path);
    }

    /**
     * Get repository file contents
     */
    @SuppressWarnings("unchecked")
    public String getFileContents(String userId, String owner, String repo, String path) {
        String accessToken = oAuthService.getAccessToken(userId);

        HttpHeaders headers = createHeaders(accessToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        String url = GITHUB_API + "/repos/" + owner + "/" + repo + "/contents/" + path;

        ResponseEntity<Map> response = restTemplate.exchange(
            url,
            HttpMethod.GET,
            request,
            Map.class
        );

        if (response.getBody() != null) {
            String content = (String) response.getBody().get("content");
            if (content != null) {
                return new String(Base64.getDecoder().decode(content.replace("\n", "")));
            }
        }

        return null;
    }

    /**
     * Create webhook on repository
     */
    @SuppressWarnings("unchecked")
    public String createWebhook(String userId, String owner, String repo, String secret) {
        String accessToken = oAuthService.getAccessToken(userId);

        HttpHeaders headers = createHeaders(accessToken);

        Map<String, Object> body = new HashMap<>();
        body.put("name", "web");
        body.put("active", true);
        body.put("events", Arrays.asList("push", "pull_request"));

        Map<String, Object> config = new HashMap<>();
        config.put("url", gitHubConfig.getWebhookUrl());
        config.put("content_type", "json");
        config.put("secret", secret);
        config.put("insecure_ssl", "0");
        body.put("config", config);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        String url = GITHUB_API + "/repos/" + owner + "/" + repo + "/hooks";

        ResponseEntity<Map> response = restTemplate.exchange(
            url,
            HttpMethod.POST,
            request,
            Map.class
        );

        if (response.getBody() != null) {
            Object id = response.getBody().get("id");
            return id != null ? String.valueOf(id) : null;
        }

        throw new RuntimeException("Failed to create webhook");
    }

    /**
     * Delete webhook from repository
     */
    public void deleteWebhook(String userId, String owner, String repo, String webhookId) {
        String accessToken = oAuthService.getAccessToken(userId);

        HttpHeaders headers = createHeaders(accessToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        String url = GITHUB_API + "/repos/" + owner + "/" + repo + "/hooks/" + webhookId;

        try {
            restTemplate.exchange(url, HttpMethod.DELETE, request, Void.class);
            log.info("Deleted webhook {} from {}/{}", webhookId, owner, repo);
        } catch (Exception e) {
            log.warn("Failed to delete webhook: {}", e.getMessage());
        }
    }

    /**
     * Get latest commit SHA for a branch
     */
    @SuppressWarnings("unchecked")
    public String getLatestCommit(String userId, String owner, String repo, String branch) {
        String accessToken = oAuthService.getAccessToken(userId);

        HttpHeaders headers = createHeaders(accessToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        String url = GITHUB_API + "/repos/" + owner + "/" + repo + "/commits/" + branch;

        ResponseEntity<Map> response = restTemplate.exchange(
            url,
            HttpMethod.GET,
            request,
            Map.class
        );

        if (response.getBody() != null) {
            return (String) response.getBody().get("sha");
        }

        return null;
    }

    private HttpHeaders createHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-GitHub-Api-Version", "2022-11-28");
        return headers;
    }

    @SuppressWarnings("unchecked")
    private GitHubRepoDTO mapToRepoDTO(Map<String, Object> repo) {
        GitHubRepoDTO dto = new GitHubRepoDTO();
        dto.setId(String.valueOf(repo.get("id")));
        dto.setName((String) repo.get("name"));
        dto.setFullName((String) repo.get("full_name"));
        dto.setDescription((String) repo.get("description"));
        dto.setHtmlUrl((String) repo.get("html_url"));
        dto.setCloneUrl((String) repo.get("clone_url"));
        dto.setDefaultBranch((String) repo.get("default_branch"));
        dto.setIsPrivate((Boolean) repo.get("private"));
        dto.setLanguage((String) repo.get("language"));
        dto.setPushedAt((String) repo.get("pushed_at"));

        Object stars = repo.get("stargazers_count");
        if (stars != null) {
            dto.setStargazersCount(((Number) stars).longValue());
        }

        Object forks = repo.get("forks_count");
        if (forks != null) {
            dto.setForksCount(((Number) forks).longValue());
        }

        Map<String, Object> owner = (Map<String, Object>) repo.get("owner");
        if (owner != null) {
            dto.setOwner((String) owner.get("login"));
            dto.setOwnerAvatarUrl((String) owner.get("avatar_url"));
        }

        return dto;
    }
}
