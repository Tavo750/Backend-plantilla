package com.plantilla.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestor de conexiones WebSocket para el Monitoreo Mapa.
 * Mantiene un registro de sesiones activas y permite hacer broadcast
 * del estado del monitoreo a todos los clientes conectados.
 *
 * Usa el ObjectMapper de Spring (con JavaTimeModule registrado) para serializar
 * tipos como LocalDateTime correctamente.
 */
@Component
@RequiredArgsConstructor
public class MonitoreoWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(MonitoreoWebSocketHandler.class);

    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    private final ObjectMapper objectMapper; // inyectado por Spring — incluye JavaTimeModule

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        log.debug("WS conectado: {} | total sesiones: {}", session.getId(), sessions.size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        log.debug("WS desconectado: {} | total sesiones: {}", session.getId(), sessions.size());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // El cliente no envía mensajes; solo recibe — ignorar cualquier entrada
    }

    /**
     * Envía el objeto serializado como JSON a todas las sesiones activas.
     */
    public void broadcast(Object payload) {
        if (sessions.isEmpty()) return;
        try {
            String json = objectMapper.writeValueAsString(payload);
            TextMessage msg = new TextMessage(json);
            sessions.removeIf(s -> !s.isOpen());
            for (WebSocketSession s : sessions) {
                try { s.sendMessage(msg); }
                catch (Exception e) { log.warn("Error enviando a sesión {}: {}", s.getId(), e.getMessage()); }
            }
        } catch (Exception e) {
            log.error("Error serializando broadcast: {}", e.getMessage());
        }
    }

    public int getConexionesActivas() { return sessions.size(); }
}
