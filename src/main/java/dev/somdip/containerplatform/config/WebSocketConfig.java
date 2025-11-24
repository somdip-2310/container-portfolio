package dev.somdip.containerplatform.config;

import dev.somdip.containerplatform.websocket.LogStreamHandler;
import dev.somdip.containerplatform.websocket.MetricsHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;
import org.springframework.context.annotation.Bean;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {
    
    private final LogStreamHandler logStreamHandler;
    private final MetricsHandler metricsHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(logStreamHandler, "/ws/logs")
                .addHandler(metricsHandler, "/ws/metrics")
                .setAllowedOrigins("*")
                .withSockJS(); // Fallback for browsers that don't support WebSocket
    }

    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxBinaryMessageBufferSize(1024000);
        container.setMaxTextMessageBufferSize(1024000);
        container.setMaxSessionIdleTimeout(300000L); // 5 minutes
        return container;
    }
}
