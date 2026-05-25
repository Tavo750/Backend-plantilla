package com.plantilla.backend.modules.algoritmo.alns.operators;

import com.plantilla.backend.modules.algoritmo.alns.model.Maleta;
import com.plantilla.backend.modules.algoritmo.alns.model.PlanDeRutas;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Operador de destrucción aleatorio.
 * Selecciona envíos al azar para remover (diversificación pura).
 */
public class RandomDestruction implements DestructionOperator {
    private final Random random;

    public RandomDestruction() {
        this.random = new Random();
    }

    public RandomDestruction(long seed) {
        this.random = new Random(seed);
    }

    @Override
    public PlanDeRutas destroy(PlanDeRutas plan, double porcentajeRemocion) {
        PlanDeRutas planCopia = plan.copiar();

        List<Maleta> asignadas = new ArrayList<>(planCopia.getAsignaciones().keySet());
        int cantidadARemover = Math.max(1, (int) (asignadas.size() * porcentajeRemocion));

        Collections.shuffle(asignadas, random);

        for (int i = 0; i < cantidadARemover && i < asignadas.size(); i++) {
            planCopia.desasignarMaleta(asignadas.get(i));
        }

        return planCopia;
    }

    @Override
    public String getNombre() {
        return "Random";
    }
}
