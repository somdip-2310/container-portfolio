package dev.somdip.containerplatform.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;
import java.time.Instant;
import java.util.Map;

@DynamoDbBean
public class LinkedRepository {

    private String repoLinkId;        // PK
    private String userId;            // GSI
    private String containerId;       // GSI - links to Container
    private String connectionId;      // FK to GitHubConnection

    // GitHub Repository Info
    private String repoId;            // GitHub repo ID
    private String repoFullName;      // owner/repo
    private String repoOwner;
    private String repoName;
    private String defaultBranch;
    private String deployBranch;      // Branch to deploy from (default: main)
    private Boolean isPrivate;
    private String repoUrl;
    private String cloneUrl;

    // Deployment Configuration
    private String rootDirectory;     // Subdirectory containing app (default: /)
    private String dockerfilePath;    // Path to Dockerfile (default: Dockerfile)
    private String buildCommand;      // Custom build command
    private Map<String, String> buildEnvVars;  // Build-time env vars
    private Boolean autoDeploy;       // Deploy on push (default: true)

    // Webhook Info
    private String webhookId;         // GitHub webhook ID
    private String webhookSecret;     // Secret for validating webhooks
    private WebhookStatus webhookStatus;

    // Status
    private LinkStatus status;
    private String lastError;
    private Instant lastDeployedAt;
    private String lastDeployedCommit;
    private Instant createdAt;
    private Instant updatedAt;

    public enum LinkStatus {
        ACTIVE,
        PENDING_WEBHOOK,
        WEBHOOK_ERROR,
        DISCONNECTED
    }

    public enum WebhookStatus {
        ACTIVE,
        PENDING,
        FAILED,
        NOT_CONFIGURED
    }

    @DynamoDbPartitionKey
    public String getRepoLinkId() { return repoLinkId; }
    public void setRepoLinkId(String repoLinkId) { this.repoLinkId = repoLinkId; }

    @DynamoDbSecondaryPartitionKey(indexNames = {"UserIdIndex"})
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    @DynamoDbSecondaryPartitionKey(indexNames = {"ContainerIdIndex"})
    public String getContainerId() { return containerId; }
    public void setContainerId(String containerId) { this.containerId = containerId; }

    @DynamoDbSecondaryPartitionKey(indexNames = {"RepoFullNameIndex"})
    public String getRepoFullName() { return repoFullName; }
    public void setRepoFullName(String repoFullName) { this.repoFullName = repoFullName; }

    public String getConnectionId() { return connectionId; }
    public void setConnectionId(String connectionId) { this.connectionId = connectionId; }

    public String getRepoId() { return repoId; }
    public void setRepoId(String repoId) { this.repoId = repoId; }

    public String getRepoOwner() { return repoOwner; }
    public void setRepoOwner(String repoOwner) { this.repoOwner = repoOwner; }

    public String getRepoName() { return repoName; }
    public void setRepoName(String repoName) { this.repoName = repoName; }

    public String getDefaultBranch() { return defaultBranch; }
    public void setDefaultBranch(String defaultBranch) { this.defaultBranch = defaultBranch; }

    public String getDeployBranch() { return deployBranch; }
    public void setDeployBranch(String deployBranch) { this.deployBranch = deployBranch; }

    public Boolean getIsPrivate() { return isPrivate; }
    public void setIsPrivate(Boolean isPrivate) { this.isPrivate = isPrivate; }

    public String getRepoUrl() { return repoUrl; }
    public void setRepoUrl(String repoUrl) { this.repoUrl = repoUrl; }

    public String getCloneUrl() { return cloneUrl; }
    public void setCloneUrl(String cloneUrl) { this.cloneUrl = cloneUrl; }

    public String getRootDirectory() { return rootDirectory; }
    public void setRootDirectory(String rootDirectory) { this.rootDirectory = rootDirectory; }

    public String getDockerfilePath() { return dockerfilePath; }
    public void setDockerfilePath(String dockerfilePath) { this.dockerfilePath = dockerfilePath; }

    public String getBuildCommand() { return buildCommand; }
    public void setBuildCommand(String buildCommand) { this.buildCommand = buildCommand; }

    public Map<String, String> getBuildEnvVars() { return buildEnvVars; }
    public void setBuildEnvVars(Map<String, String> buildEnvVars) { this.buildEnvVars = buildEnvVars; }

    public Boolean getAutoDeploy() { return autoDeploy; }
    public void setAutoDeploy(Boolean autoDeploy) { this.autoDeploy = autoDeploy; }

    public String getWebhookId() { return webhookId; }
    public void setWebhookId(String webhookId) { this.webhookId = webhookId; }

    public String getWebhookSecret() { return webhookSecret; }
    public void setWebhookSecret(String webhookSecret) { this.webhookSecret = webhookSecret; }

    public WebhookStatus getWebhookStatus() { return webhookStatus; }
    public void setWebhookStatus(WebhookStatus webhookStatus) { this.webhookStatus = webhookStatus; }

    public LinkStatus getStatus() { return status; }
    public void setStatus(LinkStatus status) { this.status = status; }

    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }

    public Instant getLastDeployedAt() { return lastDeployedAt; }
    public void setLastDeployedAt(Instant lastDeployedAt) { this.lastDeployedAt = lastDeployedAt; }

    public String getLastDeployedCommit() { return lastDeployedCommit; }
    public void setLastDeployedCommit(String lastDeployedCommit) { this.lastDeployedCommit = lastDeployedCommit; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
