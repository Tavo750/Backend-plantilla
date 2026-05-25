package com.plantilla.backend.modules.algoritmo.alns.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Representa un almacén de tránsito en un aeropuerto.
 * Capacidad como suma de cantidades de envíos almacenados.
 */
public class Almacen {
    private final String aeropuerto;
    private final int capacidad;
    private final List<Maleta> maletasAlmacenadas;
    private int capacidadUsada;

    public Almacen(String aeropuerto, int capacidad) {
        this.aeropuerto = aeropuerto;
        this.capacidad = capacidad;
        this.maletasAlmacenadas = new ArrayList<>();
        this.capacidadUsada = 0;
    }

    public Almacen copiar() {
        Almacen copia = new Almacen(aeropuerto, capacidad);
        copia.maletasAlmacenadas.addAll(this.maletasAlmacenadas);
        copia.capacidadUsada = this.capacidadUsada;
        return copia;
    }

    public boolean almacenarMaleta(Maleta maleta) {
        if (capacidadUsada + maleta.getCantidad() > capacidad) {
            return false;
        }
        if (!maletasAlmacenadas.contains(maleta)) {
            maletasAlmacenadas.add(maleta);
            capacidadUsada += maleta.getCantidad();
        }
        return true;
    }

    public boolean retirarMaleta(Maleta maleta) {
        boolean removed = maletasAlmacenadas.remove(maleta);
        if (removed) {
            capacidadUsada -= maleta.getCantidad();
        }
        return removed;
    }

    public boolean tieneEspacio() {
        return capacidadUsada < capacidad;
    }

    public boolean tieneEspacioPara(int cantidad) {
        return capacidadUsada + cantidad <= capacidad;
    }

    public double getOcupacion() {
        return (double) capacidadUsada / capacidad;
    }

    public int getEspaciosLibres() {
        return capacidad - capacidadUsada;
    }

    public String getAeropuerto() { return aeropuerto; }
    public int getCapacidad() { return capacidad; }
    public List<Maleta> getMaletasAlmacenadas() { return Collections.unmodifiableList(maletasAlmacenadas); }
    public int getCantidadMaletas() { return maletasAlmacenadas.size(); }
    public int getCapacidadUsada() { return capacidadUsada; }

    @Override
    public String toString() {
        return "Almacen{" + aeropuerto + ", " + capacidadUsada + "/" + capacidad + "}";
    }
}
