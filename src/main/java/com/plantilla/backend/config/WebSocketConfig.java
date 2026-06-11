package com.plantilla.backend.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Registra el endpoint WebSocket en /ws/monitoreo (sin SockJS para simplicidad).
 * El cliente Angular se conecta con: new WebSocket("ws://host/ws/monitoreo")
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final MonitoreoWebSocketHandler monitoreoHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(monitoreoHandler, "/ws/monitoreo")
                .setAllowedOriginPatterns("*");
    }
}
