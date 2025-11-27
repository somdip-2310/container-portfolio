package dev.somdip.containerplatform.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;
import java.time.Instant;
import java.util.List;

@DynamoDbBean
public class GitHubConnection {

    private String connectionId;      // PK
    private String userId;            // GSI - to find connections by user
    private String githubUserId;      // GitHub user ID
    private String githubUsername;    // GitHub username
    private String accessToken;       // Encrypted OAuth access token
    private String refreshToken;      // If using GitHub App
    private Instant tokenExpiresAt;
    private List<String> scopes;      // Granted scopes
    private ConnectionStatus status;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant lastUsedAt;
    private String avatarUrl;         // GitHub avatar URL
    private String email;             // GitHub email

    public enum ConnectionStatus {
        ACTIVE,
        EXPIRED,
        REVOKED,
        ERROR
    }

    @DynamoDbPartitionKey
    public String getConnectionId() { return connectionId; }
    public void setConnectionId(String connectionId) { this.connectionId = connectionId; }

    @DynamoDbSecondaryPartitionKey(indexNames = {"UserIdIndex"})
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getGithubUserId() { return githubUserId; }
    public void setGithubUserId(String githubUserId) { this.githubUserId = githubUserId; }

    public String getGithubUsername() { return githubUsername; }
    public void setGithubUsername(String githubUsername) { this.githubUsername = githubUsername; }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }

    public Instant getTokenExpiresAt() { return tokenExpiresAt; }
    public void setTokenExpiresAt(Instant tokenExpiresAt) { this.tokenExpiresAt = tokenExpiresAt; }

    public List<String> getScopes() { return scopes; }
    public void setScopes(List<String> scopes) { this.scopes = scopes; }

    public ConnectionStatus getStatus() { return status; }
    public void setStatus(ConnectionStatus status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Instant getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(Instant lastUsedAt) { this.lastUsedAt = lastUsedAt; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
