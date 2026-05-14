package com.plantilla.backend.modules.envio.service.impl;

import com.plantilla.backend.modules.envio.entity.EnvioMaletas;
import com.plantilla.backend.modules.envio.repository.EnvioMaletasRepository;
import com.plantilla.backend.modules.envio.service.EnvioMaletasService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EnvioMaletasServiceImpl implements EnvioMaletasService {

    private final EnvioMaletasRepository envioMaletasRepository;

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
    public EnvioMaletas crearEnvio(EnvioMaletas envio) {
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
