package com.plantilla.backend.modules.simulacion.alns;

import com.plantilla.backend.modules.algoritmo.alns.engine.ALNSEngine;
import com.plantilla.backend.modules.algoritmo.alns.model.Aeropuerto;
import com.plantilla.backend.modules.algoritmo.alns.model.Maleta;
import com.plantilla.backend.modules.algoritmo.alns.model.PlanDeRutas;
import com.plantilla.backend.modules.algoritmo.alns.model.Ruta;
import com.plantilla.backend.modules.algoritmo.alns.util.CostCalculator;
import com.plantilla.backend.modules.algoritmo.alns.util.FlightIndex;
import com.plantilla.backend.modules.algoritmo.alns.util.SolutionGenerator;
import com.plantilla.backend.modules.envio.entity.EnvioMaletas;
import com.plantilla.backend.modules.envio.entity.ParametroSemaforo;
import com.plantilla.backend.modules.envio.repository.EnvioMaletasRepository;
import com.plantilla.backend.modules.envio.repository.ParametroSemaforoRepository;
import com.plantilla.backend.modules.maestro.entity.PoliticaEntrega;
import com.plantilla.backend.modules.maestro.repository.AeropuertoRepository;
import com.plantilla.backend.modules.maestro.repository.PoliticaEntregaRepository;
import com.plantilla.backend.modules.maestro.repository.VueloRepository;
import com.plantilla.backend.modules.planificacion.entity.AsignacionVuelo;
import com.plantilla.backend.modules.planificacion.entity.PlanRuta;
import com.plantilla.backend.modules.planificacion.entity.TramoRuta;
import com.plantilla.backend.modules.planificacion.repository.AsignacionVueloRepository;
import com.plantilla.backend.modules.planificacion.repository.PlanRutaRepository;
import com.plantilla.backend.modules.planificacion.repository.TramoRutaRepository;
import com.plantilla.backend.modules.simulacion.entity.ConfiguracionSimulacion;
import com.plantilla.backend.modules.simulacion.entity.ResultadoSimulacion;
import com.plantilla.backend.modules.simulacion.repository.ConfiguracionSimulacionRepository;
import com.plantilla.backend.modules.simulacion.repository.ResultadoSimulacionRepository;
import com.plantilla.backend.shared.enums.NivelSemaforo;
import com.plantilla.backend.shared.enums.TipoAlgoritmo;
import com.plantilla.backend.shared.enums.TipoEscenario;
import com.plantilla.backend.BackendApplication;
import com.plantilla.backend.shared.errors.BusinessException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Consumer;

/**
 * Servicio principal que orquesta la simulación con ALNS sobre los datos del backend.
 *
 * Flujo:
 *  1. Validar prerrequisitos (existencia de vuelos en rango).
 *  2. Cargar aeropuertos, vuelos y envíos desde la BD.
 *  3. Ejecutar ALNS día por día (modo periodo).
 *  4. Persistir ConfiguracionSimulacion + ResultadoSimulacion + PlanRuta + TramoRuta + AsignacionVuelo.
 *  5. Devolver un resumen JSON con asignaciones, costos y métricas.
 */
@Service
@RequiredArgsConstructor
public class AlnsSimulacionService {

    private static final Logger log = LoggerFactory.getLogger(AlnsSimulacionService.class);

    // === Parámetros ALNS (configurables vía constantes; coincidir con Main.java original) ===
    private static final int MAX_ITERACIONES = 50;
    private static final double PORCENTAJE_REMOCION_MIN = 0.10;
    private static final double PORCENTAJE_REMOCION_MAX = 0.40;
    private static final double TEMPERATURA_INICIAL = 100.0;
    private static final double TASA_ENFRIAMIENTO = 0.995;
    private static final double TASA_REACCION = 0.3;
    private static final int PERIODO_ACTUALIZACION = 100;

