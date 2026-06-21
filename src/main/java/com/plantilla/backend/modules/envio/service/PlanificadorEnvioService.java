package com.plantilla.backend.modules.envio.service;

import com.plantilla.backend.modules.envio.entity.EnvioDiario;
import com.plantilla.backend.modules.envio.repository.EnvioDiarioRepository;
import com.plantilla.backend.modules.maestro.entity.PlanVueloDiario;
import com.plantilla.backend.modules.maestro.repository.PlanVueloDiarioRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

        LocalTime ahora = LocalTime.now();

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
            List<PlanVueloDiario> candidatos = planVueloRepo.findProximoVuelo(codigoOrigen, codigoDestino, ahora);

            PlanVueloDiario vuelo = null;
            for (PlanVueloDiario c : candidatos) {
                int usada = capacidadUsada.getOrDefault(c.getId(), 0);
                if (usada + envio.getCantidad() <= c.getCapacidad()) {
                    vuelo = c;
                    break;
                }
            }

            if (vuelo == null) {
                noAsignados++;
                log.debug("Sin vuelo disponible para envío {} ({} → {})", envio.getIdEnvio(), codigoOrigen, codigoDestino);
                continue;
            }

            envio.setIdPlanVueloAsignado(vuelo.getId());
            envio.setEstado(com.plantilla.backend.shared.enums.EstadoMaleta.EN_TRANSITO);
            envioDiarioRepo.save(envio);

            capacidadUsada.merge(vuelo.getId(), envio.getCantidad(), Integer::sum);
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
}
