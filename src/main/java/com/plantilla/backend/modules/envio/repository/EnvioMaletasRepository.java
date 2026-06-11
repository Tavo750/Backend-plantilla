package com.plantilla.backend.modules.envio.repository;

import com.plantilla.backend.modules.envio.entity.EnvioMaletas;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
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

    /**
     * Lista los envíos cuya fecha de registro está en el rango [desde, hasta].
     * Útil para alimentar al algoritmo ALNS con la demanda del periodo a simular.
     */
    List<EnvioMaletas> findByFechaRegistroBetween(LocalDateTime desde, LocalDateTime hasta);

    /** Cuenta los envíos desde una fecha (para calcular el tamaño del batch SC). */
    long countByFechaRegistroGreaterThanEqual(LocalDateTime desde);

    /** Carga el siguiente batch SC ordenado por fechaRegistro ASC. */
    List<EnvioMaletas> findByFechaRegistroGreaterThanEqualOrderByFechaRegistroAsc(
            LocalDateTime desde, Pageable pageable);

    /** Envío más antiguo registrado — punto de partida automático del monitoreo. */
    java.util.Optional<EnvioMaletas> findTopByOrderByFechaRegistroAsc();
}
