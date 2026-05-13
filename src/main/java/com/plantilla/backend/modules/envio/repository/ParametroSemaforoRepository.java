package com.plantilla.backend.modules.envio.repository;

import com.plantilla.backend.modules.envio.entity.ParametroSemaforo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositorio de acceso a datos de parámetros de semáforo.
 * Principio SOLID (I): Interfaz segregada para persistencia de parámetros.
 */
@Repository
public interface ParametroSemaforoRepository extends JpaRepository<ParametroSemaforo, Integer> {

    List<ParametroSemaforo> findByEntidadAndActivoTrue(String entidad);
}
