package dev.somdip.containerplatform.service;

import dev.somdip.containerplatform.model.Container;
import dev.somdip.containerplatform.model.Deployment;
import dev.somdip.containerplatform.repository.ContainerRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

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
    private final HealthCheckService healthCheckService;

    public AsyncContainerOperations(
            EcsService ecsService,
            ContainerRepository containerRepository,
            DeploymentTrackingService deploymentTrackingService,
            HealthCheckService healthCheckService) {
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
    public void deployContainerAsync(String containerId) {
        log.info("Async deployment started for container: {}", containerId);

        try {
            Container container = containerRepository.findById(containerId)
                    .orElseThrow(() -> new IllegalArgumentException("Container not found: " + containerId));

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

            containerRepository.save(container);

            // Start health monitoring
            healthCheckService.startHealthMonitoring(containerId);

            log.info("Async deployment completed successfully for container: {}", containerId);

        } catch (Exception e) {
            log.error("Async deployment failed for container: {}", containerId, e);
            try {
                Container container = containerRepository.findById(containerId)
                        .orElse(null);
                if (container != null) {
                    container.setStatus(Container.ContainerStatus.FAILED);
                    containerRepository.save(container);
                }
            } catch (Exception ex) {
                log.error("Failed to update container status to FAILED: {}", containerId, ex);
            }
        }
    }
}
