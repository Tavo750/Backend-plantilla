package com.plantilla.backend.modules.algoritmo.alns.util;

import com.plantilla.backend.modules.algoritmo.alns.model.Vuelo;

import java.util.*;

/**
 * Índice de vuelos para búsqueda rápida.
 * Organiza los vuelos por aeropuerto de origen, permitiendo búsquedas O(log n)
 * por hora de salida en vez de iterar todos los vuelos.
 */
public class FlightIndex {
    private final Map<String, List<Vuelo>> vuelosPorOrigen;

    public FlightIndex() {
        this.vuelosPorOrigen = new HashMap<>();
    }

    public FlightIndex(List<Vuelo> vuelos) {
        this.vuelosPorOrigen = new HashMap<>();
        for (Vuelo vuelo : vuelos) {
            vuelosPorOrigen.computeIfAbsent(vuelo.getOrigen(), k -> new ArrayList<>()).add(vuelo);
        }
        for (List<Vuelo> lista : vuelosPorOrigen.values()) {
            lista.sort(Comparator.comparingLong(Vuelo::getHoraSalida));
        }
    }

    public List<Vuelo> buscarVuelosDesde(String origen, long despuesUTC, int cantidad) {
        List<Vuelo> resultado = new ArrayList<>();
        List<Vuelo> vuelosOrigen = vuelosPorOrigen.get(origen);
        if (vuelosOrigen == null) return resultado;

        int idx = busquedaBinaria(vuelosOrigen, despuesUTC);
        for (int i = idx; i < vuelosOrigen.size(); i++) {
            Vuelo vuelo = vuelosOrigen.get(i);
            if (vuelo.tieneEspacioPara(cantidad)) {
                resultado.add(vuelo);
            }
        }
        return resultado;
    }

    public List<Vuelo> buscarVuelosDesdeHasta(String origen, long despuesUTC, long antesUTC, int cantidad) {
        List<Vuelo> resultado = new ArrayList<>();
        List<Vuelo> vuelosOrigen = vuelosPorOrigen.get(origen);
        if (vuelosOrigen == null) return resultado;

        int idx = busquedaBinaria(vuelosOrigen, despuesUTC);
        for (int i = idx; i < vuelosOrigen.size(); i++) {
            Vuelo vuelo = vuelosOrigen.get(i);
            if (vuelo.getHoraSalida() > antesUTC) break;
            if (vuelo.tieneEspacioPara(cantidad)) {
                resultado.add(vuelo);
            }
        }
        return resultado;
    }

    public List<Vuelo> getTodosLosVuelos() {
        List<Vuelo> todos = new ArrayList<>();
        for (List<Vuelo> lista : vuelosPorOrigen.values()) {
            todos.addAll(lista);
        }
        return todos;
    }

    public Set<String> getAeropuertosConSalida() {
        return vuelosPorOrigen.keySet();
    }

    public int getTotalVuelos() {
        int total = 0;
        for (List<Vuelo> lista : vuelosPorOrigen.values()) {
            total += lista.size();
        }
        return total;
    }

    private int busquedaBinaria(List<Vuelo> vuelos, long target) {
        int lo = 0, hi = vuelos.size();
        while (lo < hi) {
            int mid = (lo + hi) / 2;
            if (vuelos.get(mid).getHoraSalida() < target) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }
}
