package com.plantilla.backend.modules.clientes.service;

import com.plantilla.backend.modules.clientes.dto.ClienteRequest;
import com.plantilla.backend.modules.clientes.dto.ClienteResponse;
import com.plantilla.backend.shared.dto.PaginatedResponse;

/**
 * Interfaz del servicio de clientes.
 * Principio SOLID (D): Inversión de dependencias — el controller depende de esta abstracción.
 * Principio SOLID (I): Interfaz segregada para operaciones CRUD de clientes.
 */
public interface ClienteService {

    PaginatedResponse<ClienteResponse> findAll(int page, int size);

    ClienteResponse findById(Long id);

    ClienteResponse create(ClienteRequest request);

    ClienteResponse update(Long id, ClienteRequest request);

    void delete(Long id);
}
