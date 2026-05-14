package com.plantilla.backend.modules.simulacion.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Fotografía del estado del sistema por día durante la simulación por periodo.
 * Separado de los datos de aeropuertos/vuelos para normalización.
 * Principio SOLID (S): Solo representa el snapshot diario de una simulación.
 */
@Entity
@Table(name = "snapshot_diario")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SnapshotDiario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_snapshot_diario")
    private Integer idSnapshotDiario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_resultado", nullable = false)
    private ResultadoSimulacion resultadoSimulacion;

    @Column(name = "dia", nullable = false)
    private Integer dia;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;

    @Column(name = "entregadas", nullable = false)
    private Integer entregadas = 0;

    @Column(name = "retrasadas", nullable = false)
    private Integer retrasadas = 0;

    @Column(name = "en_transito", nullable = false)
    private Integer enTransito = 0;

    @Column(name = "en_espera", nullable = false)
    private Integer enEspera = 0;
}
