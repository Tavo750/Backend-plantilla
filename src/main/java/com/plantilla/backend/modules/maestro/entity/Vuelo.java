package com.plantilla.backend.modules.maestro.entity;

import com.plantilla.backend.shared.enums.EstadoVuelo;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Vuelo disponible para trasladar maletas. Capacidad varía según ruta (RAL-02).
 * No tiene carga_actual — se calcula desde Asignacion_Vuelo.
 * Principio SOLID (S): Solo representa la estructura de datos del vuelo.
 */
@Entity
@Table(name = "vuelo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Vuelo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_vuelo")
    private Integer idVuelo;

    @Column(name = "codigo_vuelo", nullable = false, unique = true, length = 30)
    private String codigoVuelo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_aeropuerto_origen", nullable = false)
    private Aeropuerto aeropuertoOrigen;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_aeropuerto_destino", nullable = false)
    private Aeropuerto aeropuertoDestino;

    @Column(name = "hora_salida", nullable = false)
    private LocalDateTime horaSalida;

    @Column(name = "hora_llegada", nullable = false)
    private LocalDateTime horaLlegada;

    @Column(name = "duracion_horas", nullable = false, precision = 4, scale = 2)
    private BigDecimal duracionHoras;

    @Column(name = "capacidad_maxima", nullable = false)
    private Integer capacidadMaxima;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoVuelo estado = EstadoVuelo.PROGRAMADO;

    @Column(name = "es_intercontinental", nullable = false)
    private Boolean esIntercontinental = false;
}
