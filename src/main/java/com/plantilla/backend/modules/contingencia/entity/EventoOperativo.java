package com.plantilla.backend.modules.contingencia.entity;

import com.plantilla.backend.modules.maestro.entity.Aeropuerto;
import com.plantilla.backend.modules.maestro.entity.Vuelo;
import com.plantilla.backend.shared.enums.TipoEvento;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Registro de incidencias operativas.
 * Cancelaciones manuales solo se registran antes del despegue (RAL-07).
 * Activa el proceso de replanificación.
 * Principio SOLID (S): Solo representa la estructura de datos de un evento operativo.
 */
@Entity
@Table(name = "evento_operativo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EventoOperativo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_evento")
    private Integer idEvento;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_evento", nullable = false, length = 30)
    private TipoEvento tipoEvento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_vuelo")
    private Vuelo vuelo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_aeropuerto")
    private Aeropuerto aeropuerto;

    @Column(name = "timestamp_evento", nullable = false)
    private LocalDateTime timestampEvento;

    @Column(name = "descripcion", columnDefinition = "TEXT")
    private String descripcion;

    @Column(name = "registrado_por", length = 100)
    private String registradoPor;

    @Column(name = "es_manual", nullable = false)
    private Boolean esManual = false;
}
