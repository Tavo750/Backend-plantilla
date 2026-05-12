package com.plantilla.backend.shared.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Respuesta genérica de la API.
 * Coincide con la interfaz ApiResponse<T> del frontend Angular.
 * Principio SOLID (I): Interfaz segregada para respuestas HTTP estándar.
 *
 * @param <T> Tipo de dato contenido en la respuesta.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private int status;
    private String message;
    private T data;
    private List<String> errors;

    // =====================================================================
    // Factory methods para respuestas comunes
    // =====================================================================

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .status(200)
                .message("Operación exitosa")
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .status(200)
                .message(message)
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> created(T data) {
        return ApiResponse.<T>builder()
                .status(201)
                .message("Recurso creado exitosamente")
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> error(int status, String message, List<String> errors) {
        return ApiResponse.<T>builder()
                .status(status)
                .message(message)
                .errors(errors)
                .build();
    }
}
