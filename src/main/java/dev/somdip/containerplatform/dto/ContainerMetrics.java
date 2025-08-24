package dev.somdip.containerplatform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContainerMetrics {
    private String containerId;
    private String containerName;
    private double cpuUsage;        // Percentage (0-100)
    private double memoryUsage;     // Percentage (0-100)
    private int cpuLimit;           // CPU units (1024 = 1 vCPU)
    private int memoryLimit;        // Memory in MB
    private Instant timestamp;
    private String status;
    
    // Calculated fields
    public double getCpuUsageVCores() {
        return (cpuUsage / 100.0) * (cpuLimit / 1024.0);
    }
    
    public double getMemoryUsageMB() {
        return (memoryUsage / 100.0) * memoryLimit;
    }
    
    public double getMemoryUsageGB() {
        return getMemoryUsageMB() / 1024.0;
    }
}