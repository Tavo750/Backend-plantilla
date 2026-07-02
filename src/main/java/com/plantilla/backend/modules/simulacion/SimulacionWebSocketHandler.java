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

    /** Tamaño de ventana por ciclo ALNS en segundos reales de reproducción (= K×5 min sim con K=120 → 10 hs sim) */
    private static final int CICLO_REAL_SEG    = 300;
    /** Ventanas que el productor mantiene planificadas POR DELANTE del reloj de pantalla (prefetch acotado) */
    private static final int PREFETCH_VENTANAS = 2;
    /** Frecuencia con la que el productor revisa si el búfer necesita otra ventana */
    private static final int CHECK_BUFER_SEG   = 10;
    private static final int K_DEFAULT         = 120;
    private static final int SC_DEFAULT        = 5000;

    private final Map<String, SimulacionSesionEstado> sesiones = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(4, r -> {
                Thread t = new Thread(r, "sim-worker");
                t.setDaemon(true);
                return t;
            });

    private final SimulacionPureService simulacionService;
    private final ObjectMapper          objectMapper;
    private final com.plantilla.backend.modules.simulacion.service.ColapsoEstimadorService colapsoEstimador;

    /** Umbral de maletas sin asignar (%) para declarar COLAPSO_DETECTADO */
    private static final double UMBRAL_COLAPSO_PCT = 30.0;

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
            } else if ("START_COLAPSO".equals(type)) {
                manejarStartColapso(session, msg);
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

    // ──────────────────────────────────────────────────────────
    // Simulación de colapso
    // ──────────────────────────────────────────────────────────

    /**
     * Busca la fecha estimada de colapso (cacheada: solo se calcula la primera vez)
     * y lanza una simulación de confirmación desde 1 día antes de esa fecha.
     */
    private void manejarStartColapso(WebSocketSession session, Map<String, Object> msg) {
        detenerSesion(session.getId());

        int K = msg.containsKey("K")
                ? ((Number) msg.get("K")).intValue() : K_DEFAULT;
        int maxMaletasSC = msg.containsKey("maxMaletasSC")
                ? ((Number) msg.get("maxMaletasSC")).intValue() : SC_DEFAULT;

        scheduler.submit(() -> {
            try {
                boolean cacheado = colapsoEstimador.hayCache();
                if (!cacheado) {
                    enviar(session, Map.of("type", "BUSCANDO_COLAPSO",
                            "mensaje", "Analizando demanda diaria vs capacidad de la flota..."));
                }

                LocalDateTime fechaColapso = colapsoEstimador.obtenerFechaColapso();
                if (fechaColapso == null) {
                    enviar(session, Map.of("type", "ERROR",
                            "mensaje", "No se encontró fecha de colapso: la demanda diaria nunca supera la capacidad de la flota."));
                    return;
                }

                LocalDateTime inicioSim = fechaColapso.minusDays(1);

                SimulacionSesionEstado estado = new SimulacionSesionEstado(
                        session.getId(), session, inicioSim, K, maxMaletasSC);
                estado.setModoColapso(true);
                sesiones.put(session.getId(), estado);

                enviar(session, Map.of(
                        "type",                   "INICIO_COLAPSO",
                        "fechaColapsoEstimadaMs", fechaColapso.toInstant(ZoneOffset.UTC).toEpochMilli(),
                        "fechaInicioSimMs",       inicioSim.toInstant(ZoneOffset.UTC).toEpochMilli(),
                        "maxCiclos",              SimulacionSesionEstado.MAX_CICLOS,
                        "cacheado",               cacheado));

                iniciarSimulacion(estado);

            } catch (Exception e) {
                log.error("Error iniciando simulacion de colapso: {}", e.getMessage(), e);
                enviar(session, Map.of("type", "ERROR", "mensaje",
                        "Error buscando fecha de colapso: " + e.getMessage()));
                sesiones.remove(session.getId());
            }
        });
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

            // 2. Productor con prefetch acotado: mantiene siempre PREFETCH_VENTANAS
            //    ventanas planificadas por delante del reloj de pantalla del cliente.
            //    El reloj de pantalla es determinista (avanza K× el tiempo real desde INIT),
            //    así que se calcula aquí sin necesidad de feedback del cliente.
            //    A diferencia del schedule fijo anterior, el tiempo de cómputo del ALNS
            //    no acumula deriva: si un ciclo tarda, el productor se pone al día solo.
            estado.setInicioRealMs(System.currentTimeMillis());
            ScheduledFuture<?> tarea = scheduler.scheduleWithFixedDelay(
                    () -> productorTick(estado),
                    0,
                    CHECK_BUFER_SEG,
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

    /**
     * Tick del productor: calcula la posición actual del reloj de pantalla y ejecuta
     * tantos ciclos ALNS como haga falta para que el búfer de datos planificados
     * quede PREFETCH_VENTANAS ventanas por delante. Se auto-detiene al llegar a
     * MAX_CICLOS (FIN lo emite ejecutarCiclo) o si la sesión fue detenida.
     */
    private void productorTick(SimulacionSesionEstado estado) {
        if (!estado.estaActiva()) {
            detenerSesion(estado.getSessionId());
            return;
        }
        synchronized (estado) {
            long ventanaSimMin   = (long) estado.getK() * CICLO_REAL_SEG / 60;
            long transcurridoSeg = (System.currentTimeMillis() - estado.getInicioRealMs()) / 1000;
            LocalDateTime relojPantalla    = estado.getFechaInicio().plusSeconds(transcurridoSeg * estado.getK());
            LocalDateTime limiteProduccion = relojPantalla.plusMinutes(ventanaSimMin * PREFETCH_VENTANAS);

            while (estado.estaActiva()
                    && sesiones.containsKey(estado.getSessionId())
                    && estado.getPunteroSim().isBefore(limiteProduccion)) {
                ejecutarCiclo(estado);
            }
        }
    }

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

            // Presupuesto ALNS: la 1ra ventana corta (el usuario espera el arranque);
            // las siguientes usan la holgura que da el prefetch para optimizar a fondo
            long presupuestoMs = (ciclo == 1) ? 10_000 : 45_000;

            Map<String, Object> resultado = simulacionService.procesarVentanaSC(
                    desde, hasta, estado.getMaxMaletasSC(), estado.getArrastreIds(), presupuestoMs);

            // Arrastre: los no asignados de esta ventana se reintentan en la siguiente
            @SuppressWarnings("unchecked")
            List<Integer> pendientes = (List<Integer>) resultado.getOrDefault("pendientesIds", List.of());
            estado.setArrastreIds(new ArrayList<>(pendientes));

            Map<String, Object> update = new LinkedHashMap<>();
            update.put("type",               "UPDATE");
            update.put("tiempoSimulacionMs",
                    hasta.toInstant(ZoneOffset.UTC).toEpochMilli());
            update.put("ciclo",        ciclo);
            update.put("nuevosVuelos", resultado.get("nuevosVuelos"));
            update.put("estadisticas", Map.of(
                    "asignados",        resultado.get("asignados"),
                    "noAsignados",      resultado.get("noAsignados"),
                    "enArrastre",       pendientes.size(),
                    "ciclosCompletados", ciclo,
                    "ciclosTotales",    SimulacionSesionEstado.MAX_CICLOS
            ));
            enviar(estado.getWsSession(), update);

            // Modo colapso: declarar COLAPSO_DETECTADO si el % sin asignar supera el umbral
            if (estado.isModoColapso()) {
                long asignados   = ((Number) resultado.get("asignados")).longValue();
                long noAsignados = ((Number) resultado.get("noAsignados")).longValue();
                long total = asignados + noAsignados;
                double pct = total > 0 ? noAsignados * 100.0 / total : 0.0;
                if (total > 0 && pct >= UMBRAL_COLAPSO_PCT) {
                    long duracionMin = java.time.Duration
                            .between(estado.getFechaInicio(), hasta).toMinutes();
                    enviar(estado.getWsSession(), Map.of(
                            "type",               "COLAPSO_DETECTADO",
                            "tiempoColapsoMs",    hasta.toInstant(ZoneOffset.UTC).toEpochMilli(),
                            "duracionSimMinutos", duracionMin,
                            "pctNoAsignados",     Math.round(pct)));
                    enviar(estado.getWsSession(), Map.of(
                            "type",              "FIN",
                            "ciclosCompletados", ciclo));
                    detenerSesion(estado.getSessionId());
                    return;
                }
            }

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
