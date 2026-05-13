package com.plantilla.backend.modules.simulacion.repository;

import com.plantilla.backend.modules.simulacion.entity.ConfiguracionSimulacion;
import com.plantilla.backend.shared.enums.TipoAlgoritmo;
import com.plantilla.backend.shared.enums.TipoEscenario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositorio de acceso a datos de configuraciones de simulación.
 * Principio SOLID (I): Interfaz segregada para persistencia de configuraciones.
 */
@Repository
public interface ConfiguracionSimulacionRepository extends JpaRepository<ConfiguracionSimulacion, Integer> {

    List<ConfiguracionSimulacion> findByTipoEscenario(TipoEscenario tipoEscenario);

    List<ConfiguracionSimulacion> findByTipoAlgoritmo(TipoAlgoritmo tipoAlgoritmo);
}
