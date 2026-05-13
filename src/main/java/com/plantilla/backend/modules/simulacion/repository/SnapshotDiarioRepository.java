package com.plantilla.backend.modules.simulacion.repository;

import com.plantilla.backend.modules.simulacion.entity.SnapshotDiario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositorio de acceso a datos de snapshots diarios.
 * Principio SOLID (I): Interfaz segregada para persistencia de snapshots.
 */
@Repository
public interface SnapshotDiarioRepository extends JpaRepository<SnapshotDiario, Integer> {

    List<SnapshotDiario> findByResultadoSimulacionIdResultadoOrderByDiaAsc(Integer idResultado);
}
