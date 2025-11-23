package dev.somdip.containerplatform.controller;

import dev.somdip.containerplatform.dto.container.ContainerResponse;
import dev.somdip.containerplatform.model.Container;
import dev.somdip.containerplatform.service.ContainerService;
import dev.somdip.containerplatform.service.LogStreamingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
     * Get all containers for the authenticated user
     */
    @GetMapping("/containers")
    public ResponseEntity<List<ContainerResponse>> listContainers(Authentication authentication) {
        try {
            String userId = authentication.getName();
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
            if (!container.getUserId().equals(authentication.getName())) {
                return ResponseEntity.status(403).build();
            }

            String logs = logStreamingService.getLatestLogs(containerId, lines);

            return ResponseEntity.ok(Map.of(
                "containerId", containerId,
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
}
