package com.plantilla.backend.modules.operaciondiaria;

import com.plantilla.backend.modules.algoritmo.alns.engine.ALNSEngine;
import com.plantilla.backend.modules.algoritmo.alns.model.Aeropuerto;
import com.plantilla.backend.modules.algoritmo.alns.model.Maleta;
import com.plantilla.backend.modules.algoritmo.alns.model.PlanDeRutas;
import com.plantilla.backend.modules.algoritmo.alns.model.Ruta;
import com.plantilla.backend.modules.algoritmo.alns.util.FlightIndex;
import com.plantilla.backend.modules.algoritmo.alns.util.SolutionGenerator;
import com.plantilla.backend.modules.envio.entity.EnvioDiario;
import com.plantilla.backend.modules.envio.repository.EnvioDiarioRepository;
import com.plantilla.backend.modules.maestro.repository.AeropuertoRepository;
import com.plantilla.backend.modules.maestro.repository.VueloRepository;
import com.plantilla.backend.modules.simulacion.alns.BackendDataAdapter;
import com.plantilla.backend.shared.enums.EstadoVuelo;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Servicio de planificación para el módulo de OPERACIÓN DIARIA.
 *
 * Es un análogo de {@link com.plantilla.backend.modules.simulacion.SimulacionPureService}
 * pero para la operación en continuo:
 *  - toma los pedidos de la tabla {@code envio_diario} (entidad {@link EnvioDiario}),
 *  - reutiliza el mismo motor ALNS y adaptador de datos (solo lectura),
 *  - NO persiste nada en la BD, NO tiene fin (planificación continua).
 *
 * No modifica ni depende del flujo de simulación.
 */
@Service
@RequiredArgsConstructor
public class OperacionDiariaPureService {

    private static final Logger log = LoggerFactory.getLogger(OperacionDiariaPureService.class);

    private static final int    MAX_ITERACIONES      = 5000;
    private static final double REMOCION_MIN         = 0.10;
    private static final double REMOCION_MAX         = 0.40;
    private static final double TEMPERATURA_INI      = 100.0;
    private static final double TASA_ENFRIAMIENTO    = 0.999;
    private static final double TASA_REACCION        = 0.3;
    private static final int    PERIODO_ACTUALIZ     = 100;
    private static final long   PRESUPUESTO_DEFAULT_MS = 20_000;
    private static final int    MAX_SIN_MEJORA       = 300;
    private static final int    MAX_ARRASTRE         = 20_000;

    private final BackendDataAdapter     dataAdapter;
    private final VueloRepository        vueloRepository;
    private final AeropuertoRepository   aeropuertoRepository;
    private final EnvioDiarioRepository  envioDiarioRepository;

    /**
     * Snapshot inicial de la operación: TODOS los vuelos del plan cuyo horario de
     * salida cae en [desde, hasta] (se muestran volando aunque vayan vacíos) y la
     * lista de aeropuertos activos.
     */
    @Transactional
    public Map<String, Object> construirSnapshot(LocalDateTime desde, LocalDateTime hasta) {
        List<com.plantilla.backend.modules.maestro.entity.Vuelo> vuelos =
                vueloRepository.findByHoraSalidaBetween(desde, hasta).stream()
                        .filter(v -> v.getEstado() != EstadoVuelo.CANCELADO)
                        .collect(Collectors.toList());

        List<com.plantilla.backend.modules.maestro.entity.Aeropuerto> aeropuertos =
                aeropuertoRepository.findByActivoTrue();

        List<Map<String, Object>> vuelosList = vuelos.stream().map(v -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("codigoVuelo",   v.getCodigoVuelo());
            m.put("origen",        v.getAeropuertoOrigen().getCodigoOaci());
            m.put("destino",       v.getAeropuertoDestino().getCodigoOaci());
            m.put("horaSalidaMs",  v.getHoraSalida().toInstant(ZoneOffset.UTC).toEpochMilli());
            m.put("horaLlegadaMs", v.getHoraLlegada().toInstant(ZoneOffset.UTC).toEpochMilli());
            m.put("capacidad",     v.getCapacidadMaxima() != null ? v.getCapacidadMaxima() : 0);
            m.put("totalMaletas",  0);
            m.put("envios", Collections.emptyList());
            return m;
        }).collect(Collectors.toList());

