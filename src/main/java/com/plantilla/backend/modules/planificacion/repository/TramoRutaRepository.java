package com.plantilla.backend.modules.planificacion.repository;

import com.plantilla.backend.modules.planificacion.entity.TramoRuta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositorio de acceso a datos de tramos de ruta.
 * Principio SOLID (I): Interfaz segregada para persistencia de tramos.
 */
@Repository
public interface TramoRutaRepository extends JpaRepository<TramoRuta, Integer> {

    List<TramoRuta> findByPlanRutaIdPlanRutaOrderByOrdenAsc(Integer idPlanRuta);

    List<TramoRuta> findByVueloIdVuelo(Integer idVuelo);
}
