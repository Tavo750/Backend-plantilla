package com.plantilla.backend.modules.simulacion.repository;

import com.plantilla.backend.modules.simulacion.entity.ColapsoCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ColapsoCacheRepository extends JpaRepository<ColapsoCache, Integer> {
    Optional<ColapsoCache> findTopByOrderByIdDesc();
}
