package com.plantilla.backend.modules.algoritmo.alns.operators;

import com.plantilla.backend.modules.algoritmo.alns.model.Maleta;
import com.plantilla.backend.modules.algoritmo.alns.model.PlanDeRutas;
import com.plantilla.backend.modules.algoritmo.alns.model.Ruta;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Worst removal: remueve los envíos que MÁS contribuyen al costo del plan
 * (rutas largas, muchas conexiones, SLA violado) para que la reparación les
 * busque una asignación mejor. Complementa a Shaw: Shaw diversifica por
 * afinidad, Worst intensifica atacando las asignaciones más caras.
 */
public class WorstDestruction implements DestructionOperator {

    /** Pesos espejo de {@link com.plantilla.backend.modules.algoritmo.alns.util.CostCalculator} */
    private static final double PESO_TIEMPO      = 0.5;
    private static final double PESO_CONEXION    = 50.0;
    private static final double PESO_ESPERA      = 0.3;
    private static final double PESO_SLA_VIOLADO = 5.0;
    private static final double DETERMINISMO     = 3.0;

    private final Random random = new Random(17);

    @Override
    public PlanDeRutas destroy(PlanDeRutas plan, double porcentajeRemocion) {
        PlanDeRutas copia = plan.copiar();

        Map<Maleta, Ruta> asignaciones = copia.getAsignaciones();
        if (asignaciones.isEmpty()) return copia;

        int aRemover = Math.max(1, (int) (asignaciones.size() * porcentajeRemocion));

        List<Map.Entry<Maleta, Double>> porCosto = new ArrayList<>();
        for (Map.Entry<Maleta, Ruta> e : asignaciones.entrySet()) {
            porCosto.add(new AbstractMap.SimpleEntry<>(e.getKey(), costoAsignacion(e.getKey(), e.getValue())));
        }
        porCosto.sort((a, b) -> Double.compare(b.getValue(), a.getValue())); // más caro primero

        int removidas = 0;
        while (removidas < aRemover && !porCosto.isEmpty()) {
            // Sesgo hacia los peores con algo de ruido (evita ciclos deterministas)
            int idx = (int) (Math.pow(random.nextDouble(), DETERMINISMO) * porCosto.size());
            Maleta maleta = porCosto.remove(Math.min(idx, porCosto.size() - 1)).getKey();
            copia.desasignarMaleta(maleta);
            removidas++;
        }

        return copia;
    }

    /** Costo individual de una asignación, espejo del cálculo por envío del CostCalculator */
    private double costoAsignacion(Maleta maleta, Ruta ruta) {
        double costo = ruta.getTiempoTotal() * PESO_TIEMPO
                     + ruta.getNumeroConexiones() * PESO_CONEXION
                     + ruta.getTiempoEspera() * PESO_ESPERA;
        long llegada = ruta.getHoraLlegadaFinal();
        if (maleta.isSLAExpirado(llegada)) {
            costo += (llegada - maleta.getSlaLimite()) * PESO_SLA_VIOLADO;
        }
        return costo * maleta.getCantidad();
    }

    @Override
    public String getNombre() {
        return "Worst";
    }
}
