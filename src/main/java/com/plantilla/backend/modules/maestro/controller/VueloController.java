package com.plantilla.backend.modules.maestro.controller;

import com.plantilla.backend.modules.maestro.entity.Vuelo;
import com.plantilla.backend.modules.maestro.repository.VueloRepository;
import com.plantilla.backend.shared.dto.ApiResponse;
import com.plantilla.backend.shared.enums.EstadoVuelo;
import com.plantilla.backend.shared.errors.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/maestro/vuelos")
@RequiredArgsConstructor
@Tag(name = "Vuelos", description = "Gestión de vuelos")
public class VueloController {

    private final VueloRepository vueloRepository;

    /**
     * Cancela un vuelo por su código. La próxima simulación ignorará este vuelo
     * y el ALNS buscará rutas alternativas para los envíos afectados.
     */
    @PatchMapping("/{codigoVuelo}/cancelar")
    @Operation(
            summary = "Cancelar un vuelo",
            description = "Marca el vuelo como CANCELADO. Al re-ejecutar la simulación, " +
                    "el ALNS excluirá este vuelo y re-rutará los envíos afectados automáticamente."
    )
    public ResponseEntity<ApiResponse<Map<String, Object>>> cancelarVuelo(
            @PathVariable String codigoVuelo) {

        Vuelo vuelo = vueloRepository.findByCodigoVuelo(codigoVuelo)
                .orElseThrow(() -> new BusinessException(
                        "No existe el vuelo con código: " + codigoVuelo));

        EstadoVuelo estadoAnterior = vuelo.getEstado();
        vuelo.setEstado(EstadoVuelo.CANCELADO);
        vueloRepository.save(vuelo);

        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("codigoVuelo", codigoVuelo);
        resultado.put("estadoAnterior", estadoAnterior);
        resultado.put("estadoActual", EstadoVuelo.CANCELADO);
        resultado.put("mensaje",
                "Vuelo cancelado. Re-ejecuta la simulación para re-rutear los envíos afectados.");

        return ResponseEntity.ok(ApiResponse.success("Vuelo cancelado correctamente", resultado));
    }

    /**
     * Reactiva un vuelo previamente cancelado.
     */
    @PatchMapping("/{codigoVuelo}/reactivar")
    @Operation(summary = "Reactivar un vuelo cancelado")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reactivarVuelo(
            @PathVariable String codigoVuelo) {

        Vuelo vuelo = vueloRepository.findByCodigoVuelo(codigoVuelo)
                .orElseThrow(() -> new BusinessException(
                        "No existe el vuelo con código: " + codigoVuelo));

        vuelo.setEstado(EstadoVuelo.PROGRAMADO);
        vueloRepository.save(vuelo);

        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("codigoVuelo", codigoVuelo);
        resultado.put("estadoActual", EstadoVuelo.PROGRAMADO);

        return ResponseEntity.ok(ApiResponse.success("Vuelo reactivado correctamente", resultado));
    }
}
