package com.plantilla.backend.modules.maestro.repository;

import com.plantilla.backend.modules.maestro.entity.Vuelo;
import com.plantilla.backend.shared.enums.EstadoVuelo;
import org.springframework.data.jpa.repository.EntityGraph;
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

    /** Vuelo con la hora de salida más temprana — define el inicio real útil de la simulación. */
    Optional<Vuelo> findTopByOrderByHoraSalidaAsc();

    List<Vuelo> findByAeropuertoOrigen_IdAeropuertoAndAeropuertoDestino_IdAeropuertoAndHoraSalidaBetweenAndEstadoNotOrderByHoraSalidaAsc(
            Integer idAeropuertoOrigen, Integer idAeropuertoDestino,
            LocalDateTime desde, LocalDateTime hasta, EstadoVuelo estado);

    @EntityGraph(attributePaths = {"aeropuertoOrigen", "aeropuertoDestino"})
    List<Vuelo> findByAeropuertoOrigen_IdAeropuerto(Integer idAeropuertoOrigen);

    /** Vuelos que están en vuelo en el momento indicado (simulación: snapshot inicial). */
    List<Vuelo> findByHoraSalidaLessThanEqualAndHoraLlegadaGreaterThanAndEstadoNot(
            LocalDateTime horaSalida, LocalDateTime horaLlegada, EstadoVuelo estado);
}
