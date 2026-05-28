package com.plantilla.backend.modules.auth.service;

import com.plantilla.backend.modules.auth.dto.UsuarioRequestDto;
import com.plantilla.backend.modules.auth.dto.UsuarioResponseDto;

import java.util.List;

/**
 * Interfaz del servicio CRUD de usuarios.
 * Principio SOLID (D): Los controllers dependen de esta abstracción.
 * Principio SOLID (I): Interfaz segregada exclusivamente para gestión de usuarios.
 */
public interface UsuarioService {

    /** Retorna todos los usuarios registrados. */
    List<UsuarioResponseDto> listarUsuarios();

    /** Busca un usuario por su ID. Lanza excepción si no existe. */
    UsuarioResponseDto obtenerUsuarioPorId(Long id);

    /** Crea un nuevo usuario a partir del formulario del frontend. */
    UsuarioResponseDto crearUsuario(UsuarioRequestDto request);

    /** Actualiza los datos de un usuario existente. La contraseña solo se cambia si viene en el DTO. */
    UsuarioResponseDto actualizarUsuario(Long id, UsuarioRequestDto request);

    /** Elimina (o desactiva) un usuario por ID. */
    void eliminarUsuario(Long id);
}
