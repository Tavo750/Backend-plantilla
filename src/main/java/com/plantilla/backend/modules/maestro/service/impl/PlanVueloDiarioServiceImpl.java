package com.plantilla.backend.modules.maestro.service.impl;

import com.plantilla.backend.modules.maestro.entity.Aeropuerto;
import com.plantilla.backend.modules.maestro.entity.PlanVueloDiario;
import com.plantilla.backend.modules.maestro.repository.AeropuertoRepository;
import com.plantilla.backend.modules.maestro.repository.PlanVueloDiarioRepository;
import com.plantilla.backend.modules.maestro.service.PlanVueloDiarioService;
import com.plantilla.backend.shared.errors.BusinessException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PlanVueloDiarioServiceImpl implements PlanVueloDiarioService {

    private static final Logger log = LoggerFactory.getLogger(PlanVueloDiarioServiceImpl.class);

    private final PlanVueloDiarioRepository repository;
    private final AeropuertoRepository aeropuertoRepository;
    private final ResourcePatternResolver resourceLoader;

    @Override
    public List<Map<String, Object>> listarConEstado() {
        List<PlanVueloDiario> vuelos = repository.findAll();
        List<Map<String, Object>> resultado = new ArrayList<>(vuelos.size());
        LocalDate hoy = LocalDate.now();

        // GMT por código OACI: la hora se guarda en UTC, pero al usuario se le muestra
        // en la hora LOCAL del aeropuerto (salida=origen, llegada=destino), igual que el
        // código del vuelo y que Operación Diaria / Gestión de Vuelos.
        Map<String, Integer> gmtPorCodigo = new LinkedHashMap<>();
        for (Aeropuerto a : aeropuertoRepository.findAll()) {
            gmtPorCodigo.put(a.getCodigoOaci(), a.getGmt() != null ? a.getGmt() : 0);
        }

        for (PlanVueloDiario v : vuelos) {
            String estado = PlanVueloDiarioService.calcularEstado(v.getHoraSalida(), v.getHoraLlegada());

            int gmtOrigen  = gmtPorCodigo.getOrDefault(v.getCodigoOrigen(), 0);
            int gmtDestino = gmtPorCodigo.getOrDefault(v.getCodigoDestino(), 0);

            // Construir datetimes UTC con la fecha de hoy (overnight: llegada es día siguiente)
            // y llevarlos a la hora LOCAL de cada aeropuerto.
            LocalDateTime horaSalidaDt = LocalDateTime.of(hoy, v.getHoraSalida()).plusHours(gmtOrigen);
            LocalDateTime horaLlegadaDt = (v.getHoraLlegada().isBefore(v.getHoraSalida())
                    ? LocalDateTime.of(hoy.plusDays(1), v.getHoraLlegada())
                    : LocalDateTime.of(hoy, v.getHoraLlegada())).plusHours(gmtDestino);

            // Código con la hora LOCAL del origen (coincide con el código real del vuelo).
            String hhmmLocal = String.format("%02d%02d",
                    horaSalidaDt.getHour(), horaSalidaDt.getMinute());

            // Calcular ocupación desde envíos asignados
            int maletagAsignadas = 0; // Se puede enriquecer con query al EnvioDiarioRepository si se necesita

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id",              v.getId());
            entry.put("codigoVuelo",     v.getCodigoOrigen() + "-" + v.getCodigoDestino() + "-" + hhmmLocal);
            entry.put("origen",          v.getCodigoOrigen());
            entry.put("destino",         v.getCodigoDestino());
            entry.put("horaSalida",      horaSalidaDt.toString());
            entry.put("horaLlegada",     horaLlegadaDt.toString());
            entry.put("capacidad",       v.getCapacidad());
            entry.put("capacidadMaxima", v.getCapacidad());
            entry.put("totalMaletas",    maletagAsignadas);
            entry.put("ocupacionPct",    v.getCapacidad() > 0 ? (double) maletagAsignadas / v.getCapacidad() * 100 : 0.0);
            entry.put("estadoVuelo",     estado);
            entry.put("semaforo",        calcularSemaforo(maletagAsignadas, v.getCapacidad()));
            resultado.add(entry);
        }
        return resultado;
    }

    @Override
    public PlanVueloDiario obtenerPorId(Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException("Plan de vuelo no encontrado con id: " + id));
    }

    @Override
    public PlanVueloDiario crear(PlanVueloDiario vuelo) {
        return repository.save(vuelo);
    }

    @Override
    public PlanVueloDiario actualizar(Integer id, PlanVueloDiario vuelo) {
        PlanVueloDiario existente = obtenerPorId(id);
        existente.setCodigoOrigen(vuelo.getCodigoOrigen());
        existente.setCodigoDestino(vuelo.getCodigoDestino());
        existente.setHoraSalida(vuelo.getHoraSalida());
        existente.setHoraLlegada(vuelo.getHoraLlegada());
        existente.setCapacidad(vuelo.getCapacidad());
        return repository.save(existente);
    }

    @Override
    public void eliminar(Integer id) {
        if (!repository.existsById(id)) throw new BusinessException("Plan de vuelo no encontrado con id: " + id);
        repository.deleteById(id);
    }

    @Override
    @Transactional
    public Map<String, Object> cargarDesdeTxt(String nombreArchivo) {
        Resource resource = resourceLoader.getResource("classpath:data/" + nombreArchivo);
        if (!resource.exists()) throw new BusinessException("Archivo no encontrado: " + nombreArchivo);

        int insertados = 0, omitidos = 0, errores = 0;
        List<PlanVueloDiario> batch = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String linea;
            while ((linea = reader.readLine()) != null) {
                linea = linea.trim();
                if (linea.isBlank()) continue;

                String[] partes = linea.split("-");
                if (partes.length != 5) { errores++; continue; }

                try {
                    String origen   = partes[0].trim();
                    String destino  = partes[1].trim();
                    LocalTime salida  = LocalTime.parse(partes[2].trim());
                    LocalTime llegada = LocalTime.parse(partes[3].trim());
                    int capacidad = Integer.parseInt(partes[4].trim());

                    // Evitar duplicados exactos
                    if (repository.existsByCodigoOrigenAndCodigoDestinoAndHoraSalida(origen, destino, salida)) {
                        omitidos++;
                        continue;
                    }

                    PlanVueloDiario pvd = new PlanVueloDiario();
                    pvd.setCodigoOrigen(origen);
                    pvd.setCodigoDestino(destino);
                    pvd.setHoraSalida(salida);
                    pvd.setHoraLlegada(llegada);
                    pvd.setCapacidad(capacidad);
                    batch.add(pvd);
                    insertados++;
                } catch (Exception e) {
                    errores++;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Error leyendo archivo: " + e.getMessage(), e);
        }

        repository.saveAll(batch);
        log.info("Carga plan vuelo diario: {} insertados, {} omitidos, {} errores", insertados, omitidos, errores);

        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("insertados", insertados);
        resultado.put("omitidos",   omitidos);
        resultado.put("errores",    errores);
        return resultado;
    }

    @Override
    public List<PlanVueloDiario> buscarProximoVuelo(String codigoOrigen, String codigoDestino, LocalTime despuesDe) {
        return repository.findProximoVuelo(codigoOrigen, codigoDestino, despuesDe);
    }

    private String calcularSemaforo(int maletas, int capacidad) {
        if (capacidad == 0) return "VACIO";
        double pct = (double) maletas / capacidad * 100;
        if (pct >= 80) return "ROJO";
        if (pct >= 50) return "AMARILLO";
        if (pct > 0)   return "VERDE";
        return "VACIO";
    }
}
