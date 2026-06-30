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
}
