package com.plantilla.backend.modules.maestro.service;

import com.plantilla.backend.modules.maestro.entity.Aeropuerto;

import java.util.List;

public interface AeropuertoService {
    List<Aeropuerto> listarAeropuertos();
    Aeropuerto obtenerAeropuertoPorId(Integer id);
    Aeropuerto crearAeropuerto(Aeropuerto aeropuerto);
    Aeropuerto actualizarAeropuerto(Integer id, Aeropuerto aeropuerto);
    void eliminarAeropuerto(Integer id);
}
