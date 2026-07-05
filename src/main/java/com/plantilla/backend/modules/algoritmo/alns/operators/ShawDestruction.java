package com.plantilla.backend.modules.algoritmo.alns.operators;

import com.plantilla.backend.modules.algoritmo.alns.model.Maleta;
import com.plantilla.backend.modules.algoritmo.alns.model.PlanDeRutas;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Shaw removal (related removal): remueve grupos de envíos RELACIONADOS entre sí
 * (mismo origen/destino, horarios de registro cercanos) para que la reparación
 * pueda recombinarlos en rutas mejores. Es el operador de referencia en la
 * literatura ALNS (Ropke &amp; Pisinger) para escapar de óptimos locales, porque
 * remover envíos que compiten por los mismos vuelos abre espacio de intercambio
 * real, a diferencia de la remoción aleatoria.
 */
public class ShawDestruction implements DestructionOperator {

    /** Exponente de aleatorización: valores altos sesgan hacia los más relacionados */
    private static final double DETERMINISMO = 3.0;

    private final Random random = new Random(13);

    @Override
    public PlanDeRutas destroy(PlanDeRutas plan, double porcentajeRemocion) {
        PlanDeRutas copia = plan.copiar();

        List<Maleta> asignadas = new ArrayList<>(copia.getAsignaciones().keySet());
        if (asignadas.isEmpty()) return copia;

        int aRemover = Math.max(1, (int) (asignadas.size() * porcentajeRemocion));

        // Semilla aleatoria; el resto se elige por afinidad con las ya removidas
        List<Maleta> removidas = new ArrayList<>();
        Maleta semilla = asignadas.remove(random.nextInt(asignadas.size()));
        copia.desasignarMaleta(semilla);
        removidas.add(semilla);

        while (removidas.size() < aRemover && !asignadas.isEmpty()) {
            Maleta referencia = removidas.get(random.nextInt(removidas.size()));
            asignadas.sort(Comparator.comparingDouble(m -> relacion(referencia, m)));
            int idx = (int) (Math.pow(random.nextDouble(), DETERMINISMO) * asignadas.size());
            Maleta elegida = asignadas.remove(Math.min(idx, asignadas.size() - 1));
            copia.desasignarMaleta(elegida);
            removidas.add(elegida);
        }

        return copia;
    }

    /** Menor valor = más relacionadas (comparten OD y se registraron casi juntas) */
    private double relacion(Maleta a, Maleta b) {
        double r = 0;
        if (!a.getAeropuertoOrigen().equals(b.getAeropuertoOrigen()))   r += 1.0;
        if (!a.getAeropuertoDestino().equals(b.getAeropuertoDestino())) r += 1.0;
        // Distancia temporal en días (1440 min): registros cercanos compiten por los mismos vuelos
        r += Math.abs(a.getFechaCreacionUTC() - b.getFechaCreacionUTC()) / 1440.0;
        return r;
    }

    @Override
    public String getNombre() {
        return "Shaw";
    }
}
