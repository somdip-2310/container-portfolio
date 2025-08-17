package dev.somdip.containerplatform.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class Deployment {
    
    private String deploymentId;
    private String containerId;
    private String userId;
    private String previousTaskDefinitionArn;
    private String newTaskDefinitionArn;
    private String previousImage;
    private String newImage;
    private DeploymentStatus status;
    private DeploymentType type;
    private String initiatedBy; // User ID or "system"
    private Instant startedAt;
    private Instant completedAt;
    private Long durationMillis;
    private List<DeploymentStep> steps;
    private String rollbackDeploymentId;
    private String errorMessage;
    private String errorCode;
    private Map<String, String> metadata;
    private DeploymentStrategy strategy;

    @DynamoDbPartitionKey
    @DynamoDbAttribute("deploymentId")
    public String getDeploymentId() {
        return deploymentId;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = "ContainerIdIndex")
    @DynamoDbAttribute("containerId")
    public String getContainerId() {
        return containerId;
    }

    @DynamoDbAttribute("status")
    public DeploymentStatus getStatus() {
        return status;
    }

    @DynamoDbAttribute("type")
    public DeploymentType getType() {
        return type;
    }

    @DynamoDbAttribute("steps")
    public List<DeploymentStep> getSteps() {
        return steps;
    }

    @DynamoDbAttribute("strategy")
    public DeploymentStrategy getStrategy() {
        return strategy;
    }

    public enum DeploymentStatus {
        PENDING, IN_PROGRESS, COMPLETED, FAILED, ROLLED_BACK, CANCELLED
    }

    public enum DeploymentType {
        CREATE, UPDATE, ROLLBACK, SCALE, DELETE
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @DynamoDbBean
    public static class DeploymentStep {
        private String stepName;
        private StepStatus status;
        private Instant startedAt;
        private Instant completedAt;
        private String message;
        private String errorMessage;

        public enum StepStatus {
            PENDING, IN_PROGRESS, COMPLETED, FAILED, SKIPPED
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @DynamoDbBean
    public static class DeploymentStrategy {
        private String type; // ROLLING_UPDATE, BLUE_GREEN, CANARY
        private Integer batchSize;
        private Integer batchInterval;
        private Integer maxFailurePercent;
        private Boolean enableCircuitBreaker;
        private Integer healthCheckGracePeriod;
    }
}