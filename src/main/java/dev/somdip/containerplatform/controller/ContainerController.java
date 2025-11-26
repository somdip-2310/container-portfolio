package dev.somdip.containerplatform.controller;

import dev.somdip.containerplatform.dto.container.CreateContainerRequest;
import dev.somdip.containerplatform.dto.container.UpdateContainerRequest;
import dev.somdip.containerplatform.dto.container.ContainerResponse;
import dev.somdip.containerplatform.dto.container.DeployContainerResponse;
import dev.somdip.containerplatform.dto.deployment.DeploymentResponse;
import dev.somdip.containerplatform.model.Container;
import dev.somdip.containerplatform.model.Deployment;
import dev.somdip.containerplatform.repository.DeploymentRepository;
import dev.somdip.containerplatform.security.CustomUserDetails;
import dev.somdip.containerplatform.model.User;
import dev.somdip.containerplatform.service.ContainerService;
import dev.somdip.containerplatform.service.LogStreamingService;
import dev.somdip.containerplatform.service.MetricsService;
import dev.somdip.containerplatform.service.UsageTrackingService;
import dev.somdip.containerplatform.service.UserService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/containers")
@CrossOrigin(origins = "*", maxAge = 3600)
public class ContainerController {
    private static final Logger log = LoggerFactory.getLogger(ContainerController.class);

    private final ContainerService containerService;
    private final DeploymentRepository deploymentRepository;
    private final LogStreamingService logStreamingService;
    private final MetricsService metricsService;
    private final UsageTrackingService usageTrackingService;
    private final UserService userService;

    public ContainerController(ContainerService containerService,
                             DeploymentRepository deploymentRepository,
                             LogStreamingService logStreamingService,
                             MetricsService metricsService,
                             UsageTrackingService usageTrackingService,
                             UserService userService) {
        this.containerService = containerService;
        this.deploymentRepository = deploymentRepository;
        this.logStreamingService = logStreamingService;
        this.metricsService = metricsService;
        this.usageTrackingService = usageTrackingService;
        this.userService = userService;
    }

    /**
     * Helper method to extract userId from Authentication
     */
    private String getUserId(Authentication authentication) {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        return userDetails.getUserId();
    }

    @PostMapping
    public ResponseEntity<ContainerResponse> createContainer(
            @Valid @RequestBody CreateContainerRequest request,
            Authentication authentication) {
        try {
            String userId = getUserId(authentication);
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
            String userId = getUserId(authentication);
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
            if (!container.getUserId().equals(getUserId(authentication))) {
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
            if (!container.getUserId().equals(getUserId(authentication))) {
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
            if (!container.getUserId().equals(getUserId(authentication))) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            // Check usage limits for FREE tier users before deployment
            String userId = getUserId(authentication);
            User user = userService.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found"));

            if (!usageTrackingService.canStartContainer(user)) {
                log.warn("User {} cannot deploy container - FREE tier limit exceeded", userId);
                return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).build();
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
            if (!container.getUserId().equals(getUserId(authentication))) {
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
            if (!container.getUserId().equals(getUserId(authentication))) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            // Check usage limits for FREE tier users before restart
            String userId = getUserId(authentication);
            User user = userService.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found"));

            if (!usageTrackingService.canStartContainer(user)) {
                log.warn("User {} cannot restart container - FREE tier limit exceeded", userId);
                return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).build();
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
            if (!container.getUserId().equals(getUserId(authentication))) {
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
    public ResponseEntity<Map<String, Object>> getContainerLogs(
            @PathVariable String containerId,
            @RequestParam(defaultValue = "100") int lines,
            Authentication authentication) {
        try {
            Container container = containerService.getContainer(containerId);
            if (!container.getUserId().equals(getUserId(authentication))) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            // Fetch logs from CloudWatch
            log.info("Fetching {} lines of logs for container: {}", lines, containerId);
            String logs = logStreamingService.getLatestLogs(containerId, lines);

            Map<String, Object> response = Map.of(
                "containerId", containerId,
                "containerName", container.getName(),
                "logs", logs,
                "lineCount", lines,
                "timestamp", System.currentTimeMillis()
            );

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("Container not found: {}", containerId);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error getting container logs for {}: {}", containerId, e.getMessage(), e);
            // Return error response with message
            Map<String, Object> errorResponse = Map.of(
                "error", "Failed to fetch logs",
                "message", e.getMessage(),
                "containerId", containerId
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @GetMapping("/{containerId}/metrics")
    public ResponseEntity<Map<String, Object>> getContainerMetrics(
            @PathVariable String containerId,
            @RequestParam(defaultValue = "1h") String period,
            Authentication authentication) {
        try {
            Container container = containerService.getContainer(containerId);
            if (!container.getUserId().equals(getUserId(authentication))) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            // Fetch metrics from CloudWatch
            log.info("Fetching metrics for container: {} with period: {}", containerId, period);
            Map<String, Object> metrics = metricsService.getContainerMetrics(
                Collections.singletonList(containerId)
            );

            // Add container metadata to response
            Map<String, Object> response = Map.of(
                "containerId", containerId,
                "containerName", container.getName(),
                "status", container.getStatus(),
                "metrics", metrics.get(containerId),
                "period", period,
                "timestamp", System.currentTimeMillis()
            );

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("Container not found: {}", containerId);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error getting container metrics for {}: {}", containerId, e.getMessage(), e);
            // Return error response with message
            Map<String, Object> errorResponse = Map.of(
                "error", "Failed to fetch metrics",
                "message", e.getMessage(),
                "containerId", containerId
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
}
