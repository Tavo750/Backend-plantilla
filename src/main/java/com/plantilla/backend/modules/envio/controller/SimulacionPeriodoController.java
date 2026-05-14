package com.plantilla.backend.modules.envio.controller;

import com.plantilla.backend.modules.envio.service.SimulacionPeriodoService;
import com.plantilla.backend.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/simulacion")
@RequiredArgsConstructor
@Tag(name = "Simulación de periodo", description = "Endpoints para ejecutar simulación de 5 días")
public class SimulacionPeriodoController {

    private final SimulacionPeriodoService simulacionPeriodoService;

    @PostMapping("/periodo")
    @Operation(summary = "Ejecutar simulación de periodo", description = "Simula la asignación de envíos a vuelos para N días")
    public ResponseEntity<ApiResponse<Map<String, Object>>> simularPeriodo(
            @RequestParam(defaultValue = "2026-01-02")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate fechaInicio,

            @RequestParam(defaultValue = "5")
            Integer dias
    ) {
        Map<String, Object> resultado = simulacionPeriodoService.simularPeriodo(fechaInicio, dias);

        return ResponseEntity.ok(
                ApiResponse.success("Simulación de periodo ejecutada correctamente", resultado)
        );
    }
}