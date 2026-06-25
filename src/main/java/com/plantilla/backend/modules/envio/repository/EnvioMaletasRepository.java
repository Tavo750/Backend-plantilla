package com.plantilla.backend.modules.envio.repository;

import com.plantilla.backend.modules.envio.entity.EnvioMaletas;
import com.plantilla.backend.shared.enums.EstadoMaleta;
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

        /**
         * Carga todos los envíos junto con sus relaciones en una sola consulta SQL
         * (evita el problema N+1 que agota el pool de conexiones).
         */
        @Query("SELECT e FROM EnvioMaletas e " +
                        "JOIN FETCH e.aerolinea " +
                        "JOIN FETCH e.aeropuertoOrigen " +
                        "JOIN FETCH e.aeropuertoDestino " +
                        "JOIN FETCH e.politicaEntrega")
        List<EnvioMaletas> findAllWithRelations();

        /**
         * Paginado con JOIN FETCH — evita cargar toda la tabla en memoria.
         * La countQuery es necesaria porque JPQL no soporta COUNT con JOIN FETCH.
         */
        @Query(value = "SELECT e FROM EnvioMaletas e " +
                        "JOIN FETCH e.aerolinea " +
                        "JOIN FETCH e.aeropuertoOrigen " +
                        "JOIN FETCH e.aeropuertoDestino " +
                        "JOIN FETCH e.politicaEntrega", countQuery = "SELECT COUNT(e) FROM EnvioMaletas e")
        Page<EnvioMaletas> findAllWithRelationsPaged(Pageable pageable);

        // List<EnvioMaletas> findByEstado(EstadoMaleta estado);

        List<EnvioMaletas> findByAerolineaIdAerolinea(Integer idAerolinea);

        List<EnvioMaletas> findByAeropuertoOrigenIdAeropuerto(Integer idAeropuertoOrigen);

        List<EnvioMaletas> findByAeropuertoDestinoIdAeropuerto(Integer idAeropuertoDestino);

        /**
         * Lista los envíos cuya fecha de registro está en el rango [desde, hasta].
         * Útil para alimentar al algoritmo ALNS con la demanda del periodo a simular.
         */
        List<EnvioMaletas> findByFechaRegistroBetween(LocalDateTime desde, LocalDateTime hasta);

        /** Versión ordenada para SC: procesamiento FIFO por fecha de registro. */
        List<EnvioMaletas> findByFechaRegistroBetweenOrderByFechaRegistroAsc(
                        LocalDateTime desde, LocalDateTime hasta);

        /** Cuenta los envíos desde una fecha (para calcular el tamaño del batch SC). */
        long countByFechaRegistroGreaterThanEqual(LocalDateTime desde);

        /** Carga el siguiente batch SC ordenado por fechaRegistro ASC. */
        List<EnvioMaletas> findByFechaRegistroGreaterThanEqualOrderByFechaRegistroAsc(
                        LocalDateTime desde, Pageable pageable);

        /** Envío más antiguo registrado — punto de partida automático del monitoreo. */
        Optional<EnvioMaletas> findTopByOrderByFechaRegistroAsc();
}
