package com.plantilla.backend.infrastructure.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filtro de logging para registrar todas las solicitudes HTTP.
 * Principio SOLID (S): Solo responsable de registrar información de requests.
 */
@Slf4j
@Component
public class LoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        long startTime = System.currentTimeMillis();

        try {
            filterChain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            log.info("[{}] {} {} - {} ({}ms)",
                    request.getMethod(),
                    request.getRequestURI(),
                    request.getQueryString() != null ? "?" + request.getQueryString() : "",
                    response.getStatus(),
                    duration);
        }
    }
}
