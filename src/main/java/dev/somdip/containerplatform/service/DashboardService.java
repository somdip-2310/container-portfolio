package dev.somdip.containerplatform.service;

import dev.somdip.containerplatform.controller.HealthController;
import dev.somdip.containerplatform.dto.DashboardStats;
import dev.somdip.containerplatform.dto.ResourceUsage;
import dev.somdip.containerplatform.dto.RecentActivity;
import dev.somdip.containerplatform.model.Container;
import dev.somdip.containerplatform.model.Deployment;
import dev.somdip.containerplatform.repository.ContainerRepository;
import dev.somdip.containerplatform.repository.DeploymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.*;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.DescribeServicesRequest;
import software.amazon.awssdk.services.ecs.model.DescribeServicesResponse;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {
    

    private final ContainerRepository containerRepository;
    private final DeploymentRepository deploymentRepository;
    private final EcsClient ecsClient;
    private final CloudWatchClient cloudWatchClient;

   

    public DashboardStats getDashboardStats(String userId) {
        try {
            // Get container counts
            List<Container> userContainers = containerRepository.findByUserId(userId);
            long totalContainers = userContainers.size();
            long runningContainers = userContainers.stream()
                .filter(c -> c.getStatus() == Container.ContainerStatus.RUNNING)
                .count();
            long stoppedContainers = userContainers.stream()
                .filter(c -> c.getStatus() == Container.ContainerStatus.STOPPED)
                .count();
            
            // Get resource usage
            ResourceUsage resourceUsage = calculateResourceUsage(userContainers);
            
            // Get recent deployments
            List<Deployment> recentDeployments = deploymentRepository.findRecentByUserId(userId, 5);
            
            // Get last month's container count for comparison
            long lastMonthCount = getLastMonthContainerCount(userId);
            long containerGrowth = totalContainers - lastMonthCount;
            
            return DashboardStats.builder()
                .totalContainers(totalContainers)
                .runningContainers(runningContainers)
                .stoppedContainers(stoppedContainers)
                .containerGrowth(containerGrowth)
                .cpuUsagePercent(resourceUsage.getCpuPercent())
                .memoryUsageGB(resourceUsage.getMemoryGB())
                .totalCpuVCores(resourceUsage.getTotalCpu())
                .totalMemoryGB(resourceUsage.getTotalMemory())
                .recentDeployments(mapDeployments(recentDeployments))
                .build();
                
        } catch (Exception e) {
            log.error("Error getting dashboard stats for user: {}", userId, e);
            return DashboardStats.builder()
                .totalContainers(0L)
                .runningContainers(0L)
                .stoppedContainers(0L)
                .containerGrowth(0L)
                .cpuUsagePercent(0.0)
                .memoryUsageGB(0.0)
                .totalCpuVCores(0.0)
                .totalMemoryGB(0.0)
                .recentDeployments(new ArrayList<>())
                .build();
        }
    }
    


    public Map<String, List<Double>> getResourceUsageHistory(String userId, int days) {
        try {
            // Get all user containers to calculate average usage
            List<Container> containers = containerRepository.findByUserId(userId);

            if (containers.isEmpty()) {
                return Map.of("cpu", Arrays.asList(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
                             "memory", Arrays.asList(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0));
            }

            // Calculate average current usage across all containers
            double avgCpu = containers.stream()
                .filter(c -> c.getResourceUsage() != null && c.getResourceUsage().getAvgCpuPercent() != null)
                .mapToDouble(c -> c.getResourceUsage().getAvgCpuPercent())
                .average()
                .orElse(0.0);

            double avgMemory = containers.stream()
                .filter(c -> c.getResourceUsage() != null && c.getResourceUsage().getAvgMemoryPercent() != null)
                .mapToDouble(c -> c.getResourceUsage().getAvgMemoryPercent())
                .average()
                .orElse(0.0);

            // Generate simulated 7-day history showing current values with slight variations
            List<Double> cpuHistory = generateHistoryData(avgCpu, 7);
            List<Double> memoryHistory = generateHistoryData(avgMemory, 7);

            Map<String, List<Double>> usage = new HashMap<>();
            usage.put("cpu", cpuHistory);
            usage.put("memory", memoryHistory);

            return usage;

        } catch (Exception e) {
            log.error("Error getting resource usage history for user: {}", userId, e);
            return Map.of("cpu", Arrays.asList(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
                         "memory", Arrays.asList(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0));
        }
    }

    
    /**
     * Get resource usage history using pre-loaded containers (avoids eventual consistency issues)
     */
    public Map<String, List<Double>> getResourceUsageHistory(List<Container> containers, int days) {
        try {
            if (containers.isEmpty()) {
                return createEmptyHistory();
            }

            // Calculate average current usage across all containers
            double avgCpu = containers.stream()
                .filter(c -> c.getResourceUsage() != null && c.getResourceUsage().getAvgCpuPercent() != null)
                .mapToDouble(c -> c.getResourceUsage().getAvgCpuPercent())
                .average()
                .orElse(0.0);

            double avgMemory = containers.stream()
                .filter(c -> c.getResourceUsage() != null && c.getResourceUsage().getAvgMemoryPercent() != null)
                .mapToDouble(c -> c.getResourceUsage().getAvgMemoryPercent())
                .average()
                .orElse(0.0);

            // Generate simulated 7-day history showing current values with slight variations
            List<Double> cpuHistory = generateHistoryData(avgCpu, days);
            List<Double> memoryHistory = generateHistoryData(avgMemory, days);
            
            // Generate network I/O history (simulated for now)
            List<Double> networkInHistory = generateHistoryData(0.5, days);  // 0.5 MB/s average
            List<Double> networkOutHistory = generateHistoryData(0.3, days); // 0.3 MB/s average

            Map<String, List<Double>> usage = new HashMap<>();
            usage.put("cpu", cpuHistory);
            usage.put("memory", memoryHistory);
            usage.put("networkIn", networkInHistory);
            usage.put("networkOut", networkOutHistory);

            return usage;

        } catch (Exception e) {
            log.error("Error getting resource usage history from containers", e);
            return createEmptyHistory();
        }
    }
    
    private Map<String, List<Double>> createEmptyHistory() {
        List<Double> zeros = Arrays.asList(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
        return Map.of(
            "cpu", zeros,
            "memory", zeros,
            "networkIn", zeros,
            "networkOut", zeros
        );
    }
    private List<Double> generateHistoryData(double currentValue, int days) {
        // Generate realistic-looking history data with slight variations around current value
        List<Double> history = new ArrayList<>();
        Random random = new Random();

        for (int i = 0; i < days; i++) {
            // Add ±20% variation
            double variation = (random.nextDouble() - 0.5) * 0.4;
            double value = Math.max(0, currentValue * (1 + variation));
            history.add(Math.round(value * 100.0) / 100.0); // Round to 2 decimal places
        }

        return history;
    }


    public List<RecentActivity> getRecentActivity(String userId, int limit) {
        List<RecentActivity> activities = new ArrayList<>();
        
        try {
            // Get recent deployments
            List<Deployment> deployments = deploymentRepository.findRecentByUserId(userId, limit);
            for (Deployment deployment : deployments) {
                activities.add(RecentActivity.builder()
                    .type("deployment")
                    .containerName(deployment.getContainerName())
                    .action(deployment.getStatus().name())
                    .timestamp(deployment.getCreatedAt())
                    .status(deployment.getStatus().name())
                    .build());
            }
            
            // Sort by timestamp
            activities.sort((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()));
            
            // Limit results
            return activities.stream()
                .limit(limit)
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            log.error("Error getting recent activity for user: {}", userId, e);
            return new ArrayList<>();
        }
    }
    
    private ResourceUsage calculateResourceUsage(List<Container> containers) {
        double totalCpu = 0;
        double usedCpu = 0;
        double totalMemory = 0;
        double usedMemoryMB = 0;

        for (Container container : containers) {
            totalCpu += container.getCpu() / 1024.0; // Convert CPU units to vCPUs
            totalMemory += container.getMemory() / 1024.0; // Convert MB to GB

            // Use actual resource usage from container if available
            if (container.getResourceUsage() != null) {
                if (container.getResourceUsage().getAvgCpuPercent() != null) {
                    // Calculate actual CPU usage based on allocated CPU and usage percentage
                    double containerCpu = container.getCpu() / 1024.0;
                    usedCpu += containerCpu * (container.getResourceUsage().getAvgCpuPercent() / 100.0);
                }
                if (container.getResourceUsage().getAvgMemoryPercent() != null) {
                    // Calculate actual memory usage based on allocated memory and usage percentage
                    usedMemoryMB += container.getMemory() * (container.getResourceUsage().getAvgMemoryPercent() / 100.0);
                }
            }
        }

        double cpuPercent = totalCpu > 0 ? (usedCpu / totalCpu) * 100 : 0;
        double usedMemoryGB = usedMemoryMB / 1024.0;

        return ResourceUsage.builder()
            .cpuPercent(cpuPercent)
            .memoryGB(usedMemoryGB)
            .totalCpu(totalCpu)
            .totalMemory(totalMemory)
            .build();
    }
    
    private long getLastMonthContainerCount(String userId) {
        // In a real implementation, query historical data
        // For now, return a simulated value
        return 10L;
    }
    
    private List<RecentActivity> mapDeployments(List<Deployment> deployments) {
        return deployments.stream()
            .map(d -> RecentActivity.builder()
                .type("deployment")
                .containerName(d.getContainerName())
                .action("Deployed")
                .timestamp(d.getCreatedAt())
                .status(d.getStatus().name())
                .build())
            .collect(Collectors.toList());
    }

}