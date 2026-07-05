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

    private static final int MAX_ITERACIONES      = 5000;
    private static final double REMOCION_MIN      = 0.10;
    private static final double REMOCION_MAX      = 0.40;
    private static final double TEMPERATURA_INI   = 100.0;
    private static final double TASA_ENFRIAMIENTO = 0.999;
    private static final double TASA_REACCION     = 0.3;
    private static final int    PERIODO_ACTUALIZ  = 100;
    /** Presupuesto de cómputo por ventana si el llamador no especifica uno */
    private static final long   PRESUPUESTO_DEFAULT_MS = 45_000;
    /** Iteraciones sin mejora global antes de cortar (estancamiento) */
    private static final int    MAX_SIN_MEJORA    = 300;
    /** Tope de maletas arrastradas entre ventanas (protección de memoria) */
    private static final int    MAX_ARRASTRE      = 20_000;

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

    /** Compatibilidad: procesa la ventana sin arrastre previo y con presupuesto por defecto. */
    @Transactional
    public Map<String, Object> procesarVentanaSC(
            LocalDateTime desde, LocalDateTime hasta, int maxMaletasSC) {
        return procesarVentanaSC(desde, hasta, maxMaletasSC, Collections.emptyList(), PRESUPUESTO_DEFAULT_MS);
    }

    /**
     * Carga la ventana SC [desde, hasta], ejecuta ALNS y devuelve los vuelos
     * con sus asignaciones — SIN persistir nada en la BD.
     *
     * Arrastre: los envíos no asignados de ventanas anteriores (por congestión o
     * por exceder el tope de la ventana) reingresan al pool mientras su SLA siga
     * siendo alcanzable — igual que en la operación real, una maleta que no
     * consiguió vuelo hoy espera en el almacén y compite en la siguiente
     * planificación. El resultado incluye "pendientesIds": los IDs a arrastrar
     * a la próxima ventana (se rehidratan por ID para evitar entidades detached).
     *
     * @param desde         Inicio de la ventana de simulación
     * @param hasta         Fin de la ventana de simulación
     * @param maxMaletasSC  Límite de maletas físicas a procesar en esta ventana
     * @param arrastreIds   IDs de envíos no asignados en ventanas anteriores
     * @param presupuestoMs Presupuesto de cómputo real para el ALNS de esta ventana
     */
    @Transactional
    public Map<String, Object> procesarVentanaSC(
            LocalDateTime desde, LocalDateTime hasta, int maxMaletasSC,
            List<Integer> arrastreIds, long presupuestoMs) {

        List<Integer> pendientesIds = new ArrayList<>();

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

        // Pool = arrastre vigente (SLA aún alcanzable) + nuevos registros de la ventana
        List<EnvioMaletas> candidatos = new ArrayList<>();
        int arrastreEntrante = 0;
        if (arrastreIds != null && !arrastreIds.isEmpty()) {
            List<EnvioMaletas> arrastre = envioMaletasRepository.findAllById(arrastreIds);
            arrastre.sort(Comparator.comparing(EnvioMaletas::getFechaRegistro));
            for (EnvioMaletas e : arrastre) {
                if (e.getFechaLimiteEntrega() != null && e.getFechaLimiteEntrega().isBefore(desde)) {
                    continue; // SLA ya inalcanzable: fallo definitivo, no se reintenta
                }
                candidatos.add(e);
                arrastreEntrante++;
            }
        }
        candidatos.addAll(
                envioMaletasRepository.findByFechaRegistroBetweenOrderByFechaRegistroAsc(desde, hasta));

        // Tope de maletas físicas: el excedente NO se descarta, pasa como arrastre
        List<EnvioMaletas> enviosSC = new ArrayList<>();
        int totalMaletasSC = 0;
        for (EnvioMaletas e : candidatos) {
            int cant = e.getCantidad() != null ? e.getCantidad() : 1;
            if (totalMaletasSC + cant > maxMaletasSC) {
                pendientesIds.add(e.getIdEnvio());
                continue;
            }
            enviosSC.add(e);
            totalMaletasSC += cant;
        }

        if (enviosSC.isEmpty()) {
            log.info("Ventana SC [{}, {}] sin envíos", desde, hasta);
            Map<String, Object> r = resultadoVacio();
            r.put("pendientesIds", acotarArrastre(pendientesIds));
            return r;
        }

        List<Maleta> maletas = enviosSC.stream()
                .map(e -> dataAdapter.convertirEnvio(e, aeropuertosAlns))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (maletas.isEmpty()) {
            return resultadoVacio();
        }

        // ALNS — SIN ESCRIBIR EN BD, con presupuesto de tiempo y parada por estancamiento
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

        log.info("ALNS SC [{}, {}]: {} pedidos → {} asignados, {} no asignados",
                desde, hasta, maletas.size(),
                mejorPlan.getTotalMaletasAsignadas(),
                mejorPlan.getMaletasNoAsignadas().size());

        // Construir respuesta sin escritura en BD
        Map<String, Map<String, Object>> vuelosMap = new LinkedHashMap<>();
        Map<String, List<Map<String, Object>>> enviosPorVuelo = new LinkedHashMap<>();
        int violacionesSla = 0;

        // Índice idEnvio → entidad original (para exponer fecha de registro al tracking)
        Map<Integer, EnvioMaletas> envioPorId = enviosSC.stream()
                .collect(Collectors.toMap(EnvioMaletas::getIdEnvio, e -> e, (a, b) -> a));

        for (Map.Entry<Maleta, Ruta> entry : mejorPlan.getAsignaciones().entrySet()) {
            Maleta maleta = entry.getKey();
            Ruta ruta     = entry.getValue();
            boolean cumpleSla = !maleta.isSLAExpirado(ruta.getHoraLlegadaFinal());
            if (!cumpleSla) violacionesSla++;

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
                EnvioMaletas envOrig = envioPorId.get(maleta.getIdEnvioBackend());
                if (envOrig != null && envOrig.getFechaRegistro() != null) {
                    envioData.put("fechaRegistroMs",
                            envOrig.getFechaRegistro().toInstant(ZoneOffset.UTC).toEpochMilli());
                    if (envOrig.getFechaLimiteEntrega() != null) {
                        envioData.put("fechaLimiteMs",
                                envOrig.getFechaLimiteEntrega().toInstant(ZoneOffset.UTC).toEpochMilli());
                    }
                }
                enviosPorVuelo.get(key).add(envioData);
            }
        }

        for (Map.Entry<String, Map<String, Object>> e : vuelosMap.entrySet()) {
            e.getValue().put("envios", enviosPorVuelo.get(e.getKey()));
        }

        // Arrastre saliente: los no asignados con SLA aún alcanzable se reintentan
        // en la próxima ventana (por ID, para rehidratarlos en esa transacción)
        for (Maleta m : mejorPlan.getMaletasNoAsignadas()) {
            if (m.getIdEnvioBackend() == null) continue;
            EnvioMaletas e = envioPorId.get(m.getIdEnvioBackend());
            if (e != null && e.getFechaLimiteEntrega() != null
                    && e.getFechaLimiteEntrega().isAfter(hasta)) {
                pendientesIds.add(e.getIdEnvio());
            }
        }

        log.info("Ventana SC [{}, {}]: arrastre entrante={} · arrastre saliente={}",
                desde, hasta, arrastreEntrante, pendientesIds.size());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nuevosVuelos",    new ArrayList<>(vuelosMap.values()));
        result.put("asignados",       mejorPlan.getTotalMaletasAsignadas());
        result.put("noAsignados",     mejorPlan.getMaletasNoAsignadas().size());
        result.put("violacionesSla",  violacionesSla);
        result.put("pendientesIds",   acotarArrastre(pendientesIds));
        result.put("arrastreEntrante", arrastreEntrante);
        return result;
    }

    // ──────────────────────────────────────────────────────────────────
    // Chequeo de día para la búsqueda de fecha de colapso
    // ──────────────────────────────────────────────────────────────────

    /** Resultado del chequeo de colapso de un día: maletas sin ruta y fuera de SLA */
    public record ChequeoDia(int sinRuta, int fueraSla) {
        public boolean hayViolacion() { return sinRuta > 0 || fueraSla > 0; }
    }

    /**
     * Evalúa si en un día calendario el planificador deja alguna maleta sin ruta
     * o fuera de SLA (definición de colapso del curso). Primero prueba con la
     * construcción greedy (rápida); solo si esta reporta violaciones ejecuta el
     * ALNS completo para confirmar que ni el mejor plan puede evitarlas.
     */
    @Transactional
    public ChequeoDia chequearDia(java.time.LocalDate dia) {
        LocalDateTime desde = dia.atStartOfDay();
        LocalDateTime hasta = desde.plusDays(1);

        Map<String, Aeropuerto> aeropuertosAlns = dataAdapter.cargarAeropuertos();
        if (aeropuertosAlns.isEmpty()) return new ChequeoDia(0, 0);

        List<EnvioMaletas> envios =
                envioMaletasRepository.findByFechaRegistroBetweenOrderByFechaRegistroAsc(desde, hasta);
        if (envios.isEmpty()) return new ChequeoDia(0, 0);

        List<Maleta> maletas = envios.stream()
                .map(e -> dataAdapter.convertirEnvio(e, aeropuertosAlns))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (maletas.isEmpty()) return new ChequeoDia(0, 0);

        List<com.plantilla.backend.modules.algoritmo.alns.model.Vuelo> vuelos =
                dataAdapter.cargarVuelos(desde, hasta.plusDays(5));
        if (vuelos.isEmpty()) return new ChequeoDia(maletas.size(), 0);

        FlightIndex flightIndex = dataAdapter.construirFlightIndex(vuelos);
        PlanDeRutas plan = SolutionGenerator.generarPlanInicial(maletas, flightIndex, aeropuertosAlns);

        // Confirmación con ALNS solo si el greedy reporta violaciones (evita falsos positivos)
        if (contarViolaciones(plan).hayViolacion() && plan.getTotalMaletasAsignadas() > 0) {
            ALNSEngine engine = new ALNSEngine(
                    MAX_ITERACIONES, REMOCION_MIN, REMOCION_MAX,
                    TEMPERATURA_INI, TASA_ENFRIAMIENTO, TASA_REACCION,
                    PERIODO_ACTUALIZ, flightIndex,
                    15_000, 150);
            plan = engine.ejecutar(plan);
        }
        ChequeoDia chequeo = contarViolaciones(plan);
        if (chequeo.hayViolacion()) {
            logDetalleViolaciones(plan, "chequearDia " + dia);
        }
        return chequeo;
    }

    private ChequeoDia contarViolaciones(PlanDeRutas plan) {
        int fueraSla = 0;
        for (Map.Entry<Maleta, Ruta> e : plan.getAsignaciones().entrySet()) {
            if (e.getKey().isSLAExpirado(e.getValue().getHoraLlegadaFinal())) fueraSla++;
        }
        return new ChequeoDia(plan.getMaletasNoAsignadas().size(), fueraSla);
    }

    /** Diagnóstico: detalla las primeras violaciones encontradas (para auditar si son genuinas) */
    private void logDetalleViolaciones(PlanDeRutas plan, String contexto) {
        int mostradas = 0;
        for (Maleta m : plan.getMaletasNoAsignadas()) {
            if (mostradas++ >= 5) break;
            log.warn("[{}] SIN RUTA: envío {} · {} → {} · registro(min)={} · deadline(min)={} · cant={}",
                    contexto, m.getIdEnvioBackend(), m.getAeropuertoOrigen(), m.getAeropuertoDestino(),
                    m.getFechaCreacionUTC(), m.getSlaLimite(), m.getCantidad());
        }
        for (Map.Entry<Maleta, Ruta> e : plan.getAsignaciones().entrySet()) {
            if (mostradas >= 10) break;
            Maleta m = e.getKey();
            Ruta r = e.getValue();
            if (m.isSLAExpirado(r.getHoraLlegadaFinal())) {
                mostradas++;
                StringBuilder tramos = new StringBuilder();
                r.getVuelos().forEach(v -> tramos.append(v.getId()).append(' '));
                log.warn("[{}] FUERA SLA: envío {} · {} → {} · registro(min)={} · deadline(min)={} · llegada(min)={} · atraso={}min · ruta: {}",
                        contexto, m.getIdEnvioBackend(), m.getAeropuertoOrigen(), m.getAeropuertoDestino(),
                        m.getFechaCreacionUTC(), m.getSlaLimite(), r.getHoraLlegadaFinal(),
                        r.getHoraLlegadaFinal() - m.getSlaLimite(), tramos);
            }
        }
    }

    /** Protección de memoria: si el arrastre crece sin control, se conservan los más antiguos */
    private List<Integer> acotarArrastre(List<Integer> ids) {
        if (ids.size() <= MAX_ARRASTRE) return ids;
        log.warn("Arrastre supera el tope ({} > {}): se truncan los más recientes", ids.size(), MAX_ARRASTRE);
        return new ArrayList<>(ids.subList(0, MAX_ARRASTRE));
    }

    private Map<String, Object> resultadoVacio() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("nuevosVuelos",    Collections.emptyList());
        r.put("asignados",       0);
        r.put("noAsignados",     0);
        r.put("violacionesSla",  0);
        r.put("pendientesIds",   Collections.emptyList());
        r.put("arrastreEntrante", 0);
        return r;
    }
}
