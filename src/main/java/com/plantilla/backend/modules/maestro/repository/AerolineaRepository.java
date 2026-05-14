package com.plantilla.backend.modules.maestro.repository;

import com.plantilla.backend.modules.maestro.entity.Aerolinea;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repositorio de acceso a datos de aerolíneas.
 * Principio SOLID (I): Interfaz segregada para persistencia de aerolíneas.
 */
@Repository
public interface AerolineaRepository extends JpaRepository<Aerolinea, Integer> {

    Optional<Aerolinea> findByCodigo(String codigo);

    boolean existsByCodigo(String codigo);
}