    private final BackendDataAdapter dataAdapter;
    private final AeropuertoRepository aeropuertoRepository;
    private final VueloRepository vueloRepository;
    private final EnvioMaletasRepository envioMaletasRepository;
    private final PoliticaEntregaRepository politicaEntregaRepository;
    private final ParametroSemaforoRepository parametroSemaforoRepository;
    private final ConfiguracionSimulacionRepository configuracionSimulacionRepository;
    private final ResultadoSimulacionRepository resultadoSimulacionRepository;
    private final PlanRutaRepository planRutaRepository;
    private final TramoRutaRepository tramoRutaRepository;
    private final AsignacionVueloRepository asignacionVueloRepository;

    @Transactional
    public Map<String, Object> simularPeriodo(LocalDate fechaInicio, int dias) {
        return simularPeriodoCore(fechaInicio, dias, null);
    }

    /**
     * Versión con callback por día para streaming SSE.
     * El callback recibe los datos de cada día conforme se va procesando.
     */
    @Transactional
    public Map<String, Object> simularPeriodoConCallback(
            LocalDate fechaInicio, int dias,
            Consumer<Map<String, Object>> diaCallback) {
        return simularPeriodoCore(fechaInicio, dias, diaCallback);
    }

    private Map<String, Object> simularPeriodoCore(
            LocalDate fechaInicio, int dias,
            Consumer<Map<String, Object>> diaCallback) {
        if (dias <= 0) {
            throw new BusinessException("La cantidad de días debe ser mayor a 0.");
        }

        long inicioGlobalMs = System.currentTimeMillis();

        LocalDateTime inicioRangoUtc = fechaInicio.atStartOfDay();
        LocalDateTime finRangoUtc = fechaInicio.plusDays(dias).atStartOfDay();
        // Para el SLA intercontinental hace falta ventana extra de vuelos
        LocalDateTime finRangoVuelosUtc = finRangoUtc.plusDays(2);

        // ── 1. Cargar aeropuertos ──
        Map<String, Aeropuerto> aeropuertos = dataAdapter.cargarAeropuertos();
        if (aeropuertos.isEmpty()) {
            throw new BusinessException("No hay aeropuertos activos en la BD. Importe el archivo aeropuertos primero.");
        }

        // ── 2. Cargar vuelos y validar ──
        List<com.plantilla.backend.modules.algoritmo.alns.model.Vuelo> vuelos =
                dataAdapter.cargarVuelos(inicioRangoUtc, finRangoVuelosUtc);

        if (vuelos.isEmpty()) {
            throw new BusinessException(String.format(
                    "No hay vuelos en el rango %s a %s. Importe planes_vuelo.txt para este periodo " +
                    "usando POST /vuelos/importar antes de simular.",
                    inicioRangoUtc, finRangoVuelosUtc));
        }

        // ── 3. Cargar envíos ──
        List<Maleta> envios = dataAdapter.cargarEnvios(inicioRangoUtc, finRangoUtc, aeropuertos);

        if (envios.isEmpty()) {
            throw new BusinessException(String.format(
                    "No hay envíos en el rango %s a %s. No se puede ejecutar la simulación.",
                    inicioRangoUtc, finRangoUtc));
        }

        log.info("Simulación ALNS iniciada | fecha={} | días={} | aeropuertos={} | vuelos={} | envíos={}",
                fechaInicio, dias, aeropuertos.size(), vuelos.size(), envios.size());

        if (!BackendApplication.GUARDAR_EN_BD) {
            log.warn("[GUARDAR_EN_BD=false] Los resultados de esta simulación NO se guardarán en la BD");
        }

        // ── 4. Persistir configuración (auditoría) ──
        ConfiguracionSimulacion configuracion = guardarConfiguracion(fechaInicio, dias);

        // ── 5. Ejecutar ALNS por día ──
        FlightIndex flightIndexCompleto = dataAdapter.construirFlightIndex(vuelos);

        Map<Integer, List<Maleta>> enviosPorDia = agruparPorDia(envios);
        int diaInicio = enviosPorDia.keySet().stream().min(Integer::compareTo).orElse(0);
        int diaFin = enviosPorDia.keySet().stream().max(Integer::compareTo).orElse(0);

        List<Maleta> enviosArrastre = new ArrayList<>();
        ResumenAcumulado resumen = new ResumenAcumulado();
        List<Map<String, Object>> eventos = new ArrayList<>();

        Map<Integer, com.plantilla.backend.modules.maestro.entity.Vuelo> vuelosBackendMap =
                indexarVuelosBackendPorId(vuelos);
        Map<Integer, EnvioMaletas> enviosBackendMap = indexarEnviosBackendPorId(envios);

        PoliticaEntrega politicaEntrega = politicaEntregaRepository.findByActivaTrue()
                .orElseGet(() -> politicaEntregaRepository.findAll().stream().findFirst().orElse(null));
        ParametroSemaforo parametroSemaforo = parametroSemaforoRepository
                .findByEntidadAndActivoTrue("ASIGNACION_VUELO").stream().findFirst()
                .orElseGet(() -> parametroSemaforoRepository.findAll().stream().findFirst().orElse(null));

        for (int dia = diaInicio; dia <= diaFin; dia++) {
            List<Maleta> enviosDelDia = enviosPorDia.getOrDefault(dia, Collections.emptyList());

            List<Maleta> enviosAProcesar = new ArrayList<>(enviosArrastre);
            enviosAProcesar.addAll(enviosDelDia);
            enviosArrastre.clear();

            if (enviosAProcesar.isEmpty()) {
                continue;
            }

            log.info("Día {} | a procesar: {} envíos ({} arrastre)",
                    dia, enviosAProcesar.size(), enviosAProcesar.size() - enviosDelDia.size());

            long inicioDiaMs = System.currentTimeMillis();

            // Solución inicial
            PlanDeRutas planInicial = SolutionGenerator.generarPlanInicial(
                    enviosAProcesar, flightIndexCompleto, aeropuertos);
            double costoInicial = CostCalculator.calcularCosto(planInicial);

            // ALNS — solo si hay envíos sin asignar que mejorar
            PlanDeRutas mejorPlan;
            boolean hayNoAsignados = !planInicial.getMaletasNoAsignadas().isEmpty();
            if (planInicial.getTotalMaletasAsignadas() > 0 && hayNoAsignados) {
                ALNSEngine engine = new ALNSEngine(
                        MAX_ITERACIONES,
                        PORCENTAJE_REMOCION_MIN,
                        PORCENTAJE_REMOCION_MAX,
                        TEMPERATURA_INICIAL,
                        TASA_ENFRIAMIENTO,
                        TASA_REACCION,
                        PERIODO_ACTUALIZACION,
                        flightIndexCompleto
                );
                mejorPlan = engine.ejecutar(planInicial);
            } else {
                // Solución inicial ya asignó todo (o no hay nada asignable) → no hay ganancia en correr ALNS
                mejorPlan = planInicial;
            }

            long finDiaMs = System.currentTimeMillis();
            int tiempoComputoMs = (int) (finDiaMs - inicioDiaMs);

            double costoFinal = CostCalculator.calcularCosto(mejorPlan);
            log.info("Día {} | inicial {} asignados (costo {}) → final {} asignados (costo {}) | {} ms",
                    dia, planInicial.getTotalMaletasAsignadas(), String.format("%.2f", costoInicial),
                    mejorPlan.getTotalMaletasAsignadas(), String.format("%.2f", costoFinal),
                    tiempoComputoMs);

            // Capturar índice antes de persistir para saber los eventos de este día
            int prevEventosSize = eventos.size();

            // Persistir y construir eventos para este día
            int violacionesSLADia = persistirYRegistrarPlan(
                    mejorPlan, configuracion, politicaEntrega, parametroSemaforo,
                    vuelosBackendMap, enviosBackendMap, eventos, tiempoComputoMs);

            // Acumular resumen
            resumen.totalEnviosProcesados += enviosAProcesar.size();
            resumen.totalEnviosAsignados += mejorPlan.getTotalMaletasAsignadas();
            resumen.totalEnviosNoAsignados += mejorPlan.getMaletasNoAsignadas().size();
            resumen.totalMaletasFisicasAsignadas += mejorPlan.getTotalMaletasFisicasAsignadas();
            resumen.totalMaletasFisicas += mejorPlan.getTotalMaletasFisicas();
            resumen.costoAcumulado += costoFinal;
            resumen.violacionesSLA += violacionesSLADia;
            resumen.diasProcesados++;

            // Emitir callback SSE con los datos de este día
            if (diaCallback != null) {
                List<Map<String, Object>> eventosDelDia =
                        new ArrayList<>(eventos.subList(prevEventosSize, eventos.size()));
                Map<String, Object> diaData = new LinkedHashMap<>();
                diaData.put("dia", resumen.diasProcesados);
                diaData.put("fecha", fechaInicio.plusDays(resumen.diasProcesados - 1).toString());
                diaData.put("eventos", eventosDelDia);
                Map<String, Object> stats = new LinkedHashMap<>();
                stats.put("enviosAProcesar", enviosAProcesar.size());
                stats.put("asignados", mejorPlan.getTotalMaletasAsignadas());
                stats.put("noAsignados", mejorPlan.getMaletasNoAsignadas().size());
                stats.put("violacionesSLA", violacionesSLADia);
                diaData.put("estadisticas", stats);
                try {
                    diaCallback.accept(diaData);
                } catch (Exception e) {
                    log.warn("Error emitiendo evento SSE para día {}: {}", resumen.diasProcesados, e.getMessage());
                }
            }

            // Envíos no asignados pasan al día siguiente
            enviosArrastre.addAll(mejorPlan.getMaletasNoAsignadas());
        }

        long tiempoTotalMs = System.currentTimeMillis() - inicioGlobalMs;

        // ── 6. Persistir resultado consolidado ──
        ResultadoSimulacion resultado = guardarResultado(
                configuracion, resumen, enviosArrastre.size(), tiempoTotalMs);

        // ── 7. Construir respuesta JSON ──
        return construirRespuesta(fechaInicio, dias, configuracion, resultado,
                resumen, enviosArrastre.size(), tiempoTotalMs, eventos);
    }

