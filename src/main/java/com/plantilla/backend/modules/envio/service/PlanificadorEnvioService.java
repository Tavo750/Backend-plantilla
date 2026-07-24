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
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private final EnvioDiarioRepository envioDiarioRepo;
    private final PlanVueloDiarioRepository planVueloRepo;
    private final MonitoreoRealTimeService monitoreoRealTimeService;

    /**
     * DESACTIVADO como planificador: la asignación de vuelos la hace ahora el
     * planificador ALNS de OPERACIÓN DIARIA (OperacionDiariaPureService), que
     * persiste idPlanVueloAsignado/fechaHoraSalidaAsignada/estado en envio_diario.
     * Tener dos planificadores producía asignaciones distintas entre el mapa de
     * operación diaria y Registro de Maletas (desincronización).
     */
    @Transactional
    public void planificar() {
        if (true) return;   // no-op: fuente única = planificador de operación diaria
        log.info("Planificador iniciando ciclo...");

        List<EnvioDiario> pendientes = envioDiarioRepo.findByEstado("REGISTRADA");
        if (pendientes.isEmpty()) {
            log.info("No hay envíos pendientes");
            return;
        }

        LocalDateTime ahora = LocalDateTime.now(LIMA);

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
        // Las horas asignadas las persiste el planificador de OPERACIÓN DIARIA en UTC
        // (mismas horas que la tabla vuelo y el mapa). Comparar contra 'ahora' UTC.
        LocalDateTime ahora = LocalDateTime.now(java.time.ZoneOffset.UTC);

        // Incluir RETRASADA para que puedan transicionar a ENTREGADA cuando el vuelo aterrice
        List<EnvioDiario> enviosConVuelo = new ArrayList<>();
        enviosConVuelo.addAll(envioDiarioRepo.findByEstado("EN_ESPERA"));
        enviosConVuelo.addAll(envioDiarioRepo.findByEstado("EN_TRANSITO"));
        enviosConVuelo.addAll(envioDiarioRepo.findByEstado("RETRASADA"));

        // Paquetes aún sin vuelo que superaron su fecha límite (la fecha límite está en la
        // hora LOCAL del aeropuerto de origen → comparar contra el 'ahora' local del origen)
        List<EnvioDiario> sinVuelo = envioDiarioRepo.findByEstado("REGISTRADA");
        for (EnvioDiario envio : sinVuelo) {
            if (envio.getIdPlanVueloAsignado() != null || envio.getFechaLimiteEntrega() == null) continue;
            int gmt = envio.getAeropuertoOrigen() != null && envio.getAeropuertoOrigen().getGmt() != null
                    ? envio.getAeropuertoOrigen().getGmt() : -5;
            LocalDateTime ahoraLocalOrigen = ahora.plusHours(gmt);
            if (!ahoraLocalOrigen.isBefore(envio.getFechaLimiteEntrega())) {
                envio.setEstado(EstadoMaleta.RETRASADA);
                envioDiarioRepo.save(envio);
            }
        }

        int entregados = 0, enTransito = 0, retrasados = 0, errores = 0;
        for (EnvioDiario envio : enviosConVuelo) {
            try {
                // Usar EXACTAMENTE las horas persistidas por el planificador de operación
                // diaria (no recalcular: eso desincronizaba Registro de Maletas del mapa).
                LocalDateTime fechaHoraSalida  = envio.getFechaHoraSalidaAsignada();
                LocalDateTime fechaHoraLlegada = envio.getFechaHoraLlegadaAsignada();
                if (fechaHoraSalida == null || fechaHoraLlegada == null) continue;

                int gmt = envio.getAeropuertoOrigen() != null && envio.getAeropuertoOrigen().getGmt() != null
                        ? envio.getAeropuertoOrigen().getGmt() : -5;
                boolean deadlinePasado = envio.getFechaLimiteEntrega() != null
                        && !ahora.plusHours(gmt).isBefore(envio.getFechaLimiteEntrega());

                // Ventana de 15 minutos tras el aterrizaje para marcar como ENTREGADA
                LocalDateTime entregadaDesde = fechaHoraLlegada.plusMinutes(15);

                EstadoMaleta nuevo;
                if (!ahora.isBefore(entregadaDesde)) {
                    nuevo = EstadoMaleta.ENTREGADA;
                } else if (!ahora.isBefore(fechaHoraLlegada)) {
                    nuevo = EstadoMaleta.EN_TRANSITO;
                } else if (!ahora.isBefore(fechaHoraSalida)) {
                    nuevo = deadlinePasado ? EstadoMaleta.RETRASADA : EstadoMaleta.EN_TRANSITO;
                } else {
                    nuevo = deadlinePasado ? EstadoMaleta.RETRASADA : EstadoMaleta.EN_ESPERA;
                }

                if (nuevo != envio.getEstado()) {
                    envio.setEstado(nuevo);
                    envioDiarioRepo.save(envio);
                }
                if (nuevo == EstadoMaleta.ENTREGADA) entregados++;
                else if (nuevo == EstadoMaleta.EN_TRANSITO) enTransito++;
                else if (nuevo == EstadoMaleta.RETRASADA) retrasados++;
            } catch (Exception ex) {
                errores++;
                log.warn("Estado no actualizado para envío {}: {}", envio.getIdEnvio(), ex.toString());
            }
        }
        log.info("actualizarEstados [{} UTC]: {} envíos con vuelo → {} entregados, {} en tránsito, {} retrasados, {} errores",
                ahora, enviosConVuelo.size(), entregados, enTransito, retrasados, errores);
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
