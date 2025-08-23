package dev.somdip.containerplatform.dto.deployment;

import dev.somdip.containerplatform.model.Deployment;
import java.time.Instant;

public class DeploymentStepResponse {
    private String stepName;
    private String status;
    private String message;
    private Instant startedAt;
    private Instant completedAt;
    private String errorMessage;
    
    public static DeploymentStepResponse from(Deployment.DeploymentStep step) {
        DeploymentStepResponse response = new DeploymentStepResponse();
        response.stepName = step.getStepName();
        response.status = step.getStatus().toString();
        response.message = step.getMessage();
        response.startedAt = step.getStartedAt();
        response.completedAt = step.getCompletedAt();
        response.errorMessage = step.getErrorMessage();
        return response;
    }
    
    // Getters and setters
    public String getStepName() { return stepName; }
    public void setStepName(String stepName) { this.stepName = stepName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}