    // ──────────────────────────────────────────────────────────────────
    // Persistencia
    // ──────────────────────────────────────────────────────────────────

    private ConfiguracionSimulacion guardarConfiguracion(LocalDate fechaInicio, int dias) {
        ConfiguracionSimulacion cfg = new ConfiguracionSimulacion();
        cfg.setTipoEscenario(TipoEscenario.SIMULACION_PERIODO);
        cfg.setTipoAlgoritmo(TipoAlgoritmo.ALNS);
        cfg.setFechaInicio(fechaInicio.atStartOfDay());
        cfg.setDiasPeriodo(dias);
        cfg.setDescripcion(String.format("Simulación ALNS de %d días desde %s", dias, fechaInicio));

        if (!BackendApplication.GUARDAR_EN_BD) {
            log.info("[GUARDAR_EN_BD=false] ConfiguracionSimulacion NO persistida en la BD");
            return cfg;
        }

        PoliticaEntrega politica = politicaEntregaRepository.findByActivaTrue()
                .or(() -> politicaEntregaRepository.findAll().stream().findFirst())
                .orElseThrow(() -> new BusinessException(
                        "No existe ninguna PoliticaEntrega configurada. Inserte al menos una antes de simular."));
        cfg.setPoliticaEntrega(politica);

        return configuracionSimulacionRepository.save(cfg);
    }

