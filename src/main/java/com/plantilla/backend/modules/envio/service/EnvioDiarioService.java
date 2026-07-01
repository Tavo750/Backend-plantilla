package com.plantilla.backend.modules.envio.service;

import com.plantilla.backend.modules.envio.dto.EnvioMaletasCreateDTO;
import com.plantilla.backend.modules.envio.entity.EnvioDiario;

import java.util.List;

public interface EnvioDiarioService {

    List<EnvioDiario> listarEnvios();

    List<EnvioDiario> listarEnviosPorAeropuerto(Integer idAeropuerto);

    EnvioDiario obtenerEnvioPorId(Integer id);

    EnvioDiario crearEnvio(EnvioMaletasCreateDTO dto);

    EnvioDiario actualizarEnvio(Integer id, EnvioDiario envio);

    void eliminarEnvio(Integer id);
}