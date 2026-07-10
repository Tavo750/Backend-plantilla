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

    /** Versión del criterio de colapso: si cambia, la caché previa se ignora.
     *  V6 = simulación CONTINUA con ocupación de almacenes; colapso = primer
     *  aeropuerto que llega a su capacidad (ya no puede recibir más pedidos);
     *  ALNS como solver. */
    private static final String CRITERIO = "COLAPSO_ALMACEN_V6";

    /** Arranque de la simulación continua. 2026 está provadamente por debajo del
     *  tope de cualquier almacén (pico de demanda 18,915/día → hubs chicos ~65%
     *  de capacidad), y enero 2027 (~13k/día) también. Arrancar en 2027-01-01 da
     *  warm-up amplio y captura el PRIMER cruce real sin regresión de fecha. */
    private static final LocalDate INICIO_SIMULACION = LocalDate.of(2027, 1, 1);

    private final JdbcTemplate jdbcTemplate;
    private final ColapsoCacheRepository cacheRepository;
    private final SimulacionPureService simulacionPureService;
    private final ColapsoContinuoService colapsoContinuoService;

    /** Marcador en BD para el resultado "no hay colapso en los datos" */
    private static final String SIN_COLAPSO = "SIN_COLAPSO";

    private volatile LocalDateTime fechaColapsoMemoria;
    /** Huella de la data (conteo de envios) con la que se calculó fechaColapsoMemoria */
    private volatile Long fingerprintMemoria;
    /** true si el resultado en memoria es "sin colapso" (para distinguir de "no calculado") */
    private volatile boolean sinColapsoMemoria = false;

    /** Memo de la huella con TTL corto: hayCache() y obtenerFechaColapso() se
     *  llaman seguidas en un mismo clic; evita hacer el COUNT(*) dos veces. */
    private volatile long fingerprintCacheVal = -1L;
    private volatile long fingerprintCacheAtMs = 0L;
    private static final long FINGERPRINT_TTL_MS = 15_000;

    /**
     * Huella de la data actual: MAX(id_envio) de envio_maletas. Se calcula por el
     * índice de clave primaria, así que es instantáneo (a diferencia de COUNT(*),
     * que sobre 10M filas y por el túnel SSH tardaba varios segundos ANTES de
     * cualquier mensaje al cliente). Cualquier recarga/ampliación de la data
     * inserta filas con ids nuevos y sube el máximo → la caché se invalida y se
     * re-busca desde el inicio, evitando reportar una fecha calculada con data vieja.
     */
    private long fingerprintActual() {
        long ahora = System.currentTimeMillis();
        if (fingerprintCacheVal >= 0 && ahora - fingerprintCacheAtMs < FINGERPRINT_TTL_MS) {
            return fingerprintCacheVal;
        }
        Long n = jdbcTemplate.queryForObject("SELECT COALESCE(MAX(id_envio), 0) FROM envio_maletas", Long.class);
        fingerprintCacheVal = (n != null ? n : -1L);
        fingerprintCacheAtMs = ahora;
        return fingerprintCacheVal;
    }

    /** Devuelve la fila de caché vigente (criterio y huella de data coinciden), o null */
    private ColapsoCache cacheVigente(long fingerprint) {
        var c = cacheRepository.findTopByOrderByIdDesc().orElse(null);
        if (c == null) return null;
        if (!CRITERIO.equals(c.getCriterio())) return null;
        if (c.getTotalEnvios() == null || c.getTotalEnvios() != fingerprint) return null;
        return c;
    }

    /** true si ya existe un resultado cacheado VIGENTE (misma huella de data y criterio) */
    public boolean hayCache() {
        try {
            long fp = fingerprintActual();
            if ((fechaColapsoMemoria != null || sinColapsoMemoria)
                    && fingerprintMemoria != null && fingerprintMemoria == fp) return true;
            return cacheVigente(fp) != null;
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
        long fingerprint = fingerprintActual();

        // 1. Caché en memoria válida solo si la huella de data no cambió
        if (fingerprintMemoria != null && fingerprintMemoria == fingerprint) {
            if (fechaColapsoMemoria != null) return fechaColapsoMemoria;
            if (sinColapsoMemoria) return null;
        }

        LocalDate desdeDatos = consultarFecha("SELECT MIN(DATE(fecha_registro)) FROM envio_maletas");
        LocalDate hastaDatos = consultarFecha("SELECT MAX(DATE(fecha_registro)) FROM envio_maletas");
        if (desdeDatos == null || hastaDatos == null) {
            log.warn("envio_maletas vacío: no se puede buscar colapso");
            return null;
        }

        // 2. Caché en BD válida solo si criterio Y huella de data coinciden
        ColapsoCache c = cacheVigente(fingerprint);
        if (c != null) {
            if (SIN_COLAPSO.equals(c.getDetalle())) {
                fingerprintMemoria = fingerprint; sinColapsoMemoria = true;
                log.info("Caché BD vigente: sin colapso en la data actual ({} envios)", fingerprint);
                return null;
            }
            fechaColapsoMemoria = c.getFechaColapsoEstimada();
            fingerprintMemoria = fingerprint;
            log.info("Fecha de colapso recuperada de caché BD: {} ({})", fechaColapsoMemoria, CRITERIO);
            return fechaColapsoMemoria;
        }

        // 3. Simulación CONTINUA con ocupación de almacenes (data cambió o sin caché)
        Long capacidadDiaria = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(capacidad), 0) FROM plan_vuelo_diario", Long.class);

        LocalDate inicio = INICIO_SIMULACION.isBefore(desdeDatos) ? desdeDatos : INICIO_SIMULACION;
        log.info("Simulación continua de colapso ({}) desde {} hasta {} · {} envios...",
                CRITERIO, inicio, hastaDatos, fingerprint);

        ColapsoContinuoService.Resultado res = colapsoContinuoService.buscar(inicio, hastaDatos, progreso);
        if (res != null) {
            log.info("Colapso encontrado el {}: {}", res.fecha(), res.motivo());
            guardarCache(res.fecha().atStartOfDay(), capacidadDiaria, res.motivo(), fingerprint);
            fingerprintMemoria = fingerprint;
            return fechaColapsoMemoria;
        }

        log.info("Sin colapso: la simulación continua no satura almacenes ni viola SLA en [{} → {}]", inicio, hastaDatos);
        fingerprintMemoria = fingerprint; sinColapsoMemoria = true;
        try {
            ColapsoCache cache = new ColapsoCache();
            cache.setFechaColapsoEstimada(hastaDatos.atStartOfDay());
            cache.setFechaCalculo(LocalDateTime.now());
            cache.setCapacidadDiaria(capacidadDiaria);
            cache.setCriterio(CRITERIO);
            cache.setDetalle(SIN_COLAPSO);
            cache.setTotalEnvios(fingerprint);
            cacheRepository.save(cache);
        } catch (Exception e) {
            log.warn("No se pudo persistir 'sin colapso' (queda en memoria): {}", e.getMessage());
        }
        return null;
    }

    private void guardarCache(LocalDateTime fecha, Long capacidadDiaria, String detalle, long fingerprint) {
        fechaColapsoMemoria = fecha;
        sinColapsoMemoria = false;
        try {
            ColapsoCache cache = new ColapsoCache();
            cache.setFechaColapsoEstimada(fecha);
            cache.setFechaCalculo(LocalDateTime.now());
            cache.setCapacidadDiaria(capacidadDiaria);
            cache.setCriterio(CRITERIO);
            cache.setDetalle(detalle);
            cache.setTotalEnvios(fingerprint);
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
