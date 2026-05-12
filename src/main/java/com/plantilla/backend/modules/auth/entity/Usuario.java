package com.plantilla.backend.modules.auth.entity;

import com.plantilla.backend.shared.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entidad que representa un usuario del sistema.
 * Principio SOLID (S): Solo representa la estructura de datos del usuario.
 */
@Entity
@Table(name = "usuarios")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Usuario extends BaseEntity {

    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @Column(name = "apellido_paterno", nullable = false, length = 100)
    private String apellidoPaterno;

    @Column(name = "apellido_materno", length = 100)
    private String apellidoMaterno;

    @Column(name = "correo", nullable = false, unique = true, length = 150)
    private String correo;

    @Column(name = "contrasena", nullable = false)
    private String contrasena;

    @Column(name = "puesto", length = 100)
    private String puesto;

    @Column(name = "foto_url", length = 500)
    private String fotoUrl;

    @Column(name = "estado", nullable = false)
    private Boolean estado = true;
}
