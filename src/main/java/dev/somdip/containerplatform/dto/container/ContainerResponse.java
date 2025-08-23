package dev.somdip.containerplatform.dto.container;

import dev.somdip.containerplatform.model.Container;
import java.time.Instant;
import java.util.Map;

public class ContainerResponse {
    private String containerId;
    private String name;
    private String image;
    private String imageTag;
    private String status;
    private String subdomain;
    private String url;
    private Integer port;
    private Integer cpu;
    private Integer memory;
    private Map<String, String> environmentVariables;
    private Instant createdAt;
    private Instant lastDeployedAt;
    private Long deploymentCount;
    
    public static ContainerResponse from(Container container) {
        ContainerResponse response = new ContainerResponse();
        response.containerId = container.getContainerId();
        response.name = container.getContainerName();
        response.image = container.getImage();
        response.imageTag = container.getImageTag();
        response.status = container.getStatus().toString();
        response.subdomain = container.getSubdomain();
        response.url = "https://" + container.getSubdomain() + ".containers.somdip.dev";
        response.port = container.getPort();
        response.cpu = container.getCpu();
        response.memory = container.getMemory();
        response.environmentVariables = container.getEnvironmentVariables();
        response.createdAt = container.getCreatedAt();
        response.lastDeployedAt = container.getLastDeployedAt();
        response.deploymentCount = container.getDeploymentCount();
        return response;
    }
    
    // All getters and setters
    public String getContainerId() { return containerId; }
    public void setContainerId(String containerId) { this.containerId = containerId; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }
    
    public String getImageTag() { return imageTag; }
    public void setImageTag(String imageTag) { this.imageTag = imageTag; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public String getSubdomain() { return subdomain; }
    public void setSubdomain(String subdomain) { this.subdomain = subdomain; }
    
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    
    public Integer getPort() { return port; }
    public void setPort(Integer port) { this.port = port; }
    
    public Integer getCpu() { return cpu; }
    public void setCpu(Integer cpu) { this.cpu = cpu; }
    
    public Integer getMemory() { return memory; }
    public void setMemory(Integer memory) { this.memory = memory; }
    
    public Map<String, String> getEnvironmentVariables() { return environmentVariables; }
    public void setEnvironmentVariables(Map<String, String> environmentVariables) { 
        this.environmentVariables = environmentVariables; 
    }
    
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    
    public Instant getLastDeployedAt() { return lastDeployedAt; }
    public void setLastDeployedAt(Instant lastDeployedAt) { this.lastDeployedAt = lastDeployedAt; }
    
    public Long getDeploymentCount() { return deploymentCount; }
    public void setDeploymentCount(Long deploymentCount) { this.deploymentCount = deploymentCount; }
}