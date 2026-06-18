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

    @Override
    public Aeropuerto obtenerAeropuertoPorId(Integer id) {
        return aeropuertoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Aeropuerto no encontrado con ID: " + id));
    }

    @Override
    public Aeropuerto crearAeropuerto(Aeropuerto aeropuerto) {
        return aeropuertoRepository.save(aeropuerto);
    }

    @Override
    public Aeropuerto actualizarAeropuerto(Integer id, Aeropuerto aeropuertoActualizado) {
        Aeropuerto aeropuerto = obtenerAeropuertoPorId(id);
        if (aeropuertoActualizado.getCiudad() != null) aeropuerto.setCiudad(aeropuertoActualizado.getCiudad());
        if (aeropuertoActualizado.getPais() != null) aeropuerto.setPais(aeropuertoActualizado.getPais());
        if (aeropuertoActualizado.getCapacidad() != null) aeropuerto.setCapacidad(aeropuertoActualizado.getCapacidad());
        if (aeropuertoActualizado.getGmt() != null) aeropuerto.setGmt(aeropuertoActualizado.getGmt());
        if (aeropuertoActualizado.getLatitud() != null) aeropuerto.setLatitud(aeropuertoActualizado.getLatitud());
        if (aeropuertoActualizado.getLongitud() != null) aeropuerto.setLongitud(aeropuertoActualizado.getLongitud());
        if (aeropuertoActualizado.getActivo() != null) aeropuerto.setActivo(aeropuertoActualizado.getActivo());
        if (aeropuertoActualizado.getContinente() != null) aeropuerto.setContinente(aeropuertoActualizado.getContinente());
        return aeropuertoRepository.save(aeropuerto);
    }

    @Override
    public void eliminarAeropuerto(Integer id) {
        Aeropuerto aeropuerto = obtenerAeropuertoPorId(id);
        aeropuertoRepository.delete(aeropuerto);
    }
}
