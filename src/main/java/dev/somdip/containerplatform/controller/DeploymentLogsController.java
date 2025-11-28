package dev.somdip.containerplatform.controller;

import dev.somdip.containerplatform.model.Deployment;
import dev.somdip.containerplatform.repository.DeploymentRepository;
import dev.somdip.containerplatform.security.CustomUserDetails;
import dev.somdip.containerplatform.service.DeploymentLogStreamService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/deployments")
public class DeploymentLogsController {
    private static final Logger log = LoggerFactory.getLogger(DeploymentLogsController.class);

    private final DeploymentLogStreamService logStreamService;
    private final DeploymentRepository deploymentRepository;

    public DeploymentLogsController(DeploymentLogStreamService logStreamService,
                                    DeploymentRepository deploymentRepository) {
        this.logStreamService = logStreamService;
        this.deploymentRepository = deploymentRepository;
    }

    /**
     * Stream deployment logs via SSE
     * GET /api/deployments/{deploymentId}/stream
     */
    @GetMapping(value = "/{deploymentId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamDeploymentLogs(
            @PathVariable String deploymentId,
            Authentication authentication) {

        String userId = getUserId(authentication);
        log.info("SSE stream requested for deployment: {} by user: {}", deploymentId, userId);

        return logStreamService.streamDeploymentLogs(deploymentId, userId);
    }

    /**
     * Get latest deployment for a container
     * GET /api/deployments/container/{containerId}/latest
     */
    @GetMapping("/container/{containerId}/latest")
    public ResponseEntity<?> getLatestDeployment(
            @PathVariable String containerId,
            Authentication authentication) {

        String userId = getUserId(authentication);

        Optional<Deployment> deploymentOpt = deploymentRepository.findLatestByContainerId(containerId);
        if (deploymentOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Deployment deployment = deploymentOpt.get();
        if (!deployment.getUserId().equals(userId)) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("deploymentId", deployment.getDeploymentId());
        response.put("containerId", deployment.getContainerId());
        response.put("containerName", deployment.getContainerName());
        response.put("status", deployment.getStatus().name());
        response.put("createdAt", deployment.getCreatedAt() != null ? deployment.getCreatedAt().toString() : null);

        return ResponseEntity.ok(response);
    }

    private String getUserId(Authentication authentication) {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        return userDetails.getUserId();
    }
}
