package dev.somdip.containerplatform.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

import java.time.Instant;

/**
 * Model for tracking source code deployments
 */
@DynamoDbBean
public class SourceDeployment {

    private String deploymentId;
    private String userId;
    private String containerName;
    private String projectId;
    private String s3Key;
    private String buildId;
    private String imageUri;
    private String containerId;
    private DeploymentStatus status;
    private String currentPhase;
    private String errorMessage;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant completedAt;

    public enum DeploymentStatus {
        ANALYZING,      // Analyzing project structure
        BUILDING,       // Building Docker image
        PUSHING,        // Pushing image to ECR
        DEPLOYING,      // Deploying container
        COMPLETED,      // Successfully completed
        FAILED          // Failed at some stage
    }

    @DynamoDbPartitionKey
    @DynamoDbAttribute("deploymentId")
    public String getDeploymentId() {
        return deploymentId;
    }

    public void setDeploymentId(String deploymentId) {
        this.deploymentId = deploymentId;
    }

    @DynamoDbAttribute("userId")
    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    @DynamoDbAttribute("containerName")
    public String getContainerName() {
        return containerName;
    }

    public void setContainerName(String containerName) {
        this.containerName = containerName;
    }

    @DynamoDbAttribute("projectId")
    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    @DynamoDbAttribute("s3Key")
    public String getS3Key() {
        return s3Key;
    }

    public void setS3Key(String s3Key) {
        this.s3Key = s3Key;
    }

    @DynamoDbAttribute("buildId")
    public String getBuildId() {
        return buildId;
    }

    public void setBuildId(String buildId) {
        this.buildId = buildId;
    }

    @DynamoDbAttribute("imageUri")
    public String getImageUri() {
        return imageUri;
    }

    public void setImageUri(String imageUri) {
        this.imageUri = imageUri;
    }

    @DynamoDbAttribute("containerId")
    public String getContainerId() {
        return containerId;
    }

    public void setContainerId(String containerId) {
        this.containerId = containerId;
    }

    @DynamoDbAttribute("status")
    public DeploymentStatus getStatus() {
        return status;
    }

    public void setStatus(DeploymentStatus status) {
        this.status = status;
    }

    @DynamoDbAttribute("currentPhase")
    public String getCurrentPhase() {
        return currentPhase;
    }

    public void setCurrentPhase(String currentPhase) {
        this.currentPhase = currentPhase;
    }

    @DynamoDbAttribute("errorMessage")
    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    @DynamoDbAttribute("createdAt")
    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @DynamoDbAttribute("updatedAt")
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @DynamoDbAttribute("completedAt")
    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}
