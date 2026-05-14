package com.plantilla.backend.modules.monitoreo.repository;

import com.plantilla.backend.modules.monitoreo.entity.ReporteComparativo;
import com.plantilla.backend.shared.enums.TipoAlgoritmo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositorio de acceso a datos de reportes comparativos ALNS vs DECO.
 * Principio SOLID (I): Interfaz segregada para persistencia de reportes.
 */
@Repository
public interface ReporteComparativoRepository extends JpaRepository<ReporteComparativo, Integer> {

    List<ReporteComparativo> findByAlgoritmoGanador(TipoAlgoritmo algoritmoGanador);
}
