package com.plantilla.backend.modules.simulacion.entity;

import com.plantilla.backend.modules.maestro.entity.PoliticaEntrega;
import com.plantilla.backend.shared.enums.TipoAlgoritmo;
import com.plantilla.backend.shared.enums.TipoEscenario;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Unifica la configuración de los tres escenarios (RAL-05).
 * El tipo_escenario determina qué campos son relevantes.
 * Se registra el algoritmo para análisis comparativo posterior (RAL-06).
 * Principio SOLID (S): Solo representa la configuración de una simulación.
 */
@Entity
@Table(name = "configuracion_simulacion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ConfiguracionSimulacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_configuracion")
    private Integer idConfiguracion;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_escenario", nullable = false, length = 30)
    private TipoEscenario tipoEscenario;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_algoritmo", nullable = false, length = 10)
    private TipoAlgoritmo tipoAlgoritmo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_politica", nullable = false)
    private PoliticaEntrega politicaEntrega;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDateTime fechaInicio;

    @Column(name = "dias_periodo")
    private Integer diasPeriodo;

    @Column(name = "demanda_inicial")
    private Integer demandaInicial;

    @Column(name = "incremento_demanda")
    private Integer incrementoDemanda;

    @Column(name = "descripcion", length = 200)
    private String descripcion;
}
