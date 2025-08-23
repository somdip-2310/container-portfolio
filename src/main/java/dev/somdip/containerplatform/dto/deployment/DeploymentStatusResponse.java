package dev.somdip.containerplatform.dto.deployment;

import dev.somdip.containerplatform.model.Deployment;
import dev.somdip.containerplatform.service.DeploymentTrackingService;
import java.time.Instant;
import java.util.Map;

public class DeploymentStatusResponse {
    private String deploymentId;
    private boolean completed;
    private boolean failed;
    private double progressPercentage;
    private String currentStep;
    private Map<String, String> metrics;
    private Instant lastUpdated;
    
    public static DeploymentStatusResponse from(DeploymentTrackingService.DeploymentStatus status, 
                                              Deployment deployment) {
        DeploymentStatusResponse response = new DeploymentStatusResponse();
        response.deploymentId = status.getDeploymentId();
        response.completed = status.isCompleted();
        response.failed = status.isFailed();
        response.lastUpdated = Instant.now();
        
        // Calculate progress
        if (deployment.getSteps() != null && !deployment.getSteps().isEmpty()) {
            long completedSteps = deployment.getSteps().stream()
                .filter(step -> step.getStatus() == Deployment.DeploymentStep.StepStatus.COMPLETED)
                .count();
            response.progressPercentage = (double) completedSteps / deployment.getSteps().size() * 100;
            
            // Get current step
            deployment.getSteps().stream()
                .filter(step -> step.getStatus() == Deployment.DeploymentStep.StepStatus.IN_PROGRESS)
                .findFirst()
                .ifPresent(step -> response.currentStep = step.getStepName());
        }
        
        response.metrics = status.getMetadata();
        return response;
    }
    
    // Getters and setters
    public String getDeploymentId() { return deploymentId; }
    public void setDeploymentId(String deploymentId) { this.deploymentId = deploymentId; }
    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }
    public boolean isFailed() { return failed; }
    public void setFailed(boolean failed) { this.failed = failed; }
    public double getProgressPercentage() { return progressPercentage; }
    public void setProgressPercentage(double progressPercentage) { this.progressPercentage = progressPercentage; }
    public String getCurrentStep() { return currentStep; }
    public void setCurrentStep(String currentStep) { this.currentStep = currentStep; }
    public Map<String, String> getMetrics() { return metrics; }
    public void setMetrics(Map<String, String> metrics) { this.metrics = metrics; }
    public Instant getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(Instant lastUpdated) { this.lastUpdated = lastUpdated; }
}