    /**
     * Persiste el plan del día como PlanRuta + TramoRuta + AsignacionVuelo
     * y construye los eventos JSON. Devuelve el número de violaciones SLA en este día.
     */
    private int persistirYRegistrarPlan(
            PlanDeRutas mejorPlan,
            ConfiguracionSimulacion configuracion,
            PoliticaEntrega politicaEntrega,
            ParametroSemaforo parametroSemaforo,
            Map<Integer, com.plantilla.backend.modules.maestro.entity.Vuelo> vuelosBackendMap,
            Map<Integer, EnvioMaletas> enviosBackendMap,
            List<Map<String, Object>> eventos,
            int tiempoComputoMs) {

        int violaciones = 0;

        // Envíos asignados
        for (Map.Entry<Maleta, Ruta> asignacion : mejorPlan.getAsignaciones().entrySet()) {
            Maleta maleta = asignacion.getKey();
            Ruta ruta = asignacion.getValue();

            EnvioMaletas envioBackend = enviosBackendMap.get(maleta.getIdEnvioBackend());
            if (envioBackend == null) {
                log.warn("Envío {} no encontrado en cache JPA — se omite persistencia",
                        maleta.getIdEnvioBackend());
                continue;
            }

            boolean slaCumplido = !maleta.isSLAExpirado(ruta.getHoraLlegadaFinal());
            if (!slaCumplido) violaciones++;

            // PlanRuta
            PlanRuta planRuta = new PlanRuta();
            planRuta.setEnvioMaletas(envioBackend);
            planRuta.setTipoAlgoritmo(TipoAlgoritmo.ALNS);
            planRuta.setFechaCreacion(LocalDateTime.now());
            planRuta.setFechaLimite(dataAdapter.toLocalDateTimeUtc(maleta.getSlaLimite()));
            planRuta.setEsFactible(true);
            planRuta.setCumpleSla(slaCumplido);
            planRuta.setEsVigente(true);
            planRuta.setTiempoComputoMs(tiempoComputoMs);
            if (BackendApplication.GUARDAR_EN_BD) {
                planRuta = planRutaRepository.save(planRuta);
            }

            // TramoRuta + AsignacionVuelo por cada vuelo de la ruta
            List<Map<String, Object>> tramosEvento = new ArrayList<>();
            int orden = 1;
            for (com.plantilla.backend.modules.algoritmo.alns.model.Vuelo vAlns : ruta.getVuelos()) {
                com.plantilla.backend.modules.maestro.entity.Vuelo vuelo =
                        vuelosBackendMap.get(vAlns.getIdVueloBackend());
                if (vuelo == null) {
                    log.warn("Vuelo {} no encontrado en cache JPA — tramo omitido", vAlns.getId());
                    continue;
                }

                TramoRuta tramo = new TramoRuta();
                tramo.setPlanRuta(planRuta);
                tramo.setVuelo(vuelo);
                tramo.setAeropuertoSalida(vuelo.getAeropuertoOrigen());
                tramo.setAeropuertoLlegada(vuelo.getAeropuertoDestino());
                tramo.setOrden(orden);
                tramo.setSalidaProgramada(vuelo.getHoraSalida());
                tramo.setLlegadaProgramada(vuelo.getHoraLlegada());
                tramo.setCantidadAsignada(maleta.getCantidad());
                tramo.setHolguraHoras(calcularHolguraHoras(maleta, ruta));
                if (BackendApplication.GUARDAR_EN_BD) {
                    tramoRutaRepository.save(tramo);
                }

                if (parametroSemaforo != null) {
                    AsignacionVuelo asig = new AsignacionVuelo();
                    asig.setVuelo(vuelo);
                    asig.setEnvioMaletas(envioBackend);
                    asig.setPlanRuta(planRuta);
                    asig.setParametroSemaforo(parametroSemaforo);
                    asig.setCantidadAsignada(maleta.getCantidad());
                    asig.setNivelSemaforo(NivelSemaforo.VERDE);
                    asig.setEstadoAsignacion("ACTIVA");
                    if (BackendApplication.GUARDAR_EN_BD) {
                        asignacionVueloRepository.save(asig);
                    }
                }

                Map<String, Object> tramoEvento = new LinkedHashMap<>();
                tramoEvento.put("orden", orden);
                tramoEvento.put("codigoVuelo", vuelo.getCodigoVuelo());
                tramoEvento.put("origen", vuelo.getAeropuertoOrigen().getCodigoOaci());
                tramoEvento.put("destino", vuelo.getAeropuertoDestino().getCodigoOaci());
                tramoEvento.put("horaSalida", vuelo.getHoraSalida());
                tramoEvento.put("horaLlegada", vuelo.getHoraLlegada());
                tramosEvento.add(tramoEvento);
                orden++;
            }

            Map<String, Object> evento = new LinkedHashMap<>();
            evento.put("idEnvio", envioBackend.getIdEnvio());
            evento.put("origen", maleta.getAeropuertoOrigen());
            evento.put("destino", maleta.getAeropuertoDestino());
            evento.put("cantidad", maleta.getCantidad());
            evento.put("prioridad", maleta.getPrioridad());
            evento.put("estado", "ASIGNADO");
            evento.put("cumpleSla", slaCumplido);
            evento.put("idPlanRuta", planRuta.getIdPlanRuta());
            evento.put("tramos", tramosEvento);
            eventos.add(evento);
        }

        // Envíos no asignados (para el día actual; los arrastrados se intentan al día siguiente)
        for (Maleta maleta : mejorPlan.getMaletasNoAsignadas()) {
            EnvioMaletas envioBackend = enviosBackendMap.get(maleta.getIdEnvioBackend());
            Integer idEnvio = envioBackend != null ? envioBackend.getIdEnvio() : null;

            Map<String, Object> evento = new LinkedHashMap<>();
            evento.put("idEnvio", idEnvio);
            evento.put("origen", maleta.getAeropuertoOrigen());
            evento.put("destino", maleta.getAeropuertoDestino());
            evento.put("cantidad", maleta.getCantidad());
            evento.put("prioridad", maleta.getPrioridad());
            evento.put("estado", "NO_ASIGNADO_ARRASTRE");
            evento.put("motivo", "Sin ruta factible dentro del SLA en este día; se intenta al día siguiente");
            eventos.add(evento);
        }

        return violaciones;
    }

