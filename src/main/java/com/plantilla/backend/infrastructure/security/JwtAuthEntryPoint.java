package com.plantilla.backend.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantilla.backend.shared.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * Punto de entrada para errores de autenticación (401 Unauthorized).
 * Principio SOLID (S): Solo maneja respuestas de error de autenticación.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException {

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpStatus.UNAUTHORIZED.value());

        ApiResponse<Object> apiResponse = ApiResponse.builder()
                .status(HttpStatus.UNAUTHORIZED.value())
                .message("No autorizado. Token inválido o ausente.")
                .data(null)
                .errors(List.of(authException.getMessage()))
                .build();

        objectMapper.writeValue(response.getOutputStream(), apiResponse);
    }
}
