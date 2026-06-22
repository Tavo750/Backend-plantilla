package com.plantilla.backend.modules.envio.controller;

import com.plantilla.backend.modules.envio.entity.EnvioMaletas;
import com.plantilla.backend.modules.envio.service.EnvioMaletasService;
import com.plantilla.backend.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
    @Operation(summary = "Listar envios", description = "Obtiene la lista paginada de envios de maletas. "
            + "Parámetros opcionales: page (default 0), size (default 50), sort (default fechaRegistro,desc)")
    public ResponseEntity<ApiResponse<Page<EnvioMaletas>>> listarEnvios(
            @RequestParam(defaultValue = "0")   int page,
            @RequestParam(defaultValue = "50")  int size,
            @RequestParam(defaultValue = "fechaRegistro,desc") String sort) {

        String[] sortParts = sort.split(",");
        Sort.Direction dir = sortParts.length > 1 && sortParts[1].equalsIgnoreCase("asc")
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, Math.min(size, 200), Sort.by(dir, sortParts[0]));

        return ResponseEntity.ok(ApiResponse.success(envioMaletasService.listarEnviosPaginado(pageable)));
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

    @PostMapping("/batch")
    @Operation(summary = "Crear envios en lote",
               description = "Crea múltiples envíos en una sola transacción. Usado por la carga masiva CSV.")
    public ResponseEntity<ApiResponse<List<EnvioMaletas>>> crearEnviosBatch(
            @RequestBody List<com.plantilla.backend.modules.envio.dto.EnvioMaletasCreateDTO> envios) {
        return ResponseEntity.ok(ApiResponse.created(envioMaletasService.crearEnviosBatch(envios)));
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
