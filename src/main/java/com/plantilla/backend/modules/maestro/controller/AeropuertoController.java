package com.plantilla.backend.modules.maestro.controller;

import com.plantilla.backend.modules.maestro.entity.Aeropuerto;
import com.plantilla.backend.modules.maestro.service.AeropuertoService;
import com.plantilla.backend.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/maestro/aeropuertos")
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

    @GetMapping("/{id}")
    @Operation(summary = "Obtener aeropuerto por ID", description = "Obtiene un aeropuerto específico")
    public ResponseEntity<ApiResponse<Aeropuerto>> obtenerAeropuerto(@PathVariable Integer id) {
        Aeropuerto aeropuerto = aeropuertoService.obtenerAeropuertoPorId(id);
        return ResponseEntity.ok(ApiResponse.success("Aeropuerto recuperado exitosamente", aeropuerto));
    }

    @PostMapping
    @Operation(summary = "Crear aeropuerto", description = "Crea un nuevo aeropuerto")
    public ResponseEntity<ApiResponse<Aeropuerto>> crearAeropuerto(@Valid @RequestBody Aeropuerto aeropuerto) {
        Aeropuerto creado = aeropuertoService.crearAeropuerto(aeropuerto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(creado));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar aeropuerto", description = "Actualiza un aeropuerto existente")
    public ResponseEntity<ApiResponse<Aeropuerto>> actualizarAeropuerto(
            @PathVariable Integer id,
            @Valid @RequestBody Aeropuerto aeropuerto) {
        Aeropuerto actualizado = aeropuertoService.actualizarAeropuerto(id, aeropuerto);
        return ResponseEntity.ok(ApiResponse.success("Aeropuerto actualizado exitosamente", actualizado));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar aeropuerto", description = "Elimina un aeropuerto")
    public ResponseEntity<ApiResponse<Void>> eliminarAeropuerto(@PathVariable Integer id) {
        aeropuertoService.eliminarAeropuerto(id);
        return ResponseEntity.ok(ApiResponse.success("Aeropuerto eliminado exitosamente", null));
    }
}
