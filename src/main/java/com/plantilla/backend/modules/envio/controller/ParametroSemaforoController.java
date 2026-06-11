package com.plantilla.backend.modules.envio.controller;

import com.plantilla.backend.modules.envio.entity.ParametroSemaforo;
import com.plantilla.backend.modules.envio.repository.ParametroSemaforoRepository;
import com.plantilla.backend.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/configuracion/semaforo")
@RequiredArgsConstructor
@Tag(name = "Parámetros de Semáforo", description = "Configuración de umbrales VERDE/AMBAR/ROJO para ocupación")
public class ParametroSemaforoController {

    private final ParametroSemaforoRepository parametroSemaforoRepository;

    @GetMapping
    @Operation(summary = "Listar todos los parámetros de semáforo")
    public ResponseEntity<ApiResponse<List<ParametroSemaforo>>> listar() {
        return ResponseEntity.ok(
            ApiResponse.success("Parámetros de semáforo", parametroSemaforoRepository.findAll())
        );
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener parámetro por ID")
    public ResponseEntity<ApiResponse<ParametroSemaforo>> getById(@PathVariable Integer id) {
        return parametroSemaforoRepository.findById(id)
            .map(p -> ResponseEntity.ok(ApiResponse.success("Parámetro encontrado", p)))
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @Operation(summary = "Crear nuevo parámetro de semáforo")
    public ResponseEntity<ApiResponse<ParametroSemaforo>> crear(@RequestBody ParametroSemaforo nuevo) {
        nuevo.setIdParametro(null);
        ParametroSemaforo saved = parametroSemaforoRepository.save(nuevo);
        return ResponseEntity.ok(ApiResponse.success("Parámetro creado", saved));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar umbrales de un parámetro existente")
    public ResponseEntity<ApiResponse<ParametroSemaforo>> actualizar(
            @PathVariable Integer id,
            @RequestBody ParametroSemaforo body) {
        return parametroSemaforoRepository.findById(id)
            .map(p -> {
                p.setUmbralAmbar(body.getUmbralAmbar());
                p.setUmbralRojo(body.getUmbralRojo());
                p.setActivo(body.getActivo());
                if (body.getEntidad() != null) p.setEntidad(body.getEntidad());
                ParametroSemaforo saved = parametroSemaforoRepository.save(p);
                return ResponseEntity.ok(ApiResponse.success("Parámetro actualizado", saved));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar parámetro de semáforo")
    public ResponseEntity<ApiResponse<Void>> eliminar(@PathVariable Integer id) {
        if (!parametroSemaforoRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        parametroSemaforoRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.success("Parámetro eliminado", null));
    }
}
