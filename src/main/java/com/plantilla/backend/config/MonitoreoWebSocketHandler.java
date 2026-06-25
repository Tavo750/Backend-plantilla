package com.plantilla.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantilla.backend.modules.envio.service.MonitoreoMapaService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
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

    // Lazy: evita ciclo de dependencia circular (MonitoreoMapaService → ws → MonitoreoMapaService)
    @Autowired
    private ApplicationContext appContext;

    private MonitoreoMapaService getMonitoreoService() {
        return appContext.getBean(MonitoreoMapaService.class);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        log.debug("WS conectado: {} | total sesiones: {}", session.getId(), sessions.size());

        // Si el monitoreo ya está corriendo, enviar el snapshot actual al cliente recién conectado.
        // Esto evita la race condition donde el PLAN se emitió antes de que el WS conectara.
        try {
            MonitoreoMapaService svc = getMonitoreoService();
            Map<String, Object> snapshot = svc.obtenerSnapshot();
            Boolean activo = (Boolean) snapshot.get("activo");
            if (Boolean.TRUE.equals(activo)) {
                snapshot.put("tipo", "PLAN"); // reutilizar el mismo handler del front
                String json = objectMapper.writeValueAsString(snapshot);
                session.sendMessage(new TextMessage(json));
                log.debug("Snapshot enviado al cliente recién conectado: {}", session.getId());
            }
        } catch (Exception e) {
            log.warn("No se pudo enviar snapshot al conectar sesión {}: {}", session.getId(), e.getMessage());
        }
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
                // WebSocketSession.sendMessage NO es thread-safe: sincronizar por sesión
                // para evitar TEXT_PARTIAL_WRITING cuando tick (1 s) y broadcastPlan
                // (hilo HTTP) escriben de forma concurrente.
                synchronized (s) {
                    try {
                        if (s.isOpen()) s.sendMessage(msg);
                    } catch (Exception e) {
                        log.warn("Error enviando a sesión {}: {}", s.getId(), e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error serializando broadcast: {}", e.getMessage());
        }
    }

    public int getConexionesActivas() { return sessions.size(); }
}
