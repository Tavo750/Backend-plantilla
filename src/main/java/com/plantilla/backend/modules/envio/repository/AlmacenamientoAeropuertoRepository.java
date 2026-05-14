package com.plantilla.backend.modules.envio.repository;

import com.plantilla.backend.modules.envio.entity.AlmacenamientoAeropuerto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repositorio de acceso a datos de almacenamiento en aeropuertos.
 * Principio SOLID (I): Interfaz segregada para persistencia de almacenamiento.
 */
@Repository
public interface AlmacenamientoAeropuertoRepository extends JpaRepository<AlmacenamientoAeropuerto, Integer> {

    Optional<AlmacenamientoAeropuerto> findByAeropuertoIdAeropuerto(Integer idAeropuerto);
}
