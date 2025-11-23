package dev.somdip.containerplatform.service;

import dev.somdip.containerplatform.model.SourceDeployment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

import java.time.Instant;
import java.util.UUID;

/**
 * Service for tracking source code deployment status in DynamoDB
 */
@Service
public class SourceDeploymentTrackingService {
    private static final Logger log = LoggerFactory.getLogger(SourceDeploymentTrackingService.class);

    private final DynamoDbTable<SourceDeployment> deploymentTable;

    public SourceDeploymentTrackingService(DynamoDbEnhancedClient dynamoDbEnhancedClient,
                                          @Value("${aws.dynamodb.table.source-deployments:container-platform-source-deployments}") String tableName) {
        this.deploymentTable = dynamoDbEnhancedClient.table(tableName, TableSchema.fromBean(SourceDeployment.class));
    }

    /**
     * Create a new deployment tracking record
     */
    public SourceDeployment createDeployment(String userId, String containerName, String projectId, String s3Key) {
        SourceDeployment deployment = new SourceDeployment();
        deployment.setDeploymentId(UUID.randomUUID().toString());
        deployment.setUserId(userId);
        deployment.setContainerName(containerName);
        deployment.setProjectId(projectId);
        deployment.setS3Key(s3Key);
        deployment.setStatus(SourceDeployment.DeploymentStatus.ANALYZING);
        deployment.setCurrentPhase("Project analyzed, starting build");
        deployment.setCreatedAt(Instant.now());
        deployment.setUpdatedAt(Instant.now());

        deploymentTable.putItem(deployment);
        log.info("Created deployment tracking record: {}", deployment.getDeploymentId());

        return deployment;
    }

    /**
     * Update deployment with build information
     */
    public void updateBuildStarted(String deploymentId, String buildId, String imageUri) {
        SourceDeployment deployment = getDeployment(deploymentId);
        if (deployment != null) {
            deployment.setBuildId(buildId);
            deployment.setImageUri(imageUri);
            deployment.setStatus(SourceDeployment.DeploymentStatus.BUILDING);
            deployment.setCurrentPhase("Building Docker image");
            deployment.setUpdatedAt(Instant.now());

            deploymentTable.putItem(deployment);
            log.info("Updated deployment {} - build started: {}", deploymentId, buildId);
        }
    }

    /**
     * Update deployment status based on build progress
     */
    public void updateBuildStatus(String deploymentId, String status, String phase) {
        SourceDeployment deployment = getDeployment(deploymentId);
        if (deployment != null) {
            deployment.setStatus(mapStatus(status));
            deployment.setCurrentPhase(phase);
            deployment.setUpdatedAt(Instant.now());

            deploymentTable.putItem(deployment);
            log.info("Updated deployment {} status: {} - {}", deploymentId, status, phase);
        }
    }

    /**
     * Update deployment when container is created
     */
    public void updateContainerCreated(String deploymentId, String containerId) {
        SourceDeployment deployment = getDeployment(deploymentId);
        if (deployment != null) {
            deployment.setContainerId(containerId);
            deployment.setStatus(SourceDeployment.DeploymentStatus.DEPLOYING);
            deployment.setCurrentPhase("Deploying container to ECS");
            deployment.setUpdatedAt(Instant.now());

            deploymentTable.putItem(deployment);
            log.info("Updated deployment {} - container created: {}", deploymentId, containerId);
        }
    }

    /**
     * Mark deployment as completed
     */
    public void markCompleted(String deploymentId) {
        SourceDeployment deployment = getDeployment(deploymentId);
        if (deployment != null) {
            deployment.setStatus(SourceDeployment.DeploymentStatus.COMPLETED);
            deployment.setCurrentPhase("Deployment completed successfully");
            deployment.setUpdatedAt(Instant.now());
            deployment.setCompletedAt(Instant.now());

            deploymentTable.putItem(deployment);
            log.info("Deployment {} completed successfully", deploymentId);
        }
    }

    /**
     * Mark deployment as failed
     */
    public void markFailed(String deploymentId, String errorMessage) {
        SourceDeployment deployment = getDeployment(deploymentId);
        if (deployment != null) {
            deployment.setStatus(SourceDeployment.DeploymentStatus.FAILED);
            deployment.setCurrentPhase("Deployment failed");
            deployment.setErrorMessage(errorMessage);
            deployment.setUpdatedAt(Instant.now());
            deployment.setCompletedAt(Instant.now());

            deploymentTable.putItem(deployment);
            log.error("Deployment {} failed: {}", deploymentId, errorMessage);
        }
    }

    /**
     * Get deployment by ID
     */
    public SourceDeployment getDeployment(String deploymentId) {
        try {
            Key key = Key.builder()
                .partitionValue(deploymentId)
                .build();

            return deploymentTable.getItem(key);
        } catch (Exception e) {
            log.error("Failed to get deployment: {}", deploymentId, e);
            return null;
        }
    }

    /**
     * Map build status to deployment status
     */
    private SourceDeployment.DeploymentStatus mapStatus(String buildStatus) {
        return switch (buildStatus) {
            case "BUILDING" -> SourceDeployment.DeploymentStatus.BUILDING;
            case "BUILD_COMPLETED" -> SourceDeployment.DeploymentStatus.PUSHING;
            case "BUILD_FAILED", "BUILD_CANCELLED" -> SourceDeployment.DeploymentStatus.FAILED;
            default -> SourceDeployment.DeploymentStatus.ANALYZING;
        };
    }
}
