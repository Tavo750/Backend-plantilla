package com.plantilla.backend.modules.envio.service.impl;

import com.plantilla.backend.modules.envio.dto.EnvioMaletasCreateDTO;
import com.plantilla.backend.modules.envio.entity.EnvioDiario;
import com.plantilla.backend.modules.envio.repository.EnvioDiarioRepository;
import com.plantilla.backend.modules.envio.service.EnvioDiarioService;
import com.plantilla.backend.modules.envio.service.MonitoreoRealTimeService;
import com.plantilla.backend.modules.envio.service.PlanificadorEnvioService;
import com.plantilla.backend.modules.maestro.entity.Aeropuerto;
import com.plantilla.backend.modules.maestro.repository.AerolineaRepository;
import com.plantilla.backend.modules.maestro.repository.AeropuertoRepository;
import com.plantilla.backend.modules.maestro.repository.PoliticaEntregaRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class EnvioDiarioServiceImpl implements EnvioDiarioService {

    private final EnvioDiarioRepository envioDiarioRepository;
    private final AerolineaRepository aerolineaRepository;
    private final AeropuertoRepository aeropuertoRepository;
    private final PoliticaEntregaRepository politicaEntregaRepository;
    private final MonitoreoRealTimeService monitoreoRealTimeService;
    private final PlanificadorEnvioService planificadorEnvioService;

    public EnvioDiarioServiceImpl(
            EnvioDiarioRepository envioDiarioRepository,
            AerolineaRepository aerolineaRepository,
            AeropuertoRepository aeropuertoRepository,
            PoliticaEntregaRepository politicaEntregaRepository,
            @Lazy MonitoreoRealTimeService monitoreoRealTimeService,
            @Lazy PlanificadorEnvioService planificadorEnvioService) {
        this.envioDiarioRepository      = envioDiarioRepository;
        this.aerolineaRepository        = aerolineaRepository;
        this.aeropuertoRepository       = aeropuertoRepository;
        this.politicaEntregaRepository  = politicaEntregaRepository;
        this.monitoreoRealTimeService   = monitoreoRealTimeService;
        this.planificadorEnvioService   = planificadorEnvioService;
    }


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
    @Transactional
    public EnvioDiario crearEnvio(EnvioMaletasCreateDTO dto) {
        EnvioDiario envio = new EnvioDiario();

        if (dto.getCantidad() == null || dto.getCantidad() <= 0) {
            throw new RuntimeException("La cantidad de maletas debe ser mayor a cero");
        }

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

        if (origen.getIdAeropuerto().equals(destino.getIdAeropuerto())) {
            throw new RuntimeException("El aeropuerto de origen y destino no pueden ser iguales");
        }

        validarCapacidadPorEnviosActivos(origen, dto.getCantidad());

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

        // La fecha/hora de registro se estampa en el HUSO HORARIO DEL AEROPUERTO DE ORIGEN
        // (no siempre Lima): así cada sede (Lima, Buenos Aires, Copenhague, Delhi) registra
        // en su propia hora local, tal como exige la prueba de operación día a día.
        int gmtOrigen = origen.getGmt() != null ? origen.getGmt() : -5;
        LocalDateTime fechaRegistro = dto.getFechaRegistro() != null
                ? dto.getFechaRegistro()
                : LocalDateTime.now(java.time.ZoneOffset.ofHours(gmtOrigen));

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

        // Disparar planificación inmediata en hilo separado para asignar vuelo al instante
        // (sin esperar el ciclo automático de 5 minutos)
        new Thread(() -> {
            try {
                planificadorEnvioService.planificar();
            } catch (Exception ex) {
                // log silencioso: la planificación programada es la red de seguridad
            }
        }, "planif-on-create").start();

        monitoreoRealTimeService.broadcastPlan();

        return saved;
    }

    private void validarCapacidadPorEnviosActivos(Aeropuerto origen, Integer cantidadNueva) {
        int cantidadActual = envioDiarioRepository
                .sumCantidadActivaPorAeropuertoOrigen(origen.getIdAeropuerto());

        int capacidadMaxima = origen.getCapacidad() != null
                ? origen.getCapacidad()
                : 0;

        int disponible = capacidadMaxima - cantidadActual;

        if (cantidadNueva > disponible) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Capacidad insuficiente en el aeropuerto " + origen.getCodigoOaci()
                            + ". Capacidad máxima: " + capacidadMaxima
                            + ". Disponible: " + disponible);
        }
    }

    @Override
    public EnvioDiario actualizarEnvio(Integer id, EnvioDiario envio) {
        EnvioDiario existente = obtenerEnvioPorId(id);
        existente.setCantidad(envio.getCantidad());
        existente.setEstado(envio.getEstado());
        existente.setFechaRegistro(envio.getFechaRegistro());
        existente.setHoraRegistrada(envio.getHoraRegistrada());
        existente.setFechaLimiteEntrega(envio.getFechaLimiteEntrega());

        if (envio.getAerolinea() != null)
            existente.setAerolinea(envio.getAerolinea());
        if (envio.getAeropuertoOrigen() != null)
            existente.setAeropuertoOrigen(envio.getAeropuertoOrigen());
        if (envio.getAeropuertoDestino() != null)
            existente.setAeropuertoDestino(envio.getAeropuertoDestino());

        return envioDiarioRepository.save(existente);
    }

    @Override
    public void eliminarEnvio(Integer id) {
        envioDiarioRepository.deleteById(id);
    }

    @Override
    public List<EnvioDiario> listarEnviosPorAeropuerto(Integer idAeropuerto) {
        return envioDiarioRepository.findByAeropuertoOrigenOrDestino(idAeropuerto);
    }
}