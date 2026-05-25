package com.plantilla.backend.modules.algoritmo.alns.operators;

import com.plantilla.backend.modules.algoritmo.alns.model.Maleta;
import com.plantilla.backend.modules.algoritmo.alns.model.PlanDeRutas;
import com.plantilla.backend.modules.algoritmo.alns.model.Ruta;

import java.util.*;

/**
 * Operador de destrucción que prioriza la remoción de envíos cuyo SLA
 * está próximo a vencer o ya ha sido violado.
 */
public class SLAExpiradoDestruction implements DestructionOperator {

    @Override
    public PlanDeRutas destroy(PlanDeRutas plan, double porcentajeRemocion) {
        PlanDeRutas planCopia = plan.copiar();

        Map<Maleta, Ruta> asignaciones = planCopia.getAsignaciones();
        int cantidadARemover = Math.max(1, (int) (asignaciones.size() * porcentajeRemocion));

        List<Map.Entry<Maleta, Long>> maletasConHolgura = new ArrayList<>();
        for (Map.Entry<Maleta, Ruta> entry : asignaciones.entrySet()) {
            Maleta maleta = entry.getKey();
            Ruta ruta = entry.getValue();
            long horaLlegada = ruta.getHoraLlegadaFinal();
            long holgura = maleta.getHolguraSLA(horaLlegada);
            maletasConHolgura.add(new AbstractMap.SimpleEntry<>(maleta, holgura));
        }

        maletasConHolgura.sort(Comparator.comparingLong(Map.Entry::getValue));

        int removidas = 0;
        for (Map.Entry<Maleta, Long> entry : maletasConHolgura) {
            if (removidas >= cantidadARemover) break;
            planCopia.desasignarMaleta(entry.getKey());
            removidas++;
        }

        return planCopia;
    }

    @Override
    public String getNombre() {
        return "SLA-Expirado";
    }
}
