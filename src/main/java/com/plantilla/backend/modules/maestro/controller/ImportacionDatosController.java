package com.plantilla.backend.modules.maestro.controller;

import com.plantilla.backend.modules.envio.service.ImportacionEnviosService;
import com.plantilla.backend.modules.maestro.service.ImportacionVuelosService;
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
@RequestMapping("/maestro/importacion")
@RequiredArgsConstructor
@Tag(name = "Importación de datos", description = "Carga inicial de datos para simulación")
public class ImportacionDatosController {

    private final ImportacionVuelosService importacionVuelosService;
    private final ImportacionEnviosService importacionEnviosService;

    @PostMapping("/vuelos")
    @Operation(summary = "Importar planes de vuelo", description = "Carga planes_vuelo.txt y genera vuelos para N días")
    public ResponseEntity<ApiResponse<Map<String, Object>>> importarVuelos(
            @RequestParam(defaultValue = "2026-01-02")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate fechaInicio,

            @RequestParam(defaultValue = "5")
            Integer dias
    ) {
        Map<String, Object> resultado = importacionVuelosService.importarPlanesVuelo(fechaInicio, dias);

        return ResponseEntity.ok(
                ApiResponse.success("Planes de vuelo importados correctamente", resultado)
        );
    }

    @PostMapping("/envios")
    @Operation(summary = "Importar envíos", description = "Carga un archivo de envíos preliminares para la simulación")
    public ResponseEntity<ApiResponse<Map<String, Object>>> importarEnvios(
            @RequestParam(defaultValue = "_envios_SBBR_.txt")
            String archivo,

            @RequestParam(defaultValue = "SBBR")
            String origen,

            @RequestParam(defaultValue = "2026-01-02")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate fechaInicio,

            @RequestParam(defaultValue = "5")
            Integer dias
    ) {
        Map<String, Object> resultado = importacionEnviosService.importarEnvios(
                archivo,
                origen,
                fechaInicio,
                dias
        );

        return ResponseEntity.ok(
                ApiResponse.success("Envíos importados correctamente", resultado)
        );
    }
}