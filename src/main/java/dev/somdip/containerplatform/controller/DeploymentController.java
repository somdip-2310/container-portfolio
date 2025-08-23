package dev.somdip.containerplatform.controller;

import dev.somdip.containerplatform.dto.deployment.DeploymentResponse;
import dev.somdip.containerplatform.dto.deployment.DeploymentStatusResponse;
import dev.somdip.containerplatform.dto.deployment.HealthStatusResponse;
import dev.somdip.containerplatform.model.Deployment;
import dev.somdip.containerplatform.repository.DeploymentRepository;
import dev.somdip.containerplatform.service.ContainerService;
import dev.somdip.containerplatform.service.DeploymentTrackingService;
import dev.somdip.containerplatform.service.ContainerHealthCheckService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/deployments")
@CrossOrigin(origins = "*", maxAge = 3600)
public class DeploymentController {
    private static final Logger log = LoggerFactory.getLogger(DeploymentController.class);
    
    private final DeploymentRepository deploymentRepository;
    private final ContainerService containerService;
    
    public DeploymentController(DeploymentRepository deploymentRepository,
                              ContainerService containerService) {
        this.deploymentRepository = deploymentRepository;
        this.containerService = containerService;
    }
    
    @GetMapping("/{deploymentId}")
    public ResponseEntity<DeploymentResponse> getDeployment(
            @PathVariable String deploymentId,
            Authentication authentication) {
        Deployment deployment = deploymentRepository.findById(deploymentId)
            .orElse(null);
            
        if (deployment == null) {
            return ResponseEntity.notFound().build();
        }
        
        // Check if user owns this deployment
        if (!deployment.getUserId().equals(authentication.getName())) {
            return ResponseEntity.status(403).build();
        }
        
        return ResponseEntity.ok(DeploymentResponse.from(deployment));
    }
    
    @GetMapping("/container/{containerId}")
    public ResponseEntity<List<DeploymentResponse>> getContainerDeployments(
            @PathVariable String containerId,
            @RequestParam(defaultValue = "10") int limit,
            Authentication authentication) {
        // Verify user owns the container
        try {
            containerService.getContainer(containerId);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
        
        List<Deployment> deployments = deploymentRepository.findByContainerIdWithLimit(containerId, limit);
        List<DeploymentResponse> responses = deployments.stream()
            .map(DeploymentResponse::from)
            .collect(Collectors.toList());
            
        return ResponseEntity.ok(responses);
    }
    
    @GetMapping("/{deploymentId}/status")
    public ResponseEntity<DeploymentStatusResponse> getDeploymentStatus(
            @PathVariable String deploymentId,
            Authentication authentication) {
        // Verify deployment exists and user owns it
        Deployment deployment = deploymentRepository.findById(deploymentId)
            .orElse(null);
            
        if (deployment == null) {
            return ResponseEntity.notFound().build();
        }
        
        if (!deployment.getUserId().equals(authentication.getName())) {
            return ResponseEntity.status(403).build();
        }
        
        DeploymentTrackingService.DeploymentStatus status = 
            containerService.getDeploymentStatus(deploymentId);
            
        return ResponseEntity.ok(DeploymentStatusResponse.from(status, deployment));
    }
    
    @GetMapping("/container/{containerId}/health")
    public ResponseEntity<HealthStatusResponse> getContainerHealth(
            @PathVariable String containerId,
            Authentication authentication) {
        // Verify user owns the container
        try {
            containerService.getContainer(containerId);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
        
        ContainerHealthCheckService.HealthStatus healthStatus = 
            containerService.getHealthStatus(containerId);
            
        return ResponseEntity.ok(HealthStatusResponse.from(healthStatus));
    }
    
    @PostMapping("/{deploymentId}/rollback")
    public ResponseEntity<DeploymentResponse> rollbackDeployment(
            @PathVariable String deploymentId,
            Authentication authentication) {
        // This is a placeholder for rollback functionality
        // You would implement the actual rollback logic here
        return ResponseEntity.status(501).build(); // Not Implemented
    }
}