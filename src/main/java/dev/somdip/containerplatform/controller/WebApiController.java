package dev.somdip.containerplatform.controller;

import dev.somdip.containerplatform.dto.container.ContainerResponse;
import dev.somdip.containerplatform.dto.container.CreateContainerRequest;
import dev.somdip.containerplatform.model.Container;
import dev.somdip.containerplatform.security.CustomUserDetails;
import dev.somdip.containerplatform.service.ContainerService;
import dev.somdip.containerplatform.service.LogStreamingService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Web API Controller for session-based authentication
 * These endpoints are accessed from browser pages and use session cookies instead of JWT
 */
@RestController
@RequestMapping("/web/api")
public class WebApiController {
    private static final Logger log = LoggerFactory.getLogger(WebApiController.class);

    private final ContainerService containerService;
    private final LogStreamingService logStreamingService;

    public WebApiController(ContainerService containerService,
                           LogStreamingService logStreamingService) {
        this.containerService = containerService;
        this.logStreamingService = logStreamingService;
    }

    /**
     * Helper method to extract userId from Authentication
     */
    private String getUserId(Authentication authentication) {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        return userDetails.getUserId();
    }

    /**
     * Create a new container
     */
    @PostMapping("/containers")
    public ResponseEntity<ContainerResponse> createContainer(
            @Valid @RequestBody CreateContainerRequest request,
            Authentication authentication) {
        try {
            String userId = getUserId(authentication);
            log.info("Creating container for user: {}, name: {}", userId, request.getName());

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

    /**
     * Get all containers for the authenticated user
     */
    @GetMapping("/containers")
    public ResponseEntity<List<ContainerResponse>> listContainers(Authentication authentication) {
        try {
            String userId = getUserId(authentication);
            log.debug("Fetching containers for user: {}", userId);

            List<Container> containers = containerService.listUserContainers(userId);
            List<ContainerResponse> responses = containers.stream()
                .map(ContainerResponse::from)
                .collect(Collectors.toList());

            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            log.error("Error listing containers: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get container logs
     */
    @GetMapping("/containers/{containerId}/logs")
    public ResponseEntity<Map<String, Object>> getContainerLogs(
            @PathVariable String containerId,
            @RequestParam(defaultValue = "100") int lines,
            Authentication authentication) {
        try {
            // Verify ownership
            Container container = containerService.getContainer(containerId);
            if (!container.getUserId().equals(getUserId(authentication))) {
                return ResponseEntity.status(403).build();
            }

            String logs = logStreamingService.getLatestLogs(containerId, lines);

            return ResponseEntity.ok(Map.of(
                "containerId", containerId,
                "containerName", container.getContainerName(),
                "logs", logs != null ? logs : "",
                "lineCount", lines
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error fetching logs: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    
    /**
     * Start a stopped container
     */
    @PostMapping("/containers/{containerId}/start")
    public ResponseEntity<ContainerResponse> startContainer(
            @PathVariable String containerId,
            Authentication authentication) {
        try {
            // Verify ownership
            Container container = containerService.getContainer(containerId);
            if (!container.getUserId().equals(getUserId(authentication))) {
                return ResponseEntity.status(403).build();
            }

            log.info("Starting container: {}", containerId);
            
            // Check if container is already running
            if (container.getStatus() == Container.ContainerStatus.RUNNING) {
                return ResponseEntity.badRequest().body(ContainerResponse.from(container));
            }
            
            // Deploy/start the container
            Container startedContainer = containerService.deployContainer(containerId);
            return ResponseEntity.ok(ContainerResponse.from(startedContainer));
        } catch (IllegalArgumentException e) {
            log.error("Container not found: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error starting container: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    /**
     * Delete a container
     */
    @DeleteMapping("/containers/{containerId}")
    public ResponseEntity<Void> deleteContainer(
            @PathVariable String containerId,
            Authentication authentication) {
        try {
            // Verify ownership
            Container container = containerService.getContainer(containerId);
            if (!container.getUserId().equals(getUserId(authentication))) {
                return ResponseEntity.status(403).build();
            }

            log.info("Deleting container: {}", containerId);
            containerService.deleteContainer(containerId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            log.error("Container not found: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error deleting container: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Start/Deploy a container
     */
    @PostMapping("/containers/{containerId}/deploy")
    public ResponseEntity<ContainerResponse> deployContainer(
            @PathVariable String containerId,
            Authentication authentication) {
        try {
            // Verify ownership
            Container container = containerService.getContainer(containerId);
            if (!container.getUserId().equals(getUserId(authentication))) {
                return ResponseEntity.status(403).build();
            }

            log.info("Starting/deploying container: {}", containerId);
            Container deployedContainer = containerService.deployContainer(containerId);
            return ResponseEntity.ok(ContainerResponse.from(deployedContainer));
        } catch (IllegalArgumentException e) {
            log.error("Container not found: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error deploying container: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Stop a container
     */
    @PostMapping("/containers/{containerId}/stop")
    public ResponseEntity<ContainerResponse> stopContainer(
            @PathVariable String containerId,
            Authentication authentication) {
        try {
            // Verify ownership
            Container container = containerService.getContainer(containerId);
            if (!container.getUserId().equals(getUserId(authentication))) {
                return ResponseEntity.status(403).build();
            }

            log.info("Stopping container: {}", containerId);
            Container stoppedContainer = containerService.stopContainer(containerId);
            return ResponseEntity.ok(ContainerResponse.from(stoppedContainer));
        } catch (IllegalArgumentException e) {
            log.error("Container not found: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error stopping container: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/resource-usage")
    public ResponseEntity<Map<String, Object>> getResourceUsage(Authentication authentication) {
        try {
            User user = userService.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
            
            List<Container> containers = containerService.getUserContainers(user.getUserId());
            int containerCount = containers.size();
            int containerLimit = getContainerLimitForPlan(user.getPlan());
            
            // Get storage usage
            long storageUsedMb = user.getTotalStorageGb() != null ? user.getTotalStorageGb() * 1024 : 0;
            long storageLimitMb = getStorageLimitForPlan(user.getPlan()) * 1024;
            
            Map<String, Object> usage = new HashMap<>();
            usage.put("containerCount", containerCount);
            usage.put("containerLimit", containerLimit);
            usage.put("storageUsedGb", user.getTotalStorageGb() != null ? user.getTotalStorageGb() : 0);
            usage.put("storageLimitGb", getStorageLimitForPlan(user.getPlan()));
            
            return ResponseEntity.ok(usage);
        } catch (Exception e) {
            log.error("Error fetching resource usage: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    private int getContainerLimitForPlan(User.UserPlan plan) {
        return switch (plan) {
            case FREE -> 1;
            case STARTER -> 3;
            case PRO -> 10;
            case SCALE -> 25;
            case ENTERPRISE -> Integer.MAX_VALUE;
        };
    }
    
    private int getStorageLimitForPlan(User.UserPlan plan) {
        return switch (plan) {
            case FREE -> 5;
            case STARTER -> 20;
            case PRO -> 100;
            case SCALE -> 500;
            case ENTERPRISE -> Integer.MAX_VALUE;
        };
    }
}
