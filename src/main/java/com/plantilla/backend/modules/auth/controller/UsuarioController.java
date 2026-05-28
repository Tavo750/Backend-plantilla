package com.plantilla.backend.modules.auth.controller;

import com.plantilla.backend.modules.auth.dto.UsuarioRequestDto;
import com.plantilla.backend.modules.auth.dto.UsuarioResponseDto;
import com.plantilla.backend.modules.auth.service.UsuarioService;
import com.plantilla.backend.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controlador REST para el CRUD de usuarios.
 * Base: /usuarios
 * Principio SOLID (S): Solo maneja peticiones HTTP de gestión de usuarios.
 * Principio SOLID (D): Depende de la abstracción UsuarioService.
 */
@RestController
@RequestMapping("/usuarios")
@RequiredArgsConstructor
@Tag(name = "Usuarios", description = "Endpoints CRUD para la gestión de usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;

    @GetMapping
    @Operation(summary = "Listar usuarios", description = "Obtiene la lista completa de usuarios registrados")
    public ResponseEntity<ApiResponse<List<UsuarioResponseDto>>> listarUsuarios() {
        return ResponseEntity.ok(ApiResponse.success(usuarioService.listarUsuarios()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener usuario por ID", description = "Retorna los datos de un usuario específico")
    public ResponseEntity<ApiResponse<UsuarioResponseDto>> obtenerUsuario(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(usuarioService.obtenerUsuarioPorId(id)));
    }

    @PostMapping
    @Operation(summary = "Crear usuario", description = "Crea un nuevo usuario a partir del formulario del frontend")
    public ResponseEntity<ApiResponse<UsuarioResponseDto>> crearUsuario(
            @Valid @RequestBody UsuarioRequestDto request) {
        UsuarioResponseDto creado = usuarioService.crearUsuario(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(creado));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar usuario", description = "Actualiza los datos de un usuario existente. " +
            "La contraseña solo se modifica si se incluye en el cuerpo.")
    public ResponseEntity<ApiResponse<UsuarioResponseDto>> actualizarUsuario(
            @PathVariable Long id,
            @Valid @RequestBody UsuarioRequestDto request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Usuario actualizado exitosamente",
                usuarioService.actualizarUsuario(id, request)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar usuario", description = "Elimina un usuario por su ID")
    public ResponseEntity<ApiResponse<Void>> eliminarUsuario(@PathVariable Long id) {
        usuarioService.eliminarUsuario(id);
        return ResponseEntity.ok(ApiResponse.success("Usuario eliminado exitosamente", null));
    }
}
