package com.plantilla.backend.modules.simulacion.repository;

import com.plantilla.backend.modules.simulacion.entity.SnapshotVueloDia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositorio de acceso a datos de snapshots de vuelo por día.
 * Principio SOLID (I): Interfaz segregada para persistencia de snapshots de vuelo.
 */
@Repository
public interface SnapshotVueloDiaRepository extends JpaRepository<SnapshotVueloDia, Integer> {

    List<SnapshotVueloDia> findBySnapshotDiarioIdSnapshotDiario(Integer idSnapshotDiario);
}
