package com.plantilla.backend.modules.simulacion.repository;

import com.plantilla.backend.modules.simulacion.entity.SnapshotAeropuertoColapso;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositorio de acceso a datos de snapshots de aeropuerto en colapso.
 * Principio SOLID (I): Interfaz segregada para persistencia de snapshots de aeropuerto en colapso.
 */
@Repository
public interface SnapshotAeropuertoColapsoRepository extends JpaRepository<SnapshotAeropuertoColapso, Integer> {

    List<SnapshotAeropuertoColapso> findBySnapshotColapsoIdSnapshotColapso(Integer idSnapshotColapso);

    List<SnapshotAeropuertoColapso> findByColapsadoTrue();
}
