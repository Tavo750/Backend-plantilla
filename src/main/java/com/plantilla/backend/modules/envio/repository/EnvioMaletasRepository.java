package com.plantilla.backend.modules.envio.repository;

import com.plantilla.backend.modules.envio.entity.EnvioMaletas;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio de acceso a datos de envíos de maletas.
 * Principio SOLID (I): Interfaz segregada para persistencia de envíos.
 */
@Repository
public interface EnvioMaletasRepository extends JpaRepository<EnvioMaletas, Integer> {

        List<EnvioMaletas> findByAerolineaIdAerolinea(Integer idAerolinea);

        List<EnvioMaletas> findByAeropuertoOrigenIdAeropuerto(Integer idAeropuertoOrigen);

        List<EnvioMaletas> findByAeropuertoDestinoIdAeropuerto(Integer idAeropuertoDestino);

        List<EnvioMaletas> findByFechaRegistroBetween(LocalDateTime desde, LocalDateTime hasta);

        List<EnvioMaletas> findByFechaRegistroBetweenOrderByFechaRegistroAsc(
                        LocalDateTime desde, LocalDateTime hasta);

        long countByFechaRegistroGreaterThanEqual(LocalDateTime desde);

        List<EnvioMaletas> findByFechaRegistroGreaterThanEqualOrderByFechaRegistroAsc(
                        LocalDateTime desde, Pageable pageable);

        Optional<EnvioMaletas> findTopByOrderByFechaRegistroAsc();

        List<EnvioMaletas> findByAeropuertoOrigenIdAeropuertoOrderByFechaRegistroDesc(
                        Integer idAeropuertoOrigen,
                        Pageable pageable);
}