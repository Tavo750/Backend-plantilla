package com.plantilla.backend.modules.envio.repository;

import com.plantilla.backend.modules.envio.entity.EnvioDiario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface EnvioDiarioRepository extends JpaRepository<EnvioDiario, Integer> {

    /** Envíos por estado (ej. REGISTRADA, ASIGNADA, EN_TRANSITO). */
    @Query("SELECT e FROM EnvioDiario e WHERE CAST(e.estado AS string) = :estado")
    List<EnvioDiario> findByEstado(@Param("estado") String estado);

    /**
     * Envíos ya comprometidos (EN_ESPERA/EN_TRANSITO) como PROYECCIÓN escalar — NO entidades
     * gestionadas — para reflejarlos en el mapa de Operación Diaria sin riesgo de que Hibernate
     * los vuelva a escribir (lost-update contra el job de estados). Columnas:
     * [0]idEnvio [1]estado [2]idPlan [3]salidaAsignada [4]llegadaAsignada [5]cantidad
     * [6]fechaRegistro [7]fechaLimite [8]origenOaci [9]origenGmt [10]destinoOaci
     */
    @Query("SELECT e.idEnvio, e.estado, e.idPlanVueloAsignado, e.fechaHoraSalidaAsignada, " +
           "e.fechaHoraLlegadaAsignada, e.cantidad, e.fechaRegistro, e.fechaLimiteEntrega, " +
           "o.codigoOaci, o.gmt, d.codigoOaci " +
           "FROM EnvioDiario e JOIN e.aeropuertoOrigen o JOIN e.aeropuertoDestino d " +
           "WHERE CAST(e.estado AS string) IN ('EN_ESPERA','EN_TRANSITO') " +
           "AND e.idPlanVueloAsignado IS NOT NULL " +
           "AND e.fechaHoraSalidaAsignada IS NOT NULL AND e.fechaHoraLlegadaAsignada IS NOT NULL")
    List<Object[]> findComprometidosParaMapa();

    /** Operación diaria (planificación continua): envíos registrados en una ventana. */
    List<EnvioDiario> findByFechaRegistroBetweenOrderByFechaRegistroAsc(
            LocalDateTime desde, LocalDateTime hasta);

    /** Operación diaria: ids de TODOS los envíos existentes (para reconciliar borrados). */
    @Query("SELECT e.idEnvio FROM EnvioDiario e")
    List<Integer> findAllIds();

    /** Envíos asignados a un vuelo específico del plan diario. */
    List<EnvioDiario> findByIdPlanVueloAsignado(Integer idPlanVueloAsignado);

    /** Total de maletas ya asignadas a un vuelo (para control de capacidad). */
    @Query("SELECT COALESCE(SUM(e.cantidad), 0) FROM EnvioDiario e WHERE e.idPlanVueloAsignado = :idVuelo AND CAST(e.estado AS string) = 'ASIGNADA'")
    int sumCantidadByIdPlanVueloAsignado(@Param("idVuelo") Integer idVuelo);

    /** Para monitoreo: suma de maletas activas agrupada por código OACI del aeropuerto origen. */
    @Query("SELECT e.aeropuertoOrigen.codigoOaci, SUM(e.cantidad) FROM EnvioDiario e " +
           "WHERE CAST(e.estado AS string) IN ('REGISTRADA', 'EN_ESPERA', 'EN_TRANSITO') " +
           "GROUP BY e.aeropuertoOrigen.codigoOaci")
    List<Object[]> sumCantidadPorAeropuertoOrigen();

    /** Para monitoreo: suma de maletas agrupada por id de vuelo asignado. */
    @Query("SELECT e.idPlanVueloAsignado, SUM(e.cantidad) FROM EnvioDiario e " +
           "WHERE e.idPlanVueloAsignado IS NOT NULL " +
           "GROUP BY e.idPlanVueloAsignado")
    List<Object[]> sumCantidadPorPlanVuelo();

    /** Para monitoreo: maletas en espera en aeropuerto origen (solo REGISTRADA + EN_ESPERA). */
    @Query("SELECT e.aeropuertoOrigen.codigoOaci, SUM(e.cantidad) FROM EnvioDiario e " +
           "WHERE CAST(e.estado AS string) IN ('REGISTRADA', 'EN_ESPERA') " +
           "GROUP BY e.aeropuertoOrigen.codigoOaci")
    List<Object[]> sumCantidadEnEsperaPorAeropuertoOrigen();

    /** Para monitoreo: maletas recién llegadas al aeropuerto destino (15 min). */
    @Query("SELECT e.aeropuertoDestino.codigoOaci, SUM(e.cantidad) FROM EnvioDiario e " +
           "WHERE CAST(e.estado AS string) IN ('ENTREGADA', 'RETRASADA') " +
           "AND e.fechaHoraLlegadaAsignada IS NOT NULL " +
           "AND e.fechaHoraLlegadaAsignada >= :desde " +
           "GROUP BY e.aeropuertoDestino.codigoOaci")
    List<Object[]> sumCantidadPorAeropuertoDestinoReciente(@Param("desde") LocalDateTime desde);

    /** Para monitoreo: envíos activos con vuelo asignado (datos mínimos, sin lazy loading). */
    @Query("SELECT e.idEnvio, e.aeropuertoOrigen.codigoOaci, e.aeropuertoDestino.codigoOaci, " +
           "e.cantidad, e.prioridad, e.idPlanVueloAsignado " +
           "FROM EnvioDiario e " +
           "WHERE CAST(e.estado AS string) IN ('REGISTRADA', 'EN_ESPERA', 'EN_TRANSITO') " +
           "AND e.idPlanVueloAsignado IS NOT NULL")
    List<Object[]> findEnviosActivosConVuelo();

    /** Para monitoreo: TODOS los envíos con vuelo asignado (incluye entregados/retrasados para el detalle). */
    @Query("SELECT e.idEnvio, e.aeropuertoOrigen.codigoOaci, e.aeropuertoDestino.codigoOaci, " +
           "e.cantidad, e.prioridad, e.idPlanVueloAsignado, CAST(e.estado AS string) " +
           "FROM EnvioDiario e " +
           "WHERE e.idPlanVueloAsignado IS NOT NULL")
    List<Object[]> findTodosEnviosConVuelo();

    /** Para monitoreo: suma de maletas activas (EN_ESPERA + EN_TRANSITO) por vuelo. */
    @Query("SELECT e.idPlanVueloAsignado, SUM(e.cantidad) FROM EnvioDiario e " +
           "WHERE e.idPlanVueloAsignado IS NOT NULL " +
           "AND CAST(e.estado AS string) IN ('EN_ESPERA', 'EN_TRANSITO') " +
           "GROUP BY e.idPlanVueloAsignado")
    List<Object[]> sumCantidadActivaPorPlanVuelo();
    
       /** Para validación: suma de maletas activas de un aeropuerto origen específico. */
       @Query("SELECT COALESCE(SUM(e.cantidad), 0) FROM EnvioDiario e " +
              "WHERE e.aeropuertoOrigen.idAeropuerto = :idAeropuerto " +
              "AND CAST(e.estado AS string) IN ('REGISTRADA', 'EN_ESPERA', 'EN_TRANSITO', 'RETRASADA')")
       int sumCantidadActivaPorAeropuertoOrigen(@Param("idAeropuerto") Integer idAeropuerto);

       /** Lista envíos relacionados a un aeropuerto, ya sea como origen o destino. */
       @Query("""
       SELECT e
       FROM EnvioDiario e
       WHERE e.aeropuertoOrigen.idAeropuerto = :idAeropuerto
              OR e.aeropuertoDestino.idAeropuerto = :idAeropuerto
       ORDER BY e.fechaRegistro DESC
       """)
       List<EnvioDiario> findByAeropuertoOrigenOrDestino(
              @Param("idAeropuerto") Integer idAeropuerto
);

    /** Conta el número de envíos activos (EN_ESPERA + EN_TRANSITO). */
    @Query("SELECT COUNT(e) FROM EnvioDiario e WHERE CAST(e.estado AS string) IN ('EN_ESPERA', 'EN_TRANSITO')")
    long countByEstadosAsignados();

    /** Suma total de maletas de envíos activos (EN_ESPERA + EN_TRANSITO). Returns null if no rows. */
    @Query("SELECT SUM(e.cantidad) FROM EnvioDiario e WHERE CAST(e.estado AS string) IN ('EN_ESPERA', 'EN_TRANSITO')")
    Long sumMaletasAsignadas();

    /** Cuenta envíos en estado REGISTRADA (sin vuelo aún). */
    @Query("SELECT COUNT(e) FROM EnvioDiario e WHERE CAST(e.estado AS string) = 'REGISTRADA'")
    long countRegistradas();

    /** Para monitoreo: maletas en tránsito (EN_TRANSITO) agrupadas por aeropuerto origen. */
    @Query("SELECT e.aeropuertoOrigen.codigoOaci, SUM(e.cantidad) FROM EnvioDiario e " +
           "WHERE CAST(e.estado AS string) = 'EN_TRANSITO' " +
           "GROUP BY e.aeropuertoOrigen.codigoOaci")
    List<Object[]> sumCantidadEnTransitoPorAeropuertoOrigen();
}
