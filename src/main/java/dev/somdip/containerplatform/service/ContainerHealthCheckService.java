package dev.somdip.containerplatform.service;

import dev.somdip.containerplatform.model.Container;
import dev.somdip.containerplatform.repository.ContainerRepository;
import dev.somdip.containerplatform.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.*;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.DescribeTasksRequest;
import software.amazon.awssdk.services.ecs.model.DescribeTasksResponse;
import software.amazon.awssdk.services.ecs.model.Task;

import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

@Service
public class ContainerHealthCheckService {
    private static final Logger log = LoggerFactory.getLogger(ContainerHealthCheckService.class);

    private final ContainerRepository containerRepository;
    private final UserRepository userRepository;
    private final EcsClient ecsClient;
    private final CloudWatchClient cloudWatchClient;
    private final RestTemplate restTemplate;
    private final ScheduledExecutorService executorService;
    
    @Value("${aws.ecs.cluster}")
    private String clusterName;
    
    @Value("${aws.cloudwatch.namespace}")
    private String cloudWatchNamespace;
    
    // Cache for health status
    private final Map<String, HealthStatus> healthStatusCache = new ConcurrentHashMap<>();
    
    public ContainerHealthCheckService(ContainerRepository containerRepository,
                                     UserRepository userRepository,
                                     EcsClient ecsClient,
                                     CloudWatchClient cloudWatchClient) {
        this.containerRepository = containerRepository;
        this.userRepository = userRepository;
        this.ecsClient = ecsClient;
        this.cloudWatchClient = cloudWatchClient;
        this.restTemplate = new RestTemplate();
        this.executorService = Executors.newScheduledThreadPool(5);
    }
    
    /**
     * Start health monitoring for a container
     */
    public void startHealthMonitoring(String containerId) {
        log.info("Starting health monitoring for container: {}", containerId);
        
        Container container = containerRepository.findById(containerId)
            .orElseThrow(() -> new IllegalArgumentException("Container not found: " + containerId));
        
        // Initialize health status
        HealthStatus status = new HealthStatus(containerId);
        healthStatusCache.put(containerId, status);
        
        // Schedule health checks based on container configuration
        Container.HealthCheckConfig healthConfig = container.getHealthCheck();
        if (healthConfig == null) {
            healthConfig = createDefaultHealthCheckConfig();
        }
        
        int interval = healthConfig.getInterval() != null ? healthConfig.getInterval() : 30;
        
        executorService.scheduleWithFixedDelay(
            () -> performHealthCheck(container),
            30, // Initial delay
            interval,
            TimeUnit.SECONDS
        );
    }
    
    /**
     * Stop health monitoring for a container
     */
    public void stopHealthMonitoring(String containerId) {
        log.info("Stopping health monitoring for container: {}", containerId);
        healthStatusCache.remove(containerId);
    }
    
    /**
     * Get current health status for a container
     */
    public HealthStatus getHealthStatus(String containerId) {
        return healthStatusCache.getOrDefault(containerId, new HealthStatus(containerId));
    }
    
    /**
     * Perform scheduled health checks for all running containers
     */
    @Scheduled(fixedDelay = 30000) // Every 30 seconds
    public void performScheduledHealthChecks() {
        List<Container> runningContainers = containerRepository.findAll().stream()
            .filter(c -> c.getStatus() == Container.ContainerStatus.RUNNING)
            .toList();

        log.debug("Performing health checks for {} running containers", runningContainers.size());

        for (Container container : runningContainers) {
            try {
                // Verify ECS service exists and is active before performing health check
                if (!isEcsServiceActive(container)) {
                    log.warn("Container {} has RUNNING status but ECS service is not active. Deleting from database.",
                        container.getContainerId());

                    // Delete from repository
                    containerRepository.delete(container.getContainerId());
                    healthStatusCache.remove(container.getContainerId());

                    // Decrement user's container count
                    try {
                        userRepository.incrementContainerCount(container.getUserId(), -1);
                        log.info("Decremented container count for user: {}", container.getUserId());
                    } catch (Exception e) {
                        log.error("Failed to decrement container count for user: {}", container.getUserId(), e);
                    }

                    continue;
                }

                performHealthCheck(container);
            } catch (Exception e) {
                log.error("Error performing health check for container: {}", container.getContainerId(), e);
            }
        }
    }
    
