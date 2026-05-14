package com.plantilla.backend.modules.maestro.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Aerolínea cliente que solicita el traslado de maletas a Tasf.B2B (modelo B2B
 * exclusivo).
 * Principio SOLID (S): Solo representa la estructura de datos de la aerolínea.
 */
@Entity
@Table(name = "aerolinea")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Aerolinea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_aerolinea")
    private Integer idAerolinea;

    @Column(name = "codigo", nullable = false, unique = true, length = 20)
    private String codigo;

    @Column(name = "nombre", nullable = false, length = 120)
    private String nombre;

    @Column(name = "contrasena", nullable = false, length = 100)
    private String contrasenia;

    @Column(name = "activa", nullable = false)
    private Boolean activa = true;
}
