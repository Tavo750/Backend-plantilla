package com.plantilla.backend.modules.maestro.service.impl;

import com.plantilla.backend.modules.maestro.entity.Aerolinea;
import com.plantilla.backend.modules.maestro.repository.AerolineaRepository;
import com.plantilla.backend.modules.maestro.service.AerolineaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AerolineaServiceImpl implements AerolineaService {

    private final AerolineaRepository aerolineaRepository;

    @Override
    public List<Aerolinea> listarAerolineas() {
        return aerolineaRepository.findAll();
    }

    @Override
    public Aerolinea obtenerAerolineaPorId(Integer id) {
        return aerolineaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Aerolinea no encontrada con id: " + id));
    }

    @Override
    public Aerolinea crearAerolinea(Aerolinea aerolinea) {
        return aerolineaRepository.save(aerolinea);
    }

    @Override
    public Aerolinea actualizarAerolinea(Integer id, Aerolinea aerolinea) {
        Aerolinea existente = obtenerAerolineaPorId(id);
        existente.setCodigo(aerolinea.getCodigo());
        existente.setNombre(aerolinea.getNombre());
        existente.setContrasenia(aerolinea.getContrasenia());
        existente.setActiva(aerolinea.getActiva());
        return aerolineaRepository.save(existente);
    }

    @Override
    public void eliminarAerolinea(Integer id) {
        aerolineaRepository.deleteById(id);
    }
}
