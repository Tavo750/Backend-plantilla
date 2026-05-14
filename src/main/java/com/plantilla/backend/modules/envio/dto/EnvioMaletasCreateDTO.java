package com.plantilla.backend.modules.envio.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
public class EnvioMaletasCreateDTO {
    
    private Integer idAerolinea;
    
    @JsonProperty("id_aeropuerto_origen")
    private Integer idAeropuertoOrigen;
    
    @JsonProperty("id_aeropuerto_destino")
    private Integer idAeropuertoDestino;
    
    private Integer idPolitica;
    
    private Integer cantidad;
    private LocalDateTime fechaRegistro;
    private LocalDateTime fechaLimiteEntrega;
    private LocalTime horaRegistrada;
}
