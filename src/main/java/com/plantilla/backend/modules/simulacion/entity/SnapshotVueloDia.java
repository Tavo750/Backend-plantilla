package com.plantilla.backend.modules.simulacion.entity;

import com.plantilla.backend.modules.maestro.entity.Vuelo;
import com.plantilla.backend.shared.enums.EstadoVuelo;
import com.plantilla.backend.shared.enums.NivelSemaforo;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Desglosa el uso de cada vuelo por día de simulación.
 * Principio SOLID (S): Solo representa el snapshot de un vuelo en un día dado.
 */
@Entity
@Table(name = "snapshot_vuelo_dia")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SnapshotVueloDia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_snapshot_vuelo")
    private Integer idSnapshotVuelo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_snapshot_diario", nullable = false)
    private SnapshotDiario snapshotDiario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_vuelo", nullable = false)
    private Vuelo vuelo;

    @Column(name = "carga_asignada", nullable = false)
    private Integer cargaAsignada;

    @Column(name = "porcentaje_uso", precision = 5, scale = 2)
    private BigDecimal porcentajeUso;

    @Enumerated(EnumType.STRING)
    @Column(name = "nivel_semaforo", nullable = false, length = 10)
    private NivelSemaforo nivelSemaforo;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", length = 20)
    private EstadoVuelo estado;
}
