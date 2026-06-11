package com.plantilla.backend.modules.envio.service;

import com.plantilla.backend.BackendApplication;
import com.plantilla.backend.config.MonitoreoWebSocketHandler;
import com.plantilla.backend.modules.envio.entity.EnvioMaletas;
import com.plantilla.backend.modules.envio.repository.EnvioMaletasRepository;
import com.plantilla.backend.modules.maestro.entity.Vuelo;
import com.plantilla.backend.modules.maestro.repository.VueloRepository;
import com.plantilla.backend.modules.simulacion.alns.AlnsSimulacionService;
import com.plantilla.backend.modules.simulacion.alns.BackendDataAdapter;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Orquestador server-side del Monitoreo Mapa con RELOJ SIMULADO AUTORITATIVO.
 *
 * <p>El backend mantiene un reloj de simulación continuo ({@code relojSimMin}, minutos ALNS
 * desde el epoch 2026-01-01) que avanza solo a razón de K segundos simulados por segundo real.
 * El front NO lleva su propio reloj: solo dibuja a partir del tiempo que emite el backend, por
 * lo que la simulación sobrevive a la navegación (al volver al módulo continúa donde iba).</p>
 *
 * <p>Flujo:</p>
 * <ol>
 *   <li>{@link #iniciar} arranca el reloj en la fecha de registro más antigua de la BD y lanza
 *       un primer batch PEQUEÑO para que aparezcan aviones en segundos.</li>
 *   <li>Un tick cada segundo avanza el reloj y emite un mensaje liviano por WebSocket.</li>
 *   <li>La planificación ALNS se dispara cuando el reloj se acerca al horizonte ya planificado
 *       (queda "enganchada" al reloj, evitando saltar a meses futuros).</li>
 *   <li>Los vuelos planificados se acumulan y se emiten en cada ciclo de planificación.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class MonitoreoMapaService {

    private static final Logger log = LoggerFactory.getLogger(MonitoreoMapaService.class);

    private final AlnsSimulacionService alns;
    private final MonitoreoWebSocketHandler ws;
    private final EnvioMaletasRepository envioRepository;
    private final VueloRepository vueloRepository;
    private final BackendDataAdapter dataAdapter;

    // ── Parámetros del reloj/planificación ──────────────────────────────
    private static final long   TICK_MS         = 1000;          // un tick de reloj por segundo real
    private static final double HORIZONTE_MIN   = 2 * 24 * 60;   // planificar hasta 2 días simulados por delante
    private static final int    BATCH_INICIAL   = 120;           // primer batch pequeño → aviones rápido
    private static final int    BATCH_NORMAL    = 600;           // batches siguientes
    private static final double PRUNE_ATRAS_MIN = 6 * 60;        // descartar vuelos que aterrizaron hace > 6h sim

    // ── Estado del reloj ────────────────────────────────────────────────
    private final AtomicBoolean activo       = new AtomicBoolean(false);
    private final AtomicBoolean planificando = new AtomicBoolean(false);

    private volatile double relojSimMin;            // tiempo simulado actual (min ALNS)
    private volatile long   ultimoTickRealMs;       // marca real del último tick
    private volatile LocalDateTime cursorRegistro;  // próxima fechaRegistro a planificar
    private volatile double cursorRegistroMin;      // cursorRegistro en min ALNS
    private volatile boolean primerPlanListo;
    private volatile int ciclo;

    // ── Estado acumulado ────────────────────────────────────────────────
    // Vuelos y envíos se ACUMULAN (alineados) para que "Ver envíos" de cualquier
    // vuelo en pantalla encuentre su detalle. Se podan juntos al envejecer.
    private final Map<String, Map<String, Object>> vuelos     = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> enviosAcum = new ConcurrentHashMap<>();
    private volatile List<Map<String, Object>> almacenesDetalle = new ArrayList<>();
    private volatile Map<String, Object> indicadoresGlobales     = new LinkedHashMap<>();
    private volatile int pedidosAsignados;
    private volatile int pedidosNoAsignados;
    private volatile int maletasFisicas;

    // ── Schedulers ──────────────────────────────────────────────────────
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "monitoreo-reloj");
        t.setDaemon(true);
        return t;
    });
    private final ExecutorService planExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "monitoreo-alns");
        t.setDaemon(true);
        return t;
    });
    private volatile ScheduledFuture<?> tickFuture;

    // ════════════════════════════════════════════════════════════════════
    // API pública
    // ════════════════════════════════════════════════════════════════════

    /**
     * Arranca la simulación. Idempotente: si ya está activa devuelve el snapshot actual
     * sin reiniciar (clave para que no se corte al navegar).
     */
    public synchronized Map<String, Object> iniciar(LocalDateTime fechaInicio) {
        if (activo.get()) {
            log.info("Monitoreo ya activo — devolviendo estado en curso (reloj {})",
                    relojActualUtc());
            return obtenerSnapshot();
        }

        LocalDateTime inicio = fechaInicio != null
                ? fechaInicio
                : calcularInicioAutomatico();

        cursorRegistro    = inicio;
        cursorRegistroMin = dataAdapter.toMinutosUtcDesdeEpoch(inicio);
        relojSimMin       = cursorRegistroMin; // provisional: el reloj real arranca al 1er plan con vuelos
        ultimoTickRealMs  = System.currentTimeMillis();
        vuelos.clear();
        enviosAcum.clear();
        almacenesDetalle = new ArrayList<>();
        indicadoresGlobales = new LinkedHashMap<>();
        pedidosAsignados = pedidosNoAsignados = maletasFisicas = 0;
        ciclo = 0;
        primerPlanListo = false;
        activo.set(true);

        log.info("Monitoreo iniciado | cursor de pedidos en {} | K={} (el reloj arrancará al primer plan con vuelos)",
                inicio, BackendApplication.K);

        // Primer batch PEQUEÑO inmediato. El RELOJ NO arranca aún: solo cuando
        // este plan produzca vuelos, para no avanzar el tiempo sobre un mapa vacío.
        planExecutor.submit(() -> planificar(BATCH_INICIAL));

        return obtenerSnapshot();
    }

    /** Snapshot completo para bootstrap del front (GET /estado) y para clientes que reconectan. */
    public Map<String, Object> obtenerSnapshot() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("tipo",                "SNAPSHOT");
        s.put("activo",              activo.get());
        s.put("fase",                activo.get() ? (primerPlanListo ? "CORRIENDO" : "INICIANDO") : "INACTIVO");
        s.put("relojSim",            relojActualUtc().toString());
        s.put("K",                   BackendApplication.K);
        s.put("SA",                  BackendApplication.SA);
        s.put("ciclo",               ciclo);
        s.put("pedidosAsignados",    pedidosAsignados);
        s.put("pedidosNoAsignados",  pedidosNoAsignados);
        s.put("maletasFisicas",      maletasFisicas);
        s.put("vuelos",              new ArrayList<>(vuelos.values()));
        s.put("almacenesDetalle",    almacenesDetalle);
        s.put("enviosDetalle",       new ArrayList<>(enviosAcum.values()));
        s.put("indicadoresGlobales", indicadoresGlobales);
        return s;
    }

    /** Detiene la simulación. El reloj deja de avanzar; el estado acumulado se conserva. */
    public synchronized void detener() {
        log.info("Monitoreo detenido manualmente en reloj {}", relojActualUtc());
        activo.set(false);
        if (tickFuture != null) { tickFuture.cancel(false); tickFuture = null; }
    }

    // ════════════════════════════════════════════════════════════════════
    // Reloj
    // ════════════════════════════════════════════════════════════════════

    private void tick() {
        if (!activo.get()) return;
        try {
            long now = System.currentTimeMillis();
            double dtSeg = (now - ultimoTickRealMs) / 1000.0;
            ultimoTickRealMs = now;

            // K = segundos simulados por segundo real → minutos simulados por tick = K * dtSeg / 60
            relojSimMin += BackendApplication.K * dtSeg / 60.0;

            // Disparar planificación si el reloj se acerca al horizonte ya cubierto
            if (cursorRegistroMin < relojSimMin + HORIZONTE_MIN && !planificando.get()) {
                planExecutor.submit(() -> planificar(BATCH_NORMAL));
            }

            ws.broadcast(tickPayload());
        } catch (Exception e) {
            log.warn("Error en tick del reloj: {}", e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // Planificación
    // ════════════════════════════════════════════════════════════════════

    private void planificar(int batchSize) {
        if (!activo.get()) return;
        if (!planificando.compareAndSet(false, true)) return;
        try {
            long restantes = envioRepository.countByFechaRegistroGreaterThanEqual(cursorRegistro);
            if (restantes == 0) {
                primerPlanListo = true;
                return; // sin más pedidos: el reloj sigue corriendo sobre los vuelos ya planificados
            }

            Map<String, Object> r = alns.planificarVentana(cursorRegistro, batchSize);

            mergeVuelos(r);

            // Avanzar cursor de registro
            Object ultimaStr = r.get("ultimaFechaEnvio");
            if (ultimaStr instanceof String s) {
                cursorRegistro    = LocalDateTime.parse(s);
                cursorRegistroMin = dataAdapter.toMinutosUtcDesdeEpoch(cursorRegistro);
            }

            // Acumular contadores (pedidos y maletas físicas)
            pedidosAsignados   += intDe(r.get("asignados"));
            pedidosNoAsignados += intDe(r.get("noAsignados"));
            maletasFisicas     += intDe(r.get("maletasFisicas"));

            // Paneles agregados: reflejar el último batch (ocupación/indicadores)
            almacenesDetalle    = listaDe(r.get("almacenesDetalle"));
            indicadoresGlobales = mapaDe(r.get("indicadoresGlobales"));

            ciclo++;

            log.info("Ciclo {} planificado | vuelos acumulados={} | pedidos={} | maletas={} | cursor={}",
                    ciclo, vuelos.size(), pedidosAsignados, maletasFisicas, cursorRegistro);

            // El RELOJ arranca la primera vez que haya vuelos para mostrar (no antes).
            if (tickFuture == null) {
                if (!vuelos.isEmpty()) {
                    arrancarReloj();
                } else if (envioRepository.countByFechaRegistroGreaterThanEqual(cursorRegistro) > 0) {
                    // Aún sin vuelos pero quedan pedidos: seguir buscando el primer plan con vuelos
                    planExecutor.submit(() -> planificar(BATCH_INICIAL));
                }
            }

            ws.broadcast(planPayload());
        } catch (Exception e) {
            log.error("Error planificando ciclo: {}", e.getMessage(), e);
        } finally {
            planificando.set(false);
        }
    }

    /** Arranca el reloj simulado en la hora del primer despegue planificado (menos un pequeño margen). */
    private synchronized void arrancarReloj() {
        if (tickFuture != null) return;
        double minSalida = vuelos.values().stream()
                .map(v -> v.get("horaSalida"))
                .filter(o -> o instanceof String)
                .mapToDouble(o -> dataAdapter.toMinutosUtcDesdeEpoch(LocalDateTime.parse((String) o)))
                .min()
                .orElse(relojSimMin);
        relojSimMin      = minSalida - 10; // arrancar 10 min simulados antes del primer despegue
        ultimoTickRealMs = System.currentTimeMillis();
        primerPlanListo  = true;
        log.info("Reloj arrancado en {} (primer despegue planificado)", relojActualUtc());
        tickFuture = scheduler.scheduleAtFixedRate(this::tick, TICK_MS, TICK_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * Inicio robusto del cursor de pedidos: la fechaRegistro más antigua, pero nunca antes
     * del primer vuelo disponible. Evita arrancar en pedidos "outlier" sin vuelos
     * (p. ej. un registro suelto de un año anterior al periodo simulado).
     */
    private LocalDateTime calcularInicioAutomatico() {
        LocalDateTime minPedido = envioRepository.findTopByOrderByFechaRegistroAsc()
                .map(EnvioMaletas::getFechaRegistro)
                .orElse(LocalDateTime.of(2026, 1, 1, 0, 0));
        LocalDateTime minVuelo = vueloRepository.findTopByOrderByHoraSalidaAsc()
                .map(Vuelo::getHoraSalida)
                .orElse(minPedido);
        LocalDateTime inicio = minPedido.isAfter(minVuelo) ? minPedido : minVuelo;
        log.info("Inicio automático: minPedido={} | minVuelo={} → cursor={}", minPedido, minVuelo, inicio);
        return inicio;
    }

    /**
     * Fusiona vuelos y envíos del batch en el acumulado (alineados) y poda los vuelos
     * que ya aterrizaron hace rato, descartando también los envíos que se quedan sin vuelos.
     */
    @SuppressWarnings("unchecked")
    private void mergeVuelos(Map<String, Object> r) {
        // Acumular vuelos
        Object vl = r.get("vuelos");
        if (vl instanceof List<?> lista) {
            for (Object o : lista) {
                if (!(o instanceof Map<?, ?> m)) continue;
                Object cod = m.get("codigoVuelo");
                if (cod != null) vuelos.putIfAbsent(cod.toString(), (Map<String, Object>) m);
            }
        }
        // Acumular envíos (clave: id del envío)
        Object el = r.get("enviosDetalle");
        if (el instanceof List<?> lista) {
            for (Object o : lista) {
                if (!(o instanceof Map<?, ?> m)) continue;
                Object id = m.get("id");
                if (id != null) enviosAcum.putIfAbsent(id.toString(), (Map<String, Object>) m);
            }
        }

        // Poda de vuelos cuya llegada quedó muy atrás del reloj (liberar memoria)
        double limite = relojSimMin - PRUNE_ATRAS_MIN;
        vuelos.values().removeIf(v -> {
            Object ll = v.get("horaLlegada");
            if (!(ll instanceof String s)) return false;
            try {
                return dataAdapter.toMinutosUtcDesdeEpoch(LocalDateTime.parse(s)) < limite;
            } catch (Exception ex) {
                return false;
            }
        });

        // Poda de envíos que ya no referencian ningún vuelo vivo
        enviosAcum.values().removeIf(e -> {
            Object vs = e.get("vuelos");
            if (!(vs instanceof List<?> cods)) return true;
            for (Object c : cods) { if (c != null && vuelos.containsKey(c.toString())) return false; }
            return true;
        });
    }

    // ════════════════════════════════════════════════════════════════════
    // Payloads WebSocket
    // ════════════════════════════════════════════════════════════════════

    /** Mensaje liviano por tick: solo reloj y contadores (sin lista de vuelos). */
    private Map<String, Object> tickPayload() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tipo",               "TICK");
        m.put("fase",               primerPlanListo ? "CORRIENDO" : "INICIANDO");
        m.put("relojSim",           relojActualUtc().toString());
        m.put("K",                  BackendApplication.K);
        m.put("SA",                 BackendApplication.SA);
        m.put("ciclo",              ciclo);
        m.put("pedidosAsignados",   pedidosAsignados);
        m.put("pedidosNoAsignados", pedidosNoAsignados);
        m.put("maletasFisicas",     maletasFisicas);
        return m;
    }

    /** Mensaje de planificación: snapshot completo con la lista de vuelos acumulada. */
    private Map<String, Object> planPayload() {
        Map<String, Object> m = obtenerSnapshot();
        m.put("tipo", "PLAN");
        return m;
    }

    // ════════════════════════════════════════════════════════════════════
    // Helpers
    // ════════════════════════════════════════════════════════════════════

    private LocalDateTime relojActualUtc() {
        return dataAdapter.toLocalDateTimeUtc((long) relojSimMin);
    }

    private static int intDe(Object o) {
        return o instanceof Number n ? n.intValue() : 0;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listaDe(Object o) {
        return o instanceof List<?> l ? (List<Map<String, Object>>) l : new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapaDe(Object o) {
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : new LinkedHashMap<>();
    }
}
