package com.plantilla.backend.modules.envio.service;

import com.plantilla.backend.modules.envio.entity.EnvioMaletas;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface EnvioMaletasService {
    List<EnvioMaletas> listarEnvios();
    Page<EnvioMaletas> listarEnviosPaginado(Pageable pageable);
    EnvioMaletas obtenerEnvioPorId(Integer id);
    EnvioMaletas crearEnvio(com.plantilla.backend.modules.envio.dto.EnvioMaletasCreateDTO envioDTO);
    List<EnvioMaletas> crearEnviosBatch(List<com.plantilla.backend.modules.envio.dto.EnvioMaletasCreateDTO> envios);
    EnvioMaletas actualizarEnvio(Integer id, EnvioMaletas envio);
    void eliminarEnvio(Integer id);
    List<EnvioMaletas> listarPorAeropuertoOrigen(Integer idAeropuerto);
    
}
