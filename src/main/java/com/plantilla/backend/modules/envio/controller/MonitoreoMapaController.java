package com.plantilla.backend.modules.envio.controller;

import com.plantilla.backend.modules.envio.service.MonitoreoRealTimeService;
import com.plantilla.backend.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Endpoints del módulo Monitoreo Mapa (tiempo real).
 * El reloj es el del servidor (K=1); los vuelos son de plan_vuelo_diario.
 */
@RestController
@RequestMapping("/simulacion")
@RequiredArgsConstructor
@Tag(name = "Monitoreo Mapa", description = "Monitoreo en tiempo real con plan de vuelos diario")
public class MonitoreoMapaController {

    private final MonitoreoRealTimeService monitoreoRealTimeService;

    @GetMapping("/monitoreo/config")
    @Operation(summary = "Configuración del monitoreo (K=1 tiempo real)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getConfig() {
        Map<String, Object> config = new java.util.LinkedHashMap<>();
        config.put("K",  1);
        config.put("SA", 5);
        return ResponseEntity.ok(ApiResponse.success("Configuración del monitoreo", config));
    }

    @PostMapping("/monitoreo/ejecutar")
    @Operation(
        summary = "Activar monitoreo en tiempo real (idempotente)",
        description = "Si ya está activo devuelve el estado sin reiniciar. Arranca automáticamente al inicio."
    )
    public ResponseEntity<ApiResponse<Map<String, Object>>> ejecutar() {
        Map<String, Object> estado = monitoreoRealTimeService.activar();
        return ResponseEntity.ok(ApiResponse.success("Monitoreo activo", estado));
    }

    @PostMapping("/monitoreo/iniciar")
    @Operation(summary = "Alias de /ejecutar")
    public ResponseEntity<ApiResponse<Map<String, Object>>> iniciar() {
        return ejecutar();
    }

    @GetMapping("/monitoreo/estado")
    @Operation(
        summary = "Snapshot actual del monitoreo",
        description = "Devuelve todos los vuelos del día con su estado (EN_VUELO/POR_SALIR/LLEGÓ) y el reloj real."
    )
    public ResponseEntity<ApiResponse<Map<String, Object>>> getEstado() {
        return ResponseEntity.ok(
                ApiResponse.success("Estado del monitoreo", monitoreoRealTimeService.obtenerSnapshot()));
    }

    @PostMapping("/monitoreo/detener")
    @Operation(summary = "Detener el broadcast de ticks (los datos se conservan)")
    public ResponseEntity<ApiResponse<Void>> detener() {
        monitoreoRealTimeService.detener();
        return ResponseEntity.ok(ApiResponse.success("Monitoreo detenido", null));
    }
}
