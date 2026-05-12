package com.plantilla.backend.modules.clientes.controller;

import com.plantilla.backend.modules.clientes.dto.ClienteRequest;
import com.plantilla.backend.modules.clientes.dto.ClienteResponse;
import com.plantilla.backend.modules.clientes.service.ClienteService;
import com.plantilla.backend.shared.dto.ApiResponse;
import com.plantilla.backend.shared.dto.PaginatedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controlador REST para gestión de clientes.
 * Módulo de ejemplo para demostrar la arquitectura DDD.
 * Principio SOLID (S): Solo recibe y delega solicitudes HTTP.
 * Principio SOLID (D): Depende de la abstracción ClienteService.
 */
@RestController
@RequestMapping("/clientes")
@RequiredArgsConstructor
@Tag(name = "Clientes", description = "CRUD de clientes (módulo ejemplo)")
public class ClienteController {

    private final ClienteService clienteService;

    @GetMapping
    @Operation(summary = "Listar clientes", description = "Obtiene una lista paginada de clientes")
    public ResponseEntity<ApiResponse<PaginatedResponse<ClienteResponse>>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PaginatedResponse<ClienteResponse> response = clienteService.findAll(page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener cliente", description = "Obtiene un cliente por su ID")
    public ResponseEntity<ApiResponse<ClienteResponse>> findById(@PathVariable Long id) {
        ClienteResponse response = clienteService.findById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping
    @Operation(summary = "Crear cliente", description = "Crea un nuevo cliente")
    public ResponseEntity<ApiResponse<ClienteResponse>> create(@Valid @RequestBody ClienteRequest request) {
        ClienteResponse response = clienteService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar cliente", description = "Actualiza un cliente existente")
    public ResponseEntity<ApiResponse<ClienteResponse>> update(
            @PathVariable Long id, @Valid @RequestBody ClienteRequest request) {
        ClienteResponse response = clienteService.update(id, request);
        return ResponseEntity.ok(ApiResponse.success("Cliente actualizado exitosamente", response));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar cliente", description = "Elimina un cliente por su ID")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        clienteService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Cliente eliminado exitosamente", null));
    }
}
