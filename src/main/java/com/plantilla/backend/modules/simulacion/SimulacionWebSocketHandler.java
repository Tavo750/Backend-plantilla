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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.*;

/**
 * Handler WebSocket para el módulo de simulación en tiempo real.
 *
 * Protocolo cliente → servidor:
 *   {"type":"START","fechaInicio":"2026-06-19","horaInicio":"09:00","K":120,"maxMaletasSC":1500}
 *   {"type":"LISTAR"}                 — pide la lista de simulaciones compartidas activas
 *   {"type":"JOIN","simId":"ab12cd34"} — se une a una simulación en curso
 *   {"type":"STOP"}
 *
 * Protocolo servidor → cliente:
 *   INIT      — snapshot inicial (aviones en vuelo, aeropuertos)
 *   UPDATE    — resultado de cada ciclo ALNS (cada 5 min reales)
 *   LISTA_SIMS— lista de simulaciones compartidas activas
 *   SYNC      — al unirse: instante real transcurrido para alinear el reloj con el líder
 *   FIN       — simulación terminada (12 ciclos = 60 min reales = 5 días simulados)
 *   ERROR     — error irrecuperable
 *   STOPPED   — confirmación de STOP
 *
 * Simulación COMPARTIDA (multi-dispositivo):
 *   Un START crea una simulación pública con un simId. Cada mensaje emitido se
 *   guarda en un búfer y se difunde a todas las sesiones suscritas. Otro dispositivo
 *   puede LISTAR y hacer JOIN: recibe el búfer completo (reconstruye el estado) y un
 *   SYNC que lo alinea al mismo instante que ve el líder. La interacción (pan/zoom/
 *   filtros/reloj local) es independiente en cada dispositivo.
 */
