package com.plantilla.backend.modules.envio.repository;

import com.plantilla.backend.modules.envio.entity.EnvioDiario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
}
