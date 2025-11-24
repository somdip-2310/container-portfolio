package dev.somdip.containerplatform.service;

import dev.somdip.containerplatform.dto.DashboardStats;
import dev.somdip.containerplatform.dto.DeploymentTimelineEvent;
import dev.somdip.containerplatform.dto.ResourceUsage;
import dev.somdip.containerplatform.dto.RecentActivity;
import dev.somdip.containerplatform.model.Container;
import dev.somdip.containerplatform.model.Deployment;
import dev.somdip.containerplatform.repository.ContainerRepository;
import dev.somdip.containerplatform.repository.DeploymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
            Instant endTime = Instant.now();
            Instant startTime = endTime.minus(days, ChronoUnit.DAYS);

            // Get all user containers
            List<Container> userContainers = containerRepository.findByUserId(userId);

            // Aggregate metrics from all containers
            Map<Instant, Double> cpuByTime = new TreeMap<>();
            Map<Instant, Double> memoryByTime = new TreeMap<>();
            Map<Instant, Integer> countByTime = new TreeMap<>();

            for (Container container : userContainers) {
                if (container.getServiceArn() == null) continue;

                String serviceName = extractServiceName(container.getServiceArn());
                if (serviceName == null) continue;

                // Get CPU metrics for this container
                try {
                    GetMetricStatisticsRequest cpuRequest = GetMetricStatisticsRequest.builder()
                        .namespace("AWS/ECS")
                        .metricName("CPUUtilization")
                        .dimensions(
                            Dimension.builder().name("ServiceName").value(serviceName).build(),
                            Dimension.builder().name("ClusterName").value("somdip-dev-cluster").build()
                        )
                        .startTime(startTime)
                        .endTime(endTime)
                        .period(86400) // 1 day intervals for 7 days view
                        .statistics(Statistic.AVERAGE)
                        .build();

                    GetMetricStatisticsResponse cpuResponse = cloudWatchClient.getMetricStatistics(cpuRequest);

                    for (Datapoint dp : cpuResponse.datapoints()) {
                        cpuByTime.merge(dp.timestamp(), dp.average(), Double::sum);
                        countByTime.merge(dp.timestamp(), 1, Integer::sum);
                    }

                    // Get Memory metrics for this container
                    GetMetricStatisticsRequest memoryRequest = GetMetricStatisticsRequest.builder()
                        .namespace("AWS/ECS")
                        .metricName("MemoryUtilization")
                        .dimensions(
                            Dimension.builder().name("ServiceName").value(serviceName).build(),
                            Dimension.builder().name("ClusterName").value("somdip-dev-cluster").build()
                        )
                        .startTime(startTime)
                        .endTime(endTime)
                        .period(86400) // 1 day intervals for 7 days view
                        .statistics(Statistic.AVERAGE)
                        .build();

                    GetMetricStatisticsResponse memoryResponse = cloudWatchClient.getMetricStatistics(memoryRequest);

                    for (Datapoint dp : memoryResponse.datapoints()) {
                        memoryByTime.merge(dp.timestamp(), dp.average(), Double::sum);
                    }
                } catch (Exception e) {
                    log.warn("Failed to get metrics for container: {}", container.getContainerId(), e);
                }
            }

            // Calculate averages and convert to lists
            List<Double> cpuData = cpuByTime.entrySet().stream()
                .map(e -> countByTime.getOrDefault(e.getKey(), 1) > 0 ?
                    e.getValue() / countByTime.get(e.getKey()) : e.getValue())
                .collect(Collectors.toList());

            List<Double> memoryData = memoryByTime.entrySet().stream()
                .map(e -> countByTime.getOrDefault(e.getKey(), 1) > 0 ?
                    e.getValue() / countByTime.get(e.getKey()) : e.getValue())
                .collect(Collectors.toList());

            Map<String, List<Double>> usage = new HashMap<>();
            usage.put("cpu", cpuData.isEmpty() ? List.of(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0) : cpuData);
            usage.put("memory", memoryData.isEmpty() ? List.of(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0) : memoryData);

            return usage;

        } catch (Exception e) {
            log.error("Error getting resource usage history for user: {}", userId, e);
            return Map.of("cpu", List.of(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
                         "memory", List.of(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0));
        }
    }

    public Map<String, List<Double>> getNetworkIOHistory(String userId, int days) {
        try {
            Instant endTime = Instant.now();
            Instant startTime = endTime.minus(days, ChronoUnit.DAYS);

            // Get all user containers
            List<Container> userContainers = containerRepository.findByUserId(userId);

            // Aggregate network metrics from all containers
            Map<Instant, Double> networkInByTime = new TreeMap<>();
            Map<Instant, Double> networkOutByTime = new TreeMap<>();

            for (Container container : userContainers) {
                if (container.getServiceArn() == null) continue;

                String serviceName = extractServiceName(container.getServiceArn());
                if (serviceName == null) continue;

                try {
                    // Get Network In metrics
                    GetMetricStatisticsRequest networkInRequest = GetMetricStatisticsRequest.builder()
                        .namespace("AWS/ECS")
                        .metricName("NetworkRxBytes")
                        .dimensions(
                            Dimension.builder().name("ServiceName").value(serviceName).build(),
                            Dimension.builder().name("ClusterName").value("somdip-dev-cluster").build()
                        )
                        .startTime(startTime)
                        .endTime(endTime)
                        .period(86400) // 1 day intervals
                        .statistics(Statistic.SUM)
                        .build();

                    GetMetricStatisticsResponse networkInResponse = cloudWatchClient.getMetricStatistics(networkInRequest);

                    for (Datapoint dp : networkInResponse.datapoints()) {
                        // Convert bytes to MB
                        double megabytes = dp.sum() / (1024.0 * 1024.0);
                        networkInByTime.merge(dp.timestamp(), megabytes, Double::sum);
                    }

                    // Get Network Out metrics
                    GetMetricStatisticsRequest networkOutRequest = GetMetricStatisticsRequest.builder()
                        .namespace("AWS/ECS")
                        .metricName("NetworkTxBytes")
                        .dimensions(
                            Dimension.builder().name("ServiceName").value(serviceName).build(),
                            Dimension.builder().name("ClusterName").value("somdip-dev-cluster").build()
                        )
                        .startTime(startTime)
                        .endTime(endTime)
                        .period(86400) // 1 day intervals
                        .statistics(Statistic.SUM)
                        .build();

                    GetMetricStatisticsResponse networkOutResponse = cloudWatchClient.getMetricStatistics(networkOutRequest);

                    for (Datapoint dp : networkOutResponse.datapoints()) {
                        // Convert bytes to MB
                        double megabytes = dp.sum() / (1024.0 * 1024.0);
                        networkOutByTime.merge(dp.timestamp(), megabytes, Double::sum);
                    }

                } catch (Exception e) {
                    log.warn("Failed to get network metrics for container: {}", container.getContainerId(), e);
                }
            }

            // Convert to lists
            List<Double> networkInData = new ArrayList<>(networkInByTime.values());
            List<Double> networkOutData = new ArrayList<>(networkOutByTime.values());

            Map<String, List<Double>> networkIO = new HashMap<>();
            networkIO.put("in", networkInData.isEmpty() ? List.of(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0) : networkInData);
            networkIO.put("out", networkOutData.isEmpty() ? List.of(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0) : networkOutData);

            return networkIO;

        } catch (Exception e) {
            log.error("Error getting network I/O history for user: {}", userId, e);
            return Map.of("in", List.of(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
                         "out", List.of(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0));
        }
    }

    private String extractServiceName(String serviceArn) {
        // Extract service name from ARN like:
        // arn:aws:ecs:us-east-1:257394460825:service/somdip-dev-cluster/service-{containerId}
        if (serviceArn == null) return null;
        String[] parts = serviceArn.split("/");
        return parts.length >= 3 ? parts[2] : null;
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
                    .action(deployment.getStatus() != null ? deployment.getStatus().name() : "UNKNOWN")
                    .timestamp(deployment.getCreatedAt())
                    .status(deployment.getStatus() != null ? deployment.getStatus().name() : "UNKNOWN")
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
            
            if (container.getStatus() == Container.ContainerStatus.RUNNING) {
                // Get actual resource usage from container's resource usage data
                if (container.getResourceUsage() != null) {
                    usedCpu += (container.getCpu() / 1024.0) * (container.getResourceUsage().getAvgCpuPercent() / 100.0);
                    usedMemory += (container.getMemory() / 1024.0) * (container.getResourceUsage().getAvgMemoryPercent() / 100.0);
                }
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
                .status(d.getStatus() != null ? d.getStatus().name() : "UNKNOWN")
                .build())
            .collect(Collectors.toList());
    }

    public List<DeploymentTimelineEvent> getDeploymentTimeline(String userId, int days) {
        List<DeploymentTimelineEvent> timeline = new ArrayList<>();

        try {
            Instant cutoffTime = Instant.now().minus(days, ChronoUnit.DAYS);

            // Get recent deployments
            List<Deployment> deployments = deploymentRepository.findRecentByUserId(userId, 50);

            for (Deployment deployment : deployments) {
                if (deployment.getCreatedAt() != null && deployment.getCreatedAt().isAfter(cutoffTime)) {
                    String eventType = "DEPLOYED";
                    String description = "Container deployed";

                    if (deployment.getStatus() != null) {
                        switch (deployment.getStatus()) {
                            case COMPLETED:
                                eventType = "STARTED";
                                description = "Container started successfully";
                                break;
                            case FAILED:
                                eventType = "FAILED";
                                description = "Deployment failed";
                                break;
                            case IN_PROGRESS:
                                eventType = "DEPLOYING";
                                description = "Deployment in progress";
                                break;
                            default:
                                break;
                        }
                    }

                    timeline.add(DeploymentTimelineEvent.builder()
                        .containerName(deployment.getContainerName())
                        .containerId(deployment.getContainerId())
                        .eventType(eventType)
                        .status(deployment.getStatus() != null ? deployment.getStatus().name() : "UNKNOWN")
                        .timestamp(deployment.getCreatedAt())
                        .description(description)
                        .build());
                }
            }

            // Get container state changes (stopped containers)
            List<Container> userContainers = containerRepository.findByUserId(userId);
            for (Container container : userContainers) {
                if (container.getStatus() == Container.ContainerStatus.STOPPED &&
                    container.getUpdatedAt() != null &&
                    container.getUpdatedAt().isAfter(cutoffTime)) {

                    timeline.add(DeploymentTimelineEvent.builder()
                        .containerName(container.getName())
                        .containerId(container.getContainerId())
                        .eventType("STOPPED")
                        .status("STOPPED")
                        .timestamp(container.getUpdatedAt())
                        .description("Container stopped")
                        .build());
                }
            }

            // Sort by timestamp (most recent first)
            timeline.sort((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()));

            // Limit to most recent 20 events
            return timeline.stream().limit(20).collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Error getting deployment timeline for user: {}", userId, e);
            return new ArrayList<>();
        }
    }
}