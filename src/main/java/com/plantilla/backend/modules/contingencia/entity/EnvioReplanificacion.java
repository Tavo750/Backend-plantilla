package com.plantilla.backend.modules.contingencia.entity;

import com.plantilla.backend.modules.envio.entity.EnvioMaletas;
import com.plantilla.backend.modules.planificacion.entity.PlanRuta;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Detalle por envío de cada proceso de replanificación.
 * Permite saber cuáles maletas fueron rescatadas y cuáles no.
 * Principio SOLID (S): Solo representa la relación entre un envío y su replanificación.
 */
@Entity
@Table(name = "envio_replanificacion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EnvioReplanificacion {

    @EmbeddedId
    private EnvioReplanificacionId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("idEnvio")
    @JoinColumn(name = "id_envio", nullable = false)
    private EnvioMaletas envioMaletas;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("idReplanificacion")
    @JoinColumn(name = "id_replanificacion", nullable = false)
    private Replanificacion replanificacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_plan_ruta_anterior", nullable = false)
    private PlanRuta planRutaAnterior;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_plan_ruta_nuevo")
    private PlanRuta planRutaNuevo;

    @Column(name = "sla_salvado", nullable = false)
    private Boolean slaSalvado = false;
}
