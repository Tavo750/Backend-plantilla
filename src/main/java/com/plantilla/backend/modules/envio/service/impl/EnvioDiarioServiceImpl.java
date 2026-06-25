package com.plantilla.backend.modules.envio.service.impl;

import com.plantilla.backend.modules.envio.dto.EnvioMaletasCreateDTO;
import com.plantilla.backend.modules.envio.entity.EnvioDiario;
import com.plantilla.backend.modules.envio.repository.EnvioDiarioRepository;
import com.plantilla.backend.modules.envio.service.EnvioDiarioService;
import com.plantilla.backend.modules.envio.service.MonitoreoRealTimeService;
import com.plantilla.backend.modules.maestro.entity.Aeropuerto;
import com.plantilla.backend.modules.maestro.repository.AerolineaRepository;
import com.plantilla.backend.modules.maestro.repository.AeropuertoRepository;
import com.plantilla.backend.modules.maestro.repository.PoliticaEntregaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EnvioDiarioServiceImpl implements EnvioDiarioService {

    private final EnvioDiarioRepository envioDiarioRepository;
    private final AerolineaRepository aerolineaRepository;
    private final AeropuertoRepository aeropuertoRepository;
    private final PoliticaEntregaRepository politicaEntregaRepository;
    @Lazy
    private final MonitoreoRealTimeService monitoreoRealTimeService;

    @Override
    @Transactional(readOnly = true)
    public List<EnvioDiario> listarEnvios() {
        return envioDiarioRepository.findAll();
    }

    @Override
    public EnvioDiario obtenerEnvioPorId(Integer id) {
        return envioDiarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Envío diario no encontrado con id: " + id));
    }

    @Override
    public EnvioDiario crearEnvio(EnvioMaletasCreateDTO dto) {
        EnvioDiario envio = new EnvioDiario();
        envio.setCantidad(dto.getCantidad());

        if (dto.getIdAerolinea() != null) {
            envio.setAerolinea(aerolineaRepository.findById(dto.getIdAerolinea())
                    .orElseThrow(() -> new RuntimeException("Aerolínea no encontrada")));
        } else {
            envio.setAerolinea(aerolineaRepository.findAll().stream()
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("No existe ninguna aerolínea registrada")));
        }

        Aeropuerto origen = aeropuertoRepository.findById(dto.getIdAeropuertoOrigen())
                .orElseThrow(() -> new RuntimeException("Aeropuerto origen no encontrado"));
        Aeropuerto destino = aeropuertoRepository.findById(dto.getIdAeropuertoDestino())
                .orElseThrow(() -> new RuntimeException("Aeropuerto destino no encontrado"));
        envio.setAeropuertoOrigen(origen);
        envio.setAeropuertoDestino(destino);

        if (dto.getIdPolitica() != null) {
            envio.setPoliticaEntrega(politicaEntregaRepository.findById(dto.getIdPolitica())
                    .orElseThrow(() -> new RuntimeException("Política no encontrada")));
        } else {
            envio.setPoliticaEntrega(politicaEntregaRepository.findByActivaTrue()
                    .orElseGet(() -> politicaEntregaRepository.findAll().stream()
                            .findFirst()
                            .orElseThrow(() -> new RuntimeException("No existe ninguna política de entrega"))));
        }

        LocalDateTime fechaRegistro = dto.getFechaRegistro() != null
                ? dto.getFechaRegistro()
                : LocalDateTime.now();
        envio.setFechaRegistro(fechaRegistro);
        envio.setHoraRegistrada(dto.getHoraRegistrada() != null
                ? dto.getHoraRegistrada()
                : fechaRegistro.toLocalTime());

        if (dto.getFechaLimiteEntrega() != null) {
            envio.setFechaLimiteEntrega(dto.getFechaLimiteEntrega());
        } else {
            int diasSla = String.valueOf(origen.getContinente())
                    .equals(String.valueOf(destino.getContinente())) ? 1 : 2;
            envio.setFechaLimiteEntrega(fechaRegistro.plusDays(diasSla));
        }

        EnvioDiario saved = envioDiarioRepository.save(envio);
        // Notificar al monitoreo en tiempo real para que actualice los contadores del aeropuerto
        monitoreoRealTimeService.broadcastPlan();
        return saved;
    }

    @Override
    public EnvioDiario actualizarEnvio(Integer id, EnvioDiario envio) {
        EnvioDiario existente = obtenerEnvioPorId(id);
        existente.setCantidad(envio.getCantidad());
        existente.setEstado(envio.getEstado());
        existente.setFechaRegistro(envio.getFechaRegistro());
        existente.setHoraRegistrada(envio.getHoraRegistrada());
        existente.setFechaLimiteEntrega(envio.getFechaLimiteEntrega());
        if (envio.getAerolinea() != null) existente.setAerolinea(envio.getAerolinea());
        if (envio.getAeropuertoOrigen() != null) existente.setAeropuertoOrigen(envio.getAeropuertoOrigen());
        if (envio.getAeropuertoDestino() != null) existente.setAeropuertoDestino(envio.getAeropuertoDestino());
        return envioDiarioRepository.save(existente);
    }

    @Override
    public void eliminarEnvio(Integer id) {
        envioDiarioRepository.deleteById(id);
    }
}