        List<Map<String, Object>> aeropuertosList = aeropuertos.stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("codigoOaci", a.getCodigoOaci());
            m.put("capacidad",  a.getCapacidad() != null ? a.getCapacidad() : 0);
            return m;
        }).collect(Collectors.toList());

        log.info("OpDiaria snapshot: {} vuelos en el plan, {} aeropuertos", vuelosList.size(), aeropuertosList.size());

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("vuelosEnAire", vuelosList);
        snapshot.put("aeropuertos",  aeropuertosList);
        return snapshot;
    }

    /**
     * Planifica los pedidos NUEVOS de {@code envio_diario} (los que aún no se han
     * reflejado en el mapa) y devuelve los vuelos con sus maletas asignadas — SIN
     * escribir en la BD.
     *
     * Los pedidos se seleccionan por id (no por ventana horaria) para evitar el
     * desfase de zona horaria del registro (Registro de Maletas guarda en hora de
     * Lima, mientras que los vuelos están en UTC). La disponibilidad de cada maleta
     * se fija en {@code ahora} (UTC), de modo que un pedido recién registrado se
     * enrute a vuelos FUTUROS y se refleje siempre en su almacén de origen.
     *
     * @param reflejados   ids de envíos ya reflejados en ciclos anteriores (se omiten)
     * @param ahora        instante real (UTC) del ciclo
     * @param maxMaletasSC tope de maletas por ciclo
     * @param presupuestoMs presupuesto de cómputo del ALNS
     */
    @Transactional
    public Map<String, Object> planificarNuevos(
            java.util.Set<Integer> reflejados, LocalDateTime ahora,
            int maxMaletasSC, long presupuestoMs, java.util.Set<String> ocurrenciasCanceladas) {

        Map<String, Aeropuerto> aeropuertosAlns = dataAdapter.cargarAeropuertos();
        if (aeropuertosAlns.isEmpty()) return resultadoVacio();

        // Vuelos: desde un poco antes de ahora hasta 3 días adelante (rutas multi-tramo)
        List<com.plantilla.backend.modules.algoritmo.alns.model.Vuelo> vuelos =
                dataAdapter.cargarVuelos(ahora.minusHours(2), ahora.plusDays(3));
        // Excluir ocurrencias canceladas: no se pueden usar para (re)planificar
        if (ocurrenciasCanceladas != null && !ocurrenciasCanceladas.isEmpty()) {
            vuelos = vuelos.stream()
                    .filter(v -> {
                        long salidaMs = dataAdapter.toLocalDateTimeUtc(v.getHoraSalida())
                                .toInstant(ZoneOffset.UTC).toEpochMilli();
                        return !ocurrenciasCanceladas.contains(v.getId() + "|" + salidaMs);
                    })
                    .collect(Collectors.toList());
        }
        if (vuelos.isEmpty()) {
            log.info("OpDiaria [{}] sin vuelos futuros disponibles", ahora);
            return resultadoVacio();
        }

        // Candidatos: pedidos registrados en una ventana amplia que cubre el huso de cada
        // origen (fecha_registro se guarda en la hora local del origen: los husos +GMT como
        // Delhi/Copenhague quedan ADELANTADOS respecto a UTC, por eso el margen +12 h).
        List<EnvioDiario> candidatos = envioDiarioRepository
                .findByFechaRegistroBetweenOrderByFechaRegistroAsc(ahora.minusHours(36), ahora.plusHours(12))
                .stream()
                .filter(e -> reflejados == null || !reflejados.contains(e.getIdEnvio()))
                .collect(Collectors.toList());

        List<EnvioDiario> enviosSC = new ArrayList<>();
        int totalMaletasSC = 0;
        for (EnvioDiario e : candidatos) {
            int cant = e.getCantidad() != null ? e.getCantidad() : 1;
            if (totalMaletasSC + cant > maxMaletasSC) break;
            enviosSC.add(e);
            totalMaletasSC += cant;
        }

        if (enviosSC.isEmpty()) return resultadoVacio();

        // Disponibilidad forzada a "ahora": el pedido acaba de llegar en tiempo real
        List<Maleta> maletas = enviosSC.stream()
                .map(e -> dataAdapter.convertirEnvioDiario(e, aeropuertosAlns, ahora))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (maletas.isEmpty()) return resultadoVacio();

        FlightIndex flightIndex = dataAdapter.construirFlightIndex(vuelos);
        PlanDeRutas planInicial = SolutionGenerator.generarPlanInicial(maletas, flightIndex, aeropuertosAlns);

        PlanDeRutas mejorPlan;
        if (planInicial.getTotalMaletasAsignadas() > 0 && !planInicial.getMaletasNoAsignadas().isEmpty()) {
            ALNSEngine engine = new ALNSEngine(
                    MAX_ITERACIONES, REMOCION_MIN, REMOCION_MAX,
                    TEMPERATURA_INI, TASA_ENFRIAMIENTO, TASA_REACCION,
                    PERIODO_ACTUALIZ, flightIndex,
                    presupuestoMs > 0 ? presupuestoMs : PRESUPUESTO_DEFAULT_MS, MAX_SIN_MEJORA);
            mejorPlan = engine.ejecutar(planInicial);
        } else {
            mejorPlan = planInicial;
        }

        log.info("OpDiaria [{}]: {} pedidos nuevos → {} asignados, {} no asignados",
                ahora, maletas.size(),
                mejorPlan.getTotalMaletasAsignadas(), mejorPlan.getMaletasNoAsignadas().size());

        Map<String, Map<String, Object>> vuelosMap = new LinkedHashMap<>();
        Map<String, List<Map<String, Object>>> enviosPorVuelo = new LinkedHashMap<>();
        int violacionesSla = 0;

        Map<Integer, EnvioDiario> envioPorId = enviosSC.stream()
                .collect(Collectors.toMap(EnvioDiario::getIdEnvio, e -> e, (a, b) -> a));

        for (Map.Entry<Maleta, Ruta> entry : mejorPlan.getAsignaciones().entrySet()) {
            Maleta maleta = entry.getKey();
            Ruta ruta     = entry.getValue();
            boolean cumpleSla = !maleta.isSLAExpirado(ruta.getHoraLlegadaFinal());
            if (!cumpleSla) violacionesSla++;

            for (com.plantilla.backend.modules.algoritmo.alns.model.Vuelo v : ruta.getVuelos()) {
                LocalDateTime salidaUtc  = dataAdapter.toLocalDateTimeUtc(v.getHoraSalida());
                long salidaMs = salidaUtc.toInstant(ZoneOffset.UTC).toEpochMilli();
                String key = v.getId() + "|" + salidaMs;
                if (!vuelosMap.containsKey(key)) {
                    LocalDateTime llegadaUtc = dataAdapter.toLocalDateTimeUtc(v.getHoraLlegada());
                    Map<String, Object> vd = new LinkedHashMap<>();
                    vd.put("codigoVuelo",   v.getId());
                    vd.put("origen",        v.getOrigen());
                    vd.put("destino",       v.getDestino());
                    vd.put("horaSalidaMs",  salidaMs);
                    vd.put("horaLlegadaMs", llegadaUtc.toInstant(ZoneOffset.UTC).toEpochMilli());
                    vd.put("capacidad",     v.getCapacidad());
                    vd.put("totalMaletas",  0);
                    vuelosMap.put(key, vd);
                    enviosPorVuelo.put(key, new ArrayList<>());
                }
                int prev = (int) vuelosMap.get(key).get("totalMaletas");
                vuelosMap.get(key).put("totalMaletas", prev + maleta.getCantidad());

                Map<String, Object> envioData = new LinkedHashMap<>();
                envioData.put("idEnvio",   maleta.getIdEnvioBackend());
                envioData.put("cantidad",  maleta.getCantidad());
                envioData.put("cumpleSla", cumpleSla);
                EnvioDiario envOrig = envioPorId.get(maleta.getIdEnvioBackend());
                if (envOrig != null && envOrig.getFechaRegistro() != null) {
                    // fecha_registro está en la hora LOCAL del origen; convertir a UTC real
                    // (UTC = local - GMT_origen) para que el mapa la ubique bien en el tiempo.
                    int gmtOrig = envOrig.getAeropuertoOrigen() != null
                            && envOrig.getAeropuertoOrigen().getGmt() != null
                            ? envOrig.getAeropuertoOrigen().getGmt() : 0;
                    envioData.put("fechaRegistroMs",
                            envOrig.getFechaRegistro().minusHours(gmtOrig).toInstant(ZoneOffset.UTC).toEpochMilli());
                    if (envOrig.getFechaLimiteEntrega() != null) {
                        envioData.put("fechaLimiteMs",
                                envOrig.getFechaLimiteEntrega().minusHours(gmtOrig).toInstant(ZoneOffset.UTC).toEpochMilli());
                    }
                }
                enviosPorVuelo.get(key).add(envioData);
            }
        }

        for (Map.Entry<String, Map<String, Object>> e : vuelosMap.entrySet()) {
            e.getValue().put("envios", enviosPorVuelo.get(e.getKey()));
        }

        // Ids de envíos que SÍ consiguieron ruta (para marcarlos como reflejados y no
        // reprocesarlos). Los no asignados quedan fuera → se reintentan el próximo ciclo.
        java.util.Set<Integer> asignadosIds = new java.util.HashSet<>();
        for (Maleta m : mejorPlan.getAsignaciones().keySet()) {
            if (m.getIdEnvioBackend() != null) asignadosIds.add(m.getIdEnvioBackend());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nuevosVuelos",    new ArrayList<>(vuelosMap.values()));
        result.put("asignados",       mejorPlan.getTotalMaletasAsignadas());
        result.put("noAsignados",     mejorPlan.getMaletasNoAsignadas().size());
        result.put("violacionesSla",  violacionesSla);
        result.put("asignadosIds",    new ArrayList<>(asignadosIds));
        return result;
    }

    /**
     * Lista liviana de pedidos registrados en la ventana dada, para reflejarlos en el
     * almacén de ORIGEN apenas se registran (sin esperar a que se planifiquen).
     * fechaRegistroMs se devuelve en UTC real (fecha_registro está en hora local del origen).
     */
    @Transactional
    public List<Map<String, Object>> pedidosRecientes(LocalDateTime desde, LocalDateTime hasta) {
        return envioDiarioRepository
                .findByFechaRegistroBetweenOrderByFechaRegistroAsc(desde, hasta)
                .stream()
                .map(e -> {
                    int gmt = e.getAeropuertoOrigen() != null && e.getAeropuertoOrigen().getGmt() != null
                            ? e.getAeropuertoOrigen().getGmt() : 0;
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("idEnvio",  e.getIdEnvio());
                    m.put("origen",   e.getAeropuertoOrigen().getCodigoOaci());
                    m.put("destino",  e.getAeropuertoDestino().getCodigoOaci());
                    m.put("cantidad", e.getCantidad() != null ? e.getCantidad() : 0);
                    m.put("fechaRegistroMs", e.getFechaRegistro() != null
                            ? e.getFechaRegistro().minusHours(gmt).toInstant(ZoneOffset.UTC).toEpochMilli()
                            : System.currentTimeMillis());
                    return m;
                })
                .collect(Collectors.toList());
    }

    private Map<String, Object> resultadoVacio() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("nuevosVuelos",     Collections.emptyList());
        r.put("asignados",        0);
        r.put("noAsignados",      0);
        r.put("violacionesSla",   0);
        r.put("asignadosIds",     Collections.emptyList());
        return r;
    }
}
