package com.plantilla.backend.modules.simulacion.entity;

import com.plantilla.backend.shared.enums.NivelColapso;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Resultado final de la simulación hasta colapso logístico (RAL-05).
 * Principio SOLID (S): Solo representa el resultado de la simulación de colapso.
 */
@Entity
@Table(name = "resultado_colapso")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ResultadoColapso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_resultado_colapso")
    private Integer idResultadoColapso;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_configuracion", nullable = false)
    private ConfiguracionSimulacion configuracionSimulacion;

    @Column(name = "timestamp_colapso")
    private LocalDateTime timestampColapso;

    @Enumerated(EnumType.STRING)
    @Column(name = "nivel_colapso_final", nullable = false, length = 15)
    private NivelColapso nivelColapsoFinal;

    @Column(name = "aeropuertos_colapsados", nullable = false)
    private Integer aeropuertosColapsados = 0;

    @Column(name = "score_colapso", precision = 10, scale = 4)
    private BigDecimal scoreColapso;

    @Column(name = "iteraciones_ejecutadas", nullable = false)
    private Integer iteracionesEjecutadas = 0;
}
