package com.plantilla.backend.modules.contingencia.repository;

import com.plantilla.backend.modules.contingencia.entity.Replanificacion;
import com.plantilla.backend.shared.enums.EstadoReplanificacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositorio de acceso a datos de replanificaciones.
 * Principio SOLID (I): Interfaz segregada para persistencia de replanificaciones.
 */
@Repository
public interface ReplanificacionRepository extends JpaRepository<Replanificacion, Integer> {

    List<Replanificacion> findByEstado(EstadoReplanificacion estado);

    List<Replanificacion> findByEventoOperativoIdEvento(Integer idEvento);
}
