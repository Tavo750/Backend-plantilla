package com.plantilla.backend.shared.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Entidad base con campos de auditoría.
 * Todas las entidades del sistema deben extender esta clase.
 * Principio SOLID (O): Abierto a extensión, cerrado a modificación.
 * Principio SOLID (L): Cualquier entidad hija puede sustituir a BaseEntity.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "creado_en", updatable = false)
    private LocalDateTime creadoEn;

    @Column(name = "actualizado_en")
    private LocalDateTime actualizadoEn;

    /**
     * Asigna la fecha de creación antes de persistir.
     */
    @PrePersist
    protected void onCreate() {
        this.creadoEn = LocalDateTime.now();
        this.actualizadoEn = LocalDateTime.now();
    }

    /**
     * Actualiza la fecha de modificación antes de actualizar.
     */
    @PreUpdate
    protected void onUpdate() {
        this.actualizadoEn = LocalDateTime.now();
    }
}