    private ResultadoSimulacion guardarResultado(ConfiguracionSimulacion configuracion,
                                                  ResumenAcumulado resumen,
                                                  int noAsignadosFinales,
                                                  long tiempoTotalMs) {
        ResultadoSimulacion resultado = new ResultadoSimulacion();
        resultado.setConfiguracionSimulacion(configuracion);
        resultado.setTotalEntregadas(resumen.totalEnviosAsignados);
        resultado.setTotalRetrasadas(resumen.violacionesSLA);
        resultado.setTotalEnTransito(0);
        resultado.setTotalEnEspera(noAsignadosFinales);

        if (resumen.totalEnviosProcesados > 0) {
            BigDecimal tasa = BigDecimal.valueOf(resumen.totalEnviosAsignados - resumen.violacionesSLA)
                    .divide(BigDecimal.valueOf(resumen.totalEnviosProcesados), 4, RoundingMode.HALF_UP);
            resultado.setTasaCumplimientoSla(tasa);
        }

        resultado.setTiempoEjecucionMin((int) Math.max(0, tiempoTotalMs / 60_000));
        resultado.setFechaGeneracion(LocalDateTime.now());

        if (!BackendApplication.GUARDAR_EN_BD) {
            log.info("[GUARDAR_EN_BD=false] ResultadoSimulacion NO persistido en la BD");
            return resultado;
        }
        return resultadoSimulacionRepository.save(resultado);
    }

