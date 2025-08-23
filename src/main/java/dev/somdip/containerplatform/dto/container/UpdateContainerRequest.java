package dev.somdip.containerplatform.dto.container;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import java.util.Map;

import dev.somdip.containerplatform.model.Container;

public class UpdateContainerRequest {
    
    @Min(256)
    @Max(4096)
    private Integer cpu;
    
    @Min(512)
    @Max(30720)
    private Integer memory;
    
    private Map<String, String> environmentVariables;
    
    private Container.HealthCheckConfig healthCheck;
    
    // Getters and setters
    public Integer getCpu() { 
        return cpu; 
    }
    
    public void setCpu(Integer cpu) { 
        this.cpu = cpu; 
    }
    
    public Integer getMemory() { 
        return memory; 
    }
    
    public void setMemory(Integer memory) { 
        this.memory = memory; 
    }
    
    public Map<String, String> getEnvironmentVariables() { 
        return environmentVariables; 
    }
    
    public void setEnvironmentVariables(Map<String, String> environmentVariables) { 
        this.environmentVariables = environmentVariables; 
    }
    
    public Container.HealthCheckConfig getHealthCheck() { 
        return healthCheck; 
    }
    
    public void setHealthCheck(Container.HealthCheckConfig healthCheck) { 
        this.healthCheck = healthCheck; 
    }
}