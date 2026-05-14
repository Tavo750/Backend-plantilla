package com.plantilla.backend.modules.envio.entity;

import com.plantilla.backend.modules.maestro.entity.Aeropuerto;
import com.plantilla.backend.shared.enums.NivelSemaforo;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Estado actual del almacén en cada aeropuerto.
 * Nivel semáforo calculado con rangos parametrizables (RAL-04).
 * Se actualiza cada vez que entra/sale un envío.
 * Principio SOLID (S): Solo representa el estado de almacenamiento de un aeropuerto.
 */
@Entity
@Table(name = "almacenamiento_aeropuerto")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AlmacenamientoAeropuerto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_almacenamiento")
    private Integer idAlmacenamiento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_aeropuerto", nullable = false)
    private Aeropuerto aeropuerto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_parametro", nullable = false)
    private ParametroSemaforo parametroSemaforo;

    @Column(name = "cantidad_actual", nullable = false)
    private Integer cantidadActual = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "nivel_semaforo", nullable = false, length = 10)
    private NivelSemaforo nivelSemaforo = NivelSemaforo.VERDE;

    @Column(name = "timestamp_calculo", nullable = false)
    private LocalDateTime timestampCalculo;
}
