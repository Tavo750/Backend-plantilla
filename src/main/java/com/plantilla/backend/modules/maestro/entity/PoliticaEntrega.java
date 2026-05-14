package com.plantilla.backend.modules.maestro.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Política SLA de Tasf.B2B. Solo debe haber un registro activo a la vez.
 * Principio SOLID (S): Solo representa la estructura de datos de la política de
 * entrega.
 */
@Entity
@Table(name = "politica_entrega")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PoliticaEntrega {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_politica")
    private Integer idPolitica;

    @Column(name = "dias_mismo_continente", nullable = false)
    private Integer diasMismoContinente = 1;

    @Column(name = "dias_distinto_continente", nullable = false)
    private Integer diasDistintoContinente = 2;

    // @Column(name = "horas_transito_intra", nullable = false, precision = 4, scale
    // = 2)
    // private BigDecimal horasTransitoIntra = new BigDecimal("0.50");

    // @Column(name = "horas_transito_inter", nullable = false, precision = 4, scale
    // = 2)
    // private BigDecimal horasTransitoInter = new BigDecimal("1.00");

    @Column(name = "activa", nullable = false)
    private Boolean activa = true;
}
