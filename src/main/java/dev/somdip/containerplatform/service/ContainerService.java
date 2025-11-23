package dev.somdip.containerplatform.service;

import dev.somdip.containerplatform.model.Container;
import dev.somdip.containerplatform.model.Deployment;
import dev.somdip.containerplatform.model.User;
import dev.somdip.containerplatform.repository.ContainerRepository;
import dev.somdip.containerplatform.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
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
    
    @Value("${app.container.limits.free}")
    private int freeContainerLimit;
    
    @Value("${app.container.limits.starter}")
    private int starterContainerLimit;
    
    @Value("${app.container.limits.pro}")
    private int proContainerLimit;
    
    @Value("${app.container.limits.scale}")
    private int scaleContainerLimit;
    
    public ContainerService(ContainerRepository containerRepository,
                          UserRepository userRepository,
                          EcsService ecsService,
                          DeploymentTrackingService deploymentTrackingService,
                          ContainerHealthCheckService healthCheckService) {
        this.containerRepository = containerRepository;
        this.userRepository = userRepository;
        this.ecsService = ecsService;
        this.deploymentTrackingService = deploymentTrackingService;
        this.healthCheckService = healthCheckService;
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

        // Validate that the image is a supported technology stack
        if (!isSupportedImageType(image)) {
            throw new IllegalArgumentException("Unsupported container image type. Supported stacks: nginx, node, python, java, golang, php, ruby, dotnet");
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
        
        // Set default health check configuration
        Container.HealthCheckConfig healthCheck = new Container.HealthCheckConfig();
        healthCheck.setPath("/health");
        healthCheck.setInterval(30);
        healthCheck.setTimeout(5);
        healthCheck.setHealthyThreshold(2);
        healthCheck.setUnhealthyThreshold(3);
        healthCheck.setProtocol("HTTP");
        container.setHealthCheck(healthCheck);
        
        container = containerRepository.save(container);
        userRepository.incrementContainerCount(userId, 1);
        
        log.info("Container created successfully: {}", container.getContainerId());
        return container;
    }
    
    public Container getContainer(String containerId) {
        return containerRepository.findById(containerId)
            .orElseThrow(() -> new IllegalArgumentException("Container not found"));
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
            container.setEnvironmentVariables(environmentVariables);
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
    
    public Container stopContainer(String containerId) {
        log.info("Stopping container: {}", containerId);
        
        Container container = getContainer(containerId);
        
        if (container.getStatus() != Container.ContainerStatus.RUNNING) {
            throw new IllegalStateException("Container is not running");
        }
        
        container.setStatus(Container.ContainerStatus.STOPPING);
        containerRepository.save(container);
        
        try {
            // Stop health monitoring
            healthCheckService.stopHealthMonitoring(containerId);
            
            if (container.getServiceArn() != null) {
                ecsService.stopService(container.getServiceArn(), containerId);
            }
            
            container.setStatus(Container.ContainerStatus.STOPPED);
            return containerRepository.save(container);
        } catch (Exception e) {
            log.error("Failed to stop container: {}", containerId, e);
            container.setStatus(Container.ContainerStatus.FAILED);
            containerRepository.save(container);
            throw new RuntimeException("Failed to stop container", e);
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
            case SCALE:
                return scaleContainerLimit;
            case ENTERPRISE:
                return Integer.MAX_VALUE;
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
     * Validates if the container image is a supported technology stack
     */
    private boolean isSupportedImageType(String image) {
        if (image == null || image.trim().isEmpty()) {
            return false;
        }

        String imageLower = image.toLowerCase();

        // List of supported technology stacks
        List<String> supportedStacks = Arrays.asList(
            "nginx", "httpd", "apache",      // Static web servers
            "node",                          // Node.js
            "python",                        // Python
            "java", "temurin", "openjdk", "tomcat",  // Java
            "golang", "go",                  // Go
            "php",                           // PHP
            "ruby", "rails",                 // Ruby
            "dotnet", "aspnet"               // .NET
        );

        // Check if the image contains any supported stack identifier
        for (String stack : supportedStacks) {
            if (imageLower.contains(stack)) {
                log.info("Validated supported image type: {} (detected: {})", image, stack);
                return true;
            }
        }

        log.warn("Unsupported image type detected: {}", image);
        return false;
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
     * Auto-detect the default port based on the image name/type
     * Uses pattern matching to handle both Docker Hub and ECR image names
     */
    private Integer getDefaultPortForImage(String image) {
        if (image == null) return 8080;

        String imageLower = image.toLowerCase();

        // Static web servers (nginx, apache, httpd) - port 80
        if (imageLower.contains("nginx") || imageLower.contains("httpd") || imageLower.contains("apache")) {
            log.info("Detected static web server for image {}, using default port 80", image);
            return 80;
        }

        // Node.js applications - port 3000
        if (imageLower.contains("node")) {
            log.info("Detected Node.js application for image {}, using default port 3000", image);
            return 3000;
        }

        // Python applications (Flask, Django, FastAPI) - port 8000
        if (imageLower.contains("python") || imageLower.contains("django") || imageLower.contains("flask") || imageLower.contains("fastapi")) {
            log.info("Detected Python application for image {}, using default port 8000", image);
            return 8000;
        }

        // Java applications (Spring Boot, Tomcat) - port 8080
        if (imageLower.contains("java") || imageLower.contains("temurin") || imageLower.contains("openjdk") || imageLower.contains("tomcat")) {
            log.info("Detected Java application for image {}, using default port 8080", image);
            return 8080;
        }

        // Go applications - port 8080
        if (imageLower.contains("golang") || imageLower.contains("go:") || imageLower.contains("/go")) {
            log.info("Detected Go application for image {}, using default port 8080", image);
            return 8080;
        }

        // PHP applications - port 80 (with php-fpm) or 9000 (pure php-fpm)
        if (imageLower.contains("php")) {
            log.info("Detected PHP application for image {}, using default port 80", image);
            return 80;
        }

        // Ruby/Rails applications - port 3000
        if (imageLower.contains("ruby") || imageLower.contains("rails")) {
            log.info("Detected Ruby application for image {}, using default port 3000", image);
            return 3000;
        }

        // .NET applications - port 8080 or 5000
        if (imageLower.contains("dotnet") || imageLower.contains("aspnet")) {
            log.info("Detected .NET application for image {}, using default port 8080", image);
            return 8080;
        }

        // Default fallback
        log.warn("Could not detect image type for {}, defaulting to port 8080", image);
        return 8080;
    }
}