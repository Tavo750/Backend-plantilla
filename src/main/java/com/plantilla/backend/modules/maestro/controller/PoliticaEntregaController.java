package com.plantilla.backend.modules.maestro.controller;

import com.plantilla.backend.modules.maestro.entity.PoliticaEntrega;
import com.plantilla.backend.modules.maestro.service.PoliticaEntregaService;
import com.plantilla.backend.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/maestro/politicas-entrega")
@RequiredArgsConstructor
@Tag(name = "Politicas de Entrega", description = "Endpoints CRUD para políticas de entrega")
public class PoliticaEntregaController {

    private final PoliticaEntregaService politicaEntregaService;

    @GetMapping
    @Operation(summary = "Listar politicas", description = "Obtiene la lista de todas las politicas de entrega")
    public ResponseEntity<ApiResponse<List<PoliticaEntrega>>> listarPoliticas() {
        return ResponseEntity.ok(ApiResponse.success(politicaEntregaService.listarPoliticas()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener politica por ID")
    public ResponseEntity<ApiResponse<PoliticaEntrega>> obtenerPolitica(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.success(politicaEntregaService.obtenerPoliticaPorId(id)));
    }

    @PostMapping
    @Operation(summary = "Crear politica")
    public ResponseEntity<ApiResponse<PoliticaEntrega>> crearPolitica(@RequestBody PoliticaEntrega politica) {
        return ResponseEntity.ok(ApiResponse.created(politicaEntregaService.crearPolitica(politica)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar politica")
    public ResponseEntity<ApiResponse<PoliticaEntrega>> actualizarPolitica(@PathVariable Integer id, @RequestBody PoliticaEntrega politica) {
        return ResponseEntity.ok(ApiResponse.success(politicaEntregaService.actualizarPolitica(id, politica)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar politica")
    public ResponseEntity<ApiResponse<Void>> eliminarPolitica(@PathVariable Integer id) {
        politicaEntregaService.eliminarPolitica(id);
        return ResponseEntity.ok(ApiResponse.success("Política de entrega eliminada", null));
    }
}
