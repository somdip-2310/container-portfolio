package dev.somdip.containerplatform.dto.deployment;

import dev.somdip.containerplatform.model.Deployment;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DeploymentResponse {
    private String deploymentId;
    private String containerId;
    private String status;
    private String type;
    private Instant startedAt;
    private Instant completedAt;
    private Long durationMillis;
    private List<DeploymentStepResponse> steps;
    private String errorMessage;
    private Map<String, String> metadata;
    
    public static DeploymentResponse from(Deployment deployment) {
        DeploymentResponse response = new DeploymentResponse();
        response.deploymentId = deployment.getDeploymentId();
        response.containerId = deployment.getContainerId();
        response.status = deployment.getStatus().toString();
        response.type = deployment.getType().toString();
        response.startedAt = deployment.getStartedAt();
        response.completedAt = deployment.getCompletedAt();
        response.durationMillis = deployment.getDurationMillis();
        response.errorMessage = deployment.getErrorMessage();
        response.metadata = deployment.getMetadata();
        
        if (deployment.getSteps() != null) {
            response.steps = deployment.getSteps().stream()
                .map(DeploymentStepResponse::from)
                .collect(Collectors.toList());
        }
        
        return response;
    }
    
    // Getters and setters
    public String getDeploymentId() { return deploymentId; }
    public void setDeploymentId(String deploymentId) { this.deploymentId = deploymentId; }
    public String getContainerId() { return containerId; }
    public void setContainerId(String containerId) { this.containerId = containerId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public Long getDurationMillis() { return durationMillis; }
    public void setDurationMillis(Long durationMillis) { this.durationMillis = durationMillis; }
    public List<DeploymentStepResponse> getSteps() { return steps; }
    public void setSteps(List<DeploymentStepResponse> steps) { this.steps = steps; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Map<String, String> getMetadata() { return metadata; }
    public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }
}