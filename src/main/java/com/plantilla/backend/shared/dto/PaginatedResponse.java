package com.plantilla.backend.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Respuesta paginada de la API.
 * Coincide con la interfaz PaginatedResponse<T> del frontend Angular.
 * Principio SOLID (I): Interfaz segregada para respuestas paginadas.
 *
 * @param <T> Tipo de los elementos en la página.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaginatedResponse<T> {

    private List<T> items;
    private long totalItems;
    private int page;
    private int pageSize;
    private int totalPages;

    /**
     * Crea una respuesta paginada a partir de los datos proporcionados.
     */
    public static <T> PaginatedResponse<T> of(List<T> items, long totalItems, int page, int pageSize) {
        return PaginatedResponse.<T>builder()
                .items(items)
                .totalItems(totalItems)
                .page(page)
                .pageSize(pageSize)
                .totalPages((int) Math.ceil((double) totalItems / pageSize))
                .build();
    }
}
