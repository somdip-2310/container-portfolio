package dev.somdip.containerplatform.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.somdip.containerplatform.service.ContainerService;
import dev.somdip.containerplatform.service.LogStreamingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.security.Principal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class LogStreamHandler extends TextWebSocketHandler {
    
    private final LogStreamingService logStreamingService;
    private final ContainerService containerService;
    private final ObjectMapper objectMapper;
    
    // Store active sessions and their log streaming tasks
    private final Map<String, ScheduledFuture<?>> activeSessions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("WebSocket connection established: {}", session.getId());
        
        // Send initial connection success message
        Map<String, Object> response = Map.of(
            "type", "connection",
            "status", "connected",
            "message", "Log streaming connection established"
        );
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
    }
    
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            // Parse the incoming message
            Map<String, String> request = objectMapper.readValue(message.getPayload(), Map.class);
            String action = request.get("action");
            String containerId = request.get("containerId");
            
            // Get authenticated user
            Principal principal = session.getPrincipal();
            if (principal == null) {
                sendError(session, "Authentication required");
                return;
            }
            
            // Verify container ownership
            if (!containerService.isOwner(containerId, principal.getName())) {
                sendError(session, "Unauthorized access to container");
                return;
            }
            
            switch (action) {
                case "start":
                    startLogStreaming(session, containerId);
                    break;
                case "stop":
                    stopLogStreaming(session);
                    break;
                case "filter":
                    applyLogFilter(session, containerId, request.get("filter"));
                    break;
                default:
                    sendError(session, "Unknown action: " + action);
            }
            
        } catch (Exception e) {
            log.error("Error handling WebSocket message", e);
            sendError(session, "Error processing request: " + e.getMessage());
        }
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        log.info("WebSocket connection closed: {} - {}", session.getId(), status);
        stopLogStreaming(session);
    }
    
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WebSocket transport error for session: {}", session.getId(), exception);
        stopLogStreaming(session);
    }
    
    private void startLogStreaming(WebSocketSession session, String containerId) {
        // Stop any existing streaming for this session
        stopLogStreaming(session);
        
        // Start new log streaming task
        ScheduledFuture<?> streamingTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                // Fetch logs from CloudWatch or container logs
                String logs = logStreamingService.getLatestLogs(containerId, 100);
                
                if (logs != null && !logs.isEmpty()) {
                    Map<String, Object> logMessage = Map.of(
                        "type", "logs",
                        "containerId", containerId,
                        "content", logs,
                        "timestamp", System.currentTimeMillis()
                    );
                    
                    synchronized (session) {
                        if (session.isOpen()) {
                            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(logMessage)));
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Error streaming logs for container: {}", containerId, e);
                try {
                    sendError(session, "Error streaming logs: " + e.getMessage());
                } catch (Exception ex) {
                    log.error("Error sending error message", ex);
                }
            }
        }, 0, 2, TimeUnit.SECONDS); // Stream logs every 2 seconds
        
        activeSessions.put(session.getId(), streamingTask);
        
        try {
            Map<String, Object> response = Map.of(
                "type", "stream_started",
                "containerId", containerId,
                "message", "Log streaming started"
            );
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
        } catch (Exception e) {
            log.error("Error sending stream started message", e);
        }
    }
    
    private void stopLogStreaming(WebSocketSession session) {
        ScheduledFuture<?> task = activeSessions.remove(session.getId());
        if (task != null) {
            task.cancel(false);
            try {
                Map<String, Object> response = Map.of(
                    "type", "stream_stopped",
                    "message", "Log streaming stopped"
                );
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
                }
            } catch (Exception e) {
                log.error("Error sending stream stopped message", e);
            }
        }
    }
    
    private void applyLogFilter(WebSocketSession session, String containerId, String filter) {
        // Implement log filtering logic
        try {
            Map<String, Object> response = Map.of(
                "type", "filter_applied",
                "filter", filter,
                "message", "Log filter applied"
            );
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
        } catch (Exception e) {
            log.error("Error applying log filter", e);
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
}