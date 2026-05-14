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
 * Evolución progresiva del sistema durante la simulación hasta colapso.
 * Los datos por aeropuerto se guardan en tablas relacionales, no en JSONB.
 * Principio SOLID (S): Solo representa un snapshot de iteración en la simulación de colapso.
 */
@Entity
@Table(name = "snapshot_colapso")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SnapshotColapso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_snapshot_colapso")
    private Integer idSnapshotColapso;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_resultado_colapso", nullable = false)
    private ResultadoColapso resultadoColapso;

    @Column(name = "iteracion", nullable = false)
    private Integer iteracion;

    @Column(name = "timestamp_registro", nullable = false)
    private LocalDateTime timestampRegistro;

    @Column(name = "total_entregadas", nullable = false)
    private Integer totalEntregadas = 0;

    @Column(name = "total_retrasadas", nullable = false)
    private Integer totalRetrasadas = 0;

    @Column(name = "total_en_espera", nullable = false)
    private Integer totalEnEspera = 0;

    @Column(name = "total_en_transito", nullable = false)
    private Integer totalEnTransito = 0;

    @Column(name = "score_colapso", precision = 10, scale = 4)
    private BigDecimal scoreColapso;

    @Enumerated(EnumType.STRING)
    @Column(name = "nivel_colapso", nullable = false, length = 15)
    private NivelColapso nivelColapso;
}
