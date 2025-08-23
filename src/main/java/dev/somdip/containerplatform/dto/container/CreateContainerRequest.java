package dev.somdip.containerplatform.dto.container;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;

public class CreateContainerRequest {
    @NotBlank(message = "Container name is required")
    @Size(min = 3, max = 63, message = "Container name must be between 3 and 63 characters")
    @Pattern(regexp = "^[a-z0-9][a-z0-9-]*[a-z0-9]$", 
             message = "Container name must start and end with alphanumeric characters and can contain hyphens")
    private String name;
    
    @NotBlank(message = "Image is required")
    private String image;
    
    private String imageTag = "latest";
    private Integer port = 8080;
    private Integer cpu = 256;
    private Integer memory = 512;
    private Map<String, String> environmentVariables;
    
    // Getters and setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }
    
    public String getImageTag() { return imageTag; }
    public void setImageTag(String imageTag) { this.imageTag = imageTag; }
    
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
}