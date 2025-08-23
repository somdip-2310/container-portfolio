package dev.somdip.containerplatform.service;

import dev.somdip.containerplatform.model.Container;
import dev.somdip.containerplatform.model.User;
import dev.somdip.containerplatform.repository.ContainerRepository;
import dev.somdip.containerplatform.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class ContainerService {
    private static final Logger log = LoggerFactory.getLogger(ContainerService.class);
    
    private final ContainerRepository containerRepository;
    private final UserRepository userRepository;
    private final EcsService ecsService;
    
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
                          EcsService ecsService) {
        this.containerRepository = containerRepository;
        this.userRepository = userRepository;
        this.ecsService = ecsService;
    }
    
    public Container createContainer(String userId, String name, String image, String imageTag) {
        log.info("Creating container for user: {} with image: {}:{}", userId, image, imageTag);
        
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
            
        long currentContainers = containerRepository.countActiveByUserId(userId);
        int limit = getContainerLimit(user.getPlan());
        
        if (currentContainers >= limit) {
            throw new IllegalStateException("Container limit reached for plan: " + user.getPlan());
        }
        
        if (!isValidContainerName(name)) {
            throw new IllegalArgumentException("Invalid container name. Use only lowercase letters, numbers, and hyphens");
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
        container.setPort(8080);
        container.setCpu(256);
        container.setMemory(512);
        container.setSslEnabled(true);
        container.setCreatedAt(Instant.now());
        container.setUpdatedAt(Instant.now());
        
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
        
        if (container.getStatus() == Container.ContainerStatus.RUNNING) {
            stopContainer(containerId);
        }
        
        container.setStatus(Container.ContainerStatus.DELETING);
        containerRepository.save(container);
        
        try {
            if (container.getServiceArn() != null) {
                ecsService.deleteService(container.getServiceArn());
            }
            
            containerRepository.delete(containerId);
            userRepository.incrementContainerCount(container.getUserId(), -1);
            
            log.info("Container deleted successfully: {}", containerId);
        } catch (Exception e) {
            log.error("Error deleting container: {}", containerId, e);
            container.setStatus(Container.ContainerStatus.FAILED);
            containerRepository.save(container);
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
            ecsService.deployContainer(container);
            
            container.setStatus(Container.ContainerStatus.RUNNING);
            container.setLastDeployedAt(Instant.now());
            Long deploymentCount = container.getDeploymentCount() != null ? 
                container.getDeploymentCount() : 0L;
            container.setDeploymentCount(deploymentCount + 1);
            
            return containerRepository.save(container);
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
            if (container.getServiceArn() != null) {
                ecsService.stopService(container.getServiceArn());
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
    }
}