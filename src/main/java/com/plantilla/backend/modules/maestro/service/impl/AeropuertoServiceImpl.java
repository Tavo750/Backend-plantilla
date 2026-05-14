package com.plantilla.backend.modules.maestro.service.impl;

import com.plantilla.backend.modules.maestro.entity.Aeropuerto;
import com.plantilla.backend.modules.maestro.repository.AeropuertoRepository;
import com.plantilla.backend.modules.maestro.service.AeropuertoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AeropuertoServiceImpl implements AeropuertoService {

    private final AeropuertoRepository aeropuertoRepository;

    @Override
    public List<Aeropuerto> listarAeropuertos() {
        return aeropuertoRepository.findAll();
    }
}
