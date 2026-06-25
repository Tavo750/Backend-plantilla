package com.plantilla.backend.infrastructure.config;

import com.plantilla.backend.config.MonitoreoWebSocketHandler;
import com.plantilla.backend.modules.simulacion.SimulacionWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final MonitoreoWebSocketHandler    monitoreoHandler;
    private final SimulacionWebSocketHandler   simulacionHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(monitoreoHandler, "/ws/monitoreo")
                .setAllowedOrigins("*");
        registry.addHandler(simulacionHandler, "/ws/simulacion")
                .setAllowedOrigins("*");
    }
}
