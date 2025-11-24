package dev.somdip.containerplatform.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.somdip.containerplatform.dto.ContainerMetrics;
import dev.somdip.containerplatform.service.ContainerService;
import dev.somdip.containerplatform.service.MetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class MetricsHandler extends TextWebSocketHandler {
    
	/*
    private final MetricsService metricsService;
    private final ContainerService containerService;
    private final ObjectMapper objectMapper;
    
    // Store active sessions and their metrics streaming tasks
    private final Map<String, ScheduledFuture<?>> activeSessions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("Metrics WebSocket connection established: {}", session.getId());
        
        // Send initial connection success message
        Map<String, Object> response = Map.of(
            "type", "connection",
            "status", "connected",
            "message", "Metrics streaming connection established"
        );
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
    }
    
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            // Parse the incoming message
            Map<String, Object> request = objectMapper.readValue(message.getPayload(), Map.class);
            String action = (String) request.get("action");
            
            // Get authenticated user
            Principal principal = session.getPrincipal();
            if (principal == null) {
                sendError(session, "Authentication required");
                return;
            }
            
            switch (action) {
                case "subscribe":
                    subscribeToMetrics(session, request, principal.getName());
                    break;
                case "unsubscribe":
                    unsubscribeFromMetrics(session);
                    break;
                case "get_snapshot":
                    sendMetricsSnapshot(session, request, principal.getName());
                    break;
                default:
                    sendError(session, "Unknown action: " + action);
            }
            
        } catch (Exception e) {
            log.error("Error handling metrics WebSocket message", e);
            sendError(session, "Error processing request: " + e.getMessage());
        }
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        log.info("Metrics WebSocket connection closed: {} - {}", session.getId(), status);
        unsubscribeFromMetrics(session);
    }
    
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("Metrics WebSocket transport error for session: {}", session.getId(), exception);
        unsubscribeFromMetrics(session);
    }
    
    private void subscribeToMetrics(WebSocketSession session, Map<String, Object> request, String username) {
        // Stop any existing subscription for this session
        unsubscribeFromMetrics(session);
        
        String subscriptionType = (String) request.get("type");
        List<String> containerIds = (List<String>) request.get("containerIds");
        int interval = request.containsKey("interval") ? (int) request.get("interval") : 5; // Default 5 seconds
        
        // Validate container ownership
        if (containerIds != null && !containerIds.isEmpty()) {
            for (String containerId : containerIds) {
                if (!containerService.isOwner(containerId, username)) {
                    try {
                        sendError(session, "Unauthorized access to container: " + containerId);
                    } catch (Exception e) {
                        log.error("Error sending unauthorized message", e);
                    }
                    return;
                }
            }
        }
        
        // Start metrics streaming task
        ScheduledFuture<?> streamingTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                Map<String, Object> metrics = null;
                
                switch (subscriptionType) {
                    case "container":
                        // Stream metrics for specific containers
                        if (containerIds != null && !containerIds.isEmpty()) {
                            metrics = metricsService.getContainerMetrics(containerIds);
                        }
                        break;
                    case "dashboard":
                        // Stream overall dashboard metrics
                        metrics = metricsService.getDashboardMetrics(username);
                        break;
                    case "all":
                        // Stream all user container metrics
                        metrics = metricsService.getAllUserMetrics(username);
                        break;
                }
                
                if (metrics != null) {
                    Map<String, Object> metricsMessage = Map.of(
                        "type", "metrics_update",
                        "subscriptionType", subscriptionType,
                        "data", metrics,
                        "timestamp", System.currentTimeMillis()
                    );
                    
                    synchronized (session) {
                        if (session.isOpen()) {
                            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(metricsMessage)));
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Error streaming metrics", e);
                try {
                    sendError(session, "Error streaming metrics: " + e.getMessage());
                } catch (Exception ex) {
                    log.error("Error sending error message", ex);
                }
            }
        }, 0, interval, TimeUnit.SECONDS);
        
        activeSessions.put(session.getId(), streamingTask);
        
        try {
            Map<String, Object> response = Map.of(
                "type", "subscription_started",
                "subscriptionType", subscriptionType,
                "interval", interval,
                "message", "Metrics subscription started"
            );
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
        } catch (Exception e) {
            log.error("Error sending subscription started message", e);
        }
    }
    
    private void unsubscribeFromMetrics(WebSocketSession session) {
        ScheduledFuture<?> task = activeSessions.remove(session.getId());
        if (task != null) {
            task.cancel(false);
            try {
                Map<String, Object> response = Map.of(
                    "type", "subscription_stopped",
                    "message", "Metrics subscription stopped"
                );
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
                }
            } catch (Exception e) {
                log.error("Error sending subscription stopped message", e);
            }
        }
    }
    
    private void sendMetricsSnapshot(WebSocketSession session, Map<String, Object> request, String username) {
        try {
            List<String> containerIds = (List<String>) request.get("containerIds");
            
            // Validate container ownership
            if (containerIds != null && !containerIds.isEmpty()) {
                for (String containerId : containerIds) {
                    if (!containerService.isOwner(containerId, username)) {
                        sendError(session, "Unauthorized access to container: " + containerId);
                        return;
                    }
                }
            }
            
            // Get current metrics snapshot
            Map<String, Object> metrics = metricsService.getContainerMetrics(
                containerIds != null ? containerIds : List.of()
            );
            
            Map<String, Object> response = Map.of(
                "type", "metrics_snapshot",
                "data", metrics,
                "timestamp", System.currentTimeMillis()
            );
            
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
            
        } catch (Exception e) {
            log.error("Error sending metrics snapshot", e);
            try {
                sendError(session, "Error getting metrics snapshot: " + e.getMessage());
            } catch (Exception ex) {
                log.error("Error sending error message", ex);
            }
        }
    }
    
    private void sendError(WebSocketSession session, String errorMessage) throws Exception {
        Map<String, Object> error = Map.of(
            "type", "error",
            "message", errorMessage,
            "timestamp", System.currentTimeMillis()
        );
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(error)));
    }
    */
}