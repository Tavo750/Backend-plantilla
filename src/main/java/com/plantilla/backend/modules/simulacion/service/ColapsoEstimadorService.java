package com.plantilla.backend.modules.simulacion.service;

import com.plantilla.backend.modules.simulacion.entity.ColapsoCache;
import com.plantilla.backend.modules.simulacion.repository.ColapsoCacheRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Estima la fecha de colapso logístico: primer día en que la demanda de maletas
 * registradas supera la capacidad diaria total de la flota (plan_vuelo_diario).
 *
 * La búsqueda pesada se ejecuta UNA sola vez: el resultado se cachea en memoria
 * y en la tabla colapso_cache (sobrevive reinicios del backend).
 */
@Service
@RequiredArgsConstructor
public class ColapsoEstimadorService {

    private static final Logger log = LoggerFactory.getLogger(ColapsoEstimadorService.class);

    private final JdbcTemplate jdbcTemplate;
    private final ColapsoCacheRepository cacheRepository;

    private volatile LocalDateTime fechaColapsoMemoria;

    /** true si ya existe un resultado cacheado (memoria o BD): la búsqueda será instantánea */
    public boolean hayCache() {
        if (fechaColapsoMemoria != null) return true;
        try {
            return cacheRepository.count() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Devuelve la fecha estimada de colapso (00:00 del día en que la demanda supera
     * la capacidad de la flota) o null si nunca colapsa según los datos.
     */
    public synchronized LocalDateTime obtenerFechaColapso() {
        // 1. Caché en memoria
        if (fechaColapsoMemoria != null) return fechaColapsoMemoria;

        // 2. Caché en BD
        var enBd = cacheRepository.findTopByOrderByIdDesc();
        if (enBd.isPresent()) {
            fechaColapsoMemoria = enBd.get().getFechaColapsoEstimada();
            log.info("Fecha de colapso recuperada de caché BD: {}", fechaColapsoMemoria);
            return fechaColapsoMemoria;
        }

        // 3. Calcular (única vez) y persistir
        Resultado calc = calcular();
        if (calc == null) return null;

        ColapsoCache cache = new ColapsoCache();
        cache.setFechaColapsoEstimada(calc.fecha());
        cache.setFechaCalculo(LocalDateTime.now());
        cache.setCapacidadDiaria(calc.capacidadDiaria());
        cache.setDemandaDiaColapso(calc.demanda());
        try {
            cacheRepository.save(cache);
        } catch (Exception e) {
            log.warn("No se pudo persistir la caché de colapso (se mantiene en memoria): {}", e.getMessage());
        }
        fechaColapsoMemoria = calc.fecha();
        log.info("Fecha de colapso calculada: {} (demanda {} > capacidad diaria {})",
                calc.fecha(), calc.demanda(), calc.capacidadDiaria());
        return fechaColapsoMemoria;
    }

    private record Resultado(LocalDateTime fecha, long capacidadDiaria, long demanda) {}

    /**
     * Criterio de estimación (dos niveles):
     *  1. Si algún día la demanda agregada supera la capacidad agregada de la flota,
     *     ese es el colapso teórico garantizado.
     *  2. Si no (caso normal: la capacidad agregada es holgada pero el colapso real
     *     lo causan las restricciones locales — capacidad por vuelo, rutas, SLA —),
     *     la fecha candidata es el DÍA DE MÁXIMA DEMANDA: el punto de mayor estrés
     *     del sistema. El veredicto final lo da la simulación ALNS de confirmación
     *     que corre a continuación (COLAPSO_DETECTADO si supera el umbral de
     *     maletas sin asignar, o FIN sin colapso).
     */
    private Resultado calcular() {
        Long capacidadDiaria = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(capacidad), 0) FROM plan_vuelo_diario", Long.class);
        if (capacidadDiaria == null || capacidadDiaria == 0) {
            log.warn("plan_vuelo_diario sin capacidad: no se puede estimar colapso");
            return null;
        }

        List<Map<String, Object>> demandaPorDia = jdbcTemplate.queryForList(
                "SELECT DATE(fecha_registro) AS dia, SUM(cantidad) AS demanda " +
                "FROM envio_maletas GROUP BY DATE(fecha_registro) ORDER BY dia");
        if (demandaPorDia.isEmpty()) {
            log.warn("envio_maletas vacío: no se puede estimar colapso");
            return null;
        }

        Map<String, Object> pico = null;
        long demandaPico = -1;
        for (Map<String, Object> fila : demandaPorDia) {
            long demanda = ((Number) fila.get("demanda")).longValue();
            // Nivel 1: saturación agregada (colapso teórico garantizado)
            if (demanda > capacidadDiaria) {
                return new Resultado(aFecha(fila.get("dia")).atStartOfDay(), capacidadDiaria, demanda);
            }
            // Nivel 2: registrar el pico de demanda
            if (demanda > demandaPico) {
                demandaPico = demanda;
                pico = fila;
            }
        }

        log.info("Sin saturación agregada (capacidad {}): usando pico de demanda {} como candidato",
                capacidadDiaria, demandaPico);
        return new Resultado(aFecha(pico.get("dia")).atStartOfDay(), capacidadDiaria, demandaPico);
    }

    private LocalDate aFecha(Object dia) {
        return (dia instanceof java.sql.Date sqlDate)
                ? sqlDate.toLocalDate()
                : LocalDate.parse(dia.toString());
    }
}
