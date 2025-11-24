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

    private static final Logger log = LoggerFactory.getLogger(DashboardService.class);
   

    public DashboardStats getDashboardStats(String userId) {
        try {
            // Get container counts
            List<Container> userContainers = containerRepository.findByUserId(userId);
            long totalContainers = userContainers.size();
            long runningContainers = userContainers.stream()
                .filter(c -> "RUNNING".equals(c.getStatus()))
                .count();
            long stoppedContainers = userContainers.stream()
                .filter(c -> "STOPPED".equals(c.getStatus()))
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
            Instant endTime = Instant.now();
            Instant startTime = endTime.minus(days, ChronoUnit.DAYS);
            
            // Get CPU metrics
            GetMetricStatisticsRequest cpuRequest = GetMetricStatisticsRequest.builder()
                .namespace("AWS/ECS")
                .metricName("CPUUtilization")
                .dimensions(Dimension.builder()
                    .name("ServiceName")
                    .value("user-" + userId)
                    .build())
                .startTime(startTime)
                .endTime(endTime)
                .period(3600) // 1 hour intervals
                .statistics(Statistic.AVERAGE)
                .build();
                
            GetMetricStatisticsResponse cpuResponse = cloudWatchClient.getMetricStatistics(cpuRequest);
            
            // Get Memory metrics
            GetMetricStatisticsRequest memoryRequest = GetMetricStatisticsRequest.builder()
                .namespace("AWS/ECS")
                .metricName("MemoryUtilization")
                .dimensions(Dimension.builder()
                    .name("ServiceName")
                    .value("user-" + userId)
                    .build())
                .startTime(startTime)
                .endTime(endTime)
                .period(3600) // 1 hour intervals
                .statistics(Statistic.AVERAGE)
                .build();
                
            GetMetricStatisticsResponse memoryResponse = cloudWatchClient.getMetricStatistics(memoryRequest);
            
            // Process and return data
            Map<String, List<Double>> usage = new HashMap<>();
            usage.put("cpu", cpuResponse.datapoints().stream()
                .sorted(Comparator.comparing(Datapoint::timestamp))
                .map(Datapoint::average)
                .collect(Collectors.toList()));
            usage.put("memory", memoryResponse.datapoints().stream()
                .sorted(Comparator.comparing(Datapoint::timestamp))
                .map(Datapoint::average)
                .collect(Collectors.toList()));
                
            return usage;
            
        } catch (Exception e) {
            log.error("Error getting resource usage history for user: {}", userId, e);
            return Map.of("cpu", new ArrayList<>(), "memory", new ArrayList<>());
        }
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
                    .action(deployment.getStatus())
                    .timestamp(deployment.getCreatedAt())
                    .status(deployment.getStatus())
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
        double usedMemory = 0;
        
        for (Container container : containers) {
            totalCpu += container.getCpu() / 1024.0; // Convert CPU units to vCPUs
            totalMemory += container.getMemory() / 1024.0; // Convert MB to GB
            
            if ("RUNNING".equals(container.getStatus())) {
                // In a real implementation, fetch actual usage from CloudWatch
                // For now, simulate with random values
                usedCpu += (container.getCpu() / 1024.0) * 0.45;
                usedMemory += (container.getMemory() / 1024.0) * 0.5;
            }
        }
        
        double cpuPercent = totalCpu > 0 ? (usedCpu / totalCpu) * 100 : 0;
        
        return ResourceUsage.builder()
            .cpuPercent(cpuPercent)
            .memoryGB(usedMemory)
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
                .status(d.getStatus())
                .build())
            .collect(Collectors.toList());
    }

}