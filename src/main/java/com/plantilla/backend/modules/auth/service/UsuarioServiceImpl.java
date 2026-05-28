package com.plantilla.backend.modules.auth.service;

import com.plantilla.backend.modules.auth.dto.UsuarioRequestDto;
import com.plantilla.backend.modules.auth.dto.UsuarioResponseDto;
import com.plantilla.backend.modules.auth.entity.Usuario;
import com.plantilla.backend.modules.auth.repository.UsuarioRepository;
import com.plantilla.backend.modules.maestro.entity.Aerolinea;
import com.plantilla.backend.modules.maestro.repository.AerolineaRepository;
import com.plantilla.backend.shared.errors.BusinessException;
import com.plantilla.backend.shared.helpers.StringHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Implementación del servicio CRUD de usuarios.
 * Principio SOLID (S): Solo gestiona operaciones CRUD; la autenticación queda en AuthService.
 * Principio SOLID (L): Sustituible por cualquier implementación de UsuarioService.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UsuarioServiceImpl implements UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final AerolineaRepository aerolineaRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(readOnly = true)
    public List<UsuarioResponseDto> listarUsuarios() {
        log.info("Listando todos los usuarios");
        return usuarioRepository.findAll()
                .stream()
                .map(this::toResponseDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public UsuarioResponseDto obtenerUsuarioPorId(Long id) {
        log.info("Buscando usuario con id: {}", id);
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new BusinessException("USUARIO_NO_ENCONTRADO",
                        "No se encontró el usuario con id: " + id));
        return toResponseDto(usuario);
    }

    @Override
    @Transactional
    public UsuarioResponseDto crearUsuario(UsuarioRequestDto request) {
        log.info("Creando usuario con correo: {}", request.getCorreo());

        if (usuarioRepository.existsByCorreo(request.getCorreo())) {
            throw new BusinessException("DUPLICATE_EMAIL", "El correo ya está registrado");
        }
        if (request.getContrasena() == null || request.getContrasena().isBlank()) {
            throw new BusinessException("CONTRASENA_REQUERIDA", "La contraseña es obligatoria al crear un usuario");
        }

        Usuario usuario = new Usuario();
        mapRequestToEntity(request, usuario, true);

        return toResponseDto(usuarioRepository.save(usuario));
    }

    @Override
    @Transactional
    public UsuarioResponseDto actualizarUsuario(Long id, UsuarioRequestDto request) {
        log.info("Actualizando usuario con id: {}", id);

        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new BusinessException("USUARIO_NO_ENCONTRADO",
                        "No se encontró el usuario con id: " + id));

        // Verificar duplicado de correo solo si cambió
        if (!usuario.getCorreo().equalsIgnoreCase(request.getCorreo())
                && usuarioRepository.existsByCorreo(request.getCorreo())) {
            throw new BusinessException("DUPLICATE_EMAIL", "El correo ya está en uso por otro usuario");
        }

        mapRequestToEntity(request, usuario, false);

        return toResponseDto(usuarioRepository.save(usuario));
    }

    @Override
    @Transactional
    public void eliminarUsuario(Long id) {
        log.info("Eliminando usuario con id: {}", id);
        if (!usuarioRepository.existsById(id)) {
            throw new BusinessException("USUARIO_NO_ENCONTRADO",
                    "No se encontró el usuario con id: " + id);
        }
        usuarioRepository.deleteById(id);
    }

    // =====================================================================
    // Helpers privados
    // =====================================================================

    /**
     * Mapea los campos del DTO a la entidad Usuario.
     *
     * @param esCreacion si es true, la contraseña es obligatoria y se codifica.
     *                   Si es false, solo se actualiza la contraseña cuando el DTO la incluye.
     */
    private void mapRequestToEntity(UsuarioRequestDto request, Usuario usuario, boolean esCreacion) {
        usuario.setNombre(request.getNombre());
        usuario.setApellidoPaterno(request.getApellidoPaterno());
        usuario.setApellidoMaterno(request.getApellidoMaterno());
        usuario.setCorreo(request.getCorreo());
        usuario.setPuesto(request.getPuesto());
        usuario.setFotoUrl(request.getFotoUrl());
        usuario.setEstado(usuario.getEstado() != null ? usuario.getEstado() : true);

        // Contraseña: siempre en creación, opcional en actualización
        if (esCreacion || (request.getContrasena() != null && !request.getContrasena().isBlank())) {
            usuario.setContrasena(passwordEncoder.encode(request.getContrasena()));
        }

        // Aerolínea: asociar si viene el ID, desasociar si es null
        if (request.getIdAerolinea() != null) {
            Aerolinea aerolinea = aerolineaRepository.findById(request.getIdAerolinea())
                    .orElseThrow(() -> new BusinessException("AEROLINEA_NO_ENCONTRADA",
                            "No existe la aerolínea con id: " + request.getIdAerolinea()));
            usuario.setAerolinea(aerolinea);
        } else {
            usuario.setAerolinea(null);
        }
    }

    /**
     * Convierte una entidad Usuario a su DTO de respuesta.
     */
    private UsuarioResponseDto toResponseDto(Usuario usuario) {
        UsuarioResponseDto dto = UsuarioResponseDto.builder()
                .id(usuario.getId())
                .nombre(usuario.getNombre())
                .apellidoPaterno(usuario.getApellidoPaterno())
                .apellidoMaterno(usuario.getApellidoMaterno())
                .nombreCompleto(StringHelper.buildNombreCompleto(
                        usuario.getNombre(),
                        usuario.getApellidoPaterno(),
                        usuario.getApellidoMaterno()))
                .correo(usuario.getCorreo())
                .puesto(usuario.getPuesto())
                .fotoUrl(usuario.getFotoUrl())
                .estado(usuario.getEstado())
                .build();

        if (usuario.getAerolinea() != null) {
            dto.setIdAerolinea(usuario.getAerolinea().getIdAerolinea());
            dto.setNombreAerolinea(usuario.getAerolinea().getNombre());
            dto.setCodigoAerolinea(usuario.getAerolinea().getCodigo());
        }

        return dto;
    }
}
