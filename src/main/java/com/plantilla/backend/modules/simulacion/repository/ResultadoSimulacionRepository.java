package com.plantilla.backend.modules.simulacion.repository;

import com.plantilla.backend.modules.simulacion.entity.ResultadoSimulacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositorio de acceso a datos de resultados de simulación por periodo.
 * Principio SOLID (I): Interfaz segregada para persistencia de resultados.
 */
@Repository
public interface ResultadoSimulacionRepository extends JpaRepository<ResultadoSimulacion, Integer> {

    List<ResultadoSimulacion> findByConfiguracionSimulacionIdConfiguracion(Integer idConfiguracion);
}
