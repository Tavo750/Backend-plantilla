package com.plantilla.backend.modules.algoritmo.alns.operators;

import com.plantilla.backend.modules.algoritmo.alns.model.Maleta;
import com.plantilla.backend.modules.algoritmo.alns.model.PlanDeRutas;
import com.plantilla.backend.modules.algoritmo.alns.model.Ruta;
import com.plantilla.backend.modules.algoritmo.alns.util.FlightIndex;

import java.util.*;

/**
 * Operador de reparación con Regret-2 y perturbación.
 *
 * Prioriza los envíos cuya 2da mejor opción es significativamente peor que su 1ra.
 * Regret = (costo 2da mejor) - (costo 1ra mejor).
 */
public class RegretInsertion implements RepairOperator {
    private final GreedyInsertion greedy;
    private final Random random = new Random();
    private static final double NOISE_FACTOR = 0.10;

    public RegretInsertion() {
        this.greedy = new GreedyInsertion();
    }

    @Override
    public PlanDeRutas repair(PlanDeRutas planParcial, FlightIndex flightIndex) {
        PlanDeRutas plan = planParcial.copiar();

        int sinMejora = 0;
        int prevPendientes = plan.getMaletasNoAsignadas().size();

        while (!plan.getMaletasNoAsignadas().isEmpty()) {
            List<Maleta> pendientes = new ArrayList<>(plan.getMaletasNoAsignadas());

            Maleta mejorMaleta = null;
            Ruta mejorRutaParaMejorMaleta = null;
            double maxRegret = -Double.MAX_VALUE;

            for (Maleta maleta : pendientes) {
                List<Ruta> rutasFactibles = greedy.encontrarRutasFactibles(maleta, plan, flightIndex);
                if (rutasFactibles.isEmpty()) continue;

                List<double[]> costosConIndice = new ArrayList<>();
                for (int i = 0; i < rutasFactibles.size(); i++) {
                    double costo = greedy.evaluarCostoInsercion(maleta, rutasFactibles.get(i));
                    costosConIndice.add(new double[]{costo, i});
                }

                costosConIndice.sort(Comparator.comparingDouble(a -> a[0]));

                double costoMejor = costosConIndice.get(0)[0];
                int indiceMejor = (int) costosConIndice.get(0)[1];

                double regret;
                if (costosConIndice.size() >= 2) {
                    double costoSegundo = costosConIndice.get(1)[0];
                    regret = costoSegundo - costoMejor;
                } else {
                    regret = Double.MAX_VALUE / 2;
                }

                double noise = Math.abs(regret) * NOISE_FACTOR * (random.nextDouble() * 2 - 1);
                regret += noise;

                if (regret > maxRegret) {
                    maxRegret = regret;
                    mejorMaleta = maleta;
                    mejorRutaParaMejorMaleta = rutasFactibles.get(indiceMejor);
                }
            }

            if (mejorMaleta != null && mejorRutaParaMejorMaleta != null) {
                plan.asignarMaleta(mejorMaleta, mejorRutaParaMejorMaleta);
            } else {
                break;
            }

            int currentPendientes = plan.getMaletasNoAsignadas().size();
            if (currentPendientes >= prevPendientes) {
                sinMejora++;
                if (sinMejora > pendientes.size()) break;
            } else {
                sinMejora = 0;
            }
            prevPendientes = currentPendientes;
        }

        return plan;
    }

    @Override
    public String getNombre() {
        return "Regret-2";
    }
}
