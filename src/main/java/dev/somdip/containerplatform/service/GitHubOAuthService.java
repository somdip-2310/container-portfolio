package dev.somdip.containerplatform.service;

import dev.somdip.containerplatform.config.GitHubConfig;
import dev.somdip.containerplatform.model.GitHubConnection;
import dev.somdip.containerplatform.repository.GitHubConnectionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.*;

@Service
public class GitHubOAuthService {
    private static final Logger log = LoggerFactory.getLogger(GitHubOAuthService.class);

    private final GitHubConfig gitHubConfig;
    private final GitHubConnectionRepository connectionRepository;
    private final EncryptionService encryptionService;
    private final RestTemplate restTemplate;

    public GitHubOAuthService(GitHubConfig gitHubConfig,
                              GitHubConnectionRepository connectionRepository,
                              EncryptionService encryptionService) {
        this.gitHubConfig = gitHubConfig;
        this.connectionRepository = connectionRepository;
        this.encryptionService = encryptionService;
        this.restTemplate = new RestTemplate();
    }

    /**
     * Generate OAuth authorization URL with state parameter
     */
    public String getAuthorizationUrl(String userId) {
        String state = generateState(userId);
        return gitHubConfig.getAuthorizationUrl() + state;
    }

    /**
     * Generate secure state parameter (includes user ID for callback)
     */
    private String generateState(String userId) {
        // Format: base64(userId:randomToken:timestamp)
        String token = UUID.randomUUID().toString();
        String timestamp = String.valueOf(System.currentTimeMillis());
        String raw = userId + ":" + token + ":" + timestamp;
        return Base64.getEncoder().encodeToString(raw.getBytes());
    }

    /**
     * Parse and validate state parameter
     */
    public String parseState(String state) {
        try {
            String decoded = new String(Base64.getDecoder().decode(state));
            String[] parts = decoded.split(":");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Invalid state format");
            }

            // Check timestamp (valid for 10 minutes)
            long timestamp = Long.parseLong(parts[2]);
            if (System.currentTimeMillis() - timestamp > 600000) {
                throw new IllegalArgumentException("State expired");
            }

            return parts[0]; // Return userId
        } catch (Exception e) {
            log.error("Failed to parse OAuth state", e);
            throw new IllegalArgumentException("Invalid state parameter");
        }
    }

    /**
     * Exchange authorization code for access token
     */
    @SuppressWarnings("unchecked")
    public GitHubConnection exchangeCodeForToken(String code, String userId) {
        log.info("Exchanging OAuth code for token, userId: {}", userId);

        // Exchange code for token
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

        Map<String, String> body = new HashMap<>();
        body.put("client_id", gitHubConfig.getClientId());
        body.put("client_secret", gitHubConfig.getClientSecret());
        body.put("code", code);
        body.put("redirect_uri", gitHubConfig.getRedirectUri());

        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(
            "https://github.com/login/oauth/access_token",
            request,
            Map.class
        );

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new RuntimeException("Failed to exchange code for token");
        }

        Map<String, Object> tokenResponse = response.getBody();

        if (tokenResponse.containsKey("error")) {
            throw new RuntimeException("OAuth error: " + tokenResponse.get("error_description"));
        }

        String accessToken = (String) tokenResponse.get("access_token");
        String scope = (String) tokenResponse.get("scope");

        // Get GitHub user info
        Map<String, Object> userInfo = getGitHubUserInfo(accessToken);

        // Create or update connection
        GitHubConnection connection = connectionRepository.findByUserId(userId)
            .orElse(new GitHubConnection());

        connection.setConnectionId(connection.getConnectionId() != null ?
            connection.getConnectionId() : UUID.randomUUID().toString());
        connection.setUserId(userId);
        connection.setGithubUserId(String.valueOf(userInfo.get("id")));
        connection.setGithubUsername((String) userInfo.get("login"));
        connection.setAccessToken(encryptionService.encrypt(accessToken));
        connection.setScopes(scope != null ? Arrays.asList(scope.split(",")) : Collections.emptyList());
        connection.setStatus(GitHubConnection.ConnectionStatus.ACTIVE);
        connection.setAvatarUrl((String) userInfo.get("avatar_url"));
        connection.setEmail((String) userInfo.get("email"));
        connection.setCreatedAt(connection.getCreatedAt() != null ?
            connection.getCreatedAt() : Instant.now());
        connection.setUpdatedAt(Instant.now());

        connectionRepository.save(connection);

        log.info("GitHub connection saved for user: {}, GitHub: {}",
            userId, connection.getGithubUsername());

        return connection;
    }

    /**
     * Get GitHub user info using access token
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getGitHubUserInfo(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<Map> response = restTemplate.exchange(
            gitHubConfig.getApiBaseUrl() + "/user",
            HttpMethod.GET,
            request,
            Map.class
        );

        return response.getBody();
    }

    /**
     * Get decrypted access token for a user
     */
    public String getAccessToken(String userId) {
        GitHubConnection connection = connectionRepository.findByUserId(userId)
            .orElseThrow(() -> new IllegalStateException("GitHub not connected"));

        if (connection.getStatus() != GitHubConnection.ConnectionStatus.ACTIVE) {
            throw new IllegalStateException("GitHub connection is not active");
        }

        // Update last used timestamp
        connection.setLastUsedAt(Instant.now());
        connectionRepository.save(connection);

        return encryptionService.decrypt(connection.getAccessToken());
    }

    /**
     * Get GitHub connection for a user
     */
    public Optional<GitHubConnection> getConnection(String userId) {
        return connectionRepository.findByUserId(userId);
    }

    /**
     * Revoke GitHub connection
     */
    public void revokeConnection(String userId) {
        GitHubConnection connection = connectionRepository.findByUserId(userId)
            .orElseThrow(() -> new IllegalStateException("GitHub not connected"));

        connection.setStatus(GitHubConnection.ConnectionStatus.REVOKED);
        connection.setAccessToken(null);
        connection.setUpdatedAt(Instant.now());

        connectionRepository.save(connection);

        log.info("GitHub connection revoked for user: {}", userId);
    }

    /**
     * Check if user has active GitHub connection
     */
    public boolean isConnected(String userId) {
        return connectionRepository.findByUserId(userId)
            .map(c -> c.getStatus() == GitHubConnection.ConnectionStatus.ACTIVE)
            .orElse(false);
    }

    /**
     * Check if GitHub OAuth is configured
     */
    public boolean isConfigured() {
        return gitHubConfig.isConfigured();
    }
}
