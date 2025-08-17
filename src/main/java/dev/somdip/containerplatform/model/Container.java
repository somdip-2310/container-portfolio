package dev.somdip.containerplatform.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

import java.time.Instant;
import java.util.Map;
import java.util.Set;


@DynamoDbBean
public class Container {
    
    private String containerId;
    private String userId;
    private String containerName;
    private String image;
    private String imageTag;
    private ContainerStatus status;
    private String subdomain;
    private String customDomain;
    private Integer port;
    private Integer cpu; // CPU units (256 = 0.25 vCPU)
    private Integer memory; // Memory in MB
    private Map<String, String> environmentVariables;
    private Map<String, String> labels;
    private Set<String> commands;
    private HealthCheckConfig healthCheck;
    private AutoScalingConfig autoScaling;
    private String taskDefinitionArn;
    private String serviceArn;
    private String taskArn;
    private String targetGroupArn;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant lastDeployedAt;
    private Long deploymentCount;
    private ResourceUsage resourceUsage;
    private Boolean sslEnabled;
    private String sslCertificateArn;
    
    
    
    public String getContainerName() {
		return containerName;
	}

	public void setContainerName(String containerName) {
		this.containerName = containerName;
	}

	public String getImage() {
		return image;
	}

	public void setImage(String image) {
		this.image = image;
	}

	public String getImageTag() {
		return imageTag;
	}

	public void setImageTag(String imageTag) {
		this.imageTag = imageTag;
	}

	public String getSubdomain() {
		return subdomain;
	}

	public void setSubdomain(String subdomain) {
		this.subdomain = subdomain;
	}

	public String getCustomDomain() {
		return customDomain;
	}

	public void setCustomDomain(String customDomain) {
		this.customDomain = customDomain;
	}

	public Integer getPort() {
		return port;
	}

	public void setPort(Integer port) {
		this.port = port;
	}

	public Integer getCpu() {
		return cpu;
	}

	public void setCpu(Integer cpu) {
		this.cpu = cpu;
	}

	public Integer getMemory() {
		return memory;
	}

	public void setMemory(Integer memory) {
		this.memory = memory;
	}

	public Map<String, String> getEnvironmentVariables() {
		return environmentVariables;
	}

	public void setEnvironmentVariables(Map<String, String> environmentVariables) {
		this.environmentVariables = environmentVariables;
	}

	public Map<String, String> getLabels() {
		return labels;
	}

	public void setLabels(Map<String, String> labels) {
		this.labels = labels;
	}

	public Set<String> getCommands() {
		return commands;
	}

	public void setCommands(Set<String> commands) {
		this.commands = commands;
	}

	public String getTaskDefinitionArn() {
		return taskDefinitionArn;
	}

	public void setTaskDefinitionArn(String taskDefinitionArn) {
		this.taskDefinitionArn = taskDefinitionArn;
	}

	public String getServiceArn() {
		return serviceArn;
	}

	public void setServiceArn(String serviceArn) {
		this.serviceArn = serviceArn;
	}

	public String getTaskArn() {
		return taskArn;
	}

	public void setTaskArn(String taskArn) {
		this.taskArn = taskArn;
	}

	public String getTargetGroupArn() {
		return targetGroupArn;
	}

	public void setTargetGroupArn(String targetGroupArn) {
		this.targetGroupArn = targetGroupArn;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}

	public Instant getLastDeployedAt() {
		return lastDeployedAt;
	}

	public void setLastDeployedAt(Instant lastDeployedAt) {
		this.lastDeployedAt = lastDeployedAt;
	}

	public Long getDeploymentCount() {
		return deploymentCount;
	}

	public void setDeploymentCount(Long deploymentCount) {
		this.deploymentCount = deploymentCount;
	}

	public Boolean getSslEnabled() {
		return sslEnabled;
	}

	public void setSslEnabled(Boolean sslEnabled) {
		this.sslEnabled = sslEnabled;
	}

	public String getSslCertificateArn() {
		return sslCertificateArn;
	}

	public void setSslCertificateArn(String sslCertificateArn) {
		this.sslCertificateArn = sslCertificateArn;
	}

