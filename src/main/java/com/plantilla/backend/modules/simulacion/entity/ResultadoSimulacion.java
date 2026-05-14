package com.plantilla.backend.modules.simulacion.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Resultado consolidado de una simulación por periodo.
 * Incluye métricas para análisis comparativo entre ALNS y DECO (RAL-06).
 * Principio SOLID (S): Solo representa los resultados de una simulación.
 */
@Entity
@Table(name = "resultado_simulacion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ResultadoSimulacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_resultado")
    private Integer idResultado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_configuracion", nullable = false)
    private ConfiguracionSimulacion configuracionSimulacion;

    @Column(name = "total_entregadas", nullable = false)
    private Integer totalEntregadas = 0;

    @Column(name = "total_retrasadas", nullable = false)
    private Integer totalRetrasadas = 0;

    @Column(name = "total_en_transito", nullable = false)
    private Integer totalEnTransito = 0;

    @Column(name = "total_en_espera", nullable = false)
    private Integer totalEnEspera = 0;

    @Column(name = "tasa_cumplimiento_sla", precision = 5, scale = 4)
    private BigDecimal tasaCumplimientoSla;

    @Column(name = "tiempo_ejecucion_min")
    private Integer tiempoEjecucionMin;

    @Column(name = "fecha_generacion", nullable = false)
    private LocalDateTime fechaGeneracion;
}
