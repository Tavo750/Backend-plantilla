package com.plantilla.backend.modules.envio.repository;

import com.plantilla.backend.modules.envio.entity.UbicacionEnvio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio de acceso a datos de ubicaciones de envíos.
 * Principio SOLID (I): Interfaz segregada para persistencia de ubicaciones.
 */
@Repository
public interface UbicacionEnvioRepository extends JpaRepository<UbicacionEnvio, Integer> {

    Optional<UbicacionEnvio> findByEnvioMaletasIdEnvioAndEsUbicacionActualTrue(Integer idEnvio);

    List<UbicacionEnvio> findByEnvioMaletasIdEnvioOrderByTimestampLlegadaAsc(Integer idEnvio);

    List<UbicacionEnvio> findByAeropuertoIdAeropuertoAndEsUbicacionActualTrue(Integer idAeropuerto);
}
