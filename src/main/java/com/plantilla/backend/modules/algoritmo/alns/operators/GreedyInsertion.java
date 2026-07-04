package com.plantilla.backend.modules.algoritmo.alns.operators;

import com.plantilla.backend.modules.algoritmo.alns.model.Maleta;
import com.plantilla.backend.modules.algoritmo.alns.model.PlanDeRutas;
import com.plantilla.backend.modules.algoritmo.alns.model.Ruta;
import com.plantilla.backend.modules.algoritmo.alns.model.Vuelo;
import com.plantilla.backend.modules.algoritmo.alns.util.FlightIndex;

import java.util.*;

/**
 * Operador de reparación Greedy (ávido) con perturbación aleatoria.
 *
 * Para cada envío no asignado, encuentra la ruta de menor costo incremental
 * (con ruido) y la inserta inmediatamente.
 */
public class GreedyInsertion implements RepairOperator {
    private final Random random = new Random();
    private static final double NOISE_FACTOR = 0.15;

    @Override
    public PlanDeRutas repair(PlanDeRutas planParcial, FlightIndex flightIndex) {
        PlanDeRutas plan = planParcial.copiar();

        List<Maleta> maletasPendientes = new ArrayList<>(plan.getMaletasNoAsignadas());

        Collections.shuffle(maletasPendientes, random);
        maletasPendientes.sort((m1, m2) -> {
            int cmp = Integer.compare(m1.getPrioridad(), m2.getPrioridad());
            if (cmp != 0) return cmp;
            return Long.compare(m1.getSlaLimite(), m2.getSlaLimite());
        });

        for (Maleta maleta : maletasPendientes) {
            List<Ruta> rutasFactibles = encontrarRutasFactibles(maleta, plan, flightIndex);

            // SLA primero: si existen rutas que cumplen el deadline, solo se
            // compite entre ellas; las tardías son último recurso
            List<Ruta> cumplenSla = new ArrayList<>();
            for (Ruta r : rutasFactibles) {
                if (!maleta.isSLAExpirado(r.getHoraLlegadaFinal())) cumplenSla.add(r);
            }
            List<Ruta> candidatas = cumplenSla.isEmpty() ? rutasFactibles : cumplenSla;

            Ruta mejorRuta = null;
            double mejorCosto = Double.MAX_VALUE;
            for (Ruta ruta : candidatas) {
                double costo = evaluarCostoInsercion(maleta, ruta);
                double noise = costo * NOISE_FACTOR * (random.nextDouble() * 2 - 1);
                double costoConRuido = costo + noise;

                if (costoConRuido < mejorCosto) {
                    mejorCosto = costoConRuido;
                    mejorRuta = ruta;
                }
            }

            if (mejorRuta != null) {
                plan.asignarMaleta(maleta, mejorRuta);
            }
        }

        return plan;
    }

    public List<Ruta> encontrarRutasFactibles(Maleta maleta, PlanDeRutas plan, FlightIndex flightIndex) {
        List<Ruta> rutas = new ArrayList<>();
        String origen = maleta.getAeropuertoOrigen();
        String destino = maleta.getAeropuertoDestino();
        long despuesUTC = maleta.getFechaCreacionUTC();
        long deadlineUTC = maleta.getSlaLimite();
        int cantidad = maleta.getCantidad();

        // Exploración por llegada más temprana + poda de dominados: las rutas
        // encontradas primero son las que llegan antes (mejor SLA)
        PriorityQueue<Ruta> cola = new PriorityQueue<>(
                Comparator.comparingLong(Ruta::getHoraLlegadaFinal));
        Map<String, Long> mejorLlegadaPor = new HashMap<>();

        List<Vuelo> vuelosIniciales = flightIndex.buscarVuelosDesdeHasta(
                origen, despuesUTC, deadlineUTC, cantidad);

        for (Vuelo vuelo : vuelosIniciales) {
            Vuelo vueloPlan = plan.getVuelo(vuelo.getId());
            if (vueloPlan == null || !vueloPlan.tieneEspacioPara(cantidad)) continue;

            Ruta ruta = new Ruta();
            ruta.agregarVuelo(vuelo);

            if (vuelo.getDestino().equals(destino)) {
                rutas.add(ruta);
                if (rutas.size() >= 15) return rutas;
            } else {
                Long mejor = mejorLlegadaPor.get(vuelo.getDestino());
                if (mejor == null || vuelo.getHoraLlegada() < mejor) {
                    mejorLlegadaPor.put(vuelo.getDestino(), vuelo.getHoraLlegada());
                    cola.add(ruta);
                }
            }
        }

        while (!cola.isEmpty() && rutas.size() < 15) {
            Ruta rutaActual = cola.poll();
            if (rutaActual.getNumeroVuelos() >= 3) continue;

            Vuelo ultimoVuelo = rutaActual.getVuelos().get(rutaActual.getNumeroVuelos() - 1);
            long tiempoMinSalida = ultimoVuelo.getHoraLlegada() + 30;
            List<Vuelo> siguientes = flightIndex.buscarVuelosDesdeHasta(
                    ultimoVuelo.getDestino(), tiempoMinSalida, deadlineUTC, cantidad);

            for (Vuelo siguienteVuelo : siguientes) {
                Vuelo siguientePlan = plan.getVuelo(siguienteVuelo.getId());
                if (siguientePlan == null || !siguientePlan.tieneEspacioPara(cantidad)) continue;

                boolean ciclo = false;
                for (Vuelo v : rutaActual.getVuelos()) {
                    if (v.getOrigen().equals(siguienteVuelo.getDestino())) {
                        ciclo = true;
                        break;
                    }
                }
                if (ciclo) continue;

                Ruta nuevaRuta = rutaActual.copiar();
                nuevaRuta.agregarVuelo(siguienteVuelo);

                if (siguienteVuelo.getDestino().equals(destino)) {
                    rutas.add(nuevaRuta);
                    if (rutas.size() >= 15) return rutas;
                } else if (nuevaRuta.getNumeroVuelos() < 3) {
                    Long mejor = mejorLlegadaPor.get(siguienteVuelo.getDestino());
                    if (mejor == null || siguienteVuelo.getHoraLlegada() < mejor) {
                        mejorLlegadaPor.put(siguienteVuelo.getDestino(), siguienteVuelo.getHoraLlegada());
                        cola.add(nuevaRuta);
                    }
                }
            }
        }

        return rutas;
    }

    public double evaluarCostoInsercion(Maleta maleta, Ruta ruta) {
        double costo = 0;

        costo += ruta.getTiempoTotal() * 0.5;
        costo += ruta.getNumeroConexiones() * 50.0;
        costo += ruta.getTiempoEspera() * 0.3;

        long horaLlegada = ruta.getHoraLlegadaFinal();
        if (maleta.isSLAExpirado(horaLlegada)) {
            long exceso = horaLlegada - maleta.getSlaLimite();
            costo += exceso * 5.0;
        }

        if (maleta.getPrioridad() == 1) {
            costo *= 1.5;
        } else if (maleta.getPrioridad() == 2) {
            costo *= 1.2;
        }

        costo *= (1.0 + maleta.getCantidad() * 0.05);

        return costo;
    }

    @Override
    public String getNombre() {
        return "Greedy";
    }
}
