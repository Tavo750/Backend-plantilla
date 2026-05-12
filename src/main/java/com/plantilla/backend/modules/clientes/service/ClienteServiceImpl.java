package com.plantilla.backend.modules.clientes.service;

import com.plantilla.backend.modules.clientes.dto.ClienteRequest;
import com.plantilla.backend.modules.clientes.dto.ClienteResponse;
import com.plantilla.backend.modules.clientes.entity.Cliente;
import com.plantilla.backend.modules.clientes.repository.ClienteRepository;
import com.plantilla.backend.shared.dto.PaginatedResponse;
import com.plantilla.backend.shared.errors.BusinessException;
import com.plantilla.backend.shared.errors.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Implementación del servicio de clientes.
 * Principio SOLID (S): Solo responsable de lógica de negocio de clientes.
 * Principio SOLID (L): Sustituible por cualquier implementación de ClienteService.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClienteServiceImpl implements ClienteService {

    private final ClienteRepository clienteRepository;

    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<ClienteResponse> findAll(int page, int size) {
        Page<Cliente> clientePage = clienteRepository.findAll(
                PageRequest.of(page, size, Sort.by("id").descending()));

        List<ClienteResponse> items = clientePage.getContent().stream()
                .map(this::toResponse)
                .toList();

        return PaginatedResponse.of(items, clientePage.getTotalElements(), page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public ClienteResponse findById(Long id) {
        Cliente cliente = clienteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente", "id", id));
        return toResponse(cliente);
    }

    @Override
    @Transactional
    public ClienteResponse create(ClienteRequest request) {
        if (clienteRepository.existsByRuc(request.getRuc())) {
            throw new BusinessException("DUPLICATE_RUC", "Ya existe un cliente con el RUC: " + request.getRuc());
        }

        Cliente cliente = toEntity(request);
        Cliente saved = clienteRepository.save(cliente);
        log.info("Cliente creado: {} (ID: {})", saved.getRazonSocial(), saved.getId());
        return toResponse(saved);
    }

    @Override
    @Transactional
    public ClienteResponse update(Long id, ClienteRequest request) {
        Cliente cliente = clienteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente", "id", id));

        updateEntity(request, cliente);
        Cliente updated = clienteRepository.save(cliente);
        log.info("Cliente actualizado: {} (ID: {})", updated.getRazonSocial(), updated.getId());
        return toResponse(updated);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if (!clienteRepository.existsById(id)) {
            throw new ResourceNotFoundException("Cliente", "id", id);
        }
        clienteRepository.deleteById(id);
        log.info("Cliente eliminado (ID: {})", id);
    }

    // =====================================================================
    // Mapeo Entity ↔ DTO (inline para módulos simples)
    // =====================================================================

    private ClienteResponse toResponse(Cliente entity) {
        return ClienteResponse.builder()
                .id(entity.getId())
                .razonSocial(entity.getRazonSocial())
                .ruc(entity.getRuc())
                .direccion(entity.getDireccion())
                .telefono(entity.getTelefono())
                .correo(entity.getCorreo())
                .contactoPrincipal(entity.getContactoPrincipal())
                .estado(entity.getEstado())
                .creadoEn(entity.getCreadoEn())
                .actualizadoEn(entity.getActualizadoEn())
                .build();
    }

    private Cliente toEntity(ClienteRequest request) {
        Cliente cliente = new Cliente();
        cliente.setRazonSocial(request.getRazonSocial());
        cliente.setRuc(request.getRuc());
        cliente.setDireccion(request.getDireccion());
        cliente.setTelefono(request.getTelefono());
        cliente.setCorreo(request.getCorreo());
        cliente.setContactoPrincipal(request.getContactoPrincipal());
        cliente.setEstado(request.getEstado() != null ? request.getEstado() : true);
        return cliente;
    }

    private void updateEntity(ClienteRequest request, Cliente entity) {
        entity.setRazonSocial(request.getRazonSocial());
        entity.setRuc(request.getRuc());
        entity.setDireccion(request.getDireccion());
        entity.setTelefono(request.getTelefono());
        entity.setCorreo(request.getCorreo());
        entity.setContactoPrincipal(request.getContactoPrincipal());
        if (request.getEstado() != null) {
            entity.setEstado(request.getEstado());
        }
    }
}
