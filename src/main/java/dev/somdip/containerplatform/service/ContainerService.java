package dev.somdip.containerplatform.service;

import dev.somdip.containerplatform.model.Container;
import dev.somdip.containerplatform.model.Deployment;
import dev.somdip.containerplatform.model.User;
import dev.somdip.containerplatform.repository.ContainerRepository;
import dev.somdip.containerplatform.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ContainerService {
    private static final Logger log = LoggerFactory.getLogger(ContainerService.class);
    
    private final ContainerRepository containerRepository;
    private final UserRepository userRepository;
    private final EcsService ecsService;
    private final DeploymentTrackingService deploymentTrackingService;
    private final ContainerHealthCheckService healthCheckService;
    private final AsyncContainerOperations asyncContainerOperations;

    @Value("${app.container.limits.free}")
    private int freeContainerLimit;

    @Value("${app.container.limits.starter}")
    private int starterContainerLimit;

    @Value("${app.container.limits.pro}")
    private int proContainerLimit;

    @Value("${app.container.limits.business}")
    private int businessContainerLimit;

    @Value("${app.container.limits.enterprise}")
    private int enterpriseContainerLimit;

    public ContainerService(ContainerRepository containerRepository,
                          UserRepository userRepository,
                          EcsService ecsService,
                          DeploymentTrackingService deploymentTrackingService,
                          ContainerHealthCheckService healthCheckService,
                          AsyncContainerOperations asyncContainerOperations) {
        this.containerRepository = containerRepository;
        this.userRepository = userRepository;
        this.ecsService = ecsService;
        this.deploymentTrackingService = deploymentTrackingService;
        this.healthCheckService = healthCheckService;
        this.asyncContainerOperations = asyncContainerOperations;
    }
    
    public Container createContainer(String userId, String name, String image, String imageTag, Integer port) {
        log.info("Creating container for user: {} with image: {}:{}", userId, image, imageTag);
        
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
            
        long currentContainers = containerRepository.countNonDeletedByUserId(userId);
        int limit = getContainerLimit(user.getPlan());
        
        if (currentContainers >= limit) {
            throw new IllegalStateException("Container limit reached for plan: " + user.getPlan());
        }
        
        if (!isValidContainerName(name)) {
            throw new IllegalArgumentException("Invalid container name. Use only lowercase letters, numbers, and hyphens");
        }

        // Validate image format (allow any valid Docker image reference)
        if (!isValidDockerImageReference(image)) {
            throw new IllegalArgumentException("Invalid Docker image format. Examples: nginx, myuser/myapp, ghcr.io/org/app");
        }

        String subdomain = generateSubdomain(name);
        if (containerRepository.findBySubdomain(subdomain).isPresent()) {
            throw new IllegalArgumentException("Subdomain already in use");
        }
        
        Container container = new Container();
        container.setContainerId(UUID.randomUUID().toString());
        container.setUserId(userId);
        container.setContainerName(name);
        container.setImage(image);
        container.setImageTag(imageTag != null ? imageTag : "latest");
        container.setSubdomain(subdomain);
        container.setStatus(Container.ContainerStatus.CREATING);
        container.setPort(port != null ? port : getDefaultPortForImage(image));
        container.setCpu(256);
        container.setMemory(512);
        container.setSslEnabled(true);
        container.setCreatedAt(Instant.now());
        container.setUpdatedAt(Instant.now());

        // Set PORT environment variable (critical for app to listen on correct port)
        Map<String, String> envVars = new HashMap<>();
        envVars.put("PORT", String.valueOf(container.getPort()));
        container.setEnvironmentVariables(envVars);

        // Set default health check configuration
        Container.HealthCheckConfig healthCheck = new Container.HealthCheckConfig();
        healthCheck.setPath("/health");
        healthCheck.setInterval(30);
        healthCheck.setTimeout(5);
        healthCheck.setHealthyThreshold(2);
        healthCheck.setUnhealthyThreshold(3);
        healthCheck.setProtocol("HTTP");
        container.setHealthCheck(healthCheck);

        // Initialize resource usage with zeros so dashboard/graphs don't break
        Container.ResourceUsage resourceUsage = new Container.ResourceUsage();
        resourceUsage.setAvgCpuPercent(0.0);
        resourceUsage.setAvgMemoryPercent(0.0);
        resourceUsage.setMeasurementPeriodStart(Instant.now());
        resourceUsage.setMeasurementPeriodEnd(Instant.now());
        container.setResourceUsage(resourceUsage);

        container = containerRepository.save(container);
        userRepository.incrementContainerCount(userId, 1);
        
        log.info("Container created successfully: {}", container.getContainerId());
        return container;
    }
    
    public Container getContainer(String containerId) {
        return containerRepository.findById(containerId)
            .orElseThrow(() -> new IllegalArgumentException("Container not found"));
    }
    
    public Container saveContainer(Container container) {
        return containerRepository.save(container);
    }
    
    public List<Container> listUserContainers(String userId) {
        log.debug("Listing containers for user: {}", userId);
        return containerRepository.findByUserId(userId);
    }
    
    
    
    public Container updateContainer(String containerId, Integer cpu, Integer memory, 
                                   Map<String, String> environmentVariables) {
        log.info("Updating container: {}", containerId);
        
        Container container = getContainer(containerId);
        
        if (cpu != null) {
            validateCpu(cpu);
            container.setCpu(cpu);
        }
        
        if (memory != null) {
            validateMemory(memory);
            container.setMemory(memory);
        }

        if (environmentVariables != null) {
            // Merge with existing env vars, ensuring PORT is always preserved
            Map<String, String> mergedEnvVars = new HashMap<>();
            if (container.getEnvironmentVariables() != null) {
                mergedEnvVars.putAll(container.getEnvironmentVariables());
            }
            mergedEnvVars.putAll(environmentVariables);
            // Always ensure PORT is set to the container's port
            mergedEnvVars.put("PORT", String.valueOf(container.getPort()));
            container.setEnvironmentVariables(mergedEnvVars);
        }

        container.setUpdatedAt(Instant.now());
        return containerRepository.save(container);
    }
    
    public void deleteContainer(String containerId) {
        log.info("Deleting container: {}", containerId);
        
        Container container = getContainer(containerId);
        String userId = container.getUserId();
        
        // Only stop if container is running
        if (container.getStatus() == Container.ContainerStatus.RUNNING) {
            try {
                stopContainer(containerId);
            } catch (Exception e) {
                log.warn("Failed to stop container before deletion: {}", e.getMessage());
                // Continue with deletion anyway
            }
        }
        
        // Skip if already being deleted
        if (container.getStatus() == Container.ContainerStatus.DELETING) {
            log.warn("Container {} is already being deleted", containerId);
            return;
        }
        
        container.setStatus(Container.ContainerStatus.DELETING);
        containerRepository.save(container);
        
        boolean shouldDecrementCount = true;
        
        try {
            // Step 1: Stop health monitoring (non-critical)
            try {
                healthCheckService.stopHealthMonitoring(containerId);
            } catch (Exception e) {
                log.warn("Failed to stop health monitoring: {}", e.getMessage());
            }
            
            // Step 2: Delete ECS resources (non-critical)
            if (container.getServiceArn() != null && !container.getServiceArn().isEmpty()) {
                try {
                    ecsService.deleteService(container.getServiceArn(), containerId);
                } catch (Exception e) {
                    log.warn("Failed to delete ECS service: {}", e.getMessage());
                }
            }
            
            // Step 3: Delete from repository
            containerRepository.delete(containerId);
            
            // Step 4: Decrement user container count
            userRepository.incrementContainerCount(userId, -1);
            shouldDecrementCount = false;
            
            log.info("Container deleted successfully: {}", containerId);
            
        } catch (Exception e) {
            log.error("Error during container deletion: {}", e.getMessage(), e);
            
            // If we haven't decremented the count yet and container was actually deleted, do it now
            if (shouldDecrementCount) {
                try {
                    // Check if container still exists
                    containerRepository.findById(containerId).ifPresentOrElse(
                        c -> {
                            // Container still exists, mark as failed
                            c.setStatus(Container.ContainerStatus.FAILED);
                            containerRepository.save(c);
                        },
                        () -> {
                            // Container was deleted, decrement count
                            try {
                                userRepository.incrementContainerCount(userId, -1);
                            } catch (Exception ex) {
                                log.error("Failed to decrement container count: {}", ex.getMessage());
                            }
                        }
                    );
                } catch (Exception ex) {
                    log.error("Error during cleanup: {}", ex.getMessage());
                }
            }
            
            throw new RuntimeException("Failed to delete container", e);
        }
    }
    

    public Container deployContainer(String containerId) {
        log.info("Deploying container: {}", containerId);

        Container container = getContainer(containerId);

        if (container.getStatus() == Container.ContainerStatus.RUNNING) {
            throw new IllegalStateException("Container is already running");
        }

        container.setStatus(Container.ContainerStatus.STARTING);
        containerRepository.save(container);

        try {
            // Deploy to ECS with deployment tracking
            Deployment deployment = ecsService.deployContainer(container, container.getUserId());

            // Start tracking deployment progress
            deploymentTrackingService.trackDeployment(deployment.getDeploymentId());

            // Update container with deployment info
            container.setStatus(Container.ContainerStatus.RUNNING);
            container.setLastDeployedAt(Instant.now());
            Long deploymentCount = container.getDeploymentCount() != null ?
                container.getDeploymentCount() : 0L;
            container.setDeploymentCount(deploymentCount + 1);

            Container savedContainer = containerRepository.save(container);

            // Start health monitoring
            healthCheckService.startHealthMonitoring(containerId);

            log.info("Container deployed successfully: {}", containerId);
            return savedContainer;

        } catch (Exception e) {
            log.error("Failed to deploy container: {}", containerId, e);
            container.setStatus(Container.ContainerStatus.FAILED);
            containerRepository.save(container);
            throw new RuntimeException("Failed to deploy container", e);
        }
    }

    /**
     * Start a stopped container asynchronously
     * Returns immediately with the container in STARTING status
     */
    public Container startContainerAsync(String containerId) {
        log.info("Starting container asynchronously: {}", containerId);

        Container container = getContainer(containerId);

        if (container.getStatus() == Container.ContainerStatus.RUNNING) {
            throw new IllegalStateException("Container is already running");
        }

        if (container.getStatus() == Container.ContainerStatus.STARTING) {
            // Already starting, return current state
            return container;
        }

        // Set status to STARTING and save
        container.setStatus(Container.ContainerStatus.STARTING);
        Container savedContainer = containerRepository.save(container);

        // Trigger async deployment using separate service (must be separate class for @Async to work)
        asyncContainerOperations.deployContainerAsync(containerId);

        return savedContainer;
    }
    
    public Container stopContainer(String containerId) {
        log.info("Stopping container: {}", containerId);

        Container container = getContainer(containerId);

        // Check if container is already stopped in database
        if (container.getStatus() == Container.ContainerStatus.STOPPED) {
            log.info("Container {} is already stopped", containerId);
            return container;
        }

        container.setStatus(Container.ContainerStatus.STOPPING);
        containerRepository.save(container);

        try {
            // Stop health monitoring
            healthCheckService.stopHealthMonitoring(containerId);

            if (container.getServiceArn() != null) {
                ecsService.stopService(container.getServiceArn(), containerId);
            }

            log.info("Successfully stopped container {} in ECS", containerId);
        } catch (Exception e) {
            // Log error but still update database status to avoid stuck RUNNING state
            log.error("Error stopping container {} in ECS: {}", containerId, e.getMessage());
            log.info("Updating database status to STOPPED anyway to sync with ECS state");
        }

        // Always update status to STOPPED to sync database with ECS
        container.setStatus(Container.ContainerStatus.STOPPED);
        container.setUpdatedAt(Instant.now());
        return containerRepository.save(container);
    }
    /**
     * Restart a container asynchronously
     * This method initiates the restart process and returns immediately
     */
    @Async
    public void restartContainerAsync(String containerId) {
        log.info("Starting async restart for container: {}", containerId);
        
        try {
            Container container = getContainer(containerId);
            
            // Stop the container if it's running
            if (container.getStatus() == Container.ContainerStatus.RUNNING) {
                log.info("Stopping container {} before restart", containerId);
                stopContainer(containerId);
                // Wait a bit for clean shutdown
                Thread.sleep(3000);
            }
            
            // Deploy it again
            log.info("Redeploying container {} after stop", containerId);
            deployContainer(containerId);
            log.info("Container {} restarted successfully", containerId);
            
        } catch (Exception e) {
            log.error("Error during async restart of container: {}", containerId, e);
            // Update container status to FAILED
            try {
                Container container = getContainer(containerId);
                container.setStatus(Container.ContainerStatus.FAILED);
                containerRepository.save(container);
            } catch (Exception ex) {
                log.error("Failed to update container status after restart failure", ex);
            }
        }
    }
    
    /**
     * Get deployment status for a container
     */
    public DeploymentTrackingService.DeploymentStatus getDeploymentStatus(String deploymentId) {
        return deploymentTrackingService.getDeploymentStatus(deploymentId);
    }
    
    /**
     * Get health status for a container
     */
    public ContainerHealthCheckService.HealthStatus getHealthStatus(String containerId) {
        return healthCheckService.getHealthStatus(containerId);
    }
    
    /**
     * Update container health check configuration
     */
    public Container updateHealthCheck(String containerId, Container.HealthCheckConfig healthCheck) {
        log.info("Updating health check configuration for container: {}", containerId);
        
        Container container = getContainer(containerId);
        container.setHealthCheck(healthCheck);
        container.setUpdatedAt(Instant.now());
        
        Container savedContainer = containerRepository.save(container);
        
        // Restart health monitoring with new configuration
        if (container.getStatus() == Container.ContainerStatus.RUNNING) {
            healthCheckService.stopHealthMonitoring(containerId);
            healthCheckService.startHealthMonitoring(containerId);
        }
        
        return savedContainer;
    }

    /**
     * Get all containers for a user (alias for listUserContainers)
     */
    public List<Container> getUserContainers(String userId) {
        return listUserContainers(userId);
    }

    /**
     * Check if a user owns a specific container
     */
    public boolean isOwner(String userId, String containerId) {
        try {
            Container container = getContainer(containerId);
            return container.getUserId().equals(userId);
        } catch (Exception e) {
            return false;
        }
    }

    private int getContainerLimit(User.UserPlan plan) {
        switch (plan) {
            case FREE:
                return freeContainerLimit;
            case STARTER:
                return starterContainerLimit;
            case PRO:
                return proContainerLimit;
            case BUSINESS:
                return businessContainerLimit;
            case ENTERPRISE:
                return enterpriseContainerLimit;
            default:
                return freeContainerLimit;
        }
    }
    
    private boolean isValidContainerName(String name) {
        return name != null &&
               name.matches("^[a-z0-9][a-z0-9-]*[a-z0-9]$") &&
               name.length() >= 3 &&
               name.length() <= 63;
    }

    /**
     * Validates Docker image reference format.
     * Accepts:
     * - Simple names: nginx, redis, postgres
     * - User/repo: myuser/myapp
     * - Full registry: ghcr.io/org/app, quay.io/user/app
     * - ECR: 123456789.dkr.ecr.us-east-1.amazonaws.com/repo
     */
    private boolean isValidDockerImageReference(String image) {
        if (image == null || image.trim().isEmpty()) {
            return false;
        }

        // Remove tag if present for validation
        String imageWithoutTag = image.split(":")[0];

        // Basic length validation
        if (imageWithoutTag.length() > 255) {
            log.warn("Image name too long: {}", image);
            return false;
        }

        // Check for valid characters - allow alphanumeric, dots, hyphens, underscores, and slashes
        if (!imageWithoutTag.matches("^[a-zA-Z0-9][a-zA-Z0-9._/-]*[a-zA-Z0-9]$") &&
            !imageWithoutTag.matches("^[a-zA-Z0-9]$")) {
            log.warn("Invalid image format: {}", image);
            return false;
        }

        // Check for invalid patterns
        if (imageWithoutTag.contains("//") || imageWithoutTag.contains("..")) {
            log.warn("Invalid image format (double slashes or dots): {}", image);
            return false;
        }

        log.info("Validated Docker image reference: {} (registry: {})", image, getRegistryType(image));
        return true;
    }

    /**
     * Determines the registry type from image reference
     */
    private ImageRegistryType getRegistryType(String image) {
        if (image.contains(".dkr.ecr.") && image.contains(".amazonaws.com")) {
            return ImageRegistryType.ECR;
        } else if (image.startsWith("ghcr.io/")) {
            return ImageRegistryType.GHCR;
        } else if (image.startsWith("quay.io/")) {
            return ImageRegistryType.QUAY;
        } else if (image.contains(".") && image.contains("/")) {
            return ImageRegistryType.CUSTOM;
        } else {
            return ImageRegistryType.DOCKER_HUB;
        }
    }

    public enum ImageRegistryType {
        DOCKER_HUB,
        GHCR,
        QUAY,
        ECR,
        CUSTOM
    }

    /**
     * Extracts the image name from a full reference.
     * ghcr.io/user/myapp:latest -> myapp
     * nginx:alpine -> nginx
     * myuser/myapp -> myapp
     */
    private String extractImageName(String image) {
        // Remove tag
        String withoutTag = image.split(":")[0];

        // Get last path component
        String[] parts = withoutTag.split("/");
        return parts[parts.length - 1].toLowerCase();
    }

    private String generateSubdomain(String containerName) {
        return containerName.toLowerCase().replaceAll("[^a-z0-9-]", "-");
    }
    
    private void validateCpu(int cpu) {
        List<Integer> validCpuValues = Arrays.asList(256, 512, 1024, 2048, 4096);
        if (!validCpuValues.contains(cpu)) {
            throw new IllegalArgumentException("Invalid CPU value. Must be one of: " + validCpuValues);
        }
    }
    
    private void validateMemory(int memory) {
        if (memory < 512 || memory > 30720) {
            throw new IllegalArgumentException("Memory must be between 512 MB and 30720 MB");
        }
        
        // Memory must be compatible with CPU
        Map<Integer, List<Integer>> cpuMemoryMap = Map.of(
            256, Arrays.asList(512, 1024, 2048),
            512, Arrays.asList(1024, 2048, 3072, 4096),
            1024, Arrays.asList(2048, 3072, 4096, 5120, 6144, 7168, 8192),
            2048, Arrays.asList(4096, 8192, 12288, 16384),
            4096, Arrays.asList(8192, 16384, 24576, 30720)
        );
        
        // This validation would need the CPU value, so it's simplified here
        // In a real implementation, you'd validate CPU-memory compatibility
    }
    
    /**
     * Auto-detect the default port based on the image name/type.
     * For unknown images, defaults to 8080.
     */
    private Integer getDefaultPortForImage(String image) {
        if (image == null) return 8080;

        // Extract just the image name (remove registry prefix and tag)
        String imageName = extractImageName(image);
        String imageLower = image.toLowerCase();

        // Static web servers - port 80
        if (imageName.contains("nginx") || imageName.contains("httpd") ||
            imageName.contains("apache") || imageName.contains("caddy")) {
            log.info("Detected static web server for image {}, using default port 80", image);
            return 80;
        }

        // Node.js - port 3000
        if (imageName.contains("node") || imageName.contains("nextjs") ||
            imageName.contains("express") || imageName.contains("nestjs")) {
            log.info("Detected Node.js application for image {}, using default port 3000", image);
            return 3000;
        }

        // Python - port 8000
        if (imageName.contains("python") || imageName.contains("django") ||
            imageName.contains("flask") || imageName.contains("fastapi") ||
            imageName.contains("uvicorn") || imageName.contains("gunicorn")) {
            log.info("Detected Python application for image {}, using default port 8000", image);
            return 8000;
        }

        // Java - port 8080
        if (imageName.contains("java") || imageName.contains("maven") ||
            imageName.contains("gradle") || imageName.contains("tomcat") ||
            imageName.contains("spring") || imageName.contains("openjdk") ||
            imageName.contains("temurin") || imageName.contains("quarkus") ||
            imageLower.contains("eclipse-temurin")) {
            log.info("Detected Java application for image {}, using default port 8080", image);
            return 8080;
        }

        // Go - port 8080
        if (imageName.contains("golang") || imageName.equals("go")) {
            log.info("Detected Go application for image {}, using default port 8080", image);
            return 8080;
        }

        // PHP - port 9000 (FPM) or 80 (Apache)
        if (imageName.contains("php")) {
            if (imageLower.contains("fpm")) {
                log.info("Detected PHP-FPM for image {}, using default port 9000", image);
                return 9000;
            }
            log.info("Detected PHP application for image {}, using default port 80", image);
            return 80;
        }

        // Ruby/Rails - port 3000
        if (imageName.contains("ruby") || imageName.contains("rails")) {
            log.info("Detected Ruby application for image {}, using default port 3000", image);
            return 3000;
        }

        // .NET - port 80 (Kestrel default in containers)
        if (imageName.contains("dotnet") || imageName.contains("aspnet") ||
            imageLower.contains("mcr.microsoft.com")) {
            log.info("Detected .NET application for image {}, using default port 80", image);
            return 80;
        }

        // Databases (warn user these likely won't work as web apps)
        if (imageName.contains("postgres") || imageName.contains("mysql") ||
            imageName.contains("mongo") || imageName.contains("redis") ||
            imageName.contains("mariadb")) {
            log.warn("Database image detected: {}. This may not work as a web application.", image);
            // Return common database ports
            if (imageName.contains("postgres")) return 5432;
            if (imageName.contains("mysql") || imageName.contains("mariadb")) return 3306;
            if (imageName.contains("mongo")) return 27017;
            if (imageName.contains("redis")) return 6379;
        }

        // Default for unknown images
        log.info("Unknown image type for {}, using default port 8080", image);
        return 8080;
    }
}