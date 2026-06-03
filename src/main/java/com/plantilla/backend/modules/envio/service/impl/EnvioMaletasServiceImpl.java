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

import java.time.LocalDateTime;
import java.time.LocalTime;
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

        // Aerolínea: usar la indicada o tomar la primera disponible automáticamente
        if (dto.getIdAerolinea() != null) {
            envio.setAerolinea(aerolineaRepository.findById(dto.getIdAerolinea())
                    .orElseThrow(() -> new RuntimeException("Aerolinea no encontrada")));
        } else {
            envio.setAerolinea(aerolineaRepository.findAll().stream()
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("No existe ninguna aerolínea registrada")));
        }

        envio.setAeropuertoOrigen(aeropuertoRepository.findById(dto.getIdAeropuertoOrigen())
                .orElseThrow(() -> new RuntimeException("Aeropuerto origen no encontrado")));
        envio.setAeropuertoDestino(aeropuertoRepository.findById(dto.getIdAeropuertoDestino())
                .orElseThrow(() -> new RuntimeException("Aeropuerto destino no encontrado")));

        // Política: usar la indicada o tomar la primera activa automáticamente
        if (dto.getIdPolitica() != null) {
            envio.setPoliticaEntrega(politicaEntregaRepository.findById(dto.getIdPolitica())
                    .orElseThrow(() -> new RuntimeException("Politica no encontrada")));
        } else {
            envio.setPoliticaEntrega(politicaEntregaRepository.findByActivaTrue()
                    .orElseGet(() -> politicaEntregaRepository.findAll().stream()
                            .findFirst()
                            .orElseThrow(() -> new RuntimeException("No existe ninguna política de entrega"))));
        }

        // Fecha registro: usar la indicada o la hora actual UTC
        LocalDateTime fechaRegistro = dto.getFechaRegistro() != null
                ? dto.getFechaRegistro()
                : LocalDateTime.now();
        envio.setFechaRegistro(fechaRegistro);
        envio.setHoraRegistrada(dto.getHoraRegistrada() != null
                ? dto.getHoraRegistrada()
                : fechaRegistro.toLocalTime());

        // Fecha límite: usar la indicada o calcular por SLA (1 día mismo continente, 2 diferente)
        if (dto.getFechaLimiteEntrega() != null) {
            envio.setFechaLimiteEntrega(dto.getFechaLimiteEntrega());
        } else {
            String contOrigen  = String.valueOf(envio.getAeropuertoOrigen().getContinente());
            String contDestino = String.valueOf(envio.getAeropuertoDestino().getContinente());
            int diasSla = contOrigen.equals(contDestino) ? 1 : 2;
            envio.setFechaLimiteEntrega(fechaRegistro.plusDays(diasSla));
        }

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
