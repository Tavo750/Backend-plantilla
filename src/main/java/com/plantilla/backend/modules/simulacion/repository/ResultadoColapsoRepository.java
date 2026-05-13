package com.plantilla.backend.modules.simulacion.repository;

import com.plantilla.backend.modules.simulacion.entity.ResultadoColapso;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositorio de acceso a datos de resultados de simulación de colapso.
 * Principio SOLID (I): Interfaz segregada para persistencia de resultados de colapso.
 */
@Repository
public interface ResultadoColapsoRepository extends JpaRepository<ResultadoColapso, Integer> {

    List<ResultadoColapso> findByConfiguracionSimulacionIdConfiguracion(Integer idConfiguracion);
}
