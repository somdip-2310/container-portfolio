package dev.somdip.containerplatform.service;

import dev.somdip.containerplatform.model.Container;
import dev.somdip.containerplatform.model.Deployment;
import dev.somdip.containerplatform.repository.ContainerRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Separate service for async container operations
 * @Async methods must be in a separate class to work with Spring's proxy mechanism
 */
@Slf4j
@Service
public class AsyncContainerOperations {

    private final EcsService ecsService;
    private final ContainerRepository containerRepository;
    private final DeploymentTrackingService deploymentTrackingService;
    private final ContainerHealthCheckService healthCheckService;

    public AsyncContainerOperations(
            EcsService ecsService,
            ContainerRepository containerRepository,
            DeploymentTrackingService deploymentTrackingService,
            ContainerHealthCheckService healthCheckService) {
        this.ecsService = ecsService;
        this.containerRepository = containerRepository;
        this.deploymentTrackingService = deploymentTrackingService;
        this.healthCheckService = healthCheckService;
    }

    /**
     * Asynchronously deploy a container to ECS
     * This method runs in a separate thread and won't block the API response
     */
    @Async
    @Transactional
    public void deployContainerAsync(String containerId) {
        log.info("=== ASYNC DEPLOYMENT START === Container: {}", containerId);

        try {
            Container container = containerRepository.findById(containerId)
                    .orElseThrow(() -> new IllegalArgumentException("Container not found: " + containerId));
            log.info("Container found: {} with current status: {}", containerId, container.getStatus());

            // Deploy to ECS with deployment tracking
            log.info("Deploying container to ECS: {}", containerId);
            Deployment deployment = ecsService.deployContainer(container, container.getUserId());
            log.info("ECS deployment created with ID: {}", deployment.getDeploymentId());

            // Start tracking deployment progress
            log.info("Starting deployment tracking for: {}", deployment.getDeploymentId());
            deploymentTrackingService.trackDeployment(deployment.getDeploymentId());

            // Update container with deployment info
            log.info("Updating container status to RUNNING for: {}", containerId);
            container.setStatus(Container.ContainerStatus.RUNNING);
            container.setLastDeployedAt(Instant.now());
            Long deploymentCount = container.getDeploymentCount() != null ?
                    container.getDeploymentCount() : 0L;
            container.setDeploymentCount(deploymentCount + 1);

            log.info("Saving container to database with status: {}", container.getStatus());
            Container savedContainer = containerRepository.save(container);
            log.info("Container saved successfully with ID: {} and status: {}", savedContainer.getId(), savedContainer.getStatus());

            // Start health monitoring
            log.info("Starting health monitoring for container: {}", containerId);
            healthCheckService.startHealthMonitoring(containerId);

            log.info("=== ASYNC DEPLOYMENT SUCCESS === Container: {} is now {}", containerId, savedContainer.getStatus());

        } catch (Exception e) {
            log.error("=== ASYNC DEPLOYMENT FAILED === Container: {}, Error: {}", containerId, e.getMessage(), e);
            try {
                Container container = containerRepository.findById(containerId)
                        .orElse(null);
                if (container != null) {
                    log.warn("Setting container status to FAILED: {}", containerId);
                    container.setStatus(Container.ContainerStatus.FAILED);
                    Container failedContainer = containerRepository.save(container);
                    log.info("Container status updated to: {}", failedContainer.getStatus());
                }
            } catch (Exception ex) {
                log.error("Failed to update container status to FAILED: {}", containerId, ex);
            }
        }
    }
}
