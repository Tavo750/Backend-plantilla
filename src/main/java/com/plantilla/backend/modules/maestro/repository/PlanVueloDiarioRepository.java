package com.plantilla.backend.modules.maestro.repository;

import com.plantilla.backend.modules.maestro.entity.PlanVueloDiario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
@Repository
public interface PlanVueloDiarioRepository extends JpaRepository<PlanVueloDiario, Integer> {

    List<PlanVueloDiario> findByCodigoOrigenAndCodigoDestino(String codigoOrigen, String codigoDestino);

    /** Proyección escalar id→capacidad (para el mapa de operación diaria, sin entidades gestionadas). */
    @Query("SELECT p.id, p.capacidad FROM PlanVueloDiario p")
    List<Object[]> findIdYCapacidad();

    List<PlanVueloDiario> findByCodigoOrigen(String codigoOrigen);

    List<PlanVueloDiario> findByCodigoDestino(String codigoDestino);

    /** Vuelos que todavía no han salido hoy (horaSalida > ahora). */
    List<PlanVueloDiario> findByHoraSalidaAfterOrderByHoraSalidaAsc(LocalTime ahora);

    /** Próximo vuelo disponible para una ruta específica que salga después de una hora dada. */
    @Query("SELECT p FROM PlanVueloDiario p WHERE p.codigoOrigen = :origen AND p.codigoDestino = :destino AND p.horaSalida > :ahora ORDER BY p.horaSalida ASC")
    List<PlanVueloDiario> findProximoVuelo(
            @Param("origen") String origen,
            @Param("destino") String destino,
            @Param("ahora") LocalTime ahora);

    /**Esto nos deja traer todos los vuelos de esa ruta ordenados por hora. */
    List<PlanVueloDiario> findByCodigoOrigenAndCodigoDestinoOrderByHoraSalidaAsc(
            String codigoOrigen,
            String codigoDestino
    );

    boolean existsByCodigoOrigenAndCodigoDestinoAndHoraSalida(String codigoOrigen, String codigoDestino, LocalTime horaSalida);

    /** Lookup laxo por ruta + hora de salida (para vincular el vuelo asignado al plan). */
    Optional<PlanVueloDiario> findFirstByCodigoOrigenAndCodigoDestinoAndHoraSalida(
            String codigoOrigen, String codigoDestino, LocalTime horaSalida);

    Optional<PlanVueloDiario>
    findFirstByCodigoOrigenAndCodigoDestinoAndHoraSalidaAndHoraLlegadaAndCapacidad(
            String codigoOrigen,
            String codigoDestino,
            LocalTime horaSalida,
            LocalTime horaLlegada,
            Integer capacidad
    );
}
