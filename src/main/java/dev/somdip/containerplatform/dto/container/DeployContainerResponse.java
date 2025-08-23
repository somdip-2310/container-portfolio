package dev.somdip.containerplatform.dto.container;

import dev.somdip.containerplatform.dto.deployment.DeploymentResponse;

public class DeployContainerResponse {
    private ContainerResponse container;
    private DeploymentResponse deployment;
    
    // Getters and setters
    public ContainerResponse getContainer() { return container; }
    public void setContainer(ContainerResponse container) { this.container = container; }
    public DeploymentResponse getDeployment() { return deployment; }
    public void setDeployment(DeploymentResponse deployment) { this.deployment = deployment; }
}