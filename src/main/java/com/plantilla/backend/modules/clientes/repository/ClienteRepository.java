package com.plantilla.backend.modules.clientes.repository;

import com.plantilla.backend.modules.clientes.entity.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repositorio de acceso a datos de clientes.
 * Principio SOLID (I): Interfaz segregada para persistencia de clientes.
 */
@Repository
public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    Optional<Cliente> findByRuc(String ruc);

    boolean existsByRuc(String ruc);
}
