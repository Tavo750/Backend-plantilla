package com.plantilla.backend.modules.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para creación y actualización de usuarios.
 * Incluye todos los campos que el formulario del frontend puede enviar.
 * Principio SOLID (S): Solo transporta datos de entrada del usuario.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioRequestDto {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 100, message = "El nombre no debe exceder 100 caracteres")
    private String nombre;

    @NotBlank(message = "El apellido paterno es obligatorio")
    @Size(max = 100, message = "El apellido paterno no debe exceder 100 caracteres")
    private String apellidoPaterno;

    @Size(max = 100, message = "El apellido materno no debe exceder 100 caracteres")
    private String apellidoMaterno;

    @NotBlank(message = "El correo es obligatorio")
    @Email(message = "El correo debe tener un formato válido")
    @Size(max = 150, message = "El correo no debe exceder 150 caracteres")
    private String correo;

    /** Solo requerida al crear. En actualizaciones puede omitirse (null = no cambia). */
    @Size(min = 6, max = 100, message = "La contraseña debe tener entre 6 y 100 caracteres")
    private String contrasena;

    @Size(max = 100, message = "El puesto no debe exceder 100 caracteres")
    private String puesto;

    @Size(max = 500, message = "La URL de la foto no debe exceder 500 caracteres")
    private String fotoUrl;

    /** Identificador de la aerolínea a la que pertenece el usuario (opcional). */
    private Integer idAerolinea;
}
