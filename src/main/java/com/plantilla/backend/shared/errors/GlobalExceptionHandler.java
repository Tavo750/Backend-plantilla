package com.plantilla.backend.shared.errors;

import com.plantilla.backend.shared.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;

/**
 * Manejador global de excepciones.
 * Principio SOLID (S): Solo responsable de traducir excepciones a respuestas HTTP consistentes.
 * Principio SOLID (O): Abierto a extensión agregando nuevos @ExceptionHandler.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Maneja errores de validación (campos inválidos).
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidationErrors(MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .toList();

        log.warn("Error de validación: {}", errors);

        return ResponseEntity.badRequest().body(
                ApiResponse.error(HttpStatus.BAD_REQUEST.value(), "Error de validación", errors));
    }

    /**
     * Maneja errores de recurso no encontrado (404).
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleResourceNotFound(ResourceNotFoundException ex) {
        log.warn("Recurso no encontrado: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                ApiResponse.error(HttpStatus.NOT_FOUND.value(), ex.getMessage(), null));
    }

    /**
     * Maneja errores de lógica de negocio (400).
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Object>> handleBusinessException(BusinessException ex) {
        log.warn("Error de negocio [{}]: {}", ex.getErrorCode(), ex.getMessage());

        return ResponseEntity.badRequest().body(
                ApiResponse.error(HttpStatus.BAD_REQUEST.value(), ex.getMessage(), List.of(ex.getErrorCode())));
    }

    /**
     * Maneja errores de credenciales inválidas (401).
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Object>> handleBadCredentials(BadCredentialsException ex) {
        log.warn("Credenciales inválidas: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                ApiResponse.error(HttpStatus.UNAUTHORIZED.value(), "Credenciales inválidas", null));
    }

    /**
     * Maneja timeout de peticiones asíncronas (SSE / streaming de larga duración).
     * No se registra como ERROR: es comportamiento normal cuando el cliente cierra
     * la conexión o el contenedor expira la petición async antes de que finalice la simulación.
     */
    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public ResponseEntity<Void> handleAsyncTimeout(AsyncRequestTimeoutException ex) {
        log.debug("Petición async/SSE expirada (AsyncRequestTimeoutException) — descartado silenciosamente");
        return ResponseEntity.noContent().build();
    }

    /**
     * Maneja cualquier excepción no controlada (500).
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleGenericException(Exception ex) {
        log.error("Error interno del servidor: ", ex);
        // ex.getMessage() puede ser null (p.ej. NullPointerException sin mensaje, AsyncRequestTimeoutException)
        // List.of(null) lanza NullPointerException en Java — usar valor de respaldo
        String detalle = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
        return ResponseEntity.internalServerError().body(
                ApiResponse.error(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "Error interno del servidor", List.of(detalle)));
    }
}
