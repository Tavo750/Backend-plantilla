package com.plantilla.backend.modules.maestro.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

/**
 * Plan de vuelo diario: vuelos que se repiten todos los días en las mismas horas.
 * La fecha no se almacena — se combina con la fecha del servidor en tiempo de ejecución.
 */
@Entity
@Table(name = "plan_vuelo_diario",
       indexes = {
           @Index(name = "idx_pvd_origen", columnList = "codigo_origen"),
           @Index(name = "idx_pvd_destino", columnList = "codigo_destino"),
           @Index(name = "idx_pvd_hora_salida", columnList = "hora_salida")
       })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class PlanVueloDiario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "codigo_origen", nullable = false, length = 10)
    private String codigoOrigen;

    @Column(name = "codigo_destino", nullable = false, length = 10)
    private String codigoDestino;

    @Column(name = "hora_salida", nullable = false)
    private LocalTime horaSalida;

    @Column(name = "hora_llegada", nullable = false)
    private LocalTime horaLlegada;

    @Column(name = "capacidad", nullable = false)
    private Integer capacidad;
}
