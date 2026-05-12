package com.plantilla.backend.modules.auth.repository;

import com.plantilla.backend.modules.auth.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repositorio de acceso a datos de usuarios.
 * Principio SOLID (I): Interfaz segregada para operaciones de persistencia de usuarios.
 * Principio SOLID (D): El servicio depende de esta abstracción, no de la implementación.
 */
@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    /**
     * Busca un usuario por correo electrónico.
     */
    Optional<Usuario> findByCorreo(String correo);

    /**
     * Verifica si existe un usuario con el correo dado.
     */
    boolean existsByCorreo(String correo);
}
