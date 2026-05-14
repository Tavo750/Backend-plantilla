package com.plantilla.backend.modules.envio.entity;

import com.plantilla.backend.modules.maestro.entity.Aerolinea;
import com.plantilla.backend.modules.maestro.entity.Aeropuerto;
import com.plantilla.backend.modules.maestro.entity.PoliticaEntrega;
import com.plantilla.backend.shared.enums.EstadoMaleta;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Lote de maletas estandarizadas enviadas por una aerolínea (RAL-01).
 * La ubicación actual se gestiona en UbicacionEnvio, no aquí.
 * Principio SOLID (S): Solo representa la estructura de datos del envío de
 * maletas.
 */
@Entity
@Table(name = "envio_maletas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EnvioMaletas {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_envio")
    private Integer idEnvio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_aerolinea", nullable = false)
    private Aerolinea aerolinea;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_aeropuerto_origen", nullable = false)
    private Aeropuerto aeropuertoOrigen;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_aeropuerto_destino", nullable = false)
    private Aeropuerto aeropuertoDestino;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_politica", nullable = false)
    private PoliticaEntrega politicaEntrega;

    @Column(name = "cantidad", nullable = false)
    private Integer cantidad;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoMaleta estado = EstadoMaleta.REGISTRADA;

    @Column(name = "fecha_registro", nullable = false)
    private LocalDateTime fechaRegistro;

    @Column(name = "fecha_limite_entrega", nullable = false)
    private LocalDateTime fechaLimiteEntrega;

    @Column(name = "hora_registrada", nullable = false)
    private LocalTime horaRegistrada;

    @Column(name = "prioridad", nullable = false)
    private Integer prioridad = 1;
}
