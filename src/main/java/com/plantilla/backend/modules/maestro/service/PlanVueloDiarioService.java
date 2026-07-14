package com.plantilla.backend.modules.maestro.service;

import com.plantilla.backend.modules.maestro.entity.PlanVueloDiario;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

public interface PlanVueloDiarioService {
    /** Timezone de Lima/Perú donde operan los vuelos. */
    ZoneId LIMA_ZONE = ZoneId.of("America/Lima");

    List<Map<String, Object>> listarConEstado();
    PlanVueloDiario obtenerPorId(Integer id);
    PlanVueloDiario crear(PlanVueloDiario vuelo);
    PlanVueloDiario actualizar(Integer id, PlanVueloDiario vuelo);
    void eliminar(Integer id);
    Map<String, Object> cargarDesdeTxt(String nombreArchivo);
    List<PlanVueloDiario> buscarProximoVuelo(String codigoOrigen, String codigoDestino, LocalTime despuesDe);

    /** Calcula el estado de un vuelo comparando con la hora actual en Lima. */
    static String calcularEstado(LocalTime horaSalida, LocalTime horaLlegada) {
        LocalTime ahora = LocalTime.now(LIMA_ZONE);
        if (horaSalida.isBefore(horaLlegada)) {
            // Vuelo diurno (mismo día)
            if (ahora.isBefore(horaSalida)) return "POR_SALIR";
            if (ahora.isBefore(horaLlegada)) return "EN_VUELO";
            return "LLEGÓ";
        } else {
            // Vuelo nocturno (cruza medianoche): EN_VUELO si salió O si aún no llegó
            if (!ahora.isBefore(horaSalida)) return "EN_VUELO"; // ya salió
            if (ahora.isBefore(horaLlegada)) return "EN_VUELO"; // aún no llegó (madrugada)
            // Entre horaLlegada y horaSalida → todavía no ha salido hoy
            return "POR_SALIR";
        }
    }
}
