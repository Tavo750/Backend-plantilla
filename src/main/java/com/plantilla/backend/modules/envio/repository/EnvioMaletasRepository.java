package com.plantilla.backend.modules.envio.repository;

import com.plantilla.backend.modules.envio.entity.EnvioMaletas;
import org.springframework.data.domain.Page;
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

        @Query("SELECT e FROM EnvioMaletas e JOIN FETCH e.aerolinea JOIN FETCH e.aeropuertoOrigen JOIN FETCH e.aeropuertoDestino JOIN FETCH e.politicaEntrega")
        List<EnvioMaletas> findAllWithRelations();

        /**
         * Paso 1: obtiene solo los IDs paginados — query SQL ligera, sin JOIN FETCH.
         * Evita el "HHH90003004: firstResult/maxResults specified with collection fetch"
         * que forzaba a Hibernate a paginar en memoria (causa de los ~40 s de latencia).
         */
        @Query(value = "SELECT e.idEnvio FROM EnvioMaletas e")
        Page<Integer> findIdsPaged(Pageable pageable);

        /**
         * Paso 2: carga las entidades completas (con JOIN FETCH) solo para los IDs
         * devueltos por findIdsPaged — una sola query SQL con WHERE id IN (...).
         */
        @Query("SELECT e FROM EnvioMaletas e JOIN FETCH e.aerolinea JOIN FETCH e.aeropuertoOrigen JOIN FETCH e.aeropuertoDestino JOIN FETCH e.politicaEntrega WHERE e.idEnvio IN :ids")
        List<EnvioMaletas> findByIdsWithRelations(@org.springframework.data.repository.query.Param("ids") List<Integer> ids);

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