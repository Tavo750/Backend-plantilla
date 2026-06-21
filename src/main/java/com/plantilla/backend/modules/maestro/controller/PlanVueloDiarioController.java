package com.plantilla.backend.modules.maestro.controller;

import com.plantilla.backend.modules.maestro.entity.PlanVueloDiario;
import com.plantilla.backend.modules.maestro.service.PlanVueloDiarioService;
import com.plantilla.backend.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/maestro/plan-vuelo-diario")
@RequiredArgsConstructor
@Tag(name = "Plan Vuelo Diario", description = "CRUD para el plan de vuelos diario (se repiten cada día)")
public class PlanVueloDiarioController {

    private final PlanVueloDiarioService service;

    @GetMapping
    @Operation(summary = "Listar todos los vuelos diarios con su estado actual")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listar() {
        return ResponseEntity.ok(ApiResponse.success(service.listarConEstado()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener vuelo diario por ID")
    public ResponseEntity<ApiResponse<PlanVueloDiario>> obtener(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.success(service.obtenerPorId(id)));
    }

    @PostMapping
    @Operation(summary = "Crear vuelo diario")
    public ResponseEntity<ApiResponse<PlanVueloDiario>> crear(@RequestBody PlanVueloDiario vuelo) {
        return ResponseEntity.ok(ApiResponse.created(service.crear(vuelo)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar vuelo diario")
    public ResponseEntity<ApiResponse<PlanVueloDiario>> actualizar(@PathVariable Integer id,
            @RequestBody PlanVueloDiario vuelo) {
        return ResponseEntity.ok(ApiResponse.success(service.actualizar(id, vuelo)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar vuelo diario")
    public ResponseEntity<ApiResponse<Void>> eliminar(@PathVariable Integer id) {
        service.eliminar(id);
        return ResponseEntity.ok(ApiResponse.success("Vuelo diario eliminado", null));
    }

    @PostMapping("/cargar-txt")
    @Operation(
        summary = "Cargar plan de vuelos desde archivo TXT",
        description = "Lee 'planes_vuelo.txt' del classpath:data/ e inserta los registros. Omite duplicados."
    )
    public ResponseEntity<ApiResponse<Map<String, Object>>> cargarTxt(
            @RequestParam(defaultValue = "planes_vuelo.txt") String archivo) {
        return ResponseEntity.ok(ApiResponse.success("Carga completada", service.cargarDesdeTxt(archivo)));
    }
}
