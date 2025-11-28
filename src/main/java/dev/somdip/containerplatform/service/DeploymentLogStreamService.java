package dev.somdip.containerplatform.service;

import dev.somdip.containerplatform.model.Deployment;
import dev.somdip.containerplatform.repository.DeploymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import software.amazon.awssdk.services.codebuild.CodeBuildClient;
import software.amazon.awssdk.services.codebuild.model.*;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

@Service
public class DeploymentLogStreamService {
    private static final Logger log = LoggerFactory.getLogger(DeploymentLogStreamService.class);

    private final DeploymentRepository deploymentRepository;
    private final CodeBuildClient codeBuildClient;

    // Map of deploymentId -> list of active SSE emitters
    private final ConcurrentHashMap<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    // Scheduled executor for polling
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

    // Track active polling tasks
    private final ConcurrentHashMap<String, ScheduledFuture<?>> pollingTasks = new ConcurrentHashMap<>();

    public DeploymentLogStreamService(DeploymentRepository deploymentRepository,
                                      CodeBuildClient codeBuildClient) {
        this.deploymentRepository = deploymentRepository;
        this.codeBuildClient = codeBuildClient;
    }

    /**
     * Create a new SSE emitter for streaming deployment logs
     */
    public SseEmitter streamDeploymentLogs(String deploymentId, String userId) {
        log.info("Creating SSE stream for deployment: {}", deploymentId);

        // Create emitter with 10-minute timeout
        SseEmitter emitter = new SseEmitter(600000L);

        // Verify deployment belongs to user
        Optional<Deployment> deploymentOpt = deploymentRepository.findById(deploymentId);
        if (deploymentOpt.isEmpty()) {
            try {
                emitter.send(SseEmitter.event()
                    .name("error")
                    .data("{\"error\": \"Deployment not found\"}"));
                emitter.complete();
            } catch (IOException e) {
                log.error("Error sending error message", e);
            }
            return emitter;
        }

        Deployment deployment = deploymentOpt.get();
        if (!deployment.getUserId().equals(userId)) {
            try {
                emitter.send(SseEmitter.event()
                    .name("error")
                    .data("{\"error\": \"Unauthorized\"}"));
                emitter.complete();
            } catch (IOException e) {
                log.error("Error sending error message", e);
            }
            return emitter;
        }

        // Add to emitters list
        emitters.computeIfAbsent(deploymentId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        // Handle completion/timeout/error
        emitter.onCompletion(() -> removeEmitter(deploymentId, emitter));
        emitter.onTimeout(() -> removeEmitter(deploymentId, emitter));
        emitter.onError(e -> removeEmitter(deploymentId, emitter));

        // Send initial state
        sendDeploymentStatus(deploymentId, emitter, deployment);

        // Start polling if deployment is in progress and not already polling
        if (isDeploymentActive(deployment) && !pollingTasks.containsKey(deploymentId)) {
            startPolling(deploymentId);
        }

        return emitter;
    }

    /**
     * Publish a deployment step update (called from GitHubBuildService)
     */
    public void publishStep(String deploymentId, String stepName, String status, String message) {
        log.debug("Publishing step: {} - {} - {} for deployment: {}", stepName, status, message, deploymentId);

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "step");
        event.put("stepName", stepName);
        event.put("status", status);
        event.put("message", message);
        event.put("timestamp", Instant.now().toString());

        broadcastToEmitters(deploymentId, "step", toJson(event));
    }

