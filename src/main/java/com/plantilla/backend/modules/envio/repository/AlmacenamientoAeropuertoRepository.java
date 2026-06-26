package com.plantilla.backend.modules.envio.repository;

import com.plantilla.backend.modules.envio.entity.AlmacenamientoAeropuerto;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AlmacenamientoAeropuertoRepository extends JpaRepository<AlmacenamientoAeropuerto, Integer> {

    Optional<AlmacenamientoAeropuerto> findByAeropuertoIdAeropuerto(Integer idAeropuerto);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AlmacenamientoAeropuerto> findWithLockByAeropuertoIdAeropuerto(Integer idAeropuerto);
}