package com.plantilla.backend.modules.monitoreo.entity;

import com.plantilla.backend.modules.simulacion.entity.ConfiguracionSimulacion;
import com.plantilla.backend.shared.enums.TipoAlgoritmo;
import com.plantilla.backend.shared.enums.TipoEscenario;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Indicadores operativos.
 * Vincula tipo_escenario y tipo_algoritmo para el análisis comparativo ALNS vs DECO (RAL-06).
 * Principio SOLID (S): Solo representa las métricas operativas del sistema.
 */
@Entity
@Table(name = "metrica_operativa")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MetricaOperativa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_metrica")
    private Integer idMetrica;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_configuracion")
    private ConfiguracionSimulacion configuracionSimulacion;

    @Column(name = "fecha_calculo", nullable = false)
    private LocalDateTime fechaCalculo;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_escenario", nullable = false, length = 30)
    private TipoEscenario tipoEscenario;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_algoritmo", length = 10)
    private TipoAlgoritmo tipoAlgoritmo;

    @Column(name = "total_maletas", nullable = false)
    private Integer totalMaletas = 0;

    @Column(name = "en_transito", nullable = false)
    private Integer enTransito = 0;

    @Column(name = "entregadas", nullable = false)
    private Integer entregadas = 0;

    @Column(name = "retrasadas", nullable = false)
    private Integer retrasadas = 0;

    @Column(name = "rutas_activas", nullable = false)
    private Integer rutasActivas = 0;

    @Column(name = "utilizacion_promedio", precision = 5, scale = 4)
    private BigDecimal utilizacionPromedio;

    @Column(name = "tasa_cumplimiento_sla", precision = 5, scale = 4)
    private BigDecimal tasaCumplimientoSla;

    @Column(name = "tasa_retraso", precision = 5, scale = 4)
    private BigDecimal tasaRetraso;
}
