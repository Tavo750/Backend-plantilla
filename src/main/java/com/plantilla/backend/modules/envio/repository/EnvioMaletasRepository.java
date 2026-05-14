package com.plantilla.backend.modules.envio.repository;

import com.plantilla.backend.modules.envio.entity.EnvioMaletas;
import com.plantilla.backend.shared.enums.EstadoMaleta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositorio de acceso a datos de envíos de maletas.
 * Principio SOLID (I): Interfaz segregada para persistencia de envíos.
 */
@Repository
public interface EnvioMaletasRepository extends JpaRepository<EnvioMaletas, Integer> {

    // List<EnvioMaletas> findByEstado(EstadoMaleta estado);

    List<EnvioMaletas> findByAerolineaIdAerolinea(Integer idAerolinea);

    List<EnvioMaletas> findByAeropuertoOrigenIdAeropuerto(Integer idAeropuertoOrigen);

    List<EnvioMaletas> findByAeropuertoDestinoIdAeropuerto(Integer idAeropuertoDestino);
}
