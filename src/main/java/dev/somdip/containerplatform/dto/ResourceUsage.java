package dev.somdip.containerplatform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResourceUsage {
    private double cpuPercent;
    private double memoryGB;
    private double totalCpu;
    private double totalMemory;
}