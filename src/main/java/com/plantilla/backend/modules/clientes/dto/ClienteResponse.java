package com.plantilla.backend.modules.clientes.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO de respuesta para datos de cliente.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClienteResponse {

    private Long id;
    private String razonSocial;
    private String ruc;
    private String direccion;
    private String telefono;
    private String correo;
    private String contactoPrincipal;
    private Boolean estado;
    private LocalDateTime creadoEn;
    private LocalDateTime actualizadoEn;
}