@Component
@RequiredArgsConstructor
public class SimulacionWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(SimulacionWebSocketHandler.class);

    /** Tamaño de ventana por ciclo ALNS en segundos reales de reproducción (= K×5 min sim con K=120 → 10 hs sim) */
    private static final int CICLO_REAL_SEG    = 300;
    /** Sa: separación entre arranques de ciclos del planificador (3 min reales). */
    private static final int SA_SEG            = 180;
    /** Ráfaga inicial: ventanas planificadas de corrido al arrancar (colchón) */
    private static final int RAFAGA_INICIAL    = 2;
    /** Frecuencia con la que el productor revisa si toca arrancar otro ciclo */
    private static final int CHECK_BUFER_SEG   = 10;
    private static final int K_DEFAULT         = 120;
    private static final int SC_DEFAULT        = 5000;
    /** Tras terminar, la simulación compartida se retiene este tiempo para joiners tardíos */
    private static final long RETENCION_FIN_MS = 15 * 60 * 1000L;

    /** Sesiones NO compartidas ligadas a su propietario (p.ej. simulación de colapso) */
    private final Map<String, SimulacionSesionEstado> sesiones = new ConcurrentHashMap<>();
    /** Simulaciones compartidas activas, por simId */
    private final Map<String, SimulacionSesionEstado> simsCompartidas = new ConcurrentHashMap<>();
    /** sessionId → simId de la simulación que esa sesión está viendo (para desuscribir al cerrar) */
    private final Map<String, String> sesionASim = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(4, r -> {
                Thread t = new Thread(r, "sim-worker");
                t.setDaemon(true);
                return t;
            });

    private final SimulacionPureService simulacionService;
    private final ObjectMapper          objectMapper;
    private final com.plantilla.backend.modules.simulacion.service.ColapsoEstimadorService colapsoEstimador;

    // ──────────────────────────────────────────────────────────
    // Ciclo de vida WebSocket
    // ──────────────────────────────────────────────────────────

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("SimulacionWS conectado: {}", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        // Desuscribir de la simulación compartida que estuviera viendo (no la detiene:
        // sigue viva para los demás dispositivos y para quien se una después).
        String simId = sesionASim.remove(session.getId());
        if (simId != null) {
            SimulacionSesionEstado sim = simsCompartidas.get(simId);
            if (sim != null) sim.getSuscriptores().remove(session);
        }
        // Simulaciones NO compartidas (colapso) mueren con su sesión.
        SimulacionSesionEstado propia = sesiones.get(session.getId());
        if (propia != null && propia.getSimId() == null) {
            detenerSesion(session.getId());
        } else {
            sesiones.remove(session.getId());
        }
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
            } else if ("LISTAR".equals(type)) {
                enviar(session, Map.of("type", "LISTA_SIMS", "sims", snapshotActivas()));
            } else if ("JOIN".equals(type)) {
                manejarJoin(session, (String) msg.get("simId"));
            } else if ("CANCEL_FLIGHT".equals(type)) {
                manejarCancelFlight(session, msg);
            } else if ("STOP".equals(type)) {
                String sid = sesionASim.remove(session.getId());
                if (sid != null) {
                    SimulacionSesionEstado sim = simsCompartidas.get(sid);
                    if (sim != null) sim.getSuscriptores().remove(session);
                }
                SimulacionSesionEstado propia = sesiones.get(session.getId());
                if (propia != null && propia.getSimId() == null) detenerSesion(session.getId());
                enviar(session, Map.of("type", "STOPPED"));
            }
        } catch (Exception e) {
            log.error("Error procesando mensaje WS simulacion: {}", e.getMessage());
        }
    }

    // ──────────────────────────────────────────────────────────
    // Inicio de simulación (compartida)
    // ──────────────────────────────────────────────────────────

    private void manejarStart(WebSocketSession session, Map<String, Object> msg) {
        // Si esta sesión ya estaba viendo/ejecutando algo, la despega.
        desuscribir(session);

        String fechaInicioStr = (String) msg.getOrDefault("fechaInicio", "2026-01-02");
        String horaInicioStr  = (String) msg.getOrDefault("horaInicio",  "00:00");
        int K = msg.containsKey("K")
                ? ((Number) msg.get("K")).intValue() : K_DEFAULT;
        int maxMaletasSC = msg.containsKey("maxMaletasSC")
                ? ((Number) msg.get("maxMaletasSC")).intValue() : SC_DEFAULT;

        LocalDateTime fechaInicio = LocalDateTime.parse(
                fechaInicioStr + "T" + horaInicioStr + ":00");

        String simId = UUID.randomUUID().toString().substring(0, 8);

        SimulacionSesionEstado estado = new SimulacionSesionEstado(
                session.getId(), session, fechaInicio, K, maxMaletasSC);
        estado.setSimId(simId);
        estado.setHoraInicio(horaInicioStr);
        estado.getSuscriptores().add(session);

        simsCompartidas.put(simId, estado);
        sesionASim.put(session.getId(), simId);
        sesiones.put(session.getId(), estado);

        // Ejecutar async para no bloquear el hilo del WS
        scheduler.submit(() -> iniciarSimulacion(estado));
    }

    // ──────────────────────────────────────────────────────────
    // Unirse a una simulación en curso
    // ──────────────────────────────────────────────────────────

    private void manejarJoin(WebSocketSession session, String simId) {
        if (simId == null) {
            enviar(session, Map.of("type", "ERROR", "mensaje", "Falta el identificador de la simulación."));
            return;
        }
        SimulacionSesionEstado sim = simsCompartidas.get(simId);
        if (sim == null) {
            enviar(session, Map.of("type", "ERROR", "mensaje", "La simulación ya no está disponible."));
            return;
        }
        desuscribir(session);
        sim.getSuscriptores().add(session);
        sesionASim.put(session.getId(), simId);

        // 1. Reproducir el búfer (INIT + UPDATEs [+ FIN]) para reconstruir el estado
        List<String> copia;
        synchronized (sim.getMensajesBuffer()) {
            copia = new ArrayList<>(sim.getMensajesBuffer());
        }
        for (String json : copia) enviarRaw(session, json);

        // 2. Sincronizar el reloj al mismo instante que ve el líder
        long transcurridoRealMs = System.currentTimeMillis() - sim.getInicioRealMs();
        Map<String, Object> sync = new LinkedHashMap<>();
        sync.put("type", "SYNC");
        sync.put("transcurridoRealMs", transcurridoRealMs);
        sync.put("finalizada", sim.isFinalizada());
        sync.put("colapsada", sim.isColapsada());
        sync.put("estadoColapso", sim.getEstadoColapso());
        enviar(session, sync);

        log.info("Sesion {} se unió a simulacion {} (buffer={} msgs)",
                session.getId(), simId, copia.size());
    }

    /** Lista de simulaciones compartidas para el endpoint REST y el mensaje LISTA_SIMS. */
    public List<Map<String, Object>> listarActivas() {
        return snapshotActivas();
    }

    private List<Map<String, Object>> snapshotActivas() {
        List<Map<String, Object>> out = new ArrayList<>();
        // Defensivo: un fallo puntual en una simulación no debe tumbar toda la lista
        for (SimulacionSesionEstado e : simsCompartidas.values()) {
            try {
                if (e == null) continue;
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("simId",        e.getSimId());
                m.put("fechaInicio",  e.getFechaInicio() != null ? e.getFechaInicio().toLocalDate().toString() : "");
                m.put("horaInicio",   e.getHoraInicio() != null ? e.getHoraInicio() : "00:00");
                m.put("cicloActual",  e.getCiclosEjecutados());
                m.put("maxCiclos",    SimulacionSesionEstado.MAX_CICLOS);
                m.put("finalizada",   e.isFinalizada());
                m.put("espectadores", e.getSuscriptores() != null ? e.getSuscriptores().size() : 0);
                out.add(m);
            } catch (Exception ex) {
                log.warn("No se pudo serializar una simulación activa: {}", ex.getMessage());
            }
        }
        return out;
    }

    private void desuscribir(WebSocketSession session) {
        String simIdPrevio = sesionASim.remove(session.getId());
        if (simIdPrevio != null) {
            SimulacionSesionEstado sim = simsCompartidas.get(simIdPrevio);
            if (sim != null) sim.getSuscriptores().remove(session);
        }
        SimulacionSesionEstado propia = sesiones.get(session.getId());
        if (propia != null && propia.getSimId() == null) {
            detenerSesion(session.getId());
        }
    }

    // ──────────────────────────────────────────────────────────
    // Simulación de colapso (NO compartida)
    // ──────────────────────────────────────────────────────────

    private void manejarStartColapso(WebSocketSession session, Map<String, Object> msg) {
        desuscribir(session);
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
                            "mensaje", "Buscando el primer día con maletas sin ruta o fuera de SLA..."));
                }

                LocalDateTime fechaColapso = colapsoEstimador.obtenerFechaColapso(
                        msg2 -> enviar(session, Map.of("type", "BUSCANDO_COLAPSO", "mensaje", msg2)));
                if (fechaColapso == null) {
                    enviar(session, Map.of("type", "ERROR",
                            "mensaje", "No se encontró colapso: el planificador cubre toda la demanda sin maletas sin ruta ni fuera de SLA."));
                    return;
                }

                LocalDateTime inicioSim = fechaColapso.minusDays(1);

                SimulacionSesionEstado estado = new SimulacionSesionEstado(
                        session.getId(), session, inicioSim, K, maxMaletasSC);
                estado.setModoColapso(true);
                estado.getSuscriptores().add(session);
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
            // 1. Snapshot inicial: aviones YA en vuelo a la fecha/hora elegida (vacíos) + aeropuertos
            Map<String, Object> snapshot =
                    simulacionService.construirSnapshotInicial(estado.getFechaInicio());

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> vuelosEnAire =
                    (List<Map<String, Object>>) snapshot.getOrDefault("vuelosEnAire", List.of());
            estado.registrarOcurrenciasInit(vuelosEnAire);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> aeropuertos =
                    (List<Map<String, Object>>) snapshot.getOrDefault("aeropuertos", List.of());
            estado.registrarCapacidades(aeropuertos);

            Map<String, Object> initMsg = new LinkedHashMap<>();
            initMsg.put("type",               "INIT");
            initMsg.put("tiempoSimulacionMs",
                    estado.getFechaInicio().toInstant(ZoneOffset.UTC).toEpochMilli());
            initMsg.put("K",             estado.getK());
            initMsg.put("vuelosEnAire",  snapshot.get("vuelosEnAire"));
            initMsg.put("aeropuertos",   snapshot.get("aeropuertos"));
            initMsg.put("ocupacionesAeropuertos",
                    estado.calcularOcupacionesAeropuertos(estado.getFechaInicio()));

            // Ancla del reloj ANTES de emitir INIT: así los joiners calculan bien el desfase
            estado.setInicioRealMs(System.currentTimeMillis());
            emitir(estado, initMsg);

            // 2. Productor con cadencia Sa = 3 min (ráfaga inicial + 1 ciclo cada SA_SEG)
            ScheduledFuture<?> tarea = scheduler.scheduleWithFixedDelay(
                    () -> productorTick(estado),
                    0,
                    CHECK_BUFER_SEG,
                    TimeUnit.SECONDS);
            estado.setTareaScheduled(tarea);

        } catch (Exception e) {
            log.error("Error iniciando simulacion WS: {}", e.getMessage(), e);
            emitir(estado, Map.of("type", "ERROR", "mensaje", e.getMessage()));
            finalizarSim(estado, true);
        }
    }

    // ──────────────────────────────────────────────────────────
    // Ciclos ALNS
    // ──────────────────────────────────────────────────────────

    private void productorTick(SimulacionSesionEstado estado) {
        if (estado.isColapsada()) return;
        if (!estado.estaActiva()) {
            finalizarSim(estado, false);
            return;
        }
        synchronized (estado) {
            long transcurridoSeg = (System.currentTimeMillis() - estado.getInicioRealMs()) / 1000;
            int objetivo = (int) Math.min(SimulacionSesionEstado.MAX_CICLOS,
                    RAFAGA_INICIAL + transcurridoSeg / SA_SEG);

            while (estado.estaActiva()
                    && estaRegistrada(estado)
                    && estado.getCiclosEjecutados() < objetivo) {
                ejecutarCiclo(estado);
            }
        }
    }

    private boolean estaRegistrada(SimulacionSesionEstado estado) {
        return estado.getSimId() != null
                ? simsCompartidas.containsKey(estado.getSimId())
                : sesiones.containsKey(estado.getSessionId());
    }

    void ejecutarCiclo(SimulacionSesionEstado estado) {
        if (estado.isColapsada()) return;
        if (!estado.estaActiva()) {
            finalizarSim(estado, false);
            return;
        }
        try {
            int ciclo = estado.incrementarCiclo();

            long cicloSimMinutos = (long) estado.getK() * CICLO_REAL_SEG / 60;

            LocalDateTime desde = estado.getPunteroSim();
            LocalDateTime hasta = desde.plusMinutes(cicloSimMinutos);
            estado.setPunteroSim(hasta);

            log.info("Sim ciclo {}/{} | sim={} | [{} → {}]",
                    ciclo, SimulacionSesionEstado.MAX_CICLOS,
                    estado.getSimId() != null ? estado.getSimId() : estado.getSessionId(), desde, hasta);

            long presupuestoMs = (ciclo == 1) ? 10_000 : 45_000;

            Map<String, Object> resultado = simulacionService.procesarVentanaSC(
                    desde, hasta, estado.getMaxMaletasSC(), estado.getArrastreIds(), presupuestoMs,
                    estado.getOcurrenciasCanceladas());

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> enviosDemanda =
                    (List<Map<String, Object>>) resultado.getOrDefault("enviosDemanda", List.of());
            estado.registrarDemanda(enviosDemanda);

            @SuppressWarnings("unchecked")
            List<Integer> pendientes = (List<Integer>) resultado.getOrDefault("pendientesIds", List.of());
            estado.setArrastreIds(new ArrayList<>(pendientes));

            @SuppressWarnings("unchecked")
            List<String> ocurrenciasDisponibles =
                    (List<String>) resultado.getOrDefault("ocurrenciasDisponibles", List.of());
            estado.registrarOcurrencias(ocurrenciasDisponibles);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> nuevosVuelos =
                    (List<Map<String, Object>>) resultado.getOrDefault("nuevosVuelos", List.of());
            estado.registrarResultados(nuevosVuelos);

            Map<String, OcupacionAeropuerto> ocupaciones =
                    estado.calcularOcupacionesAeropuertos(hasta);

            Map<String, Object> update = new LinkedHashMap<>();
            update.put("type",               "UPDATE");
            update.put("tiempoSimulacionMs",
                    hasta.toInstant(ZoneOffset.UTC).toEpochMilli());
            update.put("ciclo",        ciclo);
            update.put("nuevosVuelos", resultado.get("nuevosVuelos"));
            update.put("ocupacionesAeropuertos", ocupaciones);
            update.put("estadisticas", Map.of(
                    "asignados",        resultado.get("asignados"),
                    "noAsignados",      resultado.get("noAsignados"),
                    "enArrastre",       pendientes.size(),
                    "ciclosCompletados", ciclo,
                    "ciclosTotales",    SimulacionSesionEstado.MAX_CICLOS
            ));
            emitir(estado, update);

            EstadoColapso colapso = estado.detectarColapso(hasta, ocupaciones);
            if (colapso != null) {
                detenerSimulacionPorColapso(estado, colapso, ciclo);
                return;
            }

            if (ciclo >= SimulacionSesionEstado.MAX_CICLOS) {
                emitir(estado, Map.of("type", "FIN", "ciclosCompletados", SimulacionSesionEstado.MAX_CICLOS));
                finalizarSim(estado, false);
            }

        } catch (Exception e) {
            log.error("Error en ciclo simulacion: {}", e.getMessage(), e);
            emitir(estado, Map.of("type", "ERROR", "mensaje", e.getMessage()));
            finalizarSim(estado, true);
        }
    }

    /**
     * Marca y detiene una simulacion una sola vez. El evento queda en el buffer
     * para que JOIN/SYNC pueda reconstruir el mismo estado colapsado.
     */
    private void detenerSimulacionPorColapso(
            SimulacionSesionEstado estado, EstadoColapso colapso, int ciclo) {
        if (!estado.marcarColapsada(colapso)) return;

        Map<String, Object> evento = new LinkedHashMap<>();
        evento.put("type", "COLAPSO_DETECTADO");
        evento.put("simId", estado.getSimId());
        evento.put("fechaSimulada", colapso.fechaColapso().toString());
        evento.put("tiempoColapsoMs",
                colapso.fechaColapso().toInstant(ZoneOffset.UTC).toEpochMilli());
        evento.put("tiempoSimulacionMs",
                colapso.fechaColapso().toInstant(ZoneOffset.UTC).toEpochMilli());
        evento.put("duracionSimMinutos", java.time.Duration
                .between(estado.getFechaInicio(), colapso.fechaColapso()).toMinutes());
        evento.put("tipoColapso", colapso.tipo());
        evento.put("mensaje", colapso.mensaje());
        evento.put("motivo", colapso.mensaje());
        evento.put("aeropuerto", colapso.codigoAeropuerto());
        evento.put("ocupacionActual", colapso.ocupacionActual());
        evento.put("capacidadMaxima", colapso.capacidadMaxima());
        evento.put("idEnvio", colapso.idEnvio());
        evento.put("fechaLimiteEntrega", colapso.fechaLimiteEntrega() != null
                ? colapso.fechaLimiteEntrega().toString() : null);
        emitir(estado, evento);

        estado.setFinalizada(true);
        if (estado.getTareaScheduled() != null) {
            estado.getTareaScheduled().cancel(false);
        }
        String simId = estado.getSimId();
        if (simId != null) {
            scheduler.schedule(() -> simsCompartidas.remove(simId),
                    RETENCION_FIN_MS, TimeUnit.MILLISECONDS);
        } else {
            sesiones.remove(estado.getSessionId());
        }
        log.warn("Simulacion {} detenida por colapso {} en ciclo {}: {}",
                simId != null ? simId : estado.getSessionId(),
                colapso.tipo(), ciclo, colapso.mensaje());
    }

    // ──────────────────────────────────────────────────────────
    // Utilidades
    // ──────────────────────────────────────────────────────────

    /** Cancela una ocurrencia sólo en el estado temporal de la simulación observada. */
    private void manejarCancelFlight(WebSocketSession session, Map<String, Object> msg) {
        String codigo = msg.get("codigoVuelo") instanceof String s ? s : null;
        String simId = sesionASim.get(session.getId());
        if (simId == null || (msg.get("simId") != null && !simId.equals(msg.get("simId")))) {
            enviarCancelFlightError(session, codigo, "La sesion no esta observando la simulacion indicada.");
            return;
        }
        SimulacionSesionEstado estado = simsCompartidas.get(simId);
        if (estado == null || estado.isFinalizada() || !estado.estaActiva()) {
            enviarCancelFlightError(session, codigo, "La simulacion no existe o ya finalizo.");
            return;
        }
        if (codigo == null || !(msg.get("horaSalidaSeleccionadaMs") instanceof Number salida)
                || !(msg.get("fechaHoraSimuladaMs") instanceof Number cancelacion)) {
            enviarCancelFlightError(session, codigo,
                    "Faltan codigoVuelo, horaSalidaSeleccionadaMs o fechaHoraSimuladaMs.");
            return;
        }
        synchronized (estado) {
            if (estado.isFinalizada() || !estado.estaActiva()) {
                enviarCancelFlightError(session, codigo, "La simulacion ya finalizo.");
                return;
            }
            LocalDateTime finHorizonte = estado.getFechaInicio().plusMinutes(
                    (long) estado.getK() * CICLO_REAL_SEG / 60 * SimulacionSesionEstado.MAX_CICLOS);
            Optional<SimulacionPureService.OcurrenciaCancelacion> ocurrencia =
                    simulacionService.resolverOcurrenciaCancelacion(
                            codigo, cancelacion.longValue(), finHorizonte,
                            estado.getOcurrenciasCanceladas());
            if (ocurrencia.isEmpty()) {
                enviarCancelFlightError(session, codigo,
                        "No existe una siguiente ocurrencia del vuelo dentro del horizonte de la simulacion");
                return;
            }
            String codigoAfectado = ocurrencia.get().codigoVuelo();
            long salidaAfectadaMs = ocurrencia.get().horaSalidaMs();
            String clave = SimulacionSesionEstado.claveOcurrencia(codigoAfectado, salidaAfectadaMs);
            boolean conocida = estado.conoceOcurrencia(clave);
            boolean existePersistida = simulacionService.existeOcurrencia(codigoAfectado, salidaAfectadaMs);
            log.debug("ORIGEN VUELO CANCELADO: {}", estado.origenOcurrencia(clave));
            log.debug("CANCEL DEBUG codigo={} seleccionadaMs={} seleccionadaUtc={} "
                            + "simuladaMs={} simuladaUtc={} afectadaMs={} afectadaUtc={} clave={} "
                            + "conocida={} existePersistida={} ciclos={}",
                    codigoAfectado, salida.longValue(), Instant.ofEpochMilli(salida.longValue()),
                    cancelacion.longValue(), Instant.ofEpochMilli(cancelacion.longValue()),
                    salidaAfectadaMs, Instant.ofEpochMilli(salidaAfectadaMs), clave,
                    conocida, existePersistida, estado.getCiclosEjecutados());
            if (estado.getOcurrenciasCanceladas().contains(clave)) {
                enviarCancelFlightError(session, codigoAfectado, "La ocurrencia ya fue cancelada.");
                return;
            }
            if (!conocida && !existePersistida) {
                enviarCancelFlightError(session, codigoAfectado,
                        "La ocurrencia calculada no existe en los vuelos de esta simulacion.");
                return;
            }
            estado.registrarOcurrencia(clave);
            estado.getOcurrenciasCanceladas().add(clave);
            Set<Integer> afectados = estado.liberarOcurrencia(
                    clave, cancelacion.longValue());
            estado.agregarAlArrastre(afectados);
            LocalDate fechaOperacion = Instant.ofEpochMilli(salidaAfectadaMs).atZone(ZoneOffset.UTC).toLocalDate();
            LocalDate fechaCancelacion = Instant.ofEpochMilli(cancelacion.longValue()).atZone(ZoneOffset.UTC).toLocalDate();
            Map<String, Object> evento = new LinkedHashMap<>();
            evento.put("type", "FLIGHT_CANCELLED");
            evento.put("codigoVuelo", codigoAfectado);
            evento.put("horaSalidaAfectadaMs", salidaAfectadaMs);
            evento.put("fechaOperacion", fechaOperacion.toString());
            evento.put("fechaHoraCancelacionMs", cancelacion.longValue());
            evento.put("enviosAfectados", new ArrayList<>(afectados));
            evento.put("cantidadEnviosAfectados", afectados.size());
            evento.put("aplicaMismoDia", fechaOperacion.equals(fechaCancelacion));
            emitir(estado, evento);

            // ── Replanificación inmediata: ejecutar un ciclo ALNS extra para
            //    reasignar las maletas del vuelo cancelado a vuelos alternativos ──
            if (!afectados.isEmpty()) {
                ejecutarCicloReplanificacion(estado);
            }
        }
    }

    private void enviarCancelFlightError(WebSocketSession session, String codigo, String mensaje) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("type", "CANCEL_FLIGHT_ERROR");
        error.put("codigoVuelo", codigo != null ? codigo : "");
        error.put("mensaje", mensaje);
        enviar(session, error);
    }

    /**
     * Ejecuta un ciclo ALNS extra de replanificación inmediata tras cancelar un vuelo.
     * No incrementa el contador de ciclos de la simulación.
     * Usa la ventana actual del puntero de simulación para buscar vuelos alternativos.
     */
    private void ejecutarCicloReplanificacion(SimulacionSesionEstado estado) {
        try {
            long cicloSimMinutos = (long) estado.getK() * CICLO_REAL_SEG / 60;

            // Usar la ventana del puntero actual (última procesada) para buscar vuelos
            LocalDateTime hasta = estado.getPunteroSim();
            LocalDateTime desde = hasta.minusMinutes(cicloSimMinutos);

            log.info("Replanificación por cancelación | sim={} | [{} → {}] | arrastre={}",
                    estado.getSimId() != null ? estado.getSimId() : estado.getSessionId(),
                    desde, hasta, estado.getArrastreIds().size());

            // Presupuesto reducido: solo replanificamos las maletas afectadas
            long presupuestoMs = 15_000;

            Map<String, Object> resultado = simulacionService.procesarVentanaSC(
                    desde, hasta, estado.getMaxMaletasSC(), estado.getArrastreIds(), presupuestoMs,
                    estado.getOcurrenciasCanceladas());

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> enviosDemanda =
                    (List<Map<String, Object>>) resultado.getOrDefault("enviosDemanda", List.of());
            estado.registrarDemanda(enviosDemanda);

            @SuppressWarnings("unchecked")
            List<Integer> pendientes = (List<Integer>) resultado.getOrDefault("pendientesIds", List.of());
            estado.setArrastreIds(new java.util.ArrayList<>(pendientes));

            @SuppressWarnings("unchecked")
            List<String> ocurrenciasDisponibles =
                    (List<String>) resultado.getOrDefault("ocurrenciasDisponibles", List.of());
            estado.registrarOcurrencias(ocurrenciasDisponibles);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> nuevosVuelos =
                    (List<Map<String, Object>>) resultado.getOrDefault("nuevosVuelos", List.of());
            estado.registrarResultados(nuevosVuelos);

            Map<String, OcupacionAeropuerto> ocupaciones =
                    estado.calcularOcupacionesAeropuertos(hasta);

            // Emitir UPDATE con las nuevas asignaciones (sin incrementar ciclo)
            Map<String, Object> update = new LinkedHashMap<>();
            update.put("type",               "UPDATE");
            update.put("tiempoSimulacionMs",
                    hasta.toInstant(ZoneOffset.UTC).toEpochMilli());
            update.put("ciclo",        estado.getCiclosEjecutados()); // no incrementa
            update.put("nuevosVuelos", resultado.get("nuevosVuelos"));
            update.put("ocupacionesAeropuertos", ocupaciones);
            update.put("estadisticas", Map.of(
                    "asignados",        resultado.get("asignados"),
                    "noAsignados",      resultado.get("noAsignados"),
                    "enArrastre",       pendientes.size(),
                    "ciclosCompletados", estado.getCiclosEjecutados(),
                    "ciclosTotales",    SimulacionSesionEstado.MAX_CICLOS
            ));
            emitir(estado, update);

            EstadoColapso colapso = estado.detectarColapso(hasta, ocupaciones);
            if (colapso != null) {
                detenerSimulacionPorColapso(
                        estado, colapso, estado.getCiclosEjecutados());
                return;
            }

            int asignados = ((Number) resultado.get("asignados")).intValue();
            int noAsignados = ((Number) resultado.get("noAsignados")).intValue();
            log.info("Replanificación completada: {} asignados, {} no asignados, {} en arrastre",
                    asignados, noAsignados, pendientes.size());

        } catch (Exception e) {
            log.error("Error en ciclo de replanificación por cancelación: {}", e.getMessage(), e);
            // No finalizar la simulación por un error de replanificación;
            // las maletas quedan en arrastre para el siguiente ciclo regular
        }
    }

    /** Termina la simulación y cancela su tarea programada. */
    private void finalizarSim(SimulacionSesionEstado estado, boolean inmediato) {
        estado.setFinalizada(true);
        if (estado.getTareaScheduled() != null) estado.getTareaScheduled().cancel(false);
        sesiones.remove(estado.getSessionId());
        String simId = estado.getSimId();
        if (simId != null) {
            if (inmediato) {
                simsCompartidas.remove(simId);
            } else {
                scheduler.schedule(() -> simsCompartidas.remove(simId),
                        RETENCION_FIN_MS, TimeUnit.MILLISECONDS);
            }
        }
    }

    private void detenerSesion(String sessionId) {
        SimulacionSesionEstado estado = sesiones.remove(sessionId);
        if (estado != null && estado.getTareaScheduled() != null) {
            estado.getTareaScheduled().cancel(false);
        }
    }

    /** Serializa el mensaje una vez, lo guarda en el búfer y lo difunde a todos los suscriptores. */
    private void emitir(SimulacionSesionEstado estado, Object payload) {
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.warn("Error serializando mensaje WS simulacion: {}", e.getMessage());
            return;
        }
        estado.getMensajesBuffer().add(json);
        for (WebSocketSession s : estado.getSuscriptores()) {
            enviarRaw(s, json);
        }
    }

    private void enviarRaw(WebSocketSession session, String json) {
        if (session == null || !session.isOpen()) return;
        try {
            synchronized (session) {
                session.sendMessage(new TextMessage(json));
            }
        } catch (Exception e) {
            log.warn("Error enviando mensaje WS simulacion [{}]: {}",
                    session.getId(), e.getMessage());
        }
    }

    private void enviar(WebSocketSession session, Object payload) {
        if (session == null || !session.isOpen()) return;
        try {
            enviarRaw(session, objectMapper.writeValueAsString(payload));
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
