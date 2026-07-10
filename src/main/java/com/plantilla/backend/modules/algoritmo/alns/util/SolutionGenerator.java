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

        // Most-constrained-first: la maleta con MENOS vuelos disponibles desde su
        // origen se asigna primero (las flexibles encuentran alternativas después).
        // Evita que una maleta fácil ocupe el último asiento del único vuelo viable
        // para una maleta difícil. Desempate: prioridad y deadline SLA.
        Map<Maleta, Integer> opciones = new HashMap<>();
        for (Maleta m : maletas) {
            opciones.put(m, flightIndex.buscarVuelosDesdeHasta(
                    m.getAeropuertoOrigen(), m.getFechaCreacionUTC(),
                    m.getSlaLimite(), m.getCantidad()).size());
        }

        List<Maleta> maletasOrdenadas = new ArrayList<>(maletas);
        maletasOrdenadas.sort((m1, m2) -> {
            int cmp = Integer.compare(opciones.get(m1), opciones.get(m2));
            if (cmp != 0) return cmp;
            cmp = Integer.compare(m1.getPrioridad(), m2.getPrioridad());
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
        // Exploración por llegada más temprana (no FIFO): las primeras rutas
        // completadas son las que llegan antes → maximiza el cumplimiento de SLA
        PriorityQueue<Ruta> cola = new PriorityQueue<>(
                Comparator.comparingLong(Ruta::getHoraLlegadaFinal));
        // Poda de dominados por (aeropuerto, nº de escalas): conservar solo la
        // llegada más temprana a un hub PERO diferenciando por profundidad, para
        // no descartar una ruta directa-a-hub (extensible con 1 escala más) solo
        // porque una ruta con más escalas llegó antes al mismo hub.
        Map<String, Long> mejorLlegadaPor = new HashMap<>();
        boolean haySlaCompatible = false;

        List<Vuelo> vuelosIniciales = flightIndex.buscarVuelosDesdeHasta(
                origen, despuesUTC, deadlineUTC, cantidad);

        for (Vuelo vuelo : vuelosIniciales) {
            Vuelo vueloPlan = plan.getVuelo(vuelo.getId());
            if (vueloPlan == null || !vueloPlan.tieneEspacioPara(cantidad)) continue;

            Ruta ruta = new Ruta();
            ruta.agregarVuelo(vuelo);

            if (vuelo.getDestino().equals(destino)) {
                rutasEncontradas.add(ruta);
                if (!maleta.isSLAExpirado(ruta.getHoraLlegadaFinal())) haySlaCompatible = true;
            } else {
                String clave = vuelo.getDestino() + "#1";
                Long mejor = mejorLlegadaPor.get(clave);
                if (mejor == null || vuelo.getHoraLlegada() < mejor) {
                    mejorLlegadaPor.put(clave, vuelo.getHoraLlegada());
                    cola.add(ruta);
                }
            }
        }

        // Sigue buscando mientras no haya al menos una ruta SLA-compatible (hasta
        // un tope amplio). Así una maleta que perdió su vuelo directo del día
        // igual encuentra la conexión que llega a tiempo, si existe.
        while (!cola.isEmpty() && (rutasEncontradas.size() < 25 || !haySlaCompatible)) {
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
                    if (!maleta.isSLAExpirado(nuevaRuta.getHoraLlegadaFinal())) haySlaCompatible = true;
                    if (rutasEncontradas.size() >= 25 && haySlaCompatible) break;
                } else if (nuevaRuta.getNumeroVuelos() < 3) {
                    String clave = siguiente.getDestino() + "#" + nuevaRuta.getNumeroVuelos();
                    Long mejor = mejorLlegadaPor.get(clave);
                    if (mejor == null || siguiente.getHoraLlegada() < mejor) {
                        mejorLlegadaPor.put(clave, siguiente.getHoraLlegada());
                        cola.add(nuevaRuta);
                    }
                }
            }
        }

        if (rutasEncontradas.isEmpty()) return null;

        // SLA primero: entre las rutas que CUMPLEN el deadline, la más rápida.
        // Solo si ninguna cumple, se acepta la de menor llegada (la menos tardía).
        Ruta mejorCumple = null;
        long mejorTiempo = Long.MAX_VALUE;
        for (Ruta r : rutasEncontradas) {
            if (!maleta.isSLAExpirado(r.getHoraLlegadaFinal()) && r.getTiempoTotal() < mejorTiempo) {
                mejorCumple = r;
                mejorTiempo = r.getTiempoTotal();
            }
        }
        if (mejorCumple != null) return mejorCumple;

        rutasEncontradas.sort(Comparator.comparingLong(Ruta::getHoraLlegadaFinal));
        return rutasEncontradas.get(0);
    }
}
