package dev.somdip.containerplatform.controller;

import dev.somdip.containerplatform.dto.container.CreateContainerRequest;
import dev.somdip.containerplatform.dto.container.UpdateContainerRequest;
import dev.somdip.containerplatform.dto.container.ContainerResponse;
import dev.somdip.containerplatform.dto.container.DeployContainerResponse;
import dev.somdip.containerplatform.dto.deployment.DeploymentResponse;
import dev.somdip.containerplatform.model.Container;
import dev.somdip.containerplatform.model.Deployment;
import dev.somdip.containerplatform.repository.DeploymentRepository;
import dev.somdip.containerplatform.service.ContainerService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/containers")
@CrossOrigin(origins = "*", maxAge = 3600)
public class ContainerController {
    private static final Logger log = LoggerFactory.getLogger(ContainerController.class);
    
    private final ContainerService containerService;
    private final DeploymentRepository deploymentRepository;
    
    public ContainerController(ContainerService containerService, 
                             DeploymentRepository deploymentRepository) {
        this.containerService = containerService;
        this.deploymentRepository = deploymentRepository;
    }
    
    @PostMapping
    public ResponseEntity<ContainerResponse> createContainer(
            @Valid @RequestBody CreateContainerRequest request,
            Authentication authentication) {
        try {
            String userId = authentication.getName();
            Container container = containerService.createContainer(
                    userId,
                    request.getName(),
                    request.getImage(),
                    request.getImageTag(),
                    request.getPort()
                );
            return ResponseEntity.status(HttpStatus.CREATED)
                .body(ContainerResponse.from(container));
        } catch (IllegalArgumentException e) {
            log.error("Invalid request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (IllegalStateException e) {
            log.error("Container limit reached: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        } catch (Exception e) {
            log.error("Unexpected error creating container: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping
    public ResponseEntity<List<ContainerResponse>> listContainers(Authentication authentication) {
        try {
            String userId = authentication.getName();
            List<Container> containers = containerService.listUserContainers(userId);
            List<ContainerResponse> responses = containers.stream()
                .map(ContainerResponse::from)
                .collect(Collectors.toList());
            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            log.error("Error listing containers: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("/{containerId}")
    public ResponseEntity<ContainerResponse> getContainer(
            @PathVariable String containerId,
            Authentication authentication) {
        try {
            Container container = containerService.getContainer(containerId);
            if (!container.getUserId().equals(authentication.getName())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            return ResponseEntity.ok(ContainerResponse.from(container));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error getting container: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PutMapping("/{containerId}")
    public ResponseEntity<ContainerResponse> updateContainer(
            @PathVariable String containerId,
            @Valid @RequestBody UpdateContainerRequest request,
            Authentication authentication) {
        try {
            Container container = containerService.getContainer(containerId);
            if (!container.getUserId().equals(authentication.getName())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            
            container = containerService.updateContainer(
                containerId, 
                request.getCpu(), 
                request.getMemory(), 
                request.getEnvironmentVariables()
            );
            
            return ResponseEntity.ok(ContainerResponse.from(container));
        } catch (IllegalArgumentException e) {
            log.error("Invalid update request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error updating container: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PostMapping("/{containerId}/deploy")
    public ResponseEntity<DeployContainerResponse> deployContainer(
            @PathVariable String containerId,
            Authentication authentication) {
        try {
            Container container = containerService.getContainer(containerId);
            if (!container.getUserId().equals(authentication.getName())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            
            // Deploy the container
            container = containerService.deployContainer(containerId);
            
            // Get the latest deployment
            Deployment latestDeployment = deploymentRepository
                .findLatestByContainerId(containerId)
                .orElse(null);
            
            DeployContainerResponse response = new DeployContainerResponse();
            response.setContainer(ContainerResponse.from(container));
            if (latestDeployment != null) {
                response.setDeployment(DeploymentResponse.from(latestDeployment));
            }
            
            return ResponseEntity.ok(response);
        } catch (IllegalStateException e) {
            log.error("Container already running: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error deploying container: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PostMapping("/{containerId}/stop")
    public ResponseEntity<ContainerResponse> stopContainer(
            @PathVariable String containerId,
            Authentication authentication) {
        try {
            Container container = containerService.getContainer(containerId);
            if (!container.getUserId().equals(authentication.getName())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            container = containerService.stopContainer(containerId);
            return ResponseEntity.ok(ContainerResponse.from(container));
        } catch (IllegalStateException e) {
            log.error("Container not running: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error stopping container: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PostMapping("/{containerId}/restart")
    public ResponseEntity<ContainerResponse> restartContainer(
            @PathVariable String containerId,
            Authentication authentication) {
        try {
            Container container = containerService.getContainer(containerId);
            if (!container.getUserId().equals(authentication.getName())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            
            // Stop the container if it's running
            if (container.getStatus() == Container.ContainerStatus.RUNNING) {
                containerService.stopContainer(containerId);
                // Wait a bit for clean shutdown
                Thread.sleep(2000);
            }
            
            // Deploy it again
            container = containerService.deployContainer(containerId);
            return ResponseEntity.ok(ContainerResponse.from(container));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error restarting container: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @DeleteMapping("/{containerId}")
    public ResponseEntity<Void> deleteContainer(
            @PathVariable String containerId,
            Authentication authentication) {
        try {
            Container container = containerService.getContainer(containerId);
            if (!container.getUserId().equals(authentication.getName())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            containerService.deleteContainer(containerId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error deleting container: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("/{containerId}/logs")
    public ResponseEntity<String> getContainerLogs(
            @PathVariable String containerId,
            @RequestParam(defaultValue = "100") int lines,
            Authentication authentication) {
        try {
            Container container = containerService.getContainer(containerId);
            if (!container.getUserId().equals(authentication.getName())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            
            // TODO: Implement log fetching from CloudWatch
            // For now, return a placeholder
            return ResponseEntity.ok("Log fetching not yet implemented");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error getting container logs: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("/{containerId}/metrics")
    public ResponseEntity<String> getContainerMetrics(
            @PathVariable String containerId,
            @RequestParam(defaultValue = "1h") String period,
            Authentication authentication) {
        try {
            Container container = containerService.getContainer(containerId);
            if (!container.getUserId().equals(authentication.getName())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            
            // TODO: Implement metrics fetching from CloudWatch
            // For now, return a placeholder
            return ResponseEntity.ok("Metrics fetching not yet implemented");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error getting container metrics: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}