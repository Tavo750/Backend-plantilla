package com.plantilla.backend.modules.monitoreo.repository;

import com.plantilla.backend.modules.monitoreo.entity.MetricaOperativa;
import com.plantilla.backend.shared.enums.TipoAlgoritmo;
import com.plantilla.backend.shared.enums.TipoEscenario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositorio de acceso a datos de métricas operativas.
 * Principio SOLID (I): Interfaz segregada para persistencia de métricas.
 */
@Repository
public interface MetricaOperativaRepository extends JpaRepository<MetricaOperativa, Integer> {

    List<MetricaOperativa> findByTipoEscenario(TipoEscenario tipoEscenario);

    List<MetricaOperativa> findByTipoAlgoritmo(TipoAlgoritmo tipoAlgoritmo);

    List<MetricaOperativa> findByConfiguracionSimulacionIdConfiguracion(Integer idConfiguracion);
}