    // ──────────────────────────────────────────────────────────────────
    // Helpers internos
    // ──────────────────────────────────────────────────────────────────

    private Map<Integer, List<Maleta>> agruparPorDia(List<Maleta> envios) {
        Map<Integer, List<Maleta>> porDia = new TreeMap<>();
        for (Maleta m : envios) {
            int dayIndex = (int) (m.getFechaCreacionUTC() / 1440);
            porDia.computeIfAbsent(dayIndex, k -> new ArrayList<>()).add(m);
        }
        return porDia;
    }

    private Map<Integer, com.plantilla.backend.modules.maestro.entity.Vuelo> indexarVuelosBackendPorId(
            List<com.plantilla.backend.modules.algoritmo.alns.model.Vuelo> vuelosAlns) {
        List<Integer> ids = new ArrayList<>(vuelosAlns.size());
        for (com.plantilla.backend.modules.algoritmo.alns.model.Vuelo v : vuelosAlns) {
            if (v.getIdVueloBackend() != null) ids.add(v.getIdVueloBackend());
        }
        Map<Integer, com.plantilla.backend.modules.maestro.entity.Vuelo> mapa = new HashMap<>(ids.size());
        if (!ids.isEmpty()) {
            for (com.plantilla.backend.modules.maestro.entity.Vuelo v : vueloRepository.findAllById(ids)) {
                mapa.put(v.getIdVuelo(), v);
            }
        }
        return mapa;
    }

