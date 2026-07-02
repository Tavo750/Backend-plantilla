package com.plantilla.backend.modules.simulacion.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Caché persistente de la fecha estimada de colapso logístico.
 * La búsqueda (demanda diaria vs capacidad de la flota) se ejecuta una sola vez;
 * los siguientes "Simular Colapso" reutilizan este registro.
 */
@Entity
@Table(name = "colapso_cache")
@Getter
@Setter
@NoArgsConstructor
public class ColapsoCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "fecha_colapso_estimada", nullable = false)
    private LocalDateTime fechaColapsoEstimada;

    @Column(name = "fecha_calculo", nullable = false)
    private LocalDateTime fechaCalculo;

    @Column(name = "capacidad_diaria")
    private Long capacidadDiaria;

    @Column(name = "demanda_dia_colapso")
    private Long demandaDiaColapso;
}
