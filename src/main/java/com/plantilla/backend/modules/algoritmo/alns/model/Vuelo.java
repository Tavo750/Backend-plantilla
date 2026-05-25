package com.plantilla.backend.modules.algoritmo.alns.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Representa un vuelo en la red aeroportuaria (modelo interno ALNS).
 * Capacidad como suma de cantidades de los envíos asignados.
 * Tiempos en minutos absolutos UTC desde epoch.
 *
 * Modelo interno del algoritmo ALNS. Para la entidad JPA ver
 * {@code com.plantilla.backend.modules.maestro.entity.Vuelo}.
 */
public class Vuelo {
    private final String id;
    private final String origen;
    private final String destino;
    private final int capacidad;
    private final long horaSalida;
    private final long horaLlegada;
    private final List<Maleta> maletasAsignadas;
    private int capacidadUsada;

    // Vínculo opcional con el id de la entidad JPA original (para persistencia)
    private final Integer idVueloBackend;

    public Vuelo(String id, String origen, String destino, int capacidad,
                 long horaSalida, long horaLlegada) {
        this(id, origen, destino, capacidad, horaSalida, horaLlegada, null);
    }

    public Vuelo(String id, String origen, String destino, int capacidad,
                 long horaSalida, long horaLlegada, Integer idVueloBackend) {
        this.id = id;
        this.origen = origen;
        this.destino = destino;
        this.capacidad = capacidad;
        this.horaSalida = horaSalida;
        this.horaLlegada = horaLlegada;
        this.maletasAsignadas = new ArrayList<>();
        this.capacidadUsada = 0;
        this.idVueloBackend = idVueloBackend;
    }

    /**
     * Crea una copia profunda del vuelo con su lista de maletas independiente.
     */
    public Vuelo copiar() {
        Vuelo copia = new Vuelo(id, origen, destino, capacidad, horaSalida, horaLlegada, idVueloBackend);
        copia.maletasAsignadas.addAll(this.maletasAsignadas);
        copia.capacidadUsada = this.capacidadUsada;
        return copia;
    }

    public boolean asignarMaleta(Maleta maleta) {
        if (capacidadUsada + maleta.getCantidad() > capacidad) {
            return false;
        }
        if (!maletasAsignadas.contains(maleta)) {
            maletasAsignadas.add(maleta);
            capacidadUsada += maleta.getCantidad();
        }
        return true;
    }

    public boolean removerMaleta(Maleta maleta) {
        boolean removed = maletasAsignadas.remove(maleta);
        if (removed) {
            capacidadUsada -= maleta.getCantidad();
        }
        return removed;
    }

    public double getOcupacion() {
        return (double) capacidadUsada / capacidad;
    }

    public boolean tieneEspacio() {
        return capacidadUsada < capacidad;
    }

    public boolean tieneEspacioPara(int cantidad) {
        return capacidadUsada + cantidad <= capacidad;
    }

    public int getEspaciosLibres() {
        return capacidad - capacidadUsada;
    }

    public long getDuracion() {
        return horaLlegada - horaSalida;
    }

    // === Getters ===
    public String getId() { return id; }
    public String getOrigen() { return origen; }
    public String getDestino() { return destino; }
    public int getCapacidad() { return capacidad; }
    public long getHoraSalida() { return horaSalida; }
    public long getHoraLlegada() { return horaLlegada; }
    public List<Maleta> getMaletasAsignadas() { return Collections.unmodifiableList(maletasAsignadas); }
    public int getCantidadMaletas() { return maletasAsignadas.size(); }
    public int getCapacidadUsada() { return capacidadUsada; }
    public Integer getIdVueloBackend() { return idVueloBackend; }

    @Override
    public String toString() {
        return "Vuelo{" + id + ", " + origen + "→" + destino +
               ", Cap=" + capacidadUsada + "/" + capacidad +
               ", Sal=" + horaSalida + ", Lleg=" + horaLlegada + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Vuelo vuelo = (Vuelo) o;
        return id.equals(vuelo.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
