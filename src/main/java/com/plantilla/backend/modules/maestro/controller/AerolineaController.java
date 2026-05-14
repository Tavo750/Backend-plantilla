package com.plantilla.backend.modules.maestro.controller;

import com.plantilla.backend.modules.maestro.entity.Aerolinea;
import com.plantilla.backend.modules.maestro.service.AerolineaService;
import com.plantilla.backend.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/maestro/aerolineas")
@RequiredArgsConstructor
@Tag(name = "Aerolineas", description = "Endpoints CRUD para aerolíneas")
public class AerolineaController {

    private final AerolineaService aerolineaService;

    @GetMapping
    @Operation(summary = "Listar aerolíneas", description = "Obtiene la lista de todas las aerolíneas")
    public ResponseEntity<ApiResponse<List<Aerolinea>>> listarAerolineas() {
        return ResponseEntity.ok(ApiResponse.success(aerolineaService.listarAerolineas()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener aerolínea por ID")
    public ResponseEntity<ApiResponse<Aerolinea>> obtenerAerolinea(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.success(aerolineaService.obtenerAerolineaPorId(id)));
    }

    @PostMapping
    @Operation(summary = "Crear aerolínea")
    public ResponseEntity<ApiResponse<Aerolinea>> crearAerolinea(@RequestBody Aerolinea aerolinea) {
        return ResponseEntity.ok(ApiResponse.created(aerolineaService.crearAerolinea(aerolinea)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar aerolínea")
    public ResponseEntity<ApiResponse<Aerolinea>> actualizarAerolinea(@PathVariable Integer id, @RequestBody Aerolinea aerolinea) {
        return ResponseEntity.ok(ApiResponse.success(aerolineaService.actualizarAerolinea(id, aerolinea)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar aerolínea")
    public ResponseEntity<ApiResponse<Void>> eliminarAerolinea(@PathVariable Integer id) {
        aerolineaService.eliminarAerolinea(id);
        return ResponseEntity.ok(ApiResponse.success("Aerolínea eliminada", null));
    }
}
