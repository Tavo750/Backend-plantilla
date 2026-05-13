package com.plantilla.backend.modules.maestro.service.impl;

import com.plantilla.backend.modules.maestro.entity.PoliticaEntrega;
import com.plantilla.backend.modules.maestro.repository.PoliticaEntregaRepository;
import com.plantilla.backend.modules.maestro.service.PoliticaEntregaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PoliticaEntregaServiceImpl implements PoliticaEntregaService {

    private final PoliticaEntregaRepository politicaEntregaRepository;

    @Override
    public List<PoliticaEntrega> listarPoliticas() {
        return politicaEntregaRepository.findAll();
    }

    @Override
    public PoliticaEntrega obtenerPoliticaPorId(Integer id) {
        return politicaEntregaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Politica de entrega no encontrada con id: " + id));
    }

    @Override
    public PoliticaEntrega crearPolitica(PoliticaEntrega politicaEntrega) {
        return politicaEntregaRepository.save(politicaEntrega);
    }

    @Override
    public PoliticaEntrega actualizarPolitica(Integer id, PoliticaEntrega politicaEntrega) {
        PoliticaEntrega existente = obtenerPoliticaPorId(id);
        existente.setDiasMismoContinente(politicaEntrega.getDiasMismoContinente());
        existente.setDiasDistintoContinente(politicaEntrega.getDiasDistintoContinente());
        existente.setActiva(politicaEntrega.getActiva());
        return politicaEntregaRepository.save(existente);
    }

    @Override
    public void eliminarPolitica(Integer id) {
        politicaEntregaRepository.deleteById(id);
    }
}
