package com.plantilla.backend.modules.simulacion;

import com.plantilla.backend.modules.algoritmo.alns.engine.ALNSEngine;
import com.plantilla.backend.modules.algoritmo.alns.model.Aeropuerto;
import com.plantilla.backend.modules.algoritmo.alns.model.Maleta;
import com.plantilla.backend.modules.algoritmo.alns.model.PlanDeRutas;
import com.plantilla.backend.modules.algoritmo.alns.model.Ruta;
import com.plantilla.backend.modules.algoritmo.alns.util.FlightIndex;
import com.plantilla.backend.modules.algoritmo.alns.util.SolutionGenerator;
import com.plantilla.backend.modules.envio.entity.EnvioMaletas;
import com.plantilla.backend.modules.envio.repository.EnvioMaletasRepository;
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
 * Servicio de simulación puro: ejecuta ALNS sobre ventanas de tiempo (SC)
 * sin escribir nada en la base de datos.
 * Destinado exclusivamente al módulo de simulación en tiempo real vía WebSocket.
 */
@Service
@RequiredArgsConstructor
public class SimulacionPureService {

    private static final Logger log = LoggerFactory.getLogger(SimulacionPureService.class);

    private static final int MAX_ITERACIONES      = 50;
    private static final double REMOCION_MIN      = 0.10;
    private static final double REMOCION_MAX      = 0.40;
    private static final double TEMPERATURA_INI   = 100.0;
    private static final double TASA_ENFRIAMIENTO = 0.995;
    private static final double TASA_REACCION     = 0.3;
    private static final int    PERIODO_ACTUALIZ  = 100;

    private final BackendDataAdapter         dataAdapter;
    private final VueloRepository            vueloRepository;
    private final AeropuertoRepository       aeropuertoRepository;
    private final EnvioMaletasRepository     envioMaletasRepository;

    /**
     * Construye el snapshot inicial: aviones ya en vuelo al momento fechaInicio
     * (sin maletas asignadas aún) y lista de aeropuertos activos.
     */
    @Transactional
    public Map<String, Object> construirSnapshotInicial(LocalDateTime fechaInicio) {
        List<com.plantilla.backend.modules.maestro.entity.Vuelo> enAire =
                vueloRepository.findByHoraSalidaLessThanEqualAndHoraLlegadaGreaterThanAndEstadoNot(
                        fechaInicio, fechaInicio, EstadoVuelo.CANCELADO);

        List<com.plantilla.backend.modules.maestro.entity.Aeropuerto> aeropuertos =
                aeropuertoRepository.findByActivoTrue();

        List<Map<String, Object>> vuelosList = enAire.stream().map(v -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("codigoVuelo", v.getCodigoVuelo());
            m.put("origen",      v.getAeropuertoOrigen().getCodigoOaci());
            m.put("destino",     v.getAeropuertoDestino().getCodigoOaci());
            m.put("horaSalidaMs",  v.getHoraSalida().toInstant(ZoneOffset.UTC).toEpochMilli());
            m.put("horaLlegadaMs", v.getHoraLlegada().toInstant(ZoneOffset.UTC).toEpochMilli());
            m.put("totalMaletas", 0);
            m.put("envios", Collections.emptyList());
            return m;
        }).collect(Collectors.toList());

