package dev.somdip.containerplatform.dto.deployment;

import dev.somdip.containerplatform.service.ContainerHealthCheckService;
import java.time.Instant;

public class HealthStatusResponse {
    private String containerId;
    private boolean healthy;
    private int consecutiveFailures;
    private int consecutiveSuccesses;
    private Instant lastCheckTime;
    private String lastError;
    private ResourceMetricsResponse resourceMetrics;
    
    public static HealthStatusResponse from(ContainerHealthCheckService.HealthStatus status) {
        HealthStatusResponse response = new HealthStatusResponse();
        response.containerId = status.getContainerId();
        response.healthy = status.isHealthy();
        response.consecutiveFailures = status.getConsecutiveFailures();
        response.consecutiveSuccesses = status.getConsecutiveSuccesses();
        response.lastCheckTime = status.getLastCheckTime();
        response.lastError = status.getLastError();
        
        if (status.getResourceMetrics() != null) {
            response.resourceMetrics = ResourceMetricsResponse.from(status.getResourceMetrics());
        }
        
        return response;
    }
    
    // Getters and setters
    public String getContainerId() { return containerId; }
    public void setContainerId(String containerId) { this.containerId = containerId; }
    public boolean isHealthy() { return healthy; }
    public void setHealthy(boolean healthy) { this.healthy = healthy; }
    public int getConsecutiveFailures() { return consecutiveFailures; }
    public void setConsecutiveFailures(int consecutiveFailures) { this.consecutiveFailures = consecutiveFailures; }
    public int getConsecutiveSuccesses() { return consecutiveSuccesses; }
    public void setConsecutiveSuccesses(int consecutiveSuccesses) { this.consecutiveSuccesses = consecutiveSuccesses; }
    public Instant getLastCheckTime() { return lastCheckTime; }
    public void setLastCheckTime(Instant lastCheckTime) { this.lastCheckTime = lastCheckTime; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public ResourceMetricsResponse getResourceMetrics() { return resourceMetrics; }
    public void setResourceMetrics(ResourceMetricsResponse resourceMetrics) { this.resourceMetrics = resourceMetrics; }
}