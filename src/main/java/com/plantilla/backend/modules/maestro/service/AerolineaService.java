package com.plantilla.backend.modules.maestro.service;

import com.plantilla.backend.modules.maestro.entity.Aerolinea;

import java.util.List;

public interface AerolineaService {
    List<Aerolinea> listarAerolineas();
    Aerolinea obtenerAerolineaPorId(Integer id);
    Aerolinea crearAerolinea(Aerolinea aerolinea);
    Aerolinea actualizarAerolinea(Integer id, Aerolinea aerolinea);
    void eliminarAerolinea(Integer id);
}
