package com.plantilla.backend.modules.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de respuesta para operaciones CRUD de usuarios.
 * No expone la contraseña ni datos sensibles internos.
 * Principio SOLID (S): Solo transporta datos de salida del usuario.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioResponseDto {

    private Long id;
    private String nombre;
    private String apellidoPaterno;
    private String apellidoMaterno;
    private String nombreCompleto;
    private String correo;
    private String puesto;
    private String fotoUrl;
    private Boolean estado;

    // Datos de la aerolínea asociada
    private Integer idAerolinea;
    private String nombreAerolinea;
    private String codigoAerolinea;
}
