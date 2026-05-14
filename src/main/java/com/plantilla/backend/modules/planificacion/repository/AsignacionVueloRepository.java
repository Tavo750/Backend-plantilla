package com.plantilla.backend.modules.planificacion.repository;

import com.plantilla.backend.modules.planificacion.entity.AsignacionVuelo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositorio de acceso a datos de asignaciones de vuelo.
 * Principio SOLID (I): Interfaz segregada para persistencia de asignaciones.
 */
@Repository
public interface AsignacionVueloRepository extends JpaRepository<AsignacionVuelo, Integer> {

    List<AsignacionVuelo> findByVueloIdVuelo(Integer idVuelo);

    List<AsignacionVuelo> findByEnvioMaletasIdEnvio(Integer idEnvio);

    List<AsignacionVuelo> findByPlanRutaIdPlanRuta(Integer idPlanRuta);

    /**
     * Calcula la carga actual de un vuelo sumando las cantidades asignadas activas.
     */
    @Query("SELECT COALESCE(SUM(a.cantidadAsignada), 0) FROM AsignacionVuelo a " +
           "WHERE a.vuelo.idVuelo = :idVuelo AND a.estadoAsignacion = 'ACTIVA'")
    Integer calcularCargaActual(@Param("idVuelo") Integer idVuelo);
}
