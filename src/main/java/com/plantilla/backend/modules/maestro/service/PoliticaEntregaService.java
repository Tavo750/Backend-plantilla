package com.plantilla.backend.modules.maestro.service;

import com.plantilla.backend.modules.maestro.entity.PoliticaEntrega;

import java.util.List;

public interface PoliticaEntregaService {
    List<PoliticaEntrega> listarPoliticas();
    PoliticaEntrega obtenerPoliticaPorId(Integer id);
    PoliticaEntrega crearPolitica(PoliticaEntrega politicaEntrega);
    PoliticaEntrega actualizarPolitica(Integer id, PoliticaEntrega politicaEntrega);
    void eliminarPolitica(Integer id);
}
