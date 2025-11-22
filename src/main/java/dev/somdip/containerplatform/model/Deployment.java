package dev.somdip.containerplatform.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;


import java.time.Instant;
import java.util.List;
import java.util.Map;

@DynamoDbBean
public class Deployment {
    
    private String deploymentId;
    private String containerId;
    private String containerName; // Container name for easy reference
    private String userId;
    private String previousTaskDefinitionArn;
    private String newTaskDefinitionArn;
    private String previousImage;
    private String newImage;
    private DeploymentStatus status;
    private DeploymentType type;
    private String initiatedBy; // User ID or "system"
    private Instant createdAt; // When deployment was created
    private Instant startedAt;
    private Instant completedAt;
    private Long durationMillis;
    private List<DeploymentStep> steps;
    private String rollbackDeploymentId;
    private String errorMessage;
    private String errorCode;
    private Map<String, String> metadata;
    private DeploymentStrategy strategy;

    
    
    public String getContainerName() {
		return containerName;
	}

	public void setContainerName(String containerName) {
		this.containerName = containerName;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public String getPreviousTaskDefinitionArn() {
		return previousTaskDefinitionArn;
	}

	public void setPreviousTaskDefinitionArn(String previousTaskDefinitionArn) {
		this.previousTaskDefinitionArn = previousTaskDefinitionArn;
	}

	public String getNewTaskDefinitionArn() {
		return newTaskDefinitionArn;
	}

	public void setNewTaskDefinitionArn(String newTaskDefinitionArn) {
		this.newTaskDefinitionArn = newTaskDefinitionArn;
	}

	public String getPreviousImage() {
		return previousImage;
	}

	public void setPreviousImage(String previousImage) {
		this.previousImage = previousImage;
	}

	public String getNewImage() {
		return newImage;
	}

	public void setNewImage(String newImage) {
		this.newImage = newImage;
	}

	public String getInitiatedBy() {
		return initiatedBy;
	}

	public void setInitiatedBy(String initiatedBy) {
		this.initiatedBy = initiatedBy;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public void setStartedAt(Instant startedAt) {
		this.startedAt = startedAt;
	}

	public Instant getCompletedAt() {
		return completedAt;
	}

	public void setCompletedAt(Instant completedAt) {
		this.completedAt = completedAt;
	}

	public Long getDurationMillis() {
		return durationMillis;
	}

	public void setDurationMillis(Long durationMillis) {
		this.durationMillis = durationMillis;
	}

	public String getRollbackDeploymentId() {
		return rollbackDeploymentId;
	}

	public void setRollbackDeploymentId(String rollbackDeploymentId) {
		this.rollbackDeploymentId = rollbackDeploymentId;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
	}

	public String getErrorCode() {
		return errorCode;
	}

	public void setErrorCode(String errorCode) {
		this.errorCode = errorCode;
	}

	public Map<String, String> getMetadata() {
		return metadata;
	}

	public void setMetadata(Map<String, String> metadata) {
		this.metadata = metadata;
	}

	public void setDeploymentId(String deploymentId) {
		this.deploymentId = deploymentId;
	}

	public void setContainerId(String containerId) {
		this.containerId = containerId;
	}

	public void setStatus(DeploymentStatus status) {
		this.status = status;
	}

	public void setType(DeploymentType type) {
		this.type = type;
	}

	public void setSteps(List<DeploymentStep> steps) {
		this.steps = steps;
	}

	public void setStrategy(DeploymentStrategy strategy) {
		this.strategy = strategy;
	}

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

		public String getStepName() {
			return stepName;
		}

		public void setStepName(String stepName) {
			this.stepName = stepName;
		}

		public StepStatus getStatus() {
			return status;
		}

		public void setStatus(StepStatus status) {
			this.status = status;
		}

		public Instant getStartedAt() {
			return startedAt;
		}

		public void setStartedAt(Instant startedAt) {
			this.startedAt = startedAt;
		}

		public Instant getCompletedAt() {
			return completedAt;
		}

		public void setCompletedAt(Instant completedAt) {
			this.completedAt = completedAt;
		}

		public String getMessage() {
			return message;
		}

		public void setMessage(String message) {
			this.message = message;
		}

		public String getErrorMessage() {
			return errorMessage;
		}

		public void setErrorMessage(String errorMessage) {
			this.errorMessage = errorMessage;
		}
        
        
    }


    @DynamoDbBean
    public static class DeploymentStrategy {
        private String type; // ROLLING_UPDATE, BLUE_GREEN, CANARY
        private Integer batchSize;
        private Integer batchInterval;
        private Integer maxFailurePercent;
        private Boolean enableCircuitBreaker;
        private Integer healthCheckGracePeriod;
		public String getType() {
			return type;
		}
		public void setType(String type) {
			this.type = type;
		}
		public Integer getBatchSize() {
			return batchSize;
		}
		public void setBatchSize(Integer batchSize) {
			this.batchSize = batchSize;
		}
		public Integer getBatchInterval() {
			return batchInterval;
		}
		public void setBatchInterval(Integer batchInterval) {
			this.batchInterval = batchInterval;
		}
		public Integer getMaxFailurePercent() {
			return maxFailurePercent;
		}
		public void setMaxFailurePercent(Integer maxFailurePercent) {
			this.maxFailurePercent = maxFailurePercent;
		}
		public Boolean getEnableCircuitBreaker() {
			return enableCircuitBreaker;
		}
		public void setEnableCircuitBreaker(Boolean enableCircuitBreaker) {
			this.enableCircuitBreaker = enableCircuitBreaker;
		}
		public Integer getHealthCheckGracePeriod() {
			return healthCheckGracePeriod;
		}
		public void setHealthCheckGracePeriod(Integer healthCheckGracePeriod) {
			this.healthCheckGracePeriod = healthCheckGracePeriod;
		}
        
        
    }
}