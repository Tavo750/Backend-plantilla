package com.plantilla.backend.modules.simulacion;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.*;

/**
 * Handler WebSocket para el módulo de simulación en tiempo real.
 *
 * Protocolo cliente → servidor:
 *   {"type":"START","fechaInicio":"2026-06-19","horaInicio":"09:00","K":120,"maxMaletasSC":1500}
 *   {"type":"STOP"}
 *
 * Protocolo servidor → cliente:
 *   INIT    — snapshot inicial (aviones en vuelo, aeropuertos)
 *   UPDATE  — resultado de cada ciclo ALNS (cada 5 min reales)
 *   FIN     — simulación terminada (12 ciclos = 60 min reales = 5 días simulados)
 *   ERROR   — error irrecuperable
 *   STOPPED — confirmación de STOP
 */
@Component
@RequiredArgsConstructor
public class SimulacionWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(SimulacionWebSocketHandler.class);

    /** 5 minutos reales entre ciclos ALNS (= K×5 min simulados con K=120 → 10 hs sim) */
    private static final int CICLO_REAL_SEG    = 300;
    /** Delay inicial antes del primer ciclo (da tiempo al cliente de procesar INIT) */
    private static final int INICIO_DELAY_SEG  = 10;
    private static final int K_DEFAULT         = 120;
    private static final int SC_DEFAULT        = 1500;

    private final Map<String, SimulacionSesionEstado> sesiones = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(4, r -> {
                Thread t = new Thread(r, "sim-worker");
                t.setDaemon(true);
                return t;
            });

    private final SimulacionPureService simulacionService;
    private final ObjectMapper          objectMapper;

    // ──────────────────────────────────────────────────────────
    // Ciclo de vida WebSocket
    // ──────────────────────────────────────────────────────────

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("SimulacionWS conectado: {}", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        detenerSesion(session.getId());
        log.info("SimulacionWS desconectado: {} ({})", session.getId(), status.getCode());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> msg = objectMapper.readValue(message.getPayload(), Map.class);
            String type = (String) msg.get("type");

            if ("START".equals(type)) {
                manejarStart(session, msg);
            } else if ("STOP".equals(type)) {
                detenerSesion(session.getId());
                enviar(session, Map.of("type", "STOPPED"));
            }
        } catch (Exception e) {
            log.error("Error procesando mensaje WS simulacion: {}", e.getMessage());
        }
    }

    // ──────────────────────────────────────────────────────────
    // Inicio de simulación
    // ──────────────────────────────────────────────────────────

    private void manejarStart(WebSocketSession session, Map<String, Object> msg) {
        detenerSesion(session.getId());

        String fechaInicioStr = (String) msg.getOrDefault("fechaInicio", "2026-01-02");
        String horaInicioStr  = (String) msg.getOrDefault("horaInicio",  "00:00");
        int K = msg.containsKey("K")
                ? ((Number) msg.get("K")).intValue() : K_DEFAULT;
        int maxMaletasSC = msg.containsKey("maxMaletasSC")
                ? ((Number) msg.get("maxMaletasSC")).intValue() : SC_DEFAULT;

        LocalDateTime fechaInicio = LocalDateTime.parse(
                fechaInicioStr + "T" + horaInicioStr + ":00");

        SimulacionSesionEstado estado = new SimulacionSesionEstado(
                session.getId(), session, fechaInicio, K, maxMaletasSC);
        sesiones.put(session.getId(), estado);

        // Ejecutar async para no bloquear el hilo del WS
        scheduler.submit(() -> iniciarSimulacion(estado));
    }

    private void iniciarSimulacion(SimulacionSesionEstado estado) {
        try {
            // 1. Enviar snapshot inicial (aviones en vuelo, aeropuertos vacíos)
            Map<String, Object> snapshot =
                    simulacionService.construirSnapshotInicial(estado.getFechaInicio());

            Map<String, Object> initMsg = new LinkedHashMap<>();
            initMsg.put("type",               "INIT");
            initMsg.put("tiempoSimulacionMs",
                    estado.getFechaInicio().toInstant(ZoneOffset.UTC).toEpochMilli());
            initMsg.put("K",             estado.getK());
            initMsg.put("vuelosEnAire",  snapshot.get("vuelosEnAire"));
            initMsg.put("aeropuertos",   snapshot.get("aeropuertos"));
            enviar(estado.getWsSession(), initMsg);

            // 2. Programar ciclos ALNS cada CICLO_REAL_SEG segundos
            ScheduledFuture<?> tarea = scheduler.scheduleWithFixedDelay(
                    () -> ejecutarCiclo(estado),
                    INICIO_DELAY_SEG,
                    CICLO_REAL_SEG,
                    TimeUnit.SECONDS);
            estado.setTareaScheduled(tarea);

        } catch (Exception e) {
            log.error("Error iniciando simulacion WS: {}", e.getMessage(), e);
            enviar(estado.getWsSession(),
                    Map.of("type", "ERROR", "mensaje", e.getMessage()));
            sesiones.remove(estado.getSessionId());
        }
    }

    // ──────────────────────────────────────────────────────────
    // Ciclos ALNS
    // ──────────────────────────────────────────────────────────

    private void ejecutarCiclo(SimulacionSesionEstado estado) {
        if (!estado.estaActiva()) {
            detenerSesion(estado.getSessionId());
            return;
        }
        try {
            int ciclo = estado.incrementarCiclo();

            // Cada ciclo avanza K × CICLO_REAL_SEG segundos en simulación
            // Con K=120 y CICLO_REAL_SEG=300: 120 × 300 / 60 = 600 min sim = 10 hs sim
            long cicloSimMinutos = (long) estado.getK() * CICLO_REAL_SEG / 60;

            LocalDateTime desde = estado.getPunteroSim();
            LocalDateTime hasta = desde.plusMinutes(cicloSimMinutos);
            estado.setPunteroSim(hasta);

            log.info("Sim ciclo {}/{} | sesion={} | [{} → {}]",
                    ciclo, SimulacionSesionEstado.MAX_CICLOS,
                    estado.getSessionId(), desde, hasta);

            Map<String, Object> resultado =
                    simulacionService.procesarVentanaSC(desde, hasta, estado.getMaxMaletasSC());

            Map<String, Object> update = new LinkedHashMap<>();
            update.put("type",               "UPDATE");
            update.put("tiempoSimulacionMs",
                    hasta.toInstant(ZoneOffset.UTC).toEpochMilli());
            update.put("ciclo",        ciclo);
            update.put("nuevosVuelos", resultado.get("nuevosVuelos"));
            update.put("estadisticas", Map.of(
                    "asignados",        resultado.get("asignados"),
                    "noAsignados",      resultado.get("noAsignados"),
                    "ciclosCompletados", ciclo,
                    "ciclosTotales",    SimulacionSesionEstado.MAX_CICLOS
            ));
            enviar(estado.getWsSession(), update);

            if (ciclo >= SimulacionSesionEstado.MAX_CICLOS) {
                enviar(estado.getWsSession(), Map.of(
                        "type",              "FIN",
                        "ciclosCompletados", SimulacionSesionEstado.MAX_CICLOS));
                detenerSesion(estado.getSessionId());
            }

        } catch (Exception e) {
            log.error("Error en ciclo simulacion: {}", e.getMessage(), e);
            enviar(estado.getWsSession(),
                    Map.of("type", "ERROR", "mensaje", e.getMessage()));
            detenerSesion(estado.getSessionId());
        }
    }

    // ──────────────────────────────────────────────────────────
    // Utilidades
    // ──────────────────────────────────────────────────────────

    private void detenerSesion(String sessionId) {
        SimulacionSesionEstado estado = sesiones.remove(sessionId);
        if (estado != null && estado.getTareaScheduled() != null) {
            estado.getTareaScheduled().cancel(false);
        }
    }

    private void enviar(WebSocketSession session, Object payload) {
        if (session == null || !session.isOpen()) return;
        try {
            synchronized (session) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
            }
        } catch (Exception e) {
            log.warn("Error enviando mensaje WS simulacion [{}]: {}",
                    session.getId(), e.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }
}