	public void setContainerId(String containerId) {
		this.containerId = containerId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public void setStatus(ContainerStatus status) {
		this.status = status;
	}

	public void setHealthCheck(HealthCheckConfig healthCheck) {
		this.healthCheck = healthCheck;
	}

	public void setAutoScaling(AutoScalingConfig autoScaling) {
		this.autoScaling = autoScaling;
	}

	public void setResourceUsage(ResourceUsage resourceUsage) {
		this.resourceUsage = resourceUsage;
	}

	@DynamoDbPartitionKey
    @DynamoDbAttribute("containerId")
    public String getContainerId() {
        return containerId;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = "UserIdIndex")
    @DynamoDbAttribute("userId")
    public String getUserId() {
        return userId;
    }

    @DynamoDbAttribute("status")
    public ContainerStatus getStatus() {
        return status;
    }

    @DynamoDbAttribute("healthCheck")
    public HealthCheckConfig getHealthCheck() {
        return healthCheck;
    }

    @DynamoDbAttribute("autoScaling")
    public AutoScalingConfig getAutoScaling() {
        return autoScaling;
    }

    @DynamoDbAttribute("resourceUsage")
    public ResourceUsage getResourceUsage() {
        return resourceUsage;
    }

    public enum ContainerStatus {
        CREATING, STARTING, RUNNING, STOPPING, STOPPED, FAILED, UPDATING, DELETING
    }


    @DynamoDbBean
    public static class HealthCheckConfig {
        private String path;
        private Integer interval;
        private Integer timeout;
        private Integer healthyThreshold;
        private Integer unhealthyThreshold;
        private String protocol; // HTTP, HTTPS, TCP
		public String getPath() {
			return path;
		}
		public void setPath(String path) {
			this.path = path;
		}
		public Integer getInterval() {
			return interval;
		}
		public void setInterval(Integer interval) {
			this.interval = interval;
		}
		public Integer getTimeout() {
			return timeout;
		}
		public void setTimeout(Integer timeout) {
			this.timeout = timeout;
		}
		public Integer getHealthyThreshold() {
			return healthyThreshold;
		}
		public void setHealthyThreshold(Integer healthyThreshold) {
			this.healthyThreshold = healthyThreshold;
		}
		public Integer getUnhealthyThreshold() {
			return unhealthyThreshold;
		}
		public void setUnhealthyThreshold(Integer unhealthyThreshold) {
			this.unhealthyThreshold = unhealthyThreshold;
		}
		public String getProtocol() {
			return protocol;
		}
		public void setProtocol(String protocol) {
			this.protocol = protocol;
		}
        
        
    }

   
    @DynamoDbBean
    public static class AutoScalingConfig {
        private Boolean enabled;
        private Integer minInstances;
        private Integer maxInstances;
        private Integer targetCpuPercent;
        private Integer targetMemoryPercent;
        private Integer scaleOutCooldown;
        private Integer scaleInCooldown;
		public Boolean getEnabled() {
			return enabled;
		}
		public void setEnabled(Boolean enabled) {
			this.enabled = enabled;
		}
		public Integer getMinInstances() {
			return minInstances;
		}
		public void setMinInstances(Integer minInstances) {
			this.minInstances = minInstances;
		}
		public Integer getMaxInstances() {
			return maxInstances;
		}
		public void setMaxInstances(Integer maxInstances) {
			this.maxInstances = maxInstances;
		}
		public Integer getTargetCpuPercent() {
			return targetCpuPercent;
		}
		public void setTargetCpuPercent(Integer targetCpuPercent) {
			this.targetCpuPercent = targetCpuPercent;
		}
		public Integer getTargetMemoryPercent() {
			return targetMemoryPercent;
		}
		public void setTargetMemoryPercent(Integer targetMemoryPercent) {
			this.targetMemoryPercent = targetMemoryPercent;
		}
		public Integer getScaleOutCooldown() {
			return scaleOutCooldown;
		}
		public void setScaleOutCooldown(Integer scaleOutCooldown) {
			this.scaleOutCooldown = scaleOutCooldown;
		}
		public Integer getScaleInCooldown() {
			return scaleInCooldown;
		}
		public void setScaleInCooldown(Integer scaleInCooldown) {
			this.scaleInCooldown = scaleInCooldown;
		}
    
    
    
    }

   
    @DynamoDbBean
    public static class ResourceUsage {
        private Double avgCpuPercent;
        private Double avgMemoryPercent;
        private Long totalRequests;
        private Long totalBandwidthBytes;
        private Instant measurementPeriodStart;
        private Instant measurementPeriodEnd;
		public Double getAvgCpuPercent() {
			return avgCpuPercent;
		}
		public void setAvgCpuPercent(Double avgCpuPercent) {
			this.avgCpuPercent = avgCpuPercent;
		}
		public Double getAvgMemoryPercent() {
			return avgMemoryPercent;
		}
		public void setAvgMemoryPercent(Double avgMemoryPercent) {
			this.avgMemoryPercent = avgMemoryPercent;
		}
		public Long getTotalRequests() {
			return totalRequests;
		}
		public void setTotalRequests(Long totalRequests) {
			this.totalRequests = totalRequests;
		}
		public Long getTotalBandwidthBytes() {
			return totalBandwidthBytes;
		}
		public void setTotalBandwidthBytes(Long totalBandwidthBytes) {
			this.totalBandwidthBytes = totalBandwidthBytes;
		}
		public Instant getMeasurementPeriodStart() {
			return measurementPeriodStart;
		}
		public void setMeasurementPeriodStart(Instant measurementPeriodStart) {
			this.measurementPeriodStart = measurementPeriodStart;
		}
		public Instant getMeasurementPeriodEnd() {
			return measurementPeriodEnd;
		}
		public void setMeasurementPeriodEnd(Instant measurementPeriodEnd) {
			this.measurementPeriodEnd = measurementPeriodEnd;
		}
    
    }
}