    /**
     * Publish a log line (called from GitHubBuildService)
     */
    public void publishLog(String deploymentId, String logLine) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "log");
        event.put("message", logLine);
        event.put("timestamp", Instant.now().toString());

        broadcastToEmitters(deploymentId, "log", toJson(event));
    }

    /**
     * Publish deployment status change
     */
    public void publishStatus(String deploymentId, Deployment.DeploymentStatus status, String message) {
        log.info("Publishing status: {} for deployment: {}", status, deploymentId);

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "status");
        event.put("status", status.name());
        event.put("message", message);
        event.put("timestamp", Instant.now().toString());

        broadcastToEmitters(deploymentId, "status", toJson(event));

        // If deployment is complete, stop polling and complete emitters
        if (status == Deployment.DeploymentStatus.COMPLETED ||
            status == Deployment.DeploymentStatus.FAILED) {
            stopPolling(deploymentId);
            completeAllEmitters(deploymentId);
        }
    }

    /**
     * Start polling CodeBuild for updates
     */
    private void startPolling(String deploymentId) {
        ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(() -> {
            try {
                pollDeploymentStatus(deploymentId);
            } catch (Exception e) {
                log.error("Error polling deployment status", e);
            }
        }, 0, 5, TimeUnit.SECONDS);

        pollingTasks.put(deploymentId, task);
    }

    /**
     * Stop polling for a deployment
     */
    private void stopPolling(String deploymentId) {
        ScheduledFuture<?> task = pollingTasks.remove(deploymentId);
        if (task != null) {
            task.cancel(false);
        }
    }

    /**
     * Poll CodeBuild for current status and logs
     */
    private void pollDeploymentStatus(String deploymentId) {
        Optional<Deployment> deploymentOpt = deploymentRepository.findById(deploymentId);
        if (deploymentOpt.isEmpty()) {
            stopPolling(deploymentId);
            return;
        }

        Deployment deployment = deploymentOpt.get();

        // Check if deployment is still active
        if (!isDeploymentActive(deployment)) {
            stopPolling(deploymentId);
            return;
        }

        // Get buildId from metadata
        Map<String, String> metadata = deployment.getMetadata();
        if (metadata == null || !metadata.containsKey("buildId")) {
            return;
        }

        String buildId = metadata.get("buildId");

        try {
            // Get build status from CodeBuild
            BatchGetBuildsRequest request = BatchGetBuildsRequest.builder()
                .ids(buildId)
                .build();

            BatchGetBuildsResponse response = codeBuildClient.batchGetBuilds(request);

            if (!response.builds().isEmpty()) {
                Build build = response.builds().get(0);

                // Get current build phase
                String currentPhase = build.currentPhase();
                String phaseStatus = build.buildStatus().toString();

                // Map CodeBuild phases to user-friendly steps
                String stepName = mapPhaseToStep(currentPhase);
                String stepMessage = getPhaseMessage(currentPhase, phaseStatus);

                // Publish step update
                publishStep(deploymentId, stepName, phaseStatus, stepMessage);

                // Check for completion
                if (build.buildStatus() == StatusType.SUCCEEDED) {
                    publishStep(deploymentId, "DEPLOYING", "IN_PROGRESS", "Deploying to ECS...");
                } else if (build.buildStatus() == StatusType.FAILED ||
                           build.buildStatus() == StatusType.FAULT ||
                           build.buildStatus() == StatusType.STOPPED) {
                    publishStatus(deploymentId, Deployment.DeploymentStatus.FAILED,
                        "Build failed: " + build.buildStatus());
                }
            }
        } catch (Exception e) {
            log.error("Error polling CodeBuild for deployment: {}", deploymentId, e);
        }
    }

    /**
     * Map CodeBuild phase to user-friendly step name
     */
    private String mapPhaseToStep(String phase) {
        if (phase == null) return "INITIALIZING";

        switch (phase.toUpperCase()) {
            case "SUBMITTED":
            case "QUEUED":
                return "INITIALIZING";
            case "PROVISIONING":
                return "PROVISIONING";
            case "DOWNLOAD_SOURCE":
                return "CLONING";
            case "INSTALL":
                return "INSTALLING_DEPENDENCIES";
            case "PRE_BUILD":
                return "PRE_BUILD";
            case "BUILD":
                return "BUILDING";
            case "POST_BUILD":
                return "PUSHING_IMAGE";
            case "UPLOAD_ARTIFACTS":
                return "PUSHING_IMAGE";
            case "FINALIZING":
                return "FINALIZING";
            case "COMPLETED":
                return "COMPLETED";
            default:
                return phase;
        }
    }

    /**
     * Get user-friendly message for phase
     */
    private String getPhaseMessage(String phase, String status) {
        if (phase == null) return "Starting build...";

        switch (phase.toUpperCase()) {
            case "SUBMITTED":
                return "Build submitted to queue...";
            case "QUEUED":
                return "Waiting for build environment...";
            case "PROVISIONING":
                return "Provisioning build environment...";
            case "DOWNLOAD_SOURCE":
                return "Cloning repository from GitHub...";
            case "INSTALL":
                return "Installing dependencies...";
            case "PRE_BUILD":
                return "Running pre-build commands...";
            case "BUILD":
                return "Building Docker image...";
            case "POST_BUILD":
                return "Running post-build commands...";
            case "UPLOAD_ARTIFACTS":
                return "Pushing image to ECR...";
            case "FINALIZING":
                return "Finalizing build...";
            case "COMPLETED":
                return "Build completed successfully!";
            default:
                return "Processing: " + phase;
        }
    }

    /**
     * Send current deployment status to a single emitter
     */
    private void sendDeploymentStatus(String deploymentId, SseEmitter emitter, Deployment deployment) {
        try {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("type", "init");
            event.put("deploymentId", deploymentId);
            event.put("status", deployment.getStatus().name());
            event.put("containerName", deployment.getContainerName());

            if (deployment.getMetadata() != null) {
                event.put("commitSha", deployment.getMetadata().get("commitSha"));
                event.put("commitMessage", deployment.getMetadata().get("commitMessage"));
            }

            if (deployment.getSteps() != null && !deployment.getSteps().isEmpty()) {
                List<Map<String, Object>> steps = new ArrayList<>();
                for (Deployment.DeploymentStep step : deployment.getSteps()) {
                    Map<String, Object> stepData = new LinkedHashMap<>();
                    stepData.put("stepName", step.getStepName());
                    stepData.put("status", step.getStatus().name());
                    stepData.put("message", step.getMessage());
                    steps.add(stepData);
                }
                event.put("steps", steps);
            }

            emitter.send(SseEmitter.event()
                .name("init")
                .data(toJson(event)));

        } catch (IOException e) {
            log.error("Error sending initial status", e);
            removeEmitter(deploymentId, emitter);
        }
    }

    /**
     * Broadcast event to all emitters for a deployment
     */
    private void broadcastToEmitters(String deploymentId, String eventName, String data) {
        List<SseEmitter> deploymentEmitters = emitters.get(deploymentId);
        if (deploymentEmitters == null || deploymentEmitters.isEmpty()) {
            return;
        }

        List<SseEmitter> failedEmitters = new ArrayList<>();

        for (SseEmitter emitter : deploymentEmitters) {
            try {
                emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(data));
            } catch (IOException e) {
                failedEmitters.add(emitter);
            }
        }

        // Remove failed emitters
        deploymentEmitters.removeAll(failedEmitters);
    }

    /**
     * Complete all emitters for a deployment
     */
    private void completeAllEmitters(String deploymentId) {
        List<SseEmitter> deploymentEmitters = emitters.remove(deploymentId);
        if (deploymentEmitters != null) {
            for (SseEmitter emitter : deploymentEmitters) {
                try {
                    emitter.complete();
                } catch (Exception e) {
                    // Ignore
                }
            }
        }
    }

    /**
     * Remove a single emitter
     */
    private void removeEmitter(String deploymentId, SseEmitter emitter) {
        List<SseEmitter> deploymentEmitters = emitters.get(deploymentId);
        if (deploymentEmitters != null) {
            deploymentEmitters.remove(emitter);

            // If no more emitters, stop polling
            if (deploymentEmitters.isEmpty()) {
                emitters.remove(deploymentId);
                stopPolling(deploymentId);
            }
        }
    }

    /**
     * Check if deployment is still active
     */
    private boolean isDeploymentActive(Deployment deployment) {
        return deployment.getStatus() == Deployment.DeploymentStatus.PENDING ||
               deployment.getStatus() == Deployment.DeploymentStatus.IN_PROGRESS;
    }

    /**
     * Convert object to JSON string
     */
    private String toJson(Map<String, Object> data) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(entry.getKey()).append("\":");
            Object value = entry.getValue();
            if (value == null) {
                sb.append("null");
            } else if (value instanceof String) {
                sb.append("\"").append(escapeJson((String) value)).append("\"");
            } else if (value instanceof Number || value instanceof Boolean) {
                sb.append(value);
            } else if (value instanceof List) {
                sb.append("[");
                boolean listFirst = true;
                for (Object item : (List<?>) value) {
                    if (!listFirst) sb.append(",");
                    listFirst = false;
                    if (item instanceof Map) {
                        sb.append(toJson((Map<String, Object>) item));
                    } else {
                        sb.append("\"").append(escapeJson(String.valueOf(item))).append("\"");
                    }
                }
                sb.append("]");
            } else {
                sb.append("\"").append(escapeJson(String.valueOf(value))).append("\"");
            }
        }
        sb.append("}");
        return sb.toString();
    }

    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
}
