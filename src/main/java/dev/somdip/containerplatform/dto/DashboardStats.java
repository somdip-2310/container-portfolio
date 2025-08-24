package dev.somdip.containerplatform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStats {
    private long totalContainers;
    private long runningContainers;
    private long stoppedContainers;
    private long containerGrowth;
    private double cpuUsagePercent;
    private double memoryUsageGB;
    private double totalCpuVCores;
    private double totalMemoryGB;
    private List<RecentActivity> recentDeployments;
}
