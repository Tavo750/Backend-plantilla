package com.plantilla.backend.modules.envio.service;

import com.plantilla.backend.config.MonitoreoWebSocketHandler;
import com.plantilla.backend.modules.envio.repository.EnvioDiarioRepository;
import com.plantilla.backend.modules.maestro.entity.Aeropuerto;
import com.plantilla.backend.modules.maestro.entity.PlanVueloDiario;
import com.plantilla.backend.modules.maestro.repository.AeropuertoRepository;
import com.plantilla.backend.modules.maestro.repository.PlanVueloDiarioRepository;
import com.plantilla.backend.modules.maestro.service.PlanVueloDiarioService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Monitoreo en tiempo real basado en el reloj del servidor.
 * Reemplaza al MonitoreoMapaService (ALNS simulado) para el módulo de Monitoreo Mapa.
 *
 * - Al arrancar la aplicación activa el monitoreo automáticamente.
 * - Cada segundo emite un TICK con el reloj real del servidor.
 * - Los vuelos que aparecen son los de plan_vuelo_diario que están en el aire AHORA.
 * - K = 1 (tiempo real, sin aceleración).
 */
@Service
@RequiredArgsConstructor
public class MonitoreoRealTimeService {

    private static final Logger log = LoggerFactory.getLogger(MonitoreoRealTimeService.class);

    private final MonitoreoWebSocketHandler ws;
    private final PlanVueloDiarioRepository planVueloRepo;
    private final AeropuertoRepository aeropuertoRepo;
    private final EnvioDiarioRepository envioDiarioRepo;

