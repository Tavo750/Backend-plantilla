package com.plantilla.backend.modules.simulacion.entity;

import com.plantilla.backend.modules.maestro.entity.Aeropuerto;
import com.plantilla.backend.shared.enums.NivelSemaforo;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Estado de cada aeropuerto en cada iteración de la simulación hasta colapso.
 * Principio SOLID (S): Solo representa el estado de un aeropuerto durante el colapso.
 */
@Entity
@Table(name = "snapshot_aeropuerto_colapso")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SnapshotAeropuertoColapso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_snap_aero_colapso")
    private Integer idSnapAeroColapso;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_snapshot_colapso", nullable = false)
    private SnapshotColapso snapshotColapso;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_aeropuerto", nullable = false)
    private Aeropuerto aeropuerto;

    @Column(name = "almacenamiento_actual", nullable = false)
    private Integer almacenamientoActual;

    @Column(name = "cantidad_en_cola", nullable = false)
    private Integer cantidadEnCola = 0;

    @Column(name = "porcentaje_utilizacion", precision = 5, scale = 2)
    private BigDecimal porcentajeUtilizacion;

    @Enumerated(EnumType.STRING)
    @Column(name = "nivel_semaforo", nullable = false, length = 10)
    private NivelSemaforo nivelSemaforo;

    @Column(name = "colapsado", nullable = false)
    private Boolean colapsado = false;
}
