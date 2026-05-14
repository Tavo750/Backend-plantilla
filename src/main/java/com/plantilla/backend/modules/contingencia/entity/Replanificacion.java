package com.plantilla.backend.modules.contingencia.entity;

import com.plantilla.backend.shared.enums.EstadoReplanificacion;
import com.plantilla.backend.shared.enums.TipoAlgoritmo;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Proceso de replanificación dinámica inmediata ante un evento (RAL-03, RAL-07).
 * Registra cuántos envíos lograron salvarse del incumplimiento SLA.
 * Principio SOLID (S): Solo representa la estructura de datos de una replanificación.
 */
@Entity
@Table(name = "replanificacion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Replanificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_replanificacion")
    private Integer idReplanificacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_evento", nullable = false)
    private EventoOperativo eventoOperativo;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_algoritmo", nullable = false, length = 10)
    private TipoAlgoritmo tipoAlgoritmo;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoReplanificacion estado = EstadoReplanificacion.PENDIENTE;

    @Column(name = "timestamp_inicio", nullable = false)
    private LocalDateTime timestampInicio;

    @Column(name = "timestamp_fin")
    private LocalDateTime timestampFin;

    @Column(name = "envios_afectados", nullable = false)
    private Integer enviosAfectados = 0;

    @Column(name = "envios_rescatados", nullable = false)
    private Integer enviosRescatados = 0;

    @Column(name = "tiempo_computo_ms")
    private Integer tiempoComputoMs;
}
