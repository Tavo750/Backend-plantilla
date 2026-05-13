package com.plantilla.backend.modules.planificacion.entity;

import com.plantilla.backend.modules.maestro.entity.Aeropuerto;
import com.plantilla.backend.modules.maestro.entity.Vuelo;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Cada tramo de un plan de ruta.
 * Incluye holgura para que el algoritmo maximice el balance de carga (RAL-03).
 * Principio SOLID (S): Solo representa un segmento dentro de un plan de ruta.
 */
@Entity
@Table(name = "tramo_ruta")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TramoRuta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_tramo_ruta")
    private Integer idTramoRuta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_plan_ruta", nullable = false)
    private PlanRuta planRuta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_vuelo", nullable = false)
    private Vuelo vuelo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_aeropuerto_salida", nullable = false)
    private Aeropuerto aeropuertoSalida;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_aeropuerto_llegada", nullable = false)
    private Aeropuerto aeropuertoLlegada;

    @Column(name = "orden", nullable = false)
    private Integer orden;

    @Column(name = "salida_programada", nullable = false)
    private LocalDateTime salidaProgramada;

    @Column(name = "llegada_programada", nullable = false)
    private LocalDateTime llegadaProgramada;

    @Column(name = "cantidad_asignada", nullable = false)
    private Integer cantidadAsignada;

    @Column(name = "holgura_horas", nullable = false, precision = 4, scale = 2)
    private BigDecimal holguraHoras = BigDecimal.ZERO;
}
