package com.plantilla.backend.modules.algoritmo.alns.util;

import com.plantilla.backend.modules.algoritmo.alns.model.Aeropuerto;
import com.plantilla.backend.modules.algoritmo.alns.model.Almacen;
import com.plantilla.backend.modules.algoritmo.alns.model.Maleta;
import com.plantilla.backend.modules.algoritmo.alns.model.PlanDeRutas;
import com.plantilla.backend.modules.algoritmo.alns.model.Ruta;
import com.plantilla.backend.modules.algoritmo.alns.model.Vuelo;

import java.util.*;

/**
 * Genera una solución inicial factible para el problema de asignación de envíos.
 * Utiliza FlightIndex para búsqueda eficiente y un enfoque greedy/BFS.
 */
public class SolutionGenerator {

    public static PlanDeRutas generarPlanInicial(List<Maleta> maletas, FlightIndex flightIndex,
                                                  Map<String, Aeropuerto> aeropuertos) {
        PlanDeRutas plan = new PlanDeRutas();

        for (Vuelo vuelo : flightIndex.getTodosLosVuelos()) {
            plan.registrarVuelo(vuelo.copiar());
        }

        for (Aeropuerto aero : aeropuertos.values()) {
            plan.registrarAlmacen(new Almacen(aero.getCodigoICAO(), aero.getCapacidadAlmacen()));
        }

        List<Maleta> maletasOrdenadas = new ArrayList<>(maletas);
        maletasOrdenadas.sort((m1, m2) -> {
            int cmp = Integer.compare(m1.getPrioridad(), m2.getPrioridad());
            if (cmp != 0) return cmp;
            return Long.compare(m1.getSlaLimite(), m2.getSlaLimite());
        });

        for (Maleta maleta : maletasOrdenadas) {
            Ruta mejorRuta = encontrarMejorRuta(maleta, plan, flightIndex);
            if (mejorRuta != null) {
                boolean ok = plan.asignarMaleta(maleta, mejorRuta);
                if (!ok) plan.agregarMaletaNoAsignada(maleta);
            } else {
                plan.agregarMaletaNoAsignada(maleta);
            }
        }

        CostCalculator.calcularCosto(plan);
        return plan;
    }

    private static Ruta encontrarMejorRuta(Maleta maleta, PlanDeRutas plan, FlightIndex flightIndex) {
        String origen = maleta.getAeropuertoOrigen();
        String destino = maleta.getAeropuertoDestino();
        long despuesUTC = maleta.getFechaCreacionUTC();
        long deadlineUTC = maleta.getSlaLimite();
        int cantidad = maleta.getCantidad();

        List<Ruta> rutasEncontradas = new ArrayList<>();
        Queue<Ruta> cola = new LinkedList<>();

        List<Vuelo> vuelosIniciales = flightIndex.buscarVuelosDesdeHasta(
                origen, despuesUTC, deadlineUTC, cantidad);

        for (Vuelo vuelo : vuelosIniciales) {
            Vuelo vueloPlan = plan.getVuelo(vuelo.getId());
            if (vueloPlan == null || !vueloPlan.tieneEspacioPara(cantidad)) continue;

            Ruta ruta = new Ruta();
            ruta.agregarVuelo(vuelo);

            if (vuelo.getDestino().equals(destino)) {
                rutasEncontradas.add(ruta);
            } else {
                cola.add(ruta);
            }
        }

        while (!cola.isEmpty() && rutasEncontradas.size() < 5) {
            Ruta actual = cola.poll();
            if (actual.getNumeroVuelos() >= 3) continue;

            Vuelo ultimo = actual.getVuelos().get(actual.getNumeroVuelos() - 1);
            long tiempoMinSalida = ultimo.getHoraLlegada() + 30;
            List<Vuelo> siguientes = flightIndex.buscarVuelosDesdeHasta(
                    ultimo.getDestino(), tiempoMinSalida, deadlineUTC, cantidad);

            for (Vuelo siguiente : siguientes) {
                Vuelo siguientePlan = plan.getVuelo(siguiente.getId());
                if (siguientePlan == null || !siguientePlan.tieneEspacioPara(cantidad)) continue;

                boolean ciclo = false;
                for (Vuelo v : actual.getVuelos()) {
                    if (v.getOrigen().equals(siguiente.getDestino())) {
                        ciclo = true;
                        break;
                    }
                }
                if (ciclo) continue;

                Ruta nuevaRuta = actual.copiar();
                nuevaRuta.agregarVuelo(siguiente);

                if (siguiente.getDestino().equals(destino)) {
                    rutasEncontradas.add(nuevaRuta);
                } else if (nuevaRuta.getNumeroVuelos() < 3) {
                    cola.add(nuevaRuta);
                }
            }
        }

        if (rutasEncontradas.isEmpty()) return null;
        rutasEncontradas.sort(Comparator.comparingLong(Ruta::getTiempoTotal));
        return rutasEncontradas.get(0);
    }
}
