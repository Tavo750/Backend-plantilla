package com.plantilla.backend.modules.operaciondiaria;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.*;

/**
 * Handler WebSocket de la OPERACIÓN DIARIA (endpoint {@code /ws/operacion-diaria}).
 *
 * A diferencia de la simulación:
 *  - existe UNA sola operación global y continua (todos los dispositivos ven lo mismo),
 *  - el reloj es tiempo real (no acelerado) y no hay corte ni fin,
 *  - todos los vuelos del plan se muestran volando aunque vayan vacíos,
 *  - el planificador corre cada {@code SA_SEG} segundos leyendo la tabla envio_diario:
 *    un pedido recién registrado se planifica, a lo sumo, en la próxima ventana.
 *
 * No comparte estado con el módulo de simulación.
 */
@Component
@RequiredArgsConstructor
public class OperacionDiariaWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(OperacionDiariaWebSocketHandler.class);

    /** Intervalo de planificación: 2 minutos (un pedido se planifica en ≤2 min). */
    private static final int SA_SEG        = 120;
    /** Intervalo de reflejo de pedidos registrados en su almacén de origen (casi instantáneo). */
    private static final int PEDIDO_POLL_SEG = 2;
    /** Tope de maletas físicas por ventana de planificación. */
    private static final int MAX_MALETAS   = 20_000;
    /** Presupuesto de cómputo del ALNS por ventana. */
    private static final long PRESUPUESTO_MS = 20_000;

    private final OperacionDiariaPureService servicio;
    private final ObjectMapper objectMapper;
    /** Solo para resolver la ocurrencia a cancelar (método de consulta genérico, sin estado del sim). */
    private final com.plantilla.backend.modules.simulacion.SimulacionPureService simulacionService;

    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(2, r -> {
                Thread t = new Thread(r, "opdiaria-worker");
                t.setDaemon(true);
                return t;
            });

    /** Única operación global (se crea al conectarse el primer dispositivo). */
    private volatile OperacionDiariaEstado estadoGlobal;
    private final Object lock = new Object();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("OpDiariaWS conectado: {}", session.getId());
        OperacionDiariaEstado estado = obtenerOArrancar();
        if (estado == null) {
            // Snapshot aún no disponible (arranque muy temprano): cerrar para que el
            // cliente reconecte en unos segundos, cuando el pre-calentamiento haya terminado.
            try { session.close(); } catch (Exception ignored) {}
            return;
        }
        estado.getSuscriptores().add(session);
        // Enviar el estado actual al recién llegado: plan de vuelos + asignaciones acumuladas
        Map<String, Object> initMsg = new LinkedHashMap<>();
        initMsg.put("type",          "INIT");
        initMsg.put("tiempoRealMs",  System.currentTimeMillis());
        initMsg.put("vuelosEnAire",  estado.getSnapshotVuelos());
        initMsg.put("aeropuertos",   estado.getSnapshotAeropuertos());
        enviar(session, initMsg);

        List<Map<String, Object>> acumulados = new ArrayList<>(estado.getAcumulados().values());
        if (!acumulados.isEmpty()) {
            Map<String, Object> upd = new LinkedHashMap<>();
            upd.put("type",         "UPDATE");
            upd.put("nuevosVuelos", acumulados);
            upd.put("estadisticas", Map.of("ciclo", estado.getCiclos()));
            enviar(session, upd);
        }

        // Pedidos ya registrados (en su almacén de origen, aún sin planificar o no)
        List<Map<String, Object>> pedidos = new ArrayList<>(estado.getPedidosAcumulados());
        if (!pedidos.isEmpty()) {
            Map<String, Object> pmsg = new LinkedHashMap<>();
            pmsg.put("type",    "PEDIDOS_REGISTRADOS");
            pmsg.put("pedidos", pedidos);
            enviar(session, pmsg);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        OperacionDiariaEstado estado = estadoGlobal;
        if (estado != null) estado.getSuscriptores().remove(session);
        log.info("OpDiariaWS desconectado: {} ({})", session.getId(), status.getCode());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> msg = objectMapper.readValue(message.getPayload(), Map.class);
            if ("CANCEL_FLIGHT".equals(msg.get("type"))) {
                manejarCancelFlight(session, msg);
            }
        } catch (Exception e) {
            log.warn("Mensaje WS opdiaria inválido: {}", e.getMessage());
        }
    }

    /**
     * Cancela la siguiente ocurrencia de un vuelo (regla de 1 h: si falta ≥1 h para la
     * salida se cancela la de HOY, si no, la del día siguiente). Las maletas que iban en
     * ese vuelo se liberan y se replanifican de inmediato a otros vuelos.
     */
    private void manejarCancelFlight(WebSocketSession session, Map<String, Object> msg) {
        String codigo = msg.get("codigoVuelo") instanceof String s ? s : null;
        OperacionDiariaEstado estado = estadoGlobal;
        if (estado == null) { enviarCancelError(session, codigo, "La operación no está iniciada."); return; }
        if (codigo == null) { enviarCancelError(session, codigo, "Falta codigoVuelo."); return; }

        long ahoraMs = msg.get("fechaHoraSimuladaMs") instanceof Number n
                ? n.longValue() : System.currentTimeMillis();
        LocalDateTime finHorizonte = LocalDateTime.now(ZoneOffset.UTC).plusDays(4);

        synchronized (estado) {
            var ocurrencia = simulacionService.resolverOcurrenciaCancelacion(
                    codigo, ahoraMs, finHorizonte, estado.getOcurrenciasCanceladas());
            if (ocurrencia.isEmpty()) {
                enviarCancelError(session, codigo,
                        "No existe una ocurrencia futura cancelable (recuerda: hasta 1 h antes de la salida).");
                return;
            }
            String codigoAfectado = ocurrencia.get().codigoVuelo();
            long salidaAfectadaMs = ocurrencia.get().horaSalidaMs();
            String clave = OperacionDiariaEstado.claveOcurrencia(codigoAfectado, salidaAfectadaMs);
            if (estado.getOcurrenciasCanceladas().contains(clave)) {
                enviarCancelError(session, codigoAfectado, "La ocurrencia ya fue cancelada.");
                return;
            }
            estado.getOcurrenciasCanceladas().add(clave);

            // Liberar las maletas de esa ocurrencia: quitarlas del vuelo cancelado y de
            // TODOS los vuelos acumulados, y sacarlas de 'reflejados' para replanificarlas.
            Map<String, Object> vueloCancelado = estado.getAcumulados().remove(clave);
            Set<Integer> afectados = new LinkedHashSet<>();
            if (vueloCancelado != null && vueloCancelado.get("envios") instanceof List<?> envs) {
                for (Object o : envs) {
                    if (o instanceof Map<?, ?> e && e.get("idEnvio") instanceof Number id) {
                        afectados.add(id.intValue());
                    }
                }
            }
            if (!afectados.isEmpty()) {
                scrubEnvios(estado, afectados);
                estado.getReflejados().removeAll(afectados);
            }

            LocalDate fechaOperacion = Instant.ofEpochMilli(salidaAfectadaMs).atZone(ZoneOffset.UTC).toLocalDate();
            LocalDate fechaCancelacion = Instant.ofEpochMilli(ahoraMs).atZone(ZoneOffset.UTC).toLocalDate();
            Map<String, Object> evento = new LinkedHashMap<>();
            evento.put("type",                    "FLIGHT_CANCELLED");
            evento.put("codigoVuelo",             codigoAfectado);
            evento.put("horaSalidaAfectadaMs",    salidaAfectadaMs);
            evento.put("fechaOperacion",          fechaOperacion.toString());
            evento.put("fechaHoraCancelacionMs",  ahoraMs);
            evento.put("enviosAfectados",         new ArrayList<>(afectados));
            evento.put("cantidadEnviosAfectados", afectados.size());
            evento.put("aplicaMismoDia",          fechaOperacion.equals(fechaCancelacion));
            broadcast(estado, evento);
        }

        // Replanificación inmediata (fuera del lock del hilo WS)
        scheduler.submit(() -> planificarTick(estado));
    }

    /** Quita los envíos indicados de todos los vuelos acumulados (evita doble conteo). */
    private void scrubEnvios(OperacionDiariaEstado estado, Set<Integer> ids) {
        for (Map<String, Object> vuelo : estado.getAcumulados().values()) {
            Object envObj = vuelo.get("envios");
            if (!(envObj instanceof List<?> lista)) continue;
            int removidas = 0;
            List<Object> restantes = new ArrayList<>();
            for (Object o : lista) {
                if (o instanceof Map<?, ?> e && e.get("idEnvio") instanceof Number id
                        && ids.contains(id.intValue())) {
                    Object cant = e.get("cantidad");
                    removidas += cant instanceof Number c ? c.intValue() : 0;
                } else {
                    restantes.add(o);
                }
            }
            if (removidas > 0) {
                vuelo.put("envios", restantes);
                int total = ((Number) vuelo.getOrDefault("totalMaletas", 0)).intValue();
                vuelo.put("totalMaletas", Math.max(0, total - removidas));
            }
        }
    }

    private void enviarCancelError(WebSocketSession session, String codigo, String mensaje) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("type",        "CANCEL_FLIGHT_ERROR");
        error.put("codigoVuelo", codigo != null ? codigo : "");
        error.put("mensaje",     mensaje);
        enviar(session, error);
    }

    private OperacionDiariaEstado obtenerOArrancar() {
        OperacionDiariaEstado estado = estadoGlobal;
        if (estado != null) return estado;
        synchronized (lock) {
            if (estadoGlobal != null) return estadoGlobal;
            estado = new OperacionDiariaEstado();
            LocalDateTime ahora = LocalDateTime.now(ZoneOffset.UTC);
            LocalDateTime inicioDia = LocalDate.now(ZoneOffset.UTC).atStartOfDay();
            estado.setPunteroSim(inicioDia); // la primera ventana cubre lo ya registrado hoy
            estado.setInicioRealMs(System.currentTimeMillis());

            // Snapshot de vuelos del plan (ventana amplia para que siempre haya aviones volando)
            List<Map<String, Object>> vuelos = null;
            List<Map<String, Object>> aeropuertos = null;
            try {
                Map<String, Object> snap = servicio.construirSnapshot(
                        ahora.minusHours(6), ahora.plusHours(18));
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> v = (List<Map<String, Object>>) snap.get("vuelosEnAire");
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> a = (List<Map<String, Object>>) snap.get("aeropuertos");
                vuelos = v; aeropuertos = a;
            } catch (Exception e) {
                log.error("Error construyendo snapshot de operación diaria", e);
            }
            // No cachear un estado vacío (p.ej. BD momentáneamente no disponible):
            // se reintenta en el pre-calentamiento o en la próxima conexión.
            if (vuelos == null || vuelos.isEmpty()) {
                log.warn("Snapshot de operación diaria vacío; se reintentará.");
                return null;
            }

            estado.getSnapshotVuelos().addAll(vuelos);
            if (aeropuertos != null) estado.getSnapshotAeropuertos().addAll(aeropuertos);

            estadoGlobal = estado;
            ScheduledFuture<?> tarea = scheduler.scheduleWithFixedDelay(
                    () -> planificarTick(estadoGlobal), 0, SA_SEG, TimeUnit.SECONDS);
            estado.setTareaScheduled(tarea);
            // Reflejo casi instantáneo de pedidos registrados en su almacén de origen,
            // sin esperar la planificación (cada PEDIDO_POLL_SEG segundos).
            scheduler.scheduleWithFixedDelay(
                    () -> notificarPedidosNuevos(estadoGlobal), 0, PEDIDO_POLL_SEG, TimeUnit.SECONDS);
            log.info("Operación diaria ARRANCADA ({} vuelos, planificación cada {} s)", vuelos.size(), SA_SEG);
            return estado;
        }
    }

    /**
     * Pre-calienta la operación diaria al arrancar la app: construye el snapshot en segundo
     * plano (con reintentos) para que la PRIMERA conexión reciba el INIT al instante, sin
     * esperar la consulta de vuelos por el túnel.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void precalentar() {
        scheduler.submit(() -> {
            for (int i = 0; i < 15 && estadoGlobal == null; i++) {
                try {
                    if (obtenerOArrancar() != null) {
                        log.info("Operación diaria pre-calentada (snapshot listo para conexiones)");
                        return;
                    }
                } catch (Exception e) {
                    log.warn("Reintentando pre-calentar operación diaria: {}", e.getMessage());
                }
                try { Thread.sleep(3000); } catch (InterruptedException ie) { return; }
            }
        });
    }

    /** Cada 2 min: planifica los pedidos NUEVOS de envio_diario y difunde las asignaciones. */
    private void planificarTick(OperacionDiariaEstado estado) {
        if (estado == null) return;
        try {
            synchronized (estado) {
                LocalDateTime ahora = LocalDateTime.now(ZoneOffset.UTC);
                int ciclo = estado.incrementarCiclo();

                Map<String, Object> result = servicio.planificarNuevos(
                        estado.getReflejados(), ahora, MAX_MALETAS, PRESUPUESTO_MS,
                        estado.getOcurrenciasCanceladas());
                estado.setPunteroSim(ahora);

                @SuppressWarnings("unchecked")
                List<Integer> asignadosIds = (List<Integer>) result.getOrDefault("asignadosIds", List.of());
                estado.getReflejados().addAll(asignadosIds);

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> delta = (List<Map<String, Object>>) result.getOrDefault("nuevosVuelos", List.of());
                mergearAcumulados(estado, delta);

                if (delta.isEmpty()) return; // nada nuevo que difundir

                Map<String, Object> upd = new LinkedHashMap<>();
                upd.put("type",         "UPDATE");
                upd.put("tiempoRealMs", ahora.toInstant(ZoneOffset.UTC).toEpochMilli());
                upd.put("ciclo",        ciclo);
                upd.put("nuevosVuelos", delta);
                upd.put("estadisticas", Map.of(
                        "asignados",   result.get("asignados"),
                        "noAsignados", result.get("noAsignados"),
                        "ciclo",       ciclo));
                broadcast(estado, upd);
            }
        } catch (Exception e) {
            log.error("Error en tick de operación diaria: {}", e.getMessage(), e);
        }
    }

    /**
     * Difunde los pedidos recién registrados para que aparezcan en su almacén de ORIGEN
     * de inmediato (sin esperar la planificación). Idempotente: cada pedido se envía una vez.
     */
    private void notificarPedidosNuevos(OperacionDiariaEstado estado) {
        if (estado == null) return;
        try {
            LocalDateTime ahora = LocalDateTime.now(ZoneOffset.UTC);
            List<Map<String, Object>> recientes =
                    servicio.pedidosRecientes(ahora.minusHours(36), ahora.plusHours(12));
            List<Map<String, Object>> nuevos = new ArrayList<>();
            for (Map<String, Object> p : recientes) {
                if (p.get("idEnvio") instanceof Number n
                        && estado.getPedidosNotificados().add(n.intValue())) {
                    nuevos.add(p);
                }
            }
            if (nuevos.isEmpty()) return;
            estado.getPedidosAcumulados().addAll(nuevos);
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("type",    "PEDIDOS_REGISTRADOS");
            msg.put("pedidos", nuevos);
            broadcast(estado, msg);
        } catch (Exception e) {
            log.warn("Error reflejando pedidos registrados en opdiaria: {}", e.getMessage());
        }
    }

    /** Acumula el delta en el estado global (para reconstruir a los que se unen luego). */
    private void mergearAcumulados(OperacionDiariaEstado estado, List<Map<String, Object>> delta) {
        for (Map<String, Object> v : delta) {
            Object codigo = v.get("codigoVuelo");
            Object salida = v.get("horaSalidaMs");
            if (!(codigo instanceof String) || !(salida instanceof Number)) continue;
            String key = codigo + "|" + ((Number) salida).longValue();
            Map<String, Object> acum = estado.getAcumulados().get(key);
            if (acum == null) {
                estado.getAcumulados().put(key, new LinkedHashMap<>(v));
            } else {
                int prev = ((Number) acum.getOrDefault("totalMaletas", 0)).intValue();
                int add  = ((Number) v.getOrDefault("totalMaletas", 0)).intValue();
                acum.put("totalMaletas", prev + add);
                @SuppressWarnings("unchecked")
                List<Object> enviosAcum = (List<Object>) acum.computeIfAbsent("envios", k -> new ArrayList<>());
                Object envN = v.get("envios");
                if (envN instanceof List<?> ln) enviosAcum.addAll(ln);
            }
        }
    }

    private void broadcast(OperacionDiariaEstado estado, Object payload) {
        String json;
        try { json = objectMapper.writeValueAsString(payload); }
        catch (Exception e) { log.warn("serialize opdiaria: {}", e.getMessage()); return; }
        for (WebSocketSession s : estado.getSuscriptores()) enviarRaw(s, json);
    }

    private void enviar(WebSocketSession session, Object payload) {
        try { enviarRaw(session, objectMapper.writeValueAsString(payload)); }
        catch (Exception e) { log.warn("Error enviando WS opdiaria [{}]: {}", session.getId(), e.getMessage()); }
    }

    private void enviarRaw(WebSocketSession session, String json) {
        if (session == null || !session.isOpen()) return;
        try {
            synchronized (session) { session.sendMessage(new TextMessage(json)); }
        } catch (Exception e) {
            log.warn("Error enviando WS opdiaria [{}]: {}", session.getId(), e.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }
}
