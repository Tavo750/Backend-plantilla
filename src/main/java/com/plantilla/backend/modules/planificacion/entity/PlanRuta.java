package com.plantilla.backend.modules.planificacion.entity;

import com.plantilla.backend.modules.envio.entity.EnvioMaletas;
import com.plantilla.backend.shared.enums.TipoAlgoritmo;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Ruta planificada para un envío.
 * Registra qué algoritmo la generó (ALNS o DECO) para el análisis comparativo (RAL-06).
 * Un envío puede tener varios planes si hubo replanificaciones.
 * Principio SOLID (S): Solo representa la estructura de datos del plan de ruta.
 */
@Entity
@Table(name = "plan_ruta")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PlanRuta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_plan_ruta")
    private Integer idPlanRuta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_envio", nullable = false)
    private EnvioMaletas envioMaletas;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_algoritmo", nullable = false, length = 10)
    private TipoAlgoritmo tipoAlgoritmo;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_limite", nullable = false)
    private LocalDateTime fechaLimite;

    @Column(name = "es_factible", nullable = false)
    private Boolean esFactible = true;

    @Column(name = "cumple_sla", nullable = false)
    private Boolean cumpleSla = true;

    @Column(name = "es_vigente", nullable = false)
    private Boolean esVigente = true;

    @Column(name = "tiempo_computo_ms")
    private Integer tiempoComputoMs;
}
