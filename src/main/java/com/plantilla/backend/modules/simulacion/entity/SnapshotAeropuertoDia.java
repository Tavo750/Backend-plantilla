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
 * Desglosa el estado de cada aeropuerto por día de simulación.
 * Principio SOLID (S): Solo representa el snapshot de un aeropuerto en un día dado.
 */
@Entity
@Table(name = "snapshot_aeropuerto_dia")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SnapshotAeropuertoDia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_snapshot_aeropuerto")
    private Integer idSnapshotAeropuerto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_snapshot_diario", nullable = false)
    private SnapshotDiario snapshotDiario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_aeropuerto", nullable = false)
    private Aeropuerto aeropuerto;

    @Column(name = "almacenamiento_actual", nullable = false)
    private Integer almacenamientoActual;

    @Column(name = "porcentaje_uso", precision = 5, scale = 2)
    private BigDecimal porcentajeUso;

    @Enumerated(EnumType.STRING)
    @Column(name = "nivel_semaforo", nullable = false, length = 10)
    private NivelSemaforo nivelSemaforo;
}