    /**
     * Check if the ECS service for this container is active
     */
    private boolean isEcsServiceActive(Container container) {
        if (container.getServiceArn() == null) {
            log.debug("Container {} has no service ARN", container.getContainerId());
            return false;
        }

        try {
            software.amazon.awssdk.services.ecs.model.DescribeServicesRequest request =
                software.amazon.awssdk.services.ecs.model.DescribeServicesRequest.builder()
                    .cluster(clusterName)
                    .services(container.getServiceArn())
                    .build();

            software.amazon.awssdk.services.ecs.model.DescribeServicesResponse response =
                ecsClient.describeServices(request);

            if (response.services().isEmpty()) {
                log.debug("No ECS service found for container {}", container.getContainerId());
                return false;
            }

            software.amazon.awssdk.services.ecs.model.Service service = response.services().get(0);
            String status = service.status();

            // Only consider ACTIVE services as valid
            boolean isActive = "ACTIVE".equals(status);
            if (!isActive) {
                log.debug("ECS service for container {} has status: {}", container.getContainerId(), status);
            }

            return isActive;

        } catch (Exception e) {
            log.error("Failed to check ECS service status for container: {}", container.getContainerId(), e);
            return false;
        }
    }

    private void performHealthCheck(Container container) {
        HealthStatus status = healthStatusCache.computeIfAbsent(
            container.getContainerId(),
            k -> new HealthStatus(container.getContainerId())
        );

        try {
            // 1. Check ECS task health
            boolean taskHealthy = checkTaskHealth(container);

            // 2. Check HTTP endpoint health (if configured)
            boolean httpHealthy = true;
            if (container.getHealthCheck() != null && container.getHealthCheck().getPath() != null) {
                httpHealthy = checkHttpHealth(container);
            }

            // 3. Check resource utilization
            ResourceMetrics metrics = checkResourceMetrics(container);
            status.setResourceMetrics(metrics);

            // Update overall health status
            boolean isHealthy = taskHealthy && httpHealthy;
            status.updateHealth(isHealthy);

            // Send metrics to CloudWatch
            sendHealthMetrics(container, status);

            // Update container status if health has changed
            updateContainerHealthStatus(container, status);

        } catch (Exception e) {
            log.error("Health check failed for container: {}", container.getContainerId(), e);
            status.updateHealth(false);
            status.setLastError(e.getMessage());
        }
    }
    
    private boolean checkTaskHealth(Container container) {
        if (container.getTaskArn() == null) {
            return false;
        }
        
        try {
            DescribeTasksRequest request = DescribeTasksRequest.builder()
                .cluster(clusterName)
                .tasks(container.getTaskArn())
                .build();
            
            DescribeTasksResponse response = ecsClient.describeTasks(request);
            
            if (!response.tasks().isEmpty()) {
                Task task = response.tasks().get(0);
                String healthStatus = task.healthStatus() != null ? 
                    task.healthStatus().toString() : "UNKNOWN";
                
                return "HEALTHY".equals(healthStatus);
            }
        } catch (Exception e) {
            log.error("Failed to check task health", e);
        }
        
        return false;
    }
    
