package com.plantilla.backend.modules.envio.controller;

import com.plantilla.backend.modules.envio.dto.EnvioMaletasCreateDTO;
import com.plantilla.backend.modules.envio.entity.EnvioDiario;
import com.plantilla.backend.modules.envio.service.EnvioDiarioService;
import com.plantilla.backend.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/envio/envio-diario")
@RequiredArgsConstructor
@Tag(name = "Envío Diario", description = "Endpoints CRUD para envíos diarios")
public class EnvioDiarioController {

    private final EnvioDiarioService envioDiarioService;

    @GetMapping
    @Operation(summary = "Listar envíos diarios", description = "Obtiene la lista de todos los envíos diarios")
    public ResponseEntity<ApiResponse<List<EnvioDiario>>> listarEnvios() {
        return ResponseEntity.ok(ApiResponse.success(envioDiarioService.listarEnvios()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener envío diario por ID")
    public ResponseEntity<ApiResponse<EnvioDiario>> obtenerEnvio(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.success(envioDiarioService.obtenerEnvioPorId(id)));
    }

    @PostMapping
    @Operation(summary = "Crear envío diario")
    public ResponseEntity<ApiResponse<EnvioDiario>> crearEnvio(@RequestBody EnvioMaletasCreateDTO dto) {
        return ResponseEntity.ok(ApiResponse.created(envioDiarioService.crearEnvio(dto)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar envío diario")
    public ResponseEntity<ApiResponse<EnvioDiario>> actualizarEnvio(@PathVariable Integer id,
            @RequestBody EnvioDiario envio) {
        return ResponseEntity.ok(ApiResponse.success(envioDiarioService.actualizarEnvio(id, envio)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar envío diario")
    public ResponseEntity<ApiResponse<Void>> eliminarEnvio(@PathVariable Integer id) {
        envioDiarioService.eliminarEnvio(id);
        return ResponseEntity.ok(ApiResponse.success("Envío diario eliminado", null));
    }
}
