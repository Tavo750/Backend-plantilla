package com.plantilla.backend.modules.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para respuesta de inicio de sesión.
 * Contiene el token JWT y los datos del usuario autenticado.
 * Principio SOLID (S): Solo transporta datos de respuesta de autenticación.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    private String token;

    @Builder.Default
    private String tipo = "Bearer";

    // Datos del usuario (coincide con interfaz Usuario del frontend)
    private Long id;
    private String nombre;
    private String apellidoPaterno;
    private String apellidoMaterno;
    private String nombreCompleto;
    private String correo;
    private String puesto;
    private String fotoUrl;
    private Boolean estado;

    // Aerolínea asociada al usuario
    private Integer idAerolinea;
}
