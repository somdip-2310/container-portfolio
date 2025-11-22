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
                if ("RUNNING".equals(container.getStatus())) {
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
        Instant endTime = Instant.now();
        Instant startTime = endTime.minus(5, ChronoUnit.MINUTES);
        
        // Fetch CPU metrics
        GetMetricStatisticsRequest cpuRequest = GetMetricStatisticsRequest.builder()
            .namespace("AWS/ECS")
            .metricName("CPUUtilization")
            .dimensions(
                Dimension.builder()
                    .name("ServiceName")
                    .value("container-" + containerId)
                    .build()
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
                Dimension.builder()
                    .name("ServiceName")
                    .value("container-" + containerId)
                    .build()
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
            
        // Get container info for limits
        Container container = containerRepository.findById(containerId)
            .orElseThrow(() -> new RuntimeException("Container not found"));
            
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
}