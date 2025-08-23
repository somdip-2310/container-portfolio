package dev.somdip.containerplatform.dto.deployment;

import dev.somdip.containerplatform.service.ContainerHealthCheckService;

public class ResourceMetricsResponse {
    private Double cpuUtilization;
    private Double memoryUtilization;
    private Double networkIn;
    private Double networkOut;
    
    public static ResourceMetricsResponse from(ContainerHealthCheckService.ResourceMetrics metrics) {
        ResourceMetricsResponse response = new ResourceMetricsResponse();
        response.cpuUtilization = metrics.getCpuUtilization();
        response.memoryUtilization = metrics.getMemoryUtilization();
        response.networkIn = metrics.getNetworkIn();
        response.networkOut = metrics.getNetworkOut();
        return response;
    }
    
    // Getters and setters
    public Double getCpuUtilization() { return cpuUtilization; }
    public void setCpuUtilization(Double cpuUtilization) { this.cpuUtilization = cpuUtilization; }
    public Double getMemoryUtilization() { return memoryUtilization; }
    public void setMemoryUtilization(Double memoryUtilization) { this.memoryUtilization = memoryUtilization; }
    public Double getNetworkIn() { return networkIn; }
    public void setNetworkIn(Double networkIn) { this.networkIn = networkIn; }
    public Double getNetworkOut() { return networkOut; }
    public void setNetworkOut(Double networkOut) { this.networkOut = networkOut; }
}