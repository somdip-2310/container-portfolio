package dev.somdip.containerplatform.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @DynamoDbBean
    public static class HealthCheckConfig {
        private String path;
        private Integer interval;
        private Integer timeout;
        private Integer healthyThreshold;
        private Integer unhealthyThreshold;
        private String protocol; // HTTP, HTTPS, TCP
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @DynamoDbBean
    public static class AutoScalingConfig {
        private Boolean enabled;
        private Integer minInstances;
        private Integer maxInstances;
        private Integer targetCpuPercent;
        private Integer targetMemoryPercent;
        private Integer scaleOutCooldown;
        private Integer scaleInCooldown;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @DynamoDbBean
    public static class ResourceUsage {
        private Double avgCpuPercent;
        private Double avgMemoryPercent;
        private Long totalRequests;
        private Long totalBandwidthBytes;
        private Instant measurementPeriodStart;
        private Instant measurementPeriodEnd;
    }
}