package com.plantilla.backend.modules.maestro.repository;

import com.plantilla.backend.modules.maestro.entity.Aeropuerto;
import com.plantilla.backend.shared.enums.Continente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio de acceso a datos de aeropuertos.
 * Principio SOLID (I): Interfaz segregada para persistencia de aeropuertos.
 */
@Repository
public interface AeropuertoRepository extends JpaRepository<Aeropuerto, Integer> {

    Optional<Aeropuerto> findByCodigoOaci(String codigoOaci);

    boolean existsByCodigoOaci(String codigoOaci);

    Optional<Aeropuerto> findByCodigo(String codigo);

    boolean existsByCodigo(String codigo);

    List<Aeropuerto> findByContinente(Continente continente);

    List<Aeropuerto> findByActivoTrue();
}
