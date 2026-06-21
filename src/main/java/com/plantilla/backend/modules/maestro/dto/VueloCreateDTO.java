package com.plantilla.backend.modules.maestro.dto;

import com.plantilla.backend.shared.enums.EstadoVuelo;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class VueloCreateDTO {
    private String codigoVuelo;
    private Integer idAeropuertoOrigen;
    private Integer idAeropuertoDestino;
    private LocalDateTime horaSalida;
    private LocalDateTime horaLlegada;
    private BigDecimal duracionHoras;
    private Integer capacidadMaxima;
    private EstadoVuelo estado;
    private Boolean esIntercontinental;
}
