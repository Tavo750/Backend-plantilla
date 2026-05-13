package com.plantilla.backend.modules.simulacion.repository;

import com.plantilla.backend.modules.simulacion.entity.SnapshotColapso;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositorio de acceso a datos de snapshots de colapso.
 * Principio SOLID (I): Interfaz segregada para persistencia de snapshots de colapso.
 */
@Repository
public interface SnapshotColapsoRepository extends JpaRepository<SnapshotColapso, Integer> {

    List<SnapshotColapso> findByResultadoColapsoIdResultadoColapsoOrderByIteracionAsc(Integer idResultadoColapso);
}
