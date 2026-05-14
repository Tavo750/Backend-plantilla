package com.plantilla.backend.modules.maestro.repository;

import com.plantilla.backend.modules.maestro.entity.PoliticaEntrega;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repositorio de acceso a datos de políticas de entrega SLA.
 * Principio SOLID (I): Interfaz segregada para persistencia de políticas.
 */
@Repository
public interface PoliticaEntregaRepository extends JpaRepository<PoliticaEntrega, Integer> {

    Optional<PoliticaEntrega> findByActivaTrue();
}
