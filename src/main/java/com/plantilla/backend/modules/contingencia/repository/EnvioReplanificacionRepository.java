package com.plantilla.backend.modules.contingencia.repository;

import com.plantilla.backend.modules.contingencia.entity.EnvioReplanificacion;
import com.plantilla.backend.modules.contingencia.entity.EnvioReplanificacionId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositorio de acceso a datos de la relación envío-replanificación.
 * Principio SOLID (I): Interfaz segregada para persistencia de detalle de replanificación.
 */
@Repository
public interface EnvioReplanificacionRepository extends JpaRepository<EnvioReplanificacion, EnvioReplanificacionId> {

    List<EnvioReplanificacion> findByIdIdReplanificacion(Integer idReplanificacion);

    List<EnvioReplanificacion> findByIdIdEnvio(Integer idEnvio);

    List<EnvioReplanificacion> findBySlaSalvadoTrue();
}