    private final AtomicBoolean activo = new AtomicBoolean(false);

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "monitoreo-rt-tick");
        t.setDaemon(true);
        return t;
    });

    private volatile ScheduledFuture<?> tickFuture;

    // Cache en memoria de aeropuertos para enriquecer datos sin ir a BD en cada tick
    private volatile Map<String, Aeropuerto> aeropuertosPorOaci = new ConcurrentHashMap<>();

    // Último estado completo para bootstrap via GET /estado
    private volatile Map<String, Object> ultimoSnapshot = new LinkedHashMap<>();

    // Contadores de planificación (actualizados por PlanificadorEnvioService)
    private volatile int pedidosAsignados   = 0;
    private volatile int pedidosNoAsignados = 0;
    private volatile int maletasFisicas     = 0;

    // ════════════════════════════════════════════════════════════════════
    // Arranque automático al iniciar la aplicación
    // ════════════════════════════════════════════════════════════════════

    @EventListener(ApplicationReadyEvent.class)
    public void arrancarAlInicio() {
        cargarAeropuertos();
        activar();
        log.info("MonitoreoRealTimeService arrancado automáticamente al iniciar la aplicación");
    }

    // ════════════════════════════════════════════════════════════════════
    // API pública
    // ════════════════════════════════════════════════════════════════════

    public synchronized Map<String, Object> activar() {
        if (activo.get()) return ultimoSnapshot;

        activo.set(true);
        tickFuture = scheduler.scheduleAtFixedRate(this::tick, 0, 1, TimeUnit.SECONDS);
        log.info("Monitoreo en tiempo real activado");

        broadcastSnapshot();
        return ultimoSnapshot;
    }

    public synchronized void detener() {
        activo.set(false);
        if (tickFuture != null) { tickFuture.cancel(false); tickFuture = null; }
        log.info("Monitoreo en tiempo real detenido");
    }

    public Map<String, Object> obtenerSnapshot() {
        return Collections.unmodifiableMap(ultimoSnapshot);
    }

    /** Llamado por PlanificadorEnvioService tras cada ciclo de planificación. */
    public void actualizarContadores(int asignados, int noAsignados, int maletas) {
        this.pedidosAsignados   = asignados;
        this.pedidosNoAsignados = noAsignados;
        this.maletasFisicas     = maletas;
    }

    /** Fuerza un broadcast PLAN completo (llamado por el planificador). */
    public void broadcastPlan() {
        broadcastSnapshot();
    }

    // ════════════════════════════════════════════════════════════════════
    // Tick (cada segundo)
    // ════════════════════════════════════════════════════════════════════

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private void tick() {
        if (!activo.get()) return;
        try {
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("tipo",               "TICK");
            msg.put("relojSim",           LocalDateTime.now(LIMA).toString());
            msg.put("K",                  1);
            msg.put("pedidosAsignados",   pedidosAsignados);
            msg.put("pedidosNoAsignados", pedidosNoAsignados);
            msg.put("maletasFisicas",     maletasFisicas);
            ws.broadcast(msg);
        } catch (Exception e) {
            log.warn("Error en tick RT: {}", e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // Snapshot completo
    // ════════════════════════════════════════════════════════════════════

    private void broadcastSnapshot() {
        Map<String, Object> snapshot = construirSnapshot("PLAN");
        ultimoSnapshot = snapshot;
        ws.broadcast(snapshot);
    }

    private Map<String, Object> construirSnapshot(String tipo) {
        List<PlanVueloDiario> todos = planVueloRepo.findAll();
        LocalDate hoy = LocalDate.now(LIMA);

        // Maletas asignadas por vuelo — consulta agregada, sin lazy loading
        Map<Integer, Integer> maletasPorVuelo = new java.util.HashMap<>();
        for (Object[] row : envioDiarioRepo.sumCantidadPorPlanVuelo()) {
            Integer idVuelo = (Integer) row[0];
            Integer suma    = ((Number) row[1]).intValue();
            maletasPorVuelo.put(idVuelo, suma);
        }

        // Maletas activas por aeropuerto origen — consulta agregada
        Map<String, Integer> maletasPorAeropuerto = new java.util.HashMap<>();
        for (Object[] row : envioDiarioRepo.sumCantidadPorAeropuertoOrigen()) {
            String oaci  = (String) row[0];
            Integer suma = ((Number) row[1]).intValue();
            maletasPorAeropuerto.put(oaci, suma);
        }

        List<Map<String, Object>> vuelos = todos.stream()
                .map(v -> enrichVuelo(v, hoy, maletasPorVuelo))
                .collect(Collectors.toList());

        List<Map<String, Object>> almacenes = construirAlmacenes(maletasPorAeropuerto);

        Map<String, Object> indicadores = new LinkedHashMap<>();
        long enVuelo  = vuelos.stream().filter(v -> "EN_VUELO".equals(v.get("estadoVuelo"))).count();
        long porSalir = vuelos.stream().filter(v -> "POR_SALIR".equals(v.get("estadoVuelo"))).count();
        long llego    = vuelos.stream().filter(v -> "LLEGÓ".equals(v.get("estadoVuelo"))).count();
        indicadores.put("totalVuelos",  vuelos.size());
        indicadores.put("enVuelo",      enVuelo);
        indicadores.put("porSalir",     porSalir);
        indicadores.put("llego",        llego);
        indicadores.put("pctFlota",     vuelos.isEmpty() ? 0.0 : (double) enVuelo / vuelos.size() * 100);
        indicadores.put("semaforoFlota","VERDE");
        indicadores.put("pctAlmacenes", 0.0);
        indicadores.put("semaforoAlmacenes", "VACIO");

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("tipo",                tipo);
        snapshot.put("activo",              activo.get());
        snapshot.put("fase",                activo.get() ? "CORRIENDO" : "INACTIVO");
        snapshot.put("relojSim",            LocalDateTime.now(LIMA).toString());
        snapshot.put("K",                   1);
        snapshot.put("SA",                  5);
        snapshot.put("ciclo",               0);
        snapshot.put("pedidosAsignados",    pedidosAsignados);
        snapshot.put("pedidosNoAsignados",  pedidosNoAsignados);
        snapshot.put("maletasFisicas",      maletasFisicas);
        snapshot.put("vuelos",              vuelos);
        snapshot.put("almacenesDetalle",    almacenes);
        snapshot.put("enviosDetalle",       List.of());
        snapshot.put("indicadoresGlobales", indicadores);
        return snapshot;
    }

    // ════════════════════════════════════════════════════════════════════
    // Helpers
    // ════════════════════════════════════════════════════════════════════

    private Map<String, Object> enrichVuelo(PlanVueloDiario v, LocalDate hoy, Map<Integer, Integer> maletasPorVuelo) {
        String estado = PlanVueloDiarioService.calcularEstado(v.getHoraSalida(), v.getHoraLlegada());

        LocalDateTime salidaDt  = LocalDateTime.of(hoy, v.getHoraSalida());
        boolean overnight = v.getHoraLlegada().isBefore(v.getHoraSalida());
        LocalDateTime llegadaDt = overnight
                ? LocalDateTime.of(hoy.plusDays(1), v.getHoraLlegada())
                : LocalDateTime.of(hoy, v.getHoraLlegada());

        String codigoVuelo = v.getCodigoOrigen() + "-" + v.getCodigoDestino() + "-"
                + v.getHoraSalida().toString().replace(":", "").substring(0, 4);

        int totalMaletas = maletasPorVuelo.getOrDefault(v.getId(), 0);
        double ocupacionPct = v.getCapacidad() > 0
                ? (double) totalMaletas / v.getCapacidad() * 100.0
                : 0.0;
        String semaforo = ocupacionPct >= 90 ? "ROJO" : ocupacionPct >= 60 ? "AMARILLO" : "VERDE";

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id",              v.getId());
        m.put("codigoVuelo",     codigoVuelo);
        m.put("origen",          v.getCodigoOrigen());
        m.put("destino",         v.getCodigoDestino());
        m.put("horaSalida",      salidaDt.toString());
        m.put("horaLlegada",     llegadaDt.toString());
        m.put("capacidadMaxima", v.getCapacidad());
        m.put("totalMaletas",    totalMaletas);
        m.put("ocupacionPct",    ocupacionPct);
        m.put("estadoVuelo",     estado);
        m.put("semaforo",        semaforo);

        Aeropuerto aOrigen  = aeropuertosPorOaci.get(v.getCodigoOrigen());
        Aeropuerto aDestino = aeropuertosPorOaci.get(v.getCodigoDestino());
        m.put("ciudadOrigen",  aOrigen  != null ? aOrigen.getCiudad()  : v.getCodigoOrigen());
        m.put("ciudadDestino", aDestino != null ? aDestino.getCiudad() : v.getCodigoDestino());
        m.put("continenteOrigen",  aOrigen  != null ? aOrigen.getContinente().name() : "");
        m.put("continenteDestino", aDestino != null ? aDestino.getContinente().name() : "");
        return m;
    }

    private List<Map<String, Object>> construirAlmacenes(Map<String, Integer> maletasPorAeropuerto) {
        return aeropuertosPorOaci.values().stream().map(a -> {
            int ocupacion = maletasPorAeropuerto.getOrDefault(a.getCodigoOaci(), 0);
            int capacidad = a.getCapacidad() != null ? a.getCapacidad() : 0;
            double pct    = capacidad > 0 ? (double) ocupacion / capacidad * 100.0 : 0.0;
            String semaforo = pct >= 90 ? "ROJO" : pct >= 60 ? "AMARILLO" : pct > 0 ? "VERDE" : "VACIO";

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("codigo",     a.getCodigoOaci());
            m.put("ciudad",     a.getCiudad());
            m.put("continente", a.getContinente().name());
            m.put("capacidad",  capacidad);
            m.put("ocupacion",  ocupacion);
            m.put("pct",        pct);
            m.put("semaforo",   semaforo);
            return m;
        }).collect(Collectors.toList());
    }

    private void cargarAeropuertos() {
        try {
            aeropuertosPorOaci = aeropuertoRepo.findAll().stream()
                    .collect(Collectors.toMap(Aeropuerto::getCodigoOaci, a -> a));
            log.info("Cache de aeropuertos cargado: {} aeropuertos", aeropuertosPorOaci.size());
        } catch (Exception e) {
            log.warn("No se pudo cargar el cache de aeropuertos: {}", e.getMessage());
        }
    }
}
