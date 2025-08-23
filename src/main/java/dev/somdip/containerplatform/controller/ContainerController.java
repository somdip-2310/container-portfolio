package dev.somdip.containerplatform.controller;

import dev.somdip.containerplatform.dto.container.CreateContainerRequest;
import dev.somdip.containerplatform.dto.container.ContainerResponse;
import dev.somdip.containerplatform.model.Container;
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
    
    public ContainerController(ContainerService containerService) {
        this.containerService = containerService;
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
                request.getImageTag()
            );
            return ResponseEntity.status(HttpStatus.CREATED)
                .body(ContainerResponse.from(container));
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.error("Error creating container: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
    
    @GetMapping
    public ResponseEntity<List<ContainerResponse>> listContainers(Authentication authentication) {
        String userId = authentication.getName();
        List<Container> containers = containerService.listUserContainers(userId);
        List<ContainerResponse> responses = containers.stream()
            .map(ContainerResponse::from)
            .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
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
        }
    }
    
    @PostMapping("/{containerId}/deploy")
    public ResponseEntity<ContainerResponse> deployContainer(
            @PathVariable String containerId,
            Authentication authentication) {
        try {
            Container container = containerService.getContainer(containerId);
            if (!container.getUserId().equals(authentication.getName())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            container = containerService.deployContainer(containerId);
            return ResponseEntity.ok(ContainerResponse.from(container));
        } catch (Exception e) {
            log.error("Error deploying container: {}", e.getMessage());
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
        } catch (Exception e) {
            log.error("Error stopping container: {}", e.getMessage());
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
        } catch (Exception e) {
            log.error("Error deleting container: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}