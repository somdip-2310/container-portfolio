package dev.somdip.containerplatform.service;

import dev.somdip.containerplatform.dto.ContainerMetrics;
import dev.somdip.containerplatform.model.Container;
import dev.somdip.containerplatform.repository.ContainerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetricsService {

    private final CloudWatchClient cloudWatchClient;
    private final ContainerRepository containerRepository;
    
    public Map<String, Object> getContainerMetrics(List<String> containerIds) {
        Map<String, Object> allMetrics = new HashMap<>();
        
        for (String containerId : containerIds) {
            try {
                ContainerMetrics metrics = fetchContainerMetrics(containerId);
                allMetrics.put(containerId, metrics);
            } catch (Exception e) {
                log.error("Error fetching metrics for container: {}", containerId, e);
                allMetrics.put(containerId, createEmptyMetrics());
            }
        }
        
        return allMetrics;
    }
    
    public Map<String, Object> getDashboardMetrics(String userId) {
        try {
            List<Container> userContainers = containerRepository.findByUserId(userId);
            
            // Aggregate metrics
            double totalCpuUsage = 0;
            double totalMemoryUsage = 0;
            int runningContainers = 0;
            
            for (Container container : userContainers) {
                if (container.getStatus() == Container.ContainerStatus.RUNNING) {
                    runningContainers++;
                    ContainerMetrics metrics = fetchContainerMetrics(container.getContainerId());
                    totalCpuUsage += metrics.getCpuUsage();
                    totalMemoryUsage += metrics.getMemoryUsage();
                }
            }
            
            return Map.of(
                "totalContainers", userContainers.size(),
                "runningContainers", runningContainers,
                "totalCpuUsage", totalCpuUsage,
                "totalMemoryUsage", totalMemoryUsage,
                "avgCpuUsage", runningContainers > 0 ? totalCpuUsage / runningContainers : 0,
                "avgMemoryUsage", runningContainers > 0 ? totalMemoryUsage / runningContainers : 0
            );
            
        } catch (Exception e) {
            log.error("Error fetching dashboard metrics for user: {}", userId, e);
            return Map.of(
                "totalContainers", 0,
                "runningContainers", 0,
                "totalCpuUsage", 0.0,
                "totalMemoryUsage", 0.0
            );
        }
    }
    
    public Map<String, Object> getAllUserMetrics(String userId) {
        try {
            List<Container> userContainers = containerRepository.findByUserId(userId);
            List<String> containerIds = userContainers.stream()
                .map(Container::getContainerId)
                .collect(Collectors.toList());
                
            return getContainerMetrics(containerIds);
            
        } catch (Exception e) {
            log.error("Error fetching all metrics for user: {}", userId, e);
            return new HashMap<>();
        }
    }
    
    private ContainerMetrics fetchContainerMetrics(String containerId) {
        // Get container info for limits and service name
        Container container = containerRepository.findById(containerId)
            .orElseThrow(() -> new RuntimeException("Container not found"));

        // Use actual resource usage data if available
        if (container.getResourceUsage() != null) {
            return ContainerMetrics.builder()
                .containerId(containerId)
                .containerName(container.getName())
                .cpuUsage(container.getResourceUsage().getAvgCpuPercent())
                .memoryUsage(container.getResourceUsage().getAvgMemoryPercent())
                .cpuLimit(container.getCpu())
                .memoryLimit(container.getMemory())
                .timestamp(Instant.now())
                .status(container.getStatus() != null ? container.getStatus().name() : "UNKNOWN")
                .build();
        }

        // Otherwise fetch from CloudWatch
        Instant endTime = Instant.now();
        Instant startTime = endTime.minus(5, ChronoUnit.MINUTES);

        String serviceName = extractServiceName(container.getServiceArn());
        if (serviceName == null) {
            return createEmptyMetrics();
        }

        // Fetch CPU metrics
        GetMetricStatisticsRequest cpuRequest = GetMetricStatisticsRequest.builder()
            .namespace("AWS/ECS")
            .metricName("CPUUtilization")
            .dimensions(
                Dimension.builder().name("ServiceName").value(serviceName).build(),
                Dimension.builder().name("ClusterName").value("somdip-dev-cluster").build()
            )
            .startTime(startTime)
            .endTime(endTime)
            .period(60) // 1 minute
            .statistics(Statistic.AVERAGE)
            .build();

        GetMetricStatisticsResponse cpuResponse = cloudWatchClient.getMetricStatistics(cpuRequest);

        // Fetch Memory metrics
        GetMetricStatisticsRequest memoryRequest = GetMetricStatisticsRequest.builder()
            .namespace("AWS/ECS")
            .metricName("MemoryUtilization")
            .dimensions(
                Dimension.builder().name("ServiceName").value(serviceName).build(),
                Dimension.builder().name("ClusterName").value("somdip-dev-cluster").build()
            )
            .startTime(startTime)
            .endTime(endTime)
            .period(60) // 1 minute
            .statistics(Statistic.AVERAGE)
            .build();

        GetMetricStatisticsResponse memoryResponse = cloudWatchClient.getMetricStatistics(memoryRequest);

        // Get the latest values
        double cpuUsage = cpuResponse.datapoints().stream()
            .max(Comparator.comparing(Datapoint::timestamp))
            .map(Datapoint::average)
            .orElse(0.0);

        double memoryUsage = memoryResponse.datapoints().stream()
            .max(Comparator.comparing(Datapoint::timestamp))
            .map(Datapoint::average)
            .orElse(0.0);

        return ContainerMetrics.builder()
            .containerId(containerId)
            .containerName(container.getName())
            .cpuUsage(cpuUsage)
            .memoryUsage(memoryUsage)
            .cpuLimit(container.getCpu())
            .memoryLimit(container.getMemory())
            .timestamp(Instant.now())
            .status(container.getStatus() != null ? container.getStatus().name() : "UNKNOWN")
            .build();
    }

    private String extractServiceName(String serviceArn) {
        // Extract service name from ARN like:
        // arn:aws:ecs:us-east-1:257394460825:service/somdip-dev-cluster/service-{containerId}
        if (serviceArn == null) return null;
        String[] parts = serviceArn.split("/");
        return parts.length >= 3 ? parts[2] : null;
    }
    
    private ContainerMetrics createEmptyMetrics() {
        return ContainerMetrics.builder()
            .cpuUsage(0.0)
            .memoryUsage(0.0)
            .cpuLimit(0)
            .memoryLimit(0)
            .timestamp(Instant.now())
            .status("UNKNOWN")
            .build();
    }

    public void updateContainerMetrics(String containerId) {
        try {
            Container container = containerRepository.findById(containerId)
                .orElseThrow(() -> new RuntimeException("Container not found"));

            // Skip if container is not running
            if (container.getStatus() != Container.ContainerStatus.RUNNING) {
                return;
            }

            // Fetch metrics from CloudWatch
            ContainerMetrics metrics = fetchContainerMetricsFromCloudWatch(container);

            // Update container resourceUsage
            Container.ResourceUsage resourceUsage = container.getResourceUsage();
            if (resourceUsage == null) {
                resourceUsage = new Container.ResourceUsage();
            }

            resourceUsage.setAvgCpuPercent(metrics.getCpuUsage());
            resourceUsage.setAvgMemoryPercent(metrics.getMemoryUsage());
            resourceUsage.setMeasurementPeriodStart(Instant.now().minus(5, ChronoUnit.MINUTES));
            resourceUsage.setMeasurementPeriodEnd(Instant.now());

            container.setResourceUsage(resourceUsage);
            containerRepository.save(container);

            log.info("Updated metrics for container {}: CPU={}%, Memory={}%",
                containerId, metrics.getCpuUsage(), metrics.getMemoryUsage());

        } catch (Exception e) {
            log.error("Error updating metrics for container: {}", containerId, e);
        }
    }

    public List<Container> updateAllUserContainerMetrics(String userId) {
        try {
            List<Container> userContainers = containerRepository.findByUserId(userId);
            for (Container container : userContainers) {
                if (container.getStatus() == Container.ContainerStatus.RUNNING) {
                    updateContainerMetrics(container.getContainerId());
                }
            }
            // Reload containers to get updated metrics
            return containerRepository.findByUserId(userId);
        } catch (Exception e) {
            log.error("Error updating metrics for user containers: {}", userId, e);
            return List.of();
    }
        }
    
    public List<Container> getUserContainers(String userId) {
        return containerRepository.findByUserId(userId);
    }
    
    @Async
    public void updateAllUserContainerMetricsAsync(String userId) {
        try {
            List<Container> userContainers = containerRepository.findByUserId(userId);
            for (Container container : userContainers) {
                if (container.getStatus() == Container.ContainerStatus.RUNNING) {
                    updateContainerMetrics(container.getContainerId());
                }
            }
        } catch (Exception e) {
            log.error("Error updating metrics asynchronously for user containers: {}", userId, e);
        }
    }

    private ContainerMetrics fetchContainerMetricsFromCloudWatch(Container container) {
        Instant endTime = Instant.now();
        Instant startTime = endTime.minus(5, ChronoUnit.MINUTES);

        String serviceName = extractServiceName(container.getServiceArn());
        if (serviceName == null) {
            return createEmptyMetrics();
        }

        try {
            // Fetch CPU metrics
            GetMetricStatisticsRequest cpuRequest = GetMetricStatisticsRequest.builder()
                .namespace("AWS/ECS")
                .metricName("CPUUtilization")
                .dimensions(
                    Dimension.builder().name("ServiceName").value(serviceName).build(),
                    Dimension.builder().name("ClusterName").value("somdip-dev-cluster").build()
                )
                .startTime(startTime)
                .endTime(endTime)
                .period(60)
                .statistics(Statistic.AVERAGE)
                .build();

            GetMetricStatisticsResponse cpuResponse = cloudWatchClient.getMetricStatistics(cpuRequest);

            // Fetch Memory metrics
            GetMetricStatisticsRequest memoryRequest = GetMetricStatisticsRequest.builder()
                .namespace("AWS/ECS")
                .metricName("MemoryUtilization")
                .dimensions(
                    Dimension.builder().name("ServiceName").value(serviceName).build(),
                    Dimension.builder().name("ClusterName").value("somdip-dev-cluster").build()
                )
                .startTime(startTime)
                .endTime(endTime)
                .period(60)
                .statistics(Statistic.AVERAGE)
                .build();

            GetMetricStatisticsResponse memoryResponse = cloudWatchClient.getMetricStatistics(memoryRequest);

            // Get the latest values
            double cpuUsage = cpuResponse.datapoints().stream()
                .max(Comparator.comparing(Datapoint::timestamp))
                .map(Datapoint::average)
                .orElse(0.0);

            double memoryUsage = memoryResponse.datapoints().stream()
                .max(Comparator.comparing(Datapoint::timestamp))
                .map(Datapoint::average)
                .orElse(0.0);

            return ContainerMetrics.builder()
                .containerId(container.getContainerId())
                .containerName(container.getName())
                .cpuUsage(cpuUsage)
                .memoryUsage(memoryUsage)
                .cpuLimit(container.getCpu())
                .memoryLimit(container.getMemory())
                .timestamp(Instant.now())
                .status(container.getStatus() != null ? container.getStatus().name() : "UNKNOWN")
                .build();
        } catch (Exception e) {
            log.error("Error fetching CloudWatch metrics for container: {}", container.getContainerId(), e);
            return createEmptyMetrics();
        }
    }

}