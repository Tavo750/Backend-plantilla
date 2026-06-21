package com.plantilla.backend.modules.maestro.controller;

import com.plantilla.backend.modules.maestro.dto.VueloCreateDTO;
import com.plantilla.backend.modules.maestro.entity.Aeropuerto;
import com.plantilla.backend.modules.maestro.entity.Vuelo;
import com.plantilla.backend.modules.maestro.repository.AeropuertoRepository;
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
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/maestro/vuelos")
@RequiredArgsConstructor
@Tag(name = "Vuelos", description = "Gestión de vuelos")
public class VueloController {

    private final VueloRepository vueloRepository;
    private final AeropuertoRepository aeropuertoRepository;

    // ── CRUD ─────────────────────────────────────────────────────

    @GetMapping
    @Operation(summary = "Listar vuelos", description = "Obtiene la lista de todos los vuelos")
    public ResponseEntity<ApiResponse<List<Vuelo>>> listarVuelos() {
        return ResponseEntity.ok(ApiResponse.success(vueloRepository.findAll()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener vuelo por ID")
    public ResponseEntity<ApiResponse<Vuelo>> obtenerVuelo(@PathVariable Integer id) {
        Vuelo vuelo = vueloRepository.findById(id)
                .orElseThrow(() -> new BusinessException("No existe el vuelo con id: " + id));
        return ResponseEntity.ok(ApiResponse.success(vuelo));
    }

    @PostMapping
    @Operation(summary = "Crear vuelo")
    public ResponseEntity<ApiResponse<Vuelo>> crearVuelo(@RequestBody VueloCreateDTO dto) {
        if (vueloRepository.existsByCodigoVuelo(dto.getCodigoVuelo())) {
            throw new BusinessException("Ya existe un vuelo con el código: " + dto.getCodigoVuelo());
        }

        Aeropuerto origen = aeropuertoRepository.findById(dto.getIdAeropuertoOrigen())
                .orElseThrow(() -> new BusinessException("Aeropuerto origen no encontrado"));
        Aeropuerto destino = aeropuertoRepository.findById(dto.getIdAeropuertoDestino())
                .orElseThrow(() -> new BusinessException("Aeropuerto destino no encontrado"));

        Vuelo vuelo = new Vuelo();
        vuelo.setCodigoVuelo(dto.getCodigoVuelo());
        vuelo.setAeropuertoOrigen(origen);
        vuelo.setAeropuertoDestino(destino);
        vuelo.setHoraSalida(dto.getHoraSalida());
        vuelo.setHoraLlegada(dto.getHoraLlegada());
        vuelo.setDuracionHoras(dto.getDuracionHoras());
        vuelo.setCapacidadMaxima(dto.getCapacidadMaxima());
        vuelo.setEstado(dto.getEstado() != null ? dto.getEstado() : EstadoVuelo.PROGRAMADO);
        vuelo.setEsIntercontinental(dto.getEsIntercontinental() != null ? dto.getEsIntercontinental() : false);

        return ResponseEntity.ok(ApiResponse.created(vueloRepository.save(vuelo)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar vuelo")
    public ResponseEntity<ApiResponse<Vuelo>> actualizarVuelo(@PathVariable Integer id,
            @RequestBody VueloCreateDTO dto) {
        Vuelo vuelo = vueloRepository.findById(id)
                .orElseThrow(() -> new BusinessException("No existe el vuelo con id: " + id));

        if (dto.getIdAeropuertoOrigen() != null) {
            vuelo.setAeropuertoOrigen(aeropuertoRepository.findById(dto.getIdAeropuertoOrigen())
                    .orElseThrow(() -> new BusinessException("Aeropuerto origen no encontrado")));
        }
        if (dto.getIdAeropuertoDestino() != null) {
            vuelo.setAeropuertoDestino(aeropuertoRepository.findById(dto.getIdAeropuertoDestino())
                    .orElseThrow(() -> new BusinessException("Aeropuerto destino no encontrado")));
        }
        if (dto.getHoraSalida() != null) vuelo.setHoraSalida(dto.getHoraSalida());
        if (dto.getHoraLlegada() != null) vuelo.setHoraLlegada(dto.getHoraLlegada());
        if (dto.getDuracionHoras() != null) vuelo.setDuracionHoras(dto.getDuracionHoras());
        if (dto.getCapacidadMaxima() != null) vuelo.setCapacidadMaxima(dto.getCapacidadMaxima());
        if (dto.getEstado() != null) vuelo.setEstado(dto.getEstado());
        if (dto.getEsIntercontinental() != null) vuelo.setEsIntercontinental(dto.getEsIntercontinental());

        return ResponseEntity.ok(ApiResponse.success(vueloRepository.save(vuelo)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar vuelo")
    public ResponseEntity<ApiResponse<Void>> eliminarVuelo(@PathVariable Integer id) {
        if (!vueloRepository.existsById(id)) {
            throw new BusinessException("No existe el vuelo con id: " + id);
        }
        vueloRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.success("Vuelo eliminado", null));
    }

    // ── Cancelar / Reactivar ─────────────────────────────────────

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
