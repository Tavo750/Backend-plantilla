package com.plantilla.backend.modules.clientes.entity;

import com.plantilla.backend.shared.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entidad que representa un cliente del sistema.
 * Módulo de ejemplo para demostrar la arquitectura DDD.
 * Principio SOLID (S): Solo representa la estructura de datos del cliente.
 */
@Entity
@Table(name = "clientes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Cliente extends BaseEntity {

    @Column(name = "razon_social", nullable = false, length = 200)
    private String razonSocial;

    @Column(name = "ruc", nullable = false, unique = true, length = 20)
    private String ruc;

    @Column(name = "direccion", length = 300)
    private String direccion;

    @Column(name = "telefono", length = 20)
    private String telefono;

    @Column(name = "correo", length = 150)
    private String correo;

    @Column(name = "contacto_principal", length = 200)
    private String contactoPrincipal;

    @Column(name = "estado", nullable = false)
    private Boolean estado = true;
}
