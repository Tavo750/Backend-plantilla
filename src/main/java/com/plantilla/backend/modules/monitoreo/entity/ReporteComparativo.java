package com.plantilla.backend.modules.monitoreo.entity;

import com.plantilla.backend.modules.simulacion.entity.ResultadoSimulacion;
import com.plantilla.backend.shared.enums.TipoAlgoritmo;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Análisis comparativo de los dos algoritmos metaheurísticos
 * bajo las mismas condiciones de estrés (RAL-06 — diseño de experimentos).
 * Principio SOLID (S): Solo representa los datos del reporte comparativo.
 */
@Entity
@Table(name = "reporte_comparativo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReporteComparativo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_reporte")
    private Integer idReporte;

    @Column(name = "fecha_generacion", nullable = false)
    private LocalDateTime fechaGeneracion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_resultado_alns")
    private ResultadoSimulacion resultadoAlns;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_resultado_deco")
    private ResultadoSimulacion resultadoDeco;

    @Column(name = "tasa_sla_alns", precision = 5, scale = 4)
    private BigDecimal tasaSlaAlns;

    @Column(name = "tasa_sla_deco", precision = 5, scale = 4)
    private BigDecimal tasaSlaDeco;

    @Column(name = "tiempo_computo_alns")
    private Integer tiempoComputoAlns;

    @Column(name = "tiempo_computo_deco")
    private Integer tiempoComputoDeco;

    @Enumerated(EnumType.STRING)
    @Column(name = "algoritmo_ganador", length = 10)
    private TipoAlgoritmo algoritmoGanador;

    @Column(name = "observaciones", columnDefinition = "TEXT")
    private String observaciones;
}