    private Map<Integer, EnvioMaletas> indexarEnviosBackendPorId(List<Maleta> envios) {
        List<Integer> ids = new ArrayList<>();
        for (Maleta m : envios) {
            if (m.getIdEnvioBackend() != null) ids.add(m.getIdEnvioBackend());
        }
        Map<Integer, EnvioMaletas> mapa = new HashMap<>(ids.size());
        if (!ids.isEmpty()) {
            for (EnvioMaletas e : envioMaletasRepository.findAllById(ids)) {
                mapa.put(e.getIdEnvio(), e);
            }
        }
        return mapa;
    }

    private BigDecimal calcularHolguraHoras(Maleta maleta, Ruta ruta) {
        long holguraMin = maleta.getHolguraSLA(ruta.getHoraLlegadaFinal());
        return BigDecimal.valueOf(holguraMin)
                .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }

    private Map<String, Object> construirRespuesta(LocalDate fechaInicio, int dias,
                                                    ConfiguracionSimulacion configuracion,
                                                    ResultadoSimulacion resultado,
                                                    ResumenAcumulado resumen,
                                                    int noAsignadosFinales,
                                                    long tiempoTotalMs,
                                                    List<Map<String, Object>> eventos) {
        Map<String, Object> resumenJson = new LinkedHashMap<>();
        resumenJson.put("idConfiguracion", configuracion.getIdConfiguracion());
        resumenJson.put("idResultado", resultado.getIdResultado());
        resumenJson.put("fechaInicio", fechaInicio.toString());
        resumenJson.put("dias", dias);
        resumenJson.put("diasProcesados", resumen.diasProcesados);
        resumenJson.put("totalEnviosProcesados", resumen.totalEnviosProcesados);
        resumenJson.put("enviosAsignados", resumen.totalEnviosAsignados);
        resumenJson.put("enviosNoAsignadosFinales", noAsignadosFinales);
        resumenJson.put("totalMaletasFisicas", resumen.totalMaletasFisicas);
        resumenJson.put("maletasFisicasAsignadas", resumen.totalMaletasFisicasAsignadas);
        resumenJson.put("violacionesSLA", resumen.violacionesSLA);
        resumenJson.put("costoAcumulado", round2(resumen.costoAcumulado));
        resumenJson.put("tasaAsignacion", resumen.totalEnviosProcesados > 0
                ? round4((double) resumen.totalEnviosAsignados / resumen.totalEnviosProcesados)
                : 0);
        resumenJson.put("tiempoTotalMs", tiempoTotalMs);

        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("resumen", resumenJson);
        respuesta.put("eventos", eventos);
        return respuesta;
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }

    /**
     * Estructura mutable para acumular resumen a lo largo de los días.
     */
    private static class ResumenAcumulado {
        int totalEnviosProcesados = 0;
        int totalEnviosAsignados = 0;
        int totalEnviosNoAsignados = 0;
        int totalMaletasFisicasAsignadas = 0;
        int totalMaletasFisicas = 0;
        double costoAcumulado = 0;
        int violacionesSLA = 0;
        int diasProcesados = 0;
    }
}
