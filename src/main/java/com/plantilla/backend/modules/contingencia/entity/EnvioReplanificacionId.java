package com.plantilla.backend.modules.contingencia.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;

/**
 * Clave primaria compuesta para la tabla envio_replanificacion.
 * Principio SOLID (S): Solo representa la identidad de la relación envío-replanificación.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class EnvioReplanificacionId implements Serializable {

    @Column(name = "id_envio")
    private Integer idEnvio;

    @Column(name = "id_replanificacion")
    private Integer idReplanificacion;
}