        List<Map<String, Object>> aeropuertosList = aeropuertos.stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("codigoOaci", a.getCodigoOaci());
            m.put("capacidad",  a.getCapacidad() != null ? a.getCapacidad() : 0);
            return m;
        }).collect(Collectors.toList());

        log.info("Snapshot inicial: {} aviones en vuelo, {} aeropuertos",
                vuelosList.size(), aeropuertosList.size());

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("vuelosEnAire", vuelosList);
        snapshot.put("aeropuertos",  aeropuertosList);
        return snapshot;
    }

    /**
     * Carga la ventana SC [desde, hasta], ejecuta ALNS y devuelve los vuelos
     * con sus asignaciones — SIN persistir nada en la BD.
     *
     * @param desde        Inicio de la ventana de simulación
     * @param hasta        Fin de la ventana de simulación
     * @param maxMaletasSC Límite de maletas físicas a procesar en esta ventana
     */
    @Transactional
    public Map<String, Object> procesarVentanaSC(
            LocalDateTime desde, LocalDateTime hasta, int maxMaletasSC) {

        Map<String, Aeropuerto> aeropuertosAlns = dataAdapter.cargarAeropuertos();
        if (aeropuertosAlns.isEmpty()) {
            return resultadoVacio();
        }

        // Vuelos: ventana extendida 5 días para permitir rutas de largo alcance
        List<com.plantilla.backend.modules.algoritmo.alns.model.Vuelo> vuelos =
                dataAdapter.cargarVuelos(desde, hasta.plusDays(5));
        if (vuelos.isEmpty()) {
            log.info("Ventana SC [{}, {}] sin vuelos disponibles", desde, hasta);
            return resultadoVacio();
        }

        // SC: cargar envíos ordenados y limitar por maletas físicas acumuladas
        List<EnvioMaletas> candidatos =
                envioMaletasRepository.findByFechaRegistroBetweenOrderByFechaRegistroAsc(desde, hasta);

        List<EnvioMaletas> enviosSC = new ArrayList<>();
        int totalMaletasSC = 0;
        for (EnvioMaletas e : candidatos) {
            int cant = e.getCantidad() != null ? e.getCantidad() : 1;
            if (totalMaletasSC + cant > maxMaletasSC) break;
            enviosSC.add(e);
            totalMaletasSC += cant;
        }

        if (enviosSC.isEmpty()) {
            log.info("Ventana SC [{}, {}] sin envíos", desde, hasta);
            return resultadoVacio();
        }

        List<Maleta> maletas = enviosSC.stream()
                .map(e -> dataAdapter.convertirEnvio(e, aeropuertosAlns))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (maletas.isEmpty()) {
            return resultadoVacio();
        }

        // ALNS — SIN ESCRIBIR EN BD
        FlightIndex flightIndex = dataAdapter.construirFlightIndex(vuelos);
        PlanDeRutas planInicial = SolutionGenerator.generarPlanInicial(maletas, flightIndex, aeropuertosAlns);

        PlanDeRutas mejorPlan;
        if (planInicial.getTotalMaletasAsignadas() > 0 && !planInicial.getMaletasNoAsignadas().isEmpty()) {
            ALNSEngine engine = new ALNSEngine(
                    MAX_ITERACIONES, REMOCION_MIN, REMOCION_MAX,
                    TEMPERATURA_INI, TASA_ENFRIAMIENTO, TASA_REACCION,
                    PERIODO_ACTUALIZ, flightIndex);
            mejorPlan = engine.ejecutar(planInicial);
        } else {
            mejorPlan = planInicial;
        }

        log.info("ALNS SC [{}, {}]: {} pedidos → {} asignados, {} no asignados",
                desde, hasta, maletas.size(),
                mejorPlan.getTotalMaletasAsignadas(),
                mejorPlan.getMaletasNoAsignadas().size());

        // Construir respuesta sin escritura en BD
        Map<String, Map<String, Object>> vuelosMap = new LinkedHashMap<>();
        Map<String, List<Map<String, Object>>> enviosPorVuelo = new LinkedHashMap<>();

        for (Map.Entry<Maleta, Ruta> entry : mejorPlan.getAsignaciones().entrySet()) {
            Maleta maleta = entry.getKey();
            Ruta ruta     = entry.getValue();
            boolean cumpleSla = !maleta.isSLAExpirado(ruta.getHoraLlegadaFinal());

            for (com.plantilla.backend.modules.algoritmo.alns.model.Vuelo v : ruta.getVuelos()) {
                String key = v.getId();
                if (!vuelosMap.containsKey(key)) {
                    LocalDateTime salidaUtc  = dataAdapter.toLocalDateTimeUtc(v.getHoraSalida());
                    LocalDateTime llegadaUtc = dataAdapter.toLocalDateTimeUtc(v.getHoraLlegada());
                    Map<String, Object> vd = new LinkedHashMap<>();
                    vd.put("codigoVuelo",  key);
                    vd.put("origen",       v.getOrigen());
                    vd.put("destino",      v.getDestino());
                    vd.put("horaSalidaMs",  salidaUtc.toInstant(ZoneOffset.UTC).toEpochMilli());
                    vd.put("horaLlegadaMs", llegadaUtc.toInstant(ZoneOffset.UTC).toEpochMilli());
                    vd.put("totalMaletas", 0);
                    vuelosMap.put(key, vd);
                    enviosPorVuelo.put(key, new ArrayList<>());
                }
                int prev = (int) vuelosMap.get(key).get("totalMaletas");
                vuelosMap.get(key).put("totalMaletas", prev + maleta.getCantidad());

                Map<String, Object> envioData = new LinkedHashMap<>();
                envioData.put("idEnvio",   maleta.getIdEnvioBackend());
                envioData.put("cantidad",  maleta.getCantidad());
                envioData.put("cumpleSla", cumpleSla);
                enviosPorVuelo.get(key).add(envioData);
            }
        }

        for (Map.Entry<String, Map<String, Object>> e : vuelosMap.entrySet()) {
            e.getValue().put("envios", enviosPorVuelo.get(e.getKey()));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nuevosVuelos", new ArrayList<>(vuelosMap.values()));
        result.put("asignados",    mejorPlan.getTotalMaletasAsignadas());
        result.put("noAsignados",  mejorPlan.getMaletasNoAsignadas().size());
        return result;
    }

    private Map<String, Object> resultadoVacio() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("nuevosVuelos", Collections.emptyList());
        r.put("asignados",    0);
        r.put("noAsignados",  0);
        return r;
    }
}
