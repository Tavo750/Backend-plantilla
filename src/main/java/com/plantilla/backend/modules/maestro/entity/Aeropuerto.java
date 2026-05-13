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

    @Column(name = "codigo_iata", nullable = false, unique = true, length = 10)
    private String codigoIata;

    @Column(name = "ciudad", nullable = false, length = 100)
    private String ciudad;

    @Column(name = "pais", nullable = false, length = 100)
    private String pais;

    @Enumerated(EnumType.STRING)
    @Column(name = "continente", nullable = false, length = 20)
    private Continente continente;

    @Column(name = "latitud", nullable = false, precision = 10, scale = 6)
    private BigDecimal latitud;

    @Column(name = "longitud", nullable = false, precision = 10, scale = 6)
    private BigDecimal longitud;

    @Column(name = "capacidad_almacenamiento", nullable = false)
    private Integer capacidadAlmacenamiento;

    @Column(name = "activo", nullable = false)
    private Boolean activo = true;
}
