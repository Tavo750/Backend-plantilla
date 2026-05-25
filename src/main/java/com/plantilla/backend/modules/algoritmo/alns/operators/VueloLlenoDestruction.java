package com.plantilla.backend.modules.algoritmo.alns.operators;

import com.plantilla.backend.modules.algoritmo.alns.model.Maleta;
import com.plantilla.backend.modules.algoritmo.alns.model.PlanDeRutas;
import com.plantilla.backend.modules.algoritmo.alns.model.Vuelo;

import java.util.*;

/**
 * Operador de destrucción que prioriza la remoción de envíos de vuelos
 * que están al límite de su capacidad (saturados).
 */
public class VueloLlenoDestruction implements DestructionOperator {

    @Override
    public PlanDeRutas destroy(PlanDeRutas plan, double porcentajeRemocion) {
        PlanDeRutas planCopia = plan.copiar();

        int totalAsignadas = planCopia.getTotalMaletasAsignadas();
        int cantidadARemover = Math.max(1, (int) (totalAsignadas * porcentajeRemocion));

        List<Vuelo> vuelosOrdenados = new ArrayList<>(planCopia.getVuelosMap().values());
        vuelosOrdenados.sort((v1, v2) -> Double.compare(v2.getOcupacion(), v1.getOcupacion()));

        Set<Maleta> maletasARemover = new LinkedHashSet<>();

        for (Vuelo vuelo : vuelosOrdenados) {
            if (maletasARemover.size() >= cantidadARemover) break;
            if (vuelo.getOcupacion() < 0.5) break;

            List<Maleta> maletasDelVuelo = new ArrayList<>(vuelo.getMaletasAsignadas());
            Collections.shuffle(maletasDelVuelo);

            for (Maleta maleta : maletasDelVuelo) {
                if (maletasARemover.size() >= cantidadARemover) break;
                maletasARemover.add(maleta);
            }
        }

        if (maletasARemover.size() < cantidadARemover) {
            List<Maleta> todasAsignadas = new ArrayList<>(planCopia.getAsignaciones().keySet());
            Collections.shuffle(todasAsignadas);
            for (Maleta m : todasAsignadas) {
                if (maletasARemover.size() >= cantidadARemover) break;
                maletasARemover.add(m);
            }
        }

        for (Maleta maleta : maletasARemover) {
            planCopia.desasignarMaleta(maleta);
        }

        return planCopia;
    }

    @Override
    public String getNombre() {
        return "Vuelo-Lleno";
    }
}
