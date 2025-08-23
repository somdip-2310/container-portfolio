package dev.somdip.containerplatform.service;

import dev.somdip.containerplatform.model.Deployment;
import dev.somdip.containerplatform.repository.DeploymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DeploymentTrackingService {
    private static final Logger log = LoggerFactory.getLogger(DeploymentTrackingService.class);
    
    private final DeploymentRepository deploymentRepository;
    private final EcsClient ecsClient;
    private final Map<String, DeploymentStatus> deploymentStatusCache = new ConcurrentHashMap<>();
    
    public DeploymentTrackingService(DeploymentRepository deploymentRepository,
                                   EcsClient ecsClient) {
        this.deploymentRepository = deploymentRepository;
        this.ecsClient = ecsClient;
    }
    
    /**
     * Track deployment progress and update status
     */
    public void trackDeployment(String deploymentId) {
        log.debug("Tracking deployment: {}", deploymentId);
        
        Deployment deployment = deploymentRepository.findById(deploymentId)
            .orElseThrow(() -> new IllegalArgumentException("Deployment not found: " + deploymentId));
        
        // Add to tracking cache
        deploymentStatusCache.put(deploymentId, new DeploymentStatus(deployment));
    }
    
    /**
     * Get real-time deployment status
     */
    public DeploymentStatus getDeploymentStatus(String deploymentId) {
        DeploymentStatus status = deploymentStatusCache.get(deploymentId);
        if (status == null) {
            Deployment deployment = deploymentRepository.findById(deploymentId)
                .orElseThrow(() -> new IllegalArgumentException("Deployment not found: " + deploymentId));
            status = new DeploymentStatus(deployment);
        }
        return status;
    }
    
    /**
     * Scheduled task to update deployment statuses
     */
    @Scheduled(fixedDelay = 5000) // Every 5 seconds
    public void updateDeploymentStatuses() {
        List<Deployment> activeDeployments = deploymentRepository.findActiveDeployments();
        
        for (Deployment deployment : activeDeployments) {
            try {
                updateDeploymentStatus(deployment);
            } catch (Exception e) {
                log.error("Error updating deployment status: {}", deployment.getDeploymentId(), e);
            }
        }
        
        // Clean up completed deployments from cache after 5 minutes
        deploymentStatusCache.entrySet().removeIf(entry -> {
            DeploymentStatus status = entry.getValue();
            return status.isCompleted() && 
                   status.getCompletedAt() != null &&
                   status.getCompletedAt().isBefore(Instant.now().minusSeconds(300));
        });
    }
    
    private void updateDeploymentStatus(Deployment deployment) {
        log.debug("Updating status for deployment: {}", deployment.getDeploymentId());
        
        // Check ECS service status
        if (deployment.getContainerId() != null) {
            String serviceName = "service-" + deployment.getContainerId();
            
            try {
                DescribeServicesRequest request = DescribeServicesRequest.builder()
                    .cluster("somdip-dev-cluster")
                    .services(serviceName)
                    .build();
                
                DescribeServicesResponse response = ecsClient.describeServices(request);
                
                if (!response.services().isEmpty()) {
                    software.amazon.awssdk.services.ecs.model.Service service = response.services().get(0);
                    
                    // Update deployment based on service status
                    updateDeploymentFromServiceStatus(deployment, service);
                }
            } catch (Exception e) {
                log.error("Error checking service status", e);
                markDeploymentFailed(deployment, "Failed to check service status: " + e.getMessage());
            }
        }
    }
    
    private void updateDeploymentFromServiceStatus(Deployment deployment, 
                                                  software.amazon.awssdk.services.ecs.model.Service service) {
        // Check if there are any deployment failures
        for (software.amazon.awssdk.services.ecs.model.Deployment ecsDeployment : service.deployments()) {
            if ("PRIMARY".equals(ecsDeployment.status())) {
                String rolloutState = ecsDeployment.rolloutState() != null ? 
                    ecsDeployment.rolloutState().toString() : "UNKNOWN";
                
                switch (rolloutState) {
                    case "COMPLETED":
                        if (deployment.getStatus() != Deployment.DeploymentStatus.COMPLETED) {
                            markDeploymentCompleted(deployment);
                        }
                        break;
                    
                    case "FAILED":
                        markDeploymentFailed(deployment, ecsDeployment.rolloutStateReason());
                        break;
                    
                    case "IN_PROGRESS":
                        // Update progress
                        updateDeploymentProgress(deployment, ecsDeployment);
                        break;
                }
            }
        }
        
        // Check if service is stable
        if (service.runningCount() == service.desiredCount() && 
            service.pendingCount() == 0) {
            
            // Check if all tasks are healthy
            checkTaskHealth(deployment, service);
        }
    }
    
    private void updateDeploymentProgress(Deployment deployment, 
                                        software.amazon.awssdk.services.ecs.model.Deployment ecsDeployment) {
        // Calculate progress percentage
        int running = ecsDeployment.runningCount();
        int desired = ecsDeployment.desiredCount();
        int pending = ecsDeployment.pendingCount();
        
        double progress = desired > 0 ? (double) running / desired * 100 : 0;
        
        log.debug("Deployment progress: {}% (Running: {}, Desired: {}, Pending: {})",
            progress, running, desired, pending);
        
        // Update deployment metadata
        Map<String, String> metadata = deployment.getMetadata();
        if (metadata == null) {
            metadata = new ConcurrentHashMap<>();
            deployment.setMetadata(metadata);
        }
        
        metadata.put("progress", String.format("%.0f", progress));
        metadata.put("runningTasks", String.valueOf(running));
        metadata.put("desiredTasks", String.valueOf(desired));
        metadata.put("pendingTasks", String.valueOf(pending));
        
        deploymentRepository.save(deployment);
    }
    
    private void checkTaskHealth(Deployment deployment, 
                               software.amazon.awssdk.services.ecs.model.Service service) {
        try {
            ListTasksRequest listRequest = ListTasksRequest.builder()
                .cluster("somdip-dev-cluster")
                .serviceName(service.serviceName())
                .desiredStatus(DesiredStatus.RUNNING)
                .build();
            
            ListTasksResponse listResponse = ecsClient.listTasks(listRequest);
            
            if (!listResponse.taskArns().isEmpty()) {
                DescribeTasksRequest describeRequest = DescribeTasksRequest.builder()
                    .cluster("somdip-dev-cluster")
                    .tasks(listResponse.taskArns())
                    .build();
                
                DescribeTasksResponse describeResponse = ecsClient.describeTasks(describeRequest);
                
                boolean allHealthy = describeResponse.tasks().stream()
                    .allMatch(task -> "HEALTHY".equals(task.healthStatus().toString()));
                
                if (allHealthy && deployment.getStatus() == Deployment.DeploymentStatus.IN_PROGRESS) {
                    markDeploymentCompleted(deployment);
                }
            }
        } catch (Exception e) {
            log.error("Error checking task health", e);
        }
    }
    
    private void markDeploymentCompleted(Deployment deployment) {
        log.info("Marking deployment as completed: {}", deployment.getDeploymentId());
        
        deployment.setStatus(Deployment.DeploymentStatus.COMPLETED);
        deployment.setCompletedAt(Instant.now());
        if (deployment.getStartedAt() != null) {
            deployment.setDurationMillis(
                Instant.now().toEpochMilli() - deployment.getStartedAt().toEpochMilli()
            );
        }
        
        // Mark all pending steps as completed
        deployment.getSteps().stream()
            .filter(step -> step.getStatus() == Deployment.DeploymentStep.StepStatus.PENDING ||
                          step.getStatus() == Deployment.DeploymentStep.StepStatus.IN_PROGRESS)
            .forEach(step -> {
                step.setStatus(Deployment.DeploymentStep.StepStatus.COMPLETED);
                step.setCompletedAt(Instant.now());
            });
        
        deploymentRepository.save(deployment);
        
        // Update cache
        DeploymentStatus status = deploymentStatusCache.get(deployment.getDeploymentId());
        if (status != null) {
            status.setCompleted(true);
            status.setCompletedAt(Instant.now());
        }
    }
    
    private void markDeploymentFailed(Deployment deployment, String reason) {
        log.error("Marking deployment as failed: {} - {}", deployment.getDeploymentId(), reason);
        
        deployment.setStatus(Deployment.DeploymentStatus.FAILED);
        deployment.setCompletedAt(Instant.now());
        deployment.setErrorMessage(reason);
        if (deployment.getStartedAt() != null) {
            deployment.setDurationMillis(
                Instant.now().toEpochMilli() - deployment.getStartedAt().toEpochMilli()
            );
        }
        
        // Mark current step as failed
        deployment.getSteps().stream()
            .filter(step -> step.getStatus() == Deployment.DeploymentStep.StepStatus.IN_PROGRESS)
            .forEach(step -> {
                step.setStatus(Deployment.DeploymentStep.StepStatus.FAILED);
                step.setCompletedAt(Instant.now());
                step.setErrorMessage(reason);
            });
        
        deploymentRepository.save(deployment);
        
        // Update cache
        DeploymentStatus status = deploymentStatusCache.get(deployment.getDeploymentId());
        if (status != null) {
            status.setCompleted(true);
            status.setFailed(true);
            status.setCompletedAt(Instant.now());
            status.setErrorMessage(reason);
        }
    }
    
    /**
     * Status object for caching deployment status
     */
    public static class DeploymentStatus {
        private final String deploymentId;
        private final String containerId;
        private boolean completed;
        private boolean failed;
        private Instant startedAt;
        private Instant completedAt;
        private String errorMessage;
        private Map<String, String> metadata;
        
        public DeploymentStatus(Deployment deployment) {
            this.deploymentId = deployment.getDeploymentId();
            this.containerId = deployment.getContainerId();
            this.completed = deployment.getStatus() == Deployment.DeploymentStatus.COMPLETED ||
                           deployment.getStatus() == Deployment.DeploymentStatus.FAILED;
            this.failed = deployment.getStatus() == Deployment.DeploymentStatus.FAILED;
            this.startedAt = deployment.getStartedAt();
            this.completedAt = deployment.getCompletedAt();
            this.errorMessage = deployment.getErrorMessage();
            this.metadata = deployment.getMetadata();
        }
        
        // Getters and setters
        public String getDeploymentId() { return deploymentId; }
        public String getContainerId() { return containerId; }
        public boolean isCompleted() { return completed; }
        public void setCompleted(boolean completed) { this.completed = completed; }
        public boolean isFailed() { return failed; }
        public void setFailed(boolean failed) { this.failed = failed; }
        public Instant getStartedAt() { return startedAt; }
        public Instant getCompletedAt() { return completedAt; }
        public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        public Map<String, String> getMetadata() { return metadata; }
    }
}