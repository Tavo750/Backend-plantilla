package com.plantilla.backend.modules.envio.entity;

import com.plantilla.backend.modules.maestro.entity.Aeropuerto;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Historial de ubicaciones de un envío. Permite trazabilidad end-to-end (RAL-01).
 * Principio SOLID (S): Solo representa la ubicación actual/histórica de un envío.
 */
@Entity
@Table(name = "ubicacion_envio")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UbicacionEnvio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_ubicacion_envio")
    private Integer idUbicacionEnvio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_envio", nullable = false)
    private EnvioMaletas envioMaletas;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_aeropuerto", nullable = false)
    private Aeropuerto aeropuerto;

    @Column(name = "timestamp_llegada", nullable = false)
    private LocalDateTime timestampLlegada;

    @Column(name = "timestamp_salida")
    private LocalDateTime timestampSalida;

    @Column(name = "es_ubicacion_actual", nullable = false)
    private Boolean esUbicacionActual = true;
}
