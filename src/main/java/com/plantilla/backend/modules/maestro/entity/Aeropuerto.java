package com.plantilla.backend.modules.maestro.entity;

import com.plantilla.backend.shared.enums.Continente;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Un único aeropuerto por ciudad. Capacidad de almacenamiento entre 500 y 800 maletas (RAL-02).
 * Principio SOLID (S): Solo representa la estructura de datos del aeropuerto.
 */
@Entity
@Table(name = "aeropuerto")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Aeropuerto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_aeropuerto")
    private Integer idAeropuerto;

    @Column(name = "codigo_oaci", nullable = false, unique = true, length = 10)
    private String codigoOaci;

    @Column(name = "ciudad", nullable = false, length = 100)
    private String ciudad;

    @Column(name = "pais", nullable = false, length = 100)
    private String pais;

    @Column(name = "codigo", nullable = false, unique = true, length = 10)
    private String codigo;

    @Column(name = "gmt", nullable = false)
    private Integer gmt;

    @Enumerated(EnumType.STRING)
    @Column(name = "continente", nullable = false, length = 20)
    private Continente continente;

    @Column(name = "latitud", nullable = false, length = 50)
    private String latitud;

    @Column(name = "longitud", nullable = false, length = 50)
    private String longitud;

    @Column(name = "capacidad", nullable = false)
    private Integer capacidad;

    @Column(name = "activo", nullable = false)
    private Boolean activo = true;
}
