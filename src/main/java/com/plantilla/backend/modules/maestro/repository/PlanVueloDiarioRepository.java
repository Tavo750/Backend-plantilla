package com.plantilla.backend.modules.maestro.repository;

import com.plantilla.backend.modules.maestro.entity.PlanVueloDiario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalTime;
import java.util.List;

@Repository
public interface PlanVueloDiarioRepository extends JpaRepository<PlanVueloDiario, Integer> {

    List<PlanVueloDiario> findByCodigoOrigenAndCodigoDestino(String codigoOrigen, String codigoDestino);

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
}
