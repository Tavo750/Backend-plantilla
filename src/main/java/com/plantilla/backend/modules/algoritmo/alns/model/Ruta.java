package com.plantilla.backend.modules.algoritmo.alns.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Secuencia de vuelos que sigue un envío desde su origen hasta su destino.
 * Los tiempos se manejan en minutos absolutos UTC.
 */
public class Ruta {
    private final List<Vuelo> vuelos;

    public Ruta() {
        this.vuelos = new ArrayList<>();
    }

    public Ruta(List<Vuelo> vuelos) {
        this.vuelos = new ArrayList<>(vuelos);
    }

    public Ruta copiar() {
        return new Ruta(this.vuelos);
    }

    public void agregarVuelo(Vuelo vuelo) {
        vuelos.add(vuelo);
    }

    public long getTiempoTotal() {
        if (vuelos.isEmpty()) return 0;
        return vuelos.get(vuelos.size() - 1).getHoraLlegada() - vuelos.get(0).getHoraSalida();
    }

    public long getHoraLlegadaFinal() {
        if (vuelos.isEmpty()) return 0;
        return vuelos.get(vuelos.size() - 1).getHoraLlegada();
    }

    public long getHoraSalidaInicial() {
        if (vuelos.isEmpty()) return 0;
        return vuelos.get(0).getHoraSalida();
    }

    public long getTiempoEspera() {
        long espera = 0;
        for (int i = 1; i < vuelos.size(); i++) {
            espera += vuelos.get(i).getHoraSalida() - vuelos.get(i - 1).getHoraLlegada();
        }
        return espera;
    }

    public int getNumeroConexiones() {
        return Math.max(0, vuelos.size() - 1);
    }

    public boolean esFactible() {
        for (int i = 1; i < vuelos.size(); i++) {
            Vuelo anterior = vuelos.get(i - 1);
            Vuelo siguiente = vuelos.get(i);
            if (!anterior.getDestino().equals(siguiente.getOrigen())) {
                return false;
            }
            if (siguiente.getHoraSalida() < anterior.getHoraLlegada() + 30) {
                return false;
            }
        }
        return true;
    }

    public String getOrigen() {
        return vuelos.isEmpty() ? null : vuelos.get(0).getOrigen();
    }

    public String getDestino() {
        return vuelos.isEmpty() ? null : vuelos.get(vuelos.size() - 1).getDestino();
    }

    public List<Vuelo> getVuelos() { return Collections.unmodifiableList(vuelos); }
    public int getNumeroVuelos() { return vuelos.size(); }
    public boolean estaVacia() { return vuelos.isEmpty(); }

    @Override
    public String toString() {
        if (vuelos.isEmpty()) return "Ruta{vacía}";
        StringBuilder sb = new StringBuilder();
        sb.append(vuelos.get(0).getOrigen());
        for (Vuelo v : vuelos) {
            sb.append(" → ").append(v.getDestino()).append(" (").append(v.getId()).append(")");
        }
        return sb.toString();
    }
}
