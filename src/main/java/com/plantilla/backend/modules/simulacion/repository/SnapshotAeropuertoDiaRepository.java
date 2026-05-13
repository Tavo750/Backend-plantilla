package com.plantilla.backend.modules.simulacion.repository;

import com.plantilla.backend.modules.simulacion.entity.SnapshotAeropuertoDia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositorio de acceso a datos de snapshots de aeropuerto por día.
 * Principio SOLID (I): Interfaz segregada para persistencia de snapshots de aeropuerto.
 */
@Repository
public interface SnapshotAeropuertoDiaRepository extends JpaRepository<SnapshotAeropuertoDia, Integer> {

    List<SnapshotAeropuertoDia> findBySnapshotDiarioIdSnapshotDiario(Integer idSnapshotDiario);
}
