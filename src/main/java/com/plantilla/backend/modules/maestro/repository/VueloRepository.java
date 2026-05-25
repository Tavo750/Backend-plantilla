package com.plantilla.backend.modules.maestro.repository;

import com.plantilla.backend.modules.maestro.entity.Vuelo;
import com.plantilla.backend.shared.enums.EstadoVuelo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio de acceso a datos de vuelos.
 * Principio SOLID (I): Interfaz segregada para persistencia de vuelos.
 */
@Repository
public interface VueloRepository extends JpaRepository<Vuelo, Integer> {

    Optional<Vuelo> findByCodigoVuelo(String codigoVuelo);

    boolean existsByCodigoVuelo(String codigoVuelo);

    List<Vuelo> findByEstado(EstadoVuelo estado);

    List<Vuelo> findByAeropuertoOrigenIdAeropuerto(Integer idAeropuertoOrigen);

    List<Vuelo> findByAeropuertoDestinoIdAeropuerto(Integer idAeropuertoDestino);

    /**
     * Lista los vuelos cuya hora de salida está dentro del rango [desde, hasta].
     * Útil para cargar la ventana de vuelos a usar por el algoritmo ALNS.
     */
    List<Vuelo> findByHoraSalidaBetween(LocalDateTime desde, LocalDateTime hasta);

    /**
     * Lista los vuelos cuya hora de salida está dentro del rango [desde, hasta] y un estado dado.
     */
    List<Vuelo> findByHoraSalidaBetweenAndEstado(LocalDateTime desde, LocalDateTime hasta, EstadoVuelo estado);
}
