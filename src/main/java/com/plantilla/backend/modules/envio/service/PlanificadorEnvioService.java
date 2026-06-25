package com.plantilla.backend.modules.envio.service;

import com.plantilla.backend.modules.envio.entity.EnvioDiario;
import com.plantilla.backend.modules.envio.repository.EnvioDiarioRepository;
import com.plantilla.backend.modules.maestro.entity.PlanVueloDiario;
import com.plantilla.backend.modules.maestro.repository.PlanVueloDiarioRepository;
import com.plantilla.backend.shared.enums.EstadoMaleta;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Comparator;
/**
 * Planificador que cada 5 minutos reales asigna los pedidos pendientes de envio_diario
 * a los próximos vuelos disponibles en plan_vuelo_diario.
 */
@Service
@RequiredArgsConstructor
public class PlanificadorEnvioService {

    private static final Logger log = LoggerFactory.getLogger(PlanificadorEnvioService.class);

    private final EnvioDiarioRepository envioDiarioRepo;
    private final PlanVueloDiarioRepository planVueloRepo;
    private final MonitoreoRealTimeService monitoreoRealTimeService;

    /**
     * Se ejecuta cada 5 minutos reales.
     * Lee envíos con estado REGISTRADA, les asigna el próximo vuelo disponible.
     */
    @Scheduled(fixedDelay = 300_000, initialDelay = 60_000)
    @Transactional
    public void planificar() {
        log.info("Planificador iniciando ciclo...");

        List<EnvioDiario> pendientes = envioDiarioRepo.findByEstado("REGISTRADA");
        if (pendientes.isEmpty()) {
            log.info("No hay envíos pendientes");
            return;
        }

        LocalDateTime ahora = LocalDateTime.now();

        // Capacidad ocupada por vuelo (id_plan_vuelo_asignado → total maletas ya asignadas)
        Map<Integer, Integer> capacidadUsada = new ConcurrentHashMap<>();
        List<EnvioDiario> yaAsignados = envioDiarioRepo.findByEstado("ASIGNADA");
        for (EnvioDiario e : yaAsignados) {
            if (e.getIdPlanVueloAsignado() != null) {
                capacidadUsada.merge(e.getIdPlanVueloAsignado(), e.getCantidad(), Integer::sum);
            }
        }

        int asignados = 0, noAsignados = 0, maletas = 0;

        for (EnvioDiario envio : pendientes) {
            if (envio.getAeropuertoOrigen() == null || envio.getAeropuertoDestino() == null) {
                noAsignados++;
                continue;
            }

            String codigoOrigen  = envio.getAeropuertoOrigen().getCodigoOaci();
            String codigoDestino = envio.getAeropuertoDestino().getCodigoOaci();

            // Buscar próximo vuelo disponible en la ruta que salga después de ahora
            List<CandidatoVuelo> candidatos = obtenerCandidatosVuelo(codigoOrigen, codigoDestino, ahora);

            CandidatoVuelo candidato = null;

            for (CandidatoVuelo c : candidatos) {
                int usada = capacidadUsada.getOrDefault(c.vuelo().getId(), 0);

                if (usada + envio.getCantidad() <= c.vuelo().getCapacidad()) {
                    candidato = c;
                    break;
                }
            }

            if (candidato == null) {
                noAsignados++;
                log.debug("Sin vuelo disponible para envío {} ({} → {})", envio.getIdEnvio(), codigoOrigen, codigoDestino);
                continue;
            }

            envio.setIdPlanVueloAsignado(candidato.vuelo().getId());
            envio.setFechaHoraSalidaAsignada(candidato.fechaHoraSalida());
            envio.setFechaHoraLlegadaAsignada(candidato.fechaHoraLlegada());
            envio.setEstado(EstadoMaleta.EN_ESPERA);
            envioDiarioRepo.save(envio);

            capacidadUsada.merge(candidato.vuelo().getId(), envio.getCantidad(), Integer::sum);
            asignados++;
            maletas += envio.getCantidad();
        }

        log.info("Planificación completada: {} asignados, {} no asignados, {} maletas", asignados, noAsignados, maletas);

        // Actualizar contadores en el monitoreo y hacer broadcast
        monitoreoRealTimeService.actualizarContadores(
                monitoreoRealTimeService.obtenerSnapshot().containsKey("pedidosAsignados")
                        ? ((int) monitoreoRealTimeService.obtenerSnapshot().get("pedidosAsignados")) + asignados
                        : asignados,
                noAsignados,
                ((int) monitoreoRealTimeService.obtenerSnapshot().getOrDefault("maletasFisicas", 0)) + maletas
        );
        monitoreoRealTimeService.broadcastPlan();
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    @Transactional
    public void actualizarEstadosPorLlegadaDeVuelo() {
        LocalDateTime ahora = LocalDateTime.now();

        // Incluir RETRASADA para que puedan transicionar a ENTREGADA cuando el vuelo aterrice
        List<EnvioDiario> enviosConVuelo = new ArrayList<>();
        enviosConVuelo.addAll(envioDiarioRepo.findByEstado("EN_ESPERA"));
        enviosConVuelo.addAll(envioDiarioRepo.findByEstado("EN_TRANSITO"));
        enviosConVuelo.addAll(envioDiarioRepo.findByEstado("RETRASADA"));

        // Verificar también paquetes sin vuelo que superaron su fecha límite
        List<EnvioDiario> sinVuelo = envioDiarioRepo.findByEstado("REGISTRADA");
        for (EnvioDiario envio : sinVuelo) {
            if (envio.getIdPlanVueloAsignado() == null && !ahora.isBefore(envio.getFechaLimiteEntrega())) {
                envio.setEstado(EstadoMaleta.RETRASADA);
                envioDiarioRepo.save(envio);
            }
        }

        for (EnvioDiario envio : enviosConVuelo) {
            if (envio.getIdPlanVueloAsignado() == null) continue;

            PlanVueloDiario vuelo = planVueloRepo.findById(envio.getIdPlanVueloAsignado()).orElse(null);
            if (vuelo == null) continue;

            LocalDateTime fechaHoraSalida  = envio.getFechaHoraSalidaAsignada();
            LocalDateTime fechaHoraLlegada = envio.getFechaHoraLlegadaAsignada();

            if (fechaHoraSalida == null || fechaHoraLlegada == null) {
                fechaHoraSalida  = calcularFechaHoraSalida(envio.getFechaRegistro(), vuelo.getHoraSalida());
                fechaHoraLlegada = calcularFechaHoraLlegada(fechaHoraSalida.toLocalDate(), vuelo);
                envio.setFechaHoraSalidaAsignada(fechaHoraSalida);
                envio.setFechaHoraLlegadaAsignada(fechaHoraLlegada);
            }

            // Ventana de 15 minutos tras el aterrizaje para marcar como ENTREGADA
            LocalDateTime entregadaDesde   = fechaHoraLlegada.plusMinutes(15);
            boolean deadlinePasado = !ahora.isBefore(envio.getFechaLimiteEntrega());

            if (!ahora.isBefore(entregadaDesde)) {
                // 15+ min después del aterrizaje → siempre ENTREGADA
                envio.setEstado(EstadoMaleta.ENTREGADA);
            } else if (!ahora.isBefore(fechaHoraLlegada)) {
                // Aterrizó pero aún en ventana de descarga (0–15 min) → EN_TRANSITO
                envio.setEstado(EstadoMaleta.EN_TRANSITO);
            } else if (!ahora.isBefore(fechaHoraSalida)) {
                // En vuelo: RETRASADA si el plazo ya pasó, sino EN_TRANSITO
                envio.setEstado(deadlinePasado ? EstadoMaleta.RETRASADA : EstadoMaleta.EN_TRANSITO);
            } else {
                // Esperando salida: RETRASADA si el plazo ya pasó, sino EN_ESPERA
                envio.setEstado(deadlinePasado ? EstadoMaleta.RETRASADA : EstadoMaleta.EN_ESPERA);
            }

            envioDiarioRepo.save(envio);
        }
    }

    private List<CandidatoVuelo> obtenerCandidatosVuelo(
            String codigoOrigen,
            String codigoDestino,
            LocalDateTime ahora
    ) {
        return planVueloRepo
                .findByCodigoOrigenAndCodigoDestinoOrderByHoraSalidaAsc(codigoOrigen, codigoDestino)
                .stream()
                .map(vuelo -> {
                    LocalDateTime salida = calcularFechaHoraSalida(ahora, vuelo.getHoraSalida());
                    LocalDateTime llegada = calcularFechaHoraLlegada(salida.toLocalDate(), vuelo);
                    return new CandidatoVuelo(vuelo, salida, llegada);
                })
                .sorted(Comparator.comparing(CandidatoVuelo::fechaHoraSalida))
                .toList();
    }

    private LocalDateTime calcularFechaHoraSalida(LocalDateTime referencia, LocalTime horaSalida) {
        LocalDate fechaSalida = referencia.toLocalDate();

        if (!horaSalida.isAfter(referencia.toLocalTime())) {
            fechaSalida = fechaSalida.plusDays(1);
        }

        return LocalDateTime.of(fechaSalida, horaSalida);
    }

    private LocalDateTime calcularFechaHoraLlegada(LocalDate fechaSalida, PlanVueloDiario vuelo) {
        LocalDateTime llegada = LocalDateTime.of(fechaSalida, vuelo.getHoraLlegada());

        if (vuelo.getHoraLlegada().isBefore(vuelo.getHoraSalida())) {
            llegada = llegada.plusDays(1);
        }

        return llegada;
    }

    private record CandidatoVuelo(
            PlanVueloDiario vuelo,
            LocalDateTime fechaHoraSalida,
            LocalDateTime fechaHoraLlegada
    ) {}
}
