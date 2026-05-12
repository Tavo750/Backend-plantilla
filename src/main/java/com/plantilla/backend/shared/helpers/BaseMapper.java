package com.plantilla.backend.shared.helpers;

import java.util.List;

/**
 * Interfaz genérica de mapeo entre Entity, Request DTO y Response DTO.
 * Principio SOLID (I): Interfaz segregada exclusivamente para operaciones de mapeo.
 * Principio SOLID (D): Las clases dependen de esta abstracción, no de implementaciones concretas.
 *
 * @param <E> Tipo de la entidad.
 * @param <RQ> Tipo del DTO de petición (Request).
 * @param <RS> Tipo del DTO de respuesta (Response).
 */
public interface BaseMapper<E, RQ, RS> {

    /**
     * Convierte un DTO de petición a entidad.
     */
    E toEntity(RQ request);

    /**
     * Convierte una entidad a DTO de respuesta.
     */
    RS toResponse(E entity);

    /**
     * Convierte una lista de entidades a lista de DTOs de respuesta.
     */
    default List<RS> toResponseList(List<E> entities) {
        return entities.stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Actualiza una entidad existente con los datos de un DTO de petición.
     */
    void updateEntity(RQ request, E entity);
}
