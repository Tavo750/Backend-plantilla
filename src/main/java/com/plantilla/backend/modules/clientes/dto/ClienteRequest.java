package com.plantilla.backend.modules.clientes.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de petición para crear/actualizar un cliente.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClienteRequest {

    @NotBlank(message = "La razón social es obligatoria")
    @Size(max = 200, message = "La razón social no debe exceder 200 caracteres")
    private String razonSocial;

    @NotBlank(message = "El RUC es obligatorio")
    @Size(max = 20, message = "El RUC no debe exceder 20 caracteres")
    private String ruc;

    @Size(max = 300, message = "La dirección no debe exceder 300 caracteres")
    private String direccion;

    @Size(max = 20, message = "El teléfono no debe exceder 20 caracteres")
    private String telefono;

    @Email(message = "El correo debe tener un formato válido")
    @Size(max = 150, message = "El correo no debe exceder 150 caracteres")
    private String correo;

    @Size(max = 200, message = "El contacto principal no debe exceder 200 caracteres")
    private String contactoPrincipal;

    private Boolean estado;
}
