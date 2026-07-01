package com.plantilla.backend.modules.envio.service.impl;

import com.plantilla.backend.modules.envio.entity.EnvioMaletas;
import com.plantilla.backend.modules.envio.repository.EnvioMaletasRepository;
import com.plantilla.backend.modules.envio.service.EnvioMaletasService;
import com.plantilla.backend.modules.envio.dto.EnvioMaletasCreateDTO;
import com.plantilla.backend.modules.maestro.entity.Aerolinea;
import com.plantilla.backend.modules.maestro.entity.Aeropuerto;
import com.plantilla.backend.modules.maestro.entity.PoliticaEntrega;
import com.plantilla.backend.modules.maestro.repository.AerolineaRepository;
import com.plantilla.backend.modules.maestro.repository.AeropuertoRepository;
import com.plantilla.backend.modules.maestro.repository.PoliticaEntregaRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EnvioMaletasServiceImpl implements EnvioMaletasService {

    private final EnvioMaletasRepository envioMaletasRepository;
    private final AerolineaRepository aerolineaRepository;
    private final AeropuertoRepository aeropuertoRepository;
    private final PoliticaEntregaRepository politicaEntregaRepository;

    @Override
    public List<EnvioMaletas> listarEnvios() {
        // JOIN FETCH: carga aerolinea, aeropuertoOrigen, aeropuertoDestino y
        // politicaEntrega
        // en una sola query SQL — evita N+1 que agota el pool de conexiones HikariCP
        return envioMaletasRepository.findAllWithRelations();
    }

    @Override
    public Page<EnvioMaletas> listarEnviosPaginado(Pageable pageable) {
        // Paginado con JOIN FETCH: seguro para tablas con miles de registros
        return envioMaletasRepository.findAllWithRelationsPaged(pageable);
    }

    @Override
    public EnvioMaletas obtenerEnvioPorId(Integer id) {
        return envioMaletasRepository.findById((Integer) id)
                .orElseThrow(() -> new RuntimeException("Envio de maletas no encontrado con id: " + id));
    }

    @Override
    public EnvioMaletas crearEnvio(EnvioMaletasCreateDTO dto) {
        EnvioMaletas envio = new EnvioMaletas();
        envio.setCantidad(dto.getCantidad());

        // Aerolínea: usar la indicada o tomar la primera disponible automáticamente
        if (dto.getIdAerolinea() != null) {
            envio.setAerolinea(aerolineaRepository.findById((Integer) dto.getIdAerolinea())
                    .orElseThrow(() -> new RuntimeException("Aerolinea no encontrada")));
        } else {
            envio.setAerolinea(aerolineaRepository.findAll().stream()
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("No existe ninguna aerolínea registrada")));
        }

        envio.setAeropuertoOrigen(aeropuertoRepository.findById((Integer) dto.getIdAeropuertoOrigen())
                .orElseThrow(() -> new RuntimeException("Aeropuerto origen no encontrado")));
        envio.setAeropuertoDestino(aeropuertoRepository.findById((Integer) dto.getIdAeropuertoDestino())
                .orElseThrow(() -> new RuntimeException("Aeropuerto destino no encontrado")));

        // Política: usar la indicada o tomar la primera activa automáticamente
        if (dto.getIdPolitica() != null) {
            envio.setPoliticaEntrega(politicaEntregaRepository.findById((Integer) dto.getIdPolitica())
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

        // Fecha límite: usar la indicada o calcular por SLA (1 día mismo continente, 2
        // diferente)
        if (dto.getFechaLimiteEntrega() != null) {
            envio.setFechaLimiteEntrega(dto.getFechaLimiteEntrega());
        } else {
            String contOrigen = String.valueOf(envio.getAeropuertoOrigen().getContinente());
            String contDestino = String.valueOf(envio.getAeropuertoDestino().getContinente());
            int diasSla = contOrigen.equals(contDestino) ? 1 : 2;
            envio.setFechaLimiteEntrega(fechaRegistro.plusDays(diasSla));
        }

        return envioMaletasRepository.save(envio);
    }

    /**
     * Crea múltiples envíos en una sola transacción (carga masiva CSV).
     * Resuelve aerolínea/política por defecto y todos los aeropuertos UNA sola vez,
     * en vez de consultar la BD fila por fila.
     */
    @Override
    @Transactional
    public List<EnvioMaletas> crearEnviosBatch(List<EnvioMaletasCreateDTO> dtos) {
        if (dtos == null || dtos.isEmpty())
            return List.of();

        Aerolinea aerolineaDefault = aerolineaRepository.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No existe ninguna aerolínea registrada"));
        PoliticaEntrega politicaDefault = politicaEntregaRepository.findByActivaTrue()
                .orElseGet(() -> politicaEntregaRepository.findAll().stream()
                        .findFirst()
                        .orElseThrow(() -> new RuntimeException("No existe ninguna política de entrega")));
        Map<Integer, Aeropuerto> aeropuertos = aeropuertoRepository.findAll().stream()
                .collect(Collectors.toMap(Aeropuerto::getIdAeropuerto, Function.identity()));

        List<EnvioMaletas> envios = new ArrayList<>(dtos.size());
        for (EnvioMaletasCreateDTO dto : dtos) {
            Aeropuerto origen = aeropuertos.get(dto.getIdAeropuertoOrigen());
            Aeropuerto destino = aeropuertos.get(dto.getIdAeropuertoDestino());
            if (origen == null)
                throw new RuntimeException("Aeropuerto origen no encontrado: " + dto.getIdAeropuertoOrigen());
            if (destino == null)
                throw new RuntimeException("Aeropuerto destino no encontrado: " + dto.getIdAeropuertoDestino());

            EnvioMaletas envio = new EnvioMaletas();
            envio.setCantidad(dto.getCantidad());
            envio.setAerolinea(aerolineaDefault);
            envio.setAeropuertoOrigen(origen);
            envio.setAeropuertoDestino(destino);
            envio.setPoliticaEntrega(politicaDefault);

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
            envios.add(envio);
        }

        return envioMaletasRepository.saveAll(envios);
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
        envioMaletasRepository.deleteById((Integer) id);
    }

    @Override
    public List<EnvioMaletas> listarPorAeropuertoOrigen(Integer idAeropuerto) {
        return envioMaletasRepository.findByAeropuertoOrigenIdAeropuertoOrderByFechaRegistroDesc(
                idAeropuerto,
                PageRequest.of(0, 100));
    }
}
