package com.plantilla.backend.modules.envio.service.impl;

import com.plantilla.backend.modules.envio.entity.EnvioMaletas;
import com.plantilla.backend.modules.envio.repository.EnvioMaletasRepository;
import com.plantilla.backend.modules.envio.service.EnvioMaletasService;
import com.plantilla.backend.modules.envio.dto.EnvioMaletasCreateDTO;
import com.plantilla.backend.modules.maestro.repository.AerolineaRepository;
import com.plantilla.backend.modules.maestro.repository.AeropuertoRepository;
import com.plantilla.backend.modules.maestro.repository.PoliticaEntregaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EnvioMaletasServiceImpl implements EnvioMaletasService {

    private final EnvioMaletasRepository envioMaletasRepository;
    private final AerolineaRepository aerolineaRepository;
    private final AeropuertoRepository aeropuertoRepository;
    private final PoliticaEntregaRepository politicaEntregaRepository;

    @Override
    public List<EnvioMaletas> listarEnvios() {
        return envioMaletasRepository.findAll();
    }

    @Override
    public EnvioMaletas obtenerEnvioPorId(Integer id) {
        return envioMaletasRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Envio de maletas no encontrado con id: " + id));
    }

    @Override
    public EnvioMaletas crearEnvio(EnvioMaletasCreateDTO dto) {
        EnvioMaletas envio = new EnvioMaletas();
        envio.setCantidad(dto.getCantidad());
        envio.setFechaRegistro(dto.getFechaRegistro());
        envio.setHoraRegistrada(dto.getHoraRegistrada());
        envio.setFechaLimiteEntrega(dto.getFechaLimiteEntrega());
        
        envio.setAerolinea(aerolineaRepository.findById(dto.getIdAerolinea())
                .orElseThrow(() -> new RuntimeException("Aerolinea no encontrada")));
        envio.setAeropuertoOrigen(aeropuertoRepository.findById(dto.getIdAeropuertoOrigen())
                .orElseThrow(() -> new RuntimeException("Aeropuerto origen no encontrado")));
        envio.setAeropuertoDestino(aeropuertoRepository.findById(dto.getIdAeropuertoDestino())
                .orElseThrow(() -> new RuntimeException("Aeropuerto destino no encontrado")));
        envio.setPoliticaEntrega(politicaEntregaRepository.findById(dto.getIdPolitica())
                .orElseThrow(() -> new RuntimeException("Politica no encontrada")));
                
        return envioMaletasRepository.save(envio);
    }

    @Override
    public EnvioMaletas actualizarEnvio(Integer id, EnvioMaletas envio) {
        EnvioMaletas existente = obtenerEnvioPorId(id);

        // Actualizar campos basicos
        existente.setCantidad(envio.getCantidad());
        existente.setFechaRegistro(envio.getFechaRegistro());
        existente.setHoraRegistrada(envio.getHoraRegistrada());
        existente.setFechaLimiteEntrega(envio.getFechaLimiteEntrega());
        existente.setAerolinea(envio.getAerolinea());
        existente.setAeropuertoOrigen(envio.getAeropuertoOrigen());
        existente.setAeropuertoDestino(envio.getAeropuertoDestino());

        return envioMaletasRepository.save(existente);
    }

    @Override
    public void eliminarEnvio(Integer id) {
        envioMaletasRepository.deleteById(id);
    }
}
