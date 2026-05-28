package com.plantilla.backend.modules.envio.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
public class EnvioMaletasCreateDTO {

    private Integer idAerolinea;

    // Acepta camelCase del frontend (idAeropuertoOrigen / idAeropuertoDestino)
    private Integer idAeropuertoOrigen;

    private Integer idAeropuertoDestino;
    
    private Integer idPolitica;
    
    private Integer cantidad;
    private LocalDateTime fechaRegistro;
    private LocalDateTime fechaLimiteEntrega;
    private LocalTime horaRegistrada;
}
