package com.plantilla.backend.modules.maestro.controller;

import com.plantilla.backend.modules.maestro.entity.Aeropuerto;
import com.plantilla.backend.modules.maestro.service.AeropuertoService;
import com.plantilla.backend.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/aeropuertos")
@RequiredArgsConstructor
@Tag(name = "Aeropuertos", description = "Endpoints de gestión de aeropuertos")
public class AeropuertoController {

    private final AeropuertoService aeropuertoService;

    @GetMapping
    @Operation(summary = "Listar aeropuertos", description = "Obtiene la lista de todos los aeropuertos")
    public ResponseEntity<ApiResponse<List<Aeropuerto>>> listarAeropuertos() {
        List<Aeropuerto> aeropuertos = aeropuertoService.listarAeropuertos();
        return ResponseEntity.ok(ApiResponse.success("Lista de aeropuertos recuperada exitosamente", aeropuertos));
    }
}
