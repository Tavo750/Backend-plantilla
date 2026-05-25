package com.plantilla.backend.modules.envio.controller;

import com.plantilla.backend.modules.envio.entity.EnvioMaletas;
import com.plantilla.backend.modules.envio.service.EnvioMaletasService;
import com.plantilla.backend.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/envio/envios-maletas")
@RequiredArgsConstructor
@Tag(name = "Envios", description = "Endpoints CRUD para envios de maletas")
public class EnvioMaletasController {

    private final EnvioMaletasService envioMaletasService;

    @GetMapping
    @Operation(summary = "Listar envios", description = "Obtiene la lista de todos los envios de maletas")
    public ResponseEntity<ApiResponse<List<EnvioMaletas>>> listarEnvios() {
        return ResponseEntity.ok(ApiResponse.success(envioMaletasService.listarEnvios()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener envio por ID")
    public ResponseEntity<ApiResponse<EnvioMaletas>> obtenerEnvio(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.success(envioMaletasService.obtenerEnvioPorId(id)));
    }

    @PostMapping
    @Operation(summary = "Crear envio")
    public ResponseEntity<ApiResponse<EnvioMaletas>> crearEnvio(@RequestBody com.plantilla.backend.modules.envio.dto.EnvioMaletasCreateDTO envioDTO) {
        return ResponseEntity.ok(ApiResponse.created(envioMaletasService.crearEnvio(envioDTO)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar envio")
    public ResponseEntity<ApiResponse<EnvioMaletas>> actualizarEnvio(@PathVariable Integer id,
            @RequestBody EnvioMaletas envio) {
        return ResponseEntity.ok(ApiResponse.success(envioMaletasService.actualizarEnvio(id, envio)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar envio")
    public ResponseEntity<ApiResponse<Void>> eliminarEnvio(@PathVariable Integer id) {
        envioMaletasService.eliminarEnvio(id);
        return ResponseEntity.ok(ApiResponse.success("Envío de maletas eliminado", null));
    }
}