    private boolean checkHttpHealth(Container container) {
        Container.HealthCheckConfig config = container.getHealthCheck();
        String healthPath = config.getPath() != null ? config.getPath() : "/health";
        
        try {
            // Get container URL
            String url = String.format("https://%s.containers.somdip.dev%s", 
                container.getSubdomain(), healthPath);
            
            // Set timeout
            int timeout = config.getTimeout() != null ? config.getTimeout() : 5;
            
            // Perform HTTP health check
            restTemplate.getForObject(URI.create(url), String.class);
            
            log.debug("HTTP health check passed for container: {}", container.getContainerId());
            return true;
            
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() >= 500) {
                log.warn("HTTP health check failed with status: {}", e.getStatusCode());
                return false;
            }
            // 4xx errors might be expected for health checks
            return true;
        } catch (ResourceAccessException e) {
            log.warn("HTTP health check timeout for container: {}", container.getContainerId());
            return false;
        } catch (Exception e) {
            log.error("HTTP health check error", e);
            return false;
        }
    }
    
    private ResourceMetrics checkResourceMetrics(Container container) {
        ResourceMetrics metrics = new ResourceMetrics();
        
        try {
            // Get CloudWatch metrics for the container
            Instant endTime = Instant.now();
            Instant startTime = endTime.minusSeconds(300); // Last 5 minutes
            
            // CPU Utilization
            Double cpuUtilization = getMetricValue(
                "ECS/ContainerInsights",
                "CpuUtilized",
                "ServiceName", "service-" + container.getContainerId(),
                startTime, endTime
            );
            metrics.setCpuUtilization(cpuUtilization);
            
            // Memory Utilization
            Double memoryUtilization = getMetricValue(
                "ECS/ContainerInsights",
                "MemoryUtilized",
                "ServiceName", "service-" + container.getContainerId(),
                startTime, endTime
            );
            metrics.setMemoryUtilization(memoryUtilization);
            
            // Network metrics
            Double networkIn = getMetricValue(
                "ECS/ContainerInsights",
                "NetworkRxBytes",
                "ServiceName", "service-" + container.getContainerId(),
                startTime, endTime
            );
            metrics.setNetworkIn(networkIn);
            
            Double networkOut = getMetricValue(
                "ECS/ContainerInsights",
                "NetworkTxBytes",
                "ServiceName", "service-" + container.getContainerId(),
                startTime, endTime
            );
            metrics.setNetworkOut(networkOut);
            
        } catch (Exception e) {
            log.error("Failed to get resource metrics", e);
        }
        
        return metrics;
    }
    
    private Double getMetricValue(String namespace, String metricName, 
                                 String dimensionName, String dimensionValue,
                                 Instant startTime, Instant endTime) {
        try {
            GetMetricStatisticsRequest request = GetMetricStatisticsRequest.builder()
                .namespace(namespace)
                .metricName(metricName)
                .dimensions(Dimension.builder()
                    .name(dimensionName)
                    .value(dimensionValue)
                    .build())
                .startTime(startTime)
                .endTime(endTime)
                .period(60) // 1 minute
                .statistics(Statistic.AVERAGE)
                .build();
            
            GetMetricStatisticsResponse response = cloudWatchClient.getMetricStatistics(request);
            
            if (!response.datapoints().isEmpty()) {
                return response.datapoints().stream()
                    .mapToDouble(Datapoint::average)
                    .average()
                    .orElse(0.0);
            }
        } catch (Exception e) {
            log.error("Failed to get metric: {} - {}", namespace, metricName, e);
        }
        
        return 0.0;
    }
    
    private void sendHealthMetrics(Container container, HealthStatus status) {
        try {
            List<MetricDatum> metrics = new ArrayList<>();
            
            // Health status metric (1 = healthy, 0 = unhealthy)
            metrics.add(MetricDatum.builder()
                .metricName("ContainerHealth")
                .value(status.isHealthy() ? 1.0 : 0.0)
                .timestamp(Instant.now())
                .dimensions(
                    Dimension.builder()
                        .name("ContainerId")
                        .value(container.getContainerId())
                        .build(),
                    Dimension.builder()
                        .name("ContainerName")
                        .value(container.getContainerName())
                        .build()
                )
                .build());
            
            // Consecutive failure count
            metrics.add(MetricDatum.builder()
                .metricName("ConsecutiveHealthCheckFailures")
                .value((double) status.getConsecutiveFailures())
                .timestamp(Instant.now())
                .dimensions(
                    Dimension.builder()
                        .name("ContainerId")
                        .value(container.getContainerId())
                        .build()
                )
                .build());
            
            // Send metrics to CloudWatch
            PutMetricDataRequest request = PutMetricDataRequest.builder()
                .namespace(cloudWatchNamespace)
                .metricData(metrics)
                .build();
            
            cloudWatchClient.putMetricData(request);
            
        } catch (Exception e) {
            log.error("Failed to send health metrics to CloudWatch", e);
        }
    }
    
    private void updateContainerHealthStatus(Container container, HealthStatus status) {
        // Verify container still exists before updating (prevent race condition with cleanup)
        if (!isEcsServiceActive(container)) {
            log.debug("Skipping health status update for container {} - ECS service no longer active",
                container.getContainerId());
            return;
        }

        // Initialize resource usage if not present
        if (container.getResourceUsage() == null) {
            container.setResourceUsage(new Container.ResourceUsage());
            container.getResourceUsage().setMeasurementPeriodStart(Instant.now());
        }

        Container.ResourceUsage usage = container.getResourceUsage();
        usage.setMeasurementPeriodEnd(Instant.now());

        // Update with latest metrics (for both healthy and unhealthy containers)
        if (status.getResourceMetrics() != null) {
            usage.setAvgCpuPercent(status.getResourceMetrics().getCpuUtilization());
            usage.setAvgMemoryPercent(status.getResourceMetrics().getMemoryUtilization());
        }

        // Save updated resource usage
        containerRepository.save(container);

        // Log warning for unhealthy containers
        Container.HealthCheckConfig config = container.getHealthCheck();
        int unhealthyThreshold = config != null && config.getUnhealthyThreshold() != null ?
            config.getUnhealthyThreshold() : 3;

        if (!status.isHealthy() && status.getConsecutiveFailures() >= unhealthyThreshold) {
            log.warn("Container {} is unhealthy after {} consecutive failures",
                container.getContainerId(), status.getConsecutiveFailures());
            // You might want to trigger alerts or auto-recovery here
        }
    }
    
    private Container.HealthCheckConfig createDefaultHealthCheckConfig() {
        Container.HealthCheckConfig config = new Container.HealthCheckConfig();
        config.setPath("/health");
        config.setInterval(30);
        config.setTimeout(5);
        config.setHealthyThreshold(2);
        config.setUnhealthyThreshold(3);
        config.setProtocol("HTTP");
        return config;
    }
    
    /**
     * Health status tracking object
     */
    public static class HealthStatus {
        private final String containerId;
        private boolean healthy = true;
        private int consecutiveFailures = 0;
        private int consecutiveSuccesses = 0;
        private Instant lastCheckTime;
        private String lastError;
        private ResourceMetrics resourceMetrics;
        
        public HealthStatus(String containerId) {
            this.containerId = containerId;
            this.lastCheckTime = Instant.now();
        }
        
        public void updateHealth(boolean isHealthy) {
            this.healthy = isHealthy;
            this.lastCheckTime = Instant.now();
            
            if (isHealthy) {
                consecutiveSuccesses++;
                consecutiveFailures = 0;
                lastError = null;
            } else {
                consecutiveFailures++;
                consecutiveSuccesses = 0;
            }
        }
        
        // Getters and setters
        public String getContainerId() { return containerId; }
        public boolean isHealthy() { return healthy; }
        public int getConsecutiveFailures() { return consecutiveFailures; }
        public int getConsecutiveSuccesses() { return consecutiveSuccesses; }
        public Instant getLastCheckTime() { return lastCheckTime; }
        public String getLastError() { return lastError; }
        public void setLastError(String lastError) { this.lastError = lastError; }
        public ResourceMetrics getResourceMetrics() { return resourceMetrics; }
        public void setResourceMetrics(ResourceMetrics resourceMetrics) { this.resourceMetrics = resourceMetrics; }
    }
    
    /**
     * Resource metrics object
     */
    public static class ResourceMetrics {
        private Double cpuUtilization;
        private Double memoryUtilization;
        private Double networkIn;
        private Double networkOut;
        
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
}