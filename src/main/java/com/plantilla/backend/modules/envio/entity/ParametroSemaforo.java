package com.plantilla.backend.modules.envio.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Rangos parametrizables para la lógica de semáforo (RAL-04).
 * Separado del código para permitir ajuste operativo.
 * Principio SOLID (S): Solo representa los parámetros de umbral del semáforo.
 */
@Entity
@Table(name = "parametro_semaforo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ParametroSemaforo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_parametro")
    private Integer idParametro;

    @Column(name = "entidad", nullable = false, length = 50)
    private String entidad;

    @Column(name = "umbral_ambar", nullable = false, precision = 5, scale = 2)
    private BigDecimal umbralAmbar;

    @Column(name = "umbral_rojo", nullable = false, precision = 5, scale = 2)
    private BigDecimal umbralRojo;

    @Column(name = "activo", nullable = false)
    private Boolean activo = true;
}
