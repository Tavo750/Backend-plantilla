package com.plantilla.backend.modules.planificacion.repository;

import com.plantilla.backend.modules.planificacion.entity.PlanRuta;
import com.plantilla.backend.shared.enums.TipoAlgoritmo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositorio de acceso a datos de planes de ruta.
 * Principio SOLID (I): Interfaz segregada para persistencia de planes de ruta.
 */
@Repository
public interface PlanRutaRepository extends JpaRepository<PlanRuta, Integer> {

    List<PlanRuta> findByEnvioMaletasIdEnvio(Integer idEnvio);

    List<PlanRuta> findByEnvioMaletasIdEnvioAndEsVigenteTrue(Integer idEnvio);

    List<PlanRuta> findByTipoAlgoritmo(TipoAlgoritmo tipoAlgoritmo);
}
