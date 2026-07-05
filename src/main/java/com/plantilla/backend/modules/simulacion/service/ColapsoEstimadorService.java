package com.plantilla.backend.modules.simulacion.service;

import com.plantilla.backend.modules.simulacion.SimulacionPureService;
import com.plantilla.backend.modules.simulacion.entity.ColapsoCache;
import com.plantilla.backend.modules.simulacion.repository.ColapsoCacheRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.function.Consumer;

/**
 * Busca la fecha de colapso logístico según la definición del curso:
 * COLAPSO = el primer día en que alguna maleta queda SIN RUTA asignable
 * o alguna maleta llega FUERA DE SU SLA (atrasada), incluso con el mejor
 * plan que el ALNS puede producir.
 *
 * La búsqueda recorre los días con demanda en orden cronológico y evalúa
 * cada uno con el planificador real ({@link SimulacionPureService#chequearDia}):
 * greedy rápido como filtro y ALNS como confirmación. Es costosa, por eso se
 * ejecuta UNA sola vez: el resultado se cachea en memoria y en la tabla
 * colapso_cache (clave de criterio incluida — si el criterio cambia de versión,
 * se recalcula).
 */
@Service
@RequiredArgsConstructor
public class ColapsoEstimadorService {

    private static final Logger log = LoggerFactory.getLogger(ColapsoEstimadorService.class);

    /** Versión del criterio de colapso: si cambia, la caché previa se ignora
     *  (V3 = enrutador con exploración por llegada temprana y preferencia SLA) */
    private static final String CRITERIO = "SLA_O_SIN_RUTA_V3";

    /** Tope de días a examinar (protección: ~2 años de datos) */
    private static final int MAX_DIAS_BUSQUEDA = 800;

    private final JdbcTemplate jdbcTemplate;
    private final ColapsoCacheRepository cacheRepository;
    private final SimulacionPureService simulacionPureService;

    /** Marcador en BD para el resultado "no hay colapso en los datos" */
    private static final String SIN_COLAPSO = "SIN_COLAPSO";

    private volatile LocalDateTime fechaColapsoMemoria;
    private volatile boolean sinColapsoConfirmado = false;

    /** true si ya existe un resultado cacheado con el criterio vigente (incluye "sin colapso") */
    public boolean hayCache() {
        if (fechaColapsoMemoria != null || sinColapsoConfirmado) return true;
        try {
            return cacheRepository.findTopByOrderByIdDesc()
                    .map(c -> CRITERIO.equals(c.getCriterio()))
                    .orElse(false);
        } catch (Exception e) {
            return false;
        }
    }

    public synchronized LocalDateTime obtenerFechaColapso() {
        return obtenerFechaColapso(null);
    }

    /**
     * Devuelve la fecha del primer día con colapso (00:00) o null si el sistema
     * nunca colapsa con los datos actuales.
     *
     * @param progreso callback opcional para reportar avance al cliente
     */
    public synchronized LocalDateTime obtenerFechaColapso(Consumer<String> progreso) {
        // 1. Caché en memoria (incluye el resultado "sin colapso")
        if (fechaColapsoMemoria != null) return fechaColapsoMemoria;
        if (sinColapsoConfirmado) return null;

        // 2. Caché en BD (solo si fue calculada con el criterio vigente)
        var enBd = cacheRepository.findTopByOrderByIdDesc();
        if (enBd.isPresent() && CRITERIO.equals(enBd.get().getCriterio())) {
            if (SIN_COLAPSO.equals(enBd.get().getDetalle())) {
                sinColapsoConfirmado = true;
                log.info("Caché BD: sin colapso en los datos ({})", CRITERIO);
                return null;
            }
            fechaColapsoMemoria = enBd.get().getFechaColapsoEstimada();
            log.info("Fecha de colapso recuperada de caché BD: {} ({})", fechaColapsoMemoria, CRITERIO);
            return fechaColapsoMemoria;
        }

        // 3. Búsqueda cronológica con el planificador real (única vez)
        LocalDate desde = consultarFecha("SELECT MIN(DATE(fecha_registro)) FROM envio_maletas");
        LocalDate hasta = consultarFecha("SELECT MAX(DATE(fecha_registro)) FROM envio_maletas");
        if (desde == null || hasta == null) {
            log.warn("envio_maletas vacío: no se puede buscar colapso");
            return null;
        }

        Long capacidadDiaria = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(capacidad), 0) FROM plan_vuelo_diario", Long.class);

        log.info("Buscando fecha de colapso ({}) entre {} y {}...", CRITERIO, desde, hasta);
        int examinados = 0;
        for (LocalDate dia = desde; !dia.isAfter(hasta) && examinados < MAX_DIAS_BUSQUEDA; dia = dia.plusDays(1)) {
            examinados++;
            if (progreso != null && examinados % 5 == 1) {
                progreso.accept("Evaluando planificación del " + dia + "...");
            }

            SimulacionPureService.ChequeoDia chequeo = simulacionPureService.chequearDia(dia);
            if (chequeo.hayViolacion()) {
                String detalle = chequeo.sinRuta() + " sin ruta · " + chequeo.fueraSla() + " fuera de SLA";
                log.info("Colapso encontrado el {}: {}", dia, detalle);
                guardarCache(dia.atStartOfDay(), capacidadDiaria, detalle);
                return fechaColapsoMemoria;
            }
        }

        log.info("Sin colapso en los datos: el planificador cubre todos los días sin violaciones");
        // Cachear también el "sin colapso": la búsqueda es cara y no debe repetirse
        sinColapsoConfirmado = true;
        try {
            ColapsoCache cache = new ColapsoCache();
            cache.setFechaColapsoEstimada(LocalDateTime.now());
            cache.setFechaCalculo(LocalDateTime.now());
            cache.setCapacidadDiaria(capacidadDiaria);
            cache.setCriterio(CRITERIO);
            cache.setDetalle(SIN_COLAPSO);
            cacheRepository.save(cache);
        } catch (Exception e) {
            log.warn("No se pudo persistir 'sin colapso' (queda en memoria): {}", e.getMessage());
        }
        return null;
    }

    private void guardarCache(LocalDateTime fecha, Long capacidadDiaria, String detalle) {
        fechaColapsoMemoria = fecha;
        try {
            ColapsoCache cache = new ColapsoCache();
            cache.setFechaColapsoEstimada(fecha);
            cache.setFechaCalculo(LocalDateTime.now());
            cache.setCapacidadDiaria(capacidadDiaria);
            cache.setCriterio(CRITERIO);
            cache.setDetalle(detalle);
            cacheRepository.save(cache);
        } catch (Exception e) {
            log.warn("No se pudo persistir la caché de colapso (queda en memoria): {}", e.getMessage());
        }
    }

    private LocalDate consultarFecha(String sql) {
        try {
            java.sql.Date d = jdbcTemplate.queryForObject(sql, java.sql.Date.class);
            return d != null ? d.toLocalDate() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
