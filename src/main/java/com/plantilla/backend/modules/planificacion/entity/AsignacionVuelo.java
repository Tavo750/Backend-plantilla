package com.plantilla.backend.modules.planificacion.entity;

import com.plantilla.backend.modules.envio.entity.EnvioMaletas;
import com.plantilla.backend.modules.envio.entity.ParametroSemaforo;
import com.plantilla.backend.modules.maestro.entity.Vuelo;
import com.plantilla.backend.shared.enums.NivelSemaforo;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Vincula la asignación de un envío a un vuelo directamente al plan de ruta que la generó.
 * Permite calcular carga_actual del vuelo en cualquier momento y trazabilidad por algoritmo.
 * Principio SOLID (S): Solo representa la asignación de un envío a un vuelo.
 */
@Entity
@Table(name = "asignacion_vuelo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AsignacionVuelo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_asignacion")
    private Integer idAsignacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_vuelo", nullable = false)
    private Vuelo vuelo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_envio", nullable = false)
    private EnvioMaletas envioMaletas;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_plan_ruta", nullable = false)
    private PlanRuta planRuta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_parametro", nullable = false)
    private ParametroSemaforo parametroSemaforo;

    @Column(name = "cantidad_asignada", nullable = false)
    private Integer cantidadAsignada;

    @Enumerated(EnumType.STRING)
    @Column(name = "nivel_semaforo", nullable = false, length = 10)
    private NivelSemaforo nivelSemaforo = NivelSemaforo.VERDE;

    @Column(name = "estado_asignacion", nullable = false, length = 30)
    private String estadoAsignacion = "ACTIVA";
}
