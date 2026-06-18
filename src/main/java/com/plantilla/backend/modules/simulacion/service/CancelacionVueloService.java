package com.plantilla.backend.modules.simulacion.service;

import com.plantilla.backend.modules.maestro.entity.Aeropuerto;
import com.plantilla.backend.modules.maestro.entity.Vuelo;
import com.plantilla.backend.modules.maestro.repository.AeropuertoRepository;
import com.plantilla.backend.modules.maestro.repository.VueloRepository;
import com.plantilla.backend.modules.planificacion.entity.AsignacionVuelo;
import com.plantilla.backend.modules.planificacion.repository.AsignacionVueloRepository;
import com.plantilla.backend.shared.enums.Continente;
import com.plantilla.backend.shared.errors.BusinessException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CancelacionVueloService {

    private static final Logger log = LoggerFactory.getLogger(CancelacionVueloService.class);

    private final VueloRepository vueloRepository;
    private final AeropuertoRepository aeropuertoRepository;
    private final AsignacionVueloRepository asignacionVueloRepository;

    /**
     * Obtiene vuelos cancelables (no despegados) filtrados por aeropuerto/continente.
     * Vuelo es cancelable si: hora_salida > hora_actual_simulada
     */
    public List<Map<String, Object>> obtenerVuelosCancelables(LocalDateTime horaActualSimulada,
                                                              Integer idAeropuerto,
                                                              Continente continente) {
        List<Vuelo> vuelos = new ArrayList<>();

        // Filtrar por aeropuerto
        if (idAeropuerto != null) {
            vuelos = vueloRepository.findByAeropuertoOrigenIdAeropuerto(idAeropuerto);
        }
        // Filtrar por continente
        else if (continente != null) {
            List<Aeropuerto> aeropuertosPorContinente = aeropuertoRepository.findByContinente(continente);
            Set<Integer> idAeropuertos = aeropuertosPorContinente.stream()
                    .map(Aeropuerto::getIdAeropuerto)
                    .collect(Collectors.toSet());

            vuelos = vueloRepository.findAll().stream()
                    .filter(v -> idAeropuertos.contains(v.getAeropuertoOrigen().getIdAeropuerto()))
                    .collect(Collectors.toList());
        } else {
            vuelos = vueloRepository.findAll();
        }

        // Filtrar: solo vuelos que aún no despegaron
        List<Map<String, Object>> result = new ArrayList<>();
        for (Vuelo vuelo : vuelos) {
            if (vuelo.getHoraSalida().isAfter(horaActualSimulada)) {
                Map<String, Object> vueloMap = new LinkedHashMap<>();
                vueloMap.put("idVuelo", vuelo.getIdVuelo());
                vueloMap.put("codigoVuelo", vuelo.getCodigoVuelo());
                vueloMap.put("origen", vuelo.getAeropuertoOrigen().getCodigo());
                vueloMap.put("destino", vuelo.getAeropuertoDestino().getCodigo());
                vueloMap.put("horaSalida", vuelo.getHoraSalida().toString());
                vueloMap.put("horaLlegada", vuelo.getHoraLlegada().toString());
                vueloMap.put("capacidadMaxima", vuelo.getCapacidadMaxima());

                // Contar maletas asignadas
                List<AsignacionVuelo> asignaciones = asignacionVueloRepository.findByVueloIdVuelo(vuelo.getIdVuelo());
                int maletasAsignadas = asignaciones.stream()
                        .map(AsignacionVuelo::getCantidadAsignada)
                        .reduce(0, Integer::sum);
                vueloMap.put("maletasAsignadas", maletasAsignadas);

                result.add(vueloMap);
            }
        }

        return result;
    }

    /**
     * Cancela un vuelo y marca sus asignaciones como canceladas.
     * Retorna información de las maletas redistribuidas.
     */
    @Transactional
    public Map<String, Object> cancelarVueloYReplanificar(Integer idVuelo) {
        Vuelo vuelo = vueloRepository.findById(idVuelo)
                .orElseThrow(() -> new BusinessException("Vuelo no encontrado con ID: " + idVuelo));

        // Obtener todas las asignaciones del vuelo
        List<AsignacionVuelo> asignaciones = asignacionVueloRepository.findByVueloIdVuelo(idVuelo);

        if (asignaciones.isEmpty()) {
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("mensaje", "Vuelo cancelado, pero no tenía maletas asignadas");
            resp.put("maletasReasignadas", 0);
            resp.put("vueloId", idVuelo);
            resp.put("vueloCodigo", vuelo.getCodigoVuelo());
            return resp;
        }

        // Marcar asignaciones como canceladas (sin borrar para traceabilidad)
        for (AsignacionVuelo asignacion : asignaciones) {
            asignacion.setEstadoAsignacion("CANCELADA");
            asignacionVueloRepository.save(asignacion);
        }

        log.info("Vuelo {} cancelado. {} asignaciones marcadas como canceladas",
                vuelo.getCodigoVuelo(), asignaciones.size());

        // TODO: Aquí iría la lógica de replanificación con ALNS
        // Por ahora, solo registramos que se canceló
        int totalMaletas = asignaciones.stream()
                .map(AsignacionVuelo::getCantidadAsignada)
                .reduce(0, Integer::sum);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("mensaje", "Vuelo cancelado y maletas marcadas para replanificación");
        resp.put("maletasAReplanificar", totalMaletas);
        resp.put("vueloId", idVuelo);
        resp.put("vueloCodigo", vuelo.getCodigoVuelo());
        resp.put("asignacionesCanceladas", asignaciones.size());

        return resp;
    }
}
