package com.plantilla.backend.modules.algoritmo.alns.model;

import java.util.*;

/**
 * Representa una solución completa del problema: asignación de cada envío a una ruta
 * dentro de la red de vuelos. Gestiona también la ocupación de vuelos y almacenes.
 * La capacidad se maneja por cantidad de maletas (no conteo de envíos).
 */
public class PlanDeRutas {
    private final Map<Maleta, Ruta> asignaciones;
    private final List<Maleta> maletasNoAsignadas;
    private final Map<String, Vuelo> vuelosMap;
    private final Map<String, Almacen> almacenesMap;
    private double costoCalculado = -1;

    public PlanDeRutas() {
        this.asignaciones = new LinkedHashMap<>();
        this.maletasNoAsignadas = new ArrayList<>();
        this.vuelosMap = new LinkedHashMap<>();
        this.almacenesMap = new LinkedHashMap<>();
    }

    public PlanDeRutas copiar() {
        PlanDeRutas copia = new PlanDeRutas();

        Map<String, Vuelo> vuelosCopia = new LinkedHashMap<>();
        for (Map.Entry<String, Vuelo> entry : this.vuelosMap.entrySet()) {
            vuelosCopia.put(entry.getKey(), entry.getValue().copiar());
        }
        copia.vuelosMap.putAll(vuelosCopia);

        for (Map.Entry<String, Almacen> entry : this.almacenesMap.entrySet()) {
            copia.almacenesMap.put(entry.getKey(), entry.getValue().copiar());
        }

        for (Map.Entry<Maleta, Ruta> entry : this.asignaciones.entrySet()) {
            copia.asignaciones.put(entry.getKey(), entry.getValue().copiar());
        }

        copia.maletasNoAsignadas.addAll(this.maletasNoAsignadas);

        return copia;
    }

    public void registrarVuelo(Vuelo vuelo) {
        vuelosMap.put(vuelo.getId(), vuelo);
    }

    public void registrarAlmacen(Almacen almacen) {
        almacenesMap.put(almacen.getAeropuerto(), almacen);
    }

    /**
     * Asigna un envío a una ruta. Retorna true si todos los vuelos tienen espacio.
     */
    public boolean asignarMaleta(Maleta maleta, Ruta ruta) {
        for (Vuelo vueloRuta : ruta.getVuelos()) {
            Vuelo vueloPlan = vuelosMap.get(vueloRuta.getId());
            if (vueloPlan != null && !vueloPlan.tieneEspacioPara(maleta.getCantidad())) {
                return false;
            }
        }

        for (Vuelo vueloRuta : ruta.getVuelos()) {
            Vuelo vueloPlan = vuelosMap.get(vueloRuta.getId());
            if (vueloPlan != null) {
                vueloPlan.asignarMaleta(maleta);
            }
        }

        asignaciones.put(maleta, ruta);
        maletasNoAsignadas.remove(maleta);
        invalidarCosto();
        return true;
    }

    public void desasignarMaleta(Maleta maleta) {
        Ruta ruta = asignaciones.remove(maleta);
        if (ruta != null) {
            for (Vuelo vueloRuta : ruta.getVuelos()) {
                Vuelo vueloPlan = vuelosMap.get(vueloRuta.getId());
                if (vueloPlan != null) {
                    vueloPlan.removerMaleta(maleta);
                }
            }
        }
        if (!maletasNoAsignadas.contains(maleta)) {
            maletasNoAsignadas.add(maleta);
        }
        invalidarCosto();
    }

    public void agregarMaletaNoAsignada(Maleta maleta) {
        if (!maletasNoAsignadas.contains(maleta) && !asignaciones.containsKey(maleta)) {
            maletasNoAsignadas.add(maleta);
        }
    }

    public boolean esFactible() {
        for (Vuelo vuelo : vuelosMap.values()) {
            if (vuelo.getCapacidadUsada() > vuelo.getCapacidad()) {
                return false;
            }
        }
        for (Almacen almacen : almacenesMap.values()) {
            if (almacen.getCapacidadUsada() > almacen.getCapacidad()) {
                return false;
            }
        }
        return true;
    }

    public int getTotalMaletasFisicasAsignadas() {
        int total = 0;
        for (Maleta m : asignaciones.keySet()) {
            total += m.getCantidad();
        }
        return total;
    }

    public int getTotalMaletasFisicas() {
        int total = getTotalMaletasFisicasAsignadas();
        for (Maleta m : maletasNoAsignadas) {
            total += m.getCantidad();
        }
        return total;
    }

    private void invalidarCosto() {
        costoCalculado = -1;
    }

    public double getCostoCalculado() {
        return costoCalculado;
    }

    public void setCostoCalculado(double costo) {
        this.costoCalculado = costo;
    }

    public Map<Maleta, Ruta> getAsignaciones() { return Collections.unmodifiableMap(asignaciones); }
    public List<Maleta> getMaletasNoAsignadas() { return Collections.unmodifiableList(maletasNoAsignadas); }
    public Map<String, Vuelo> getVuelosMap() { return Collections.unmodifiableMap(vuelosMap); }
    public Map<String, Almacen> getAlmacenesMap() { return Collections.unmodifiableMap(almacenesMap); }
    public int getTotalMaletasAsignadas() { return asignaciones.size(); }
    public int getTotalMaletas() { return asignaciones.size() + maletasNoAsignadas.size(); }

    public Vuelo getVuelo(String vueloId) {
        return vuelosMap.get(vueloId);
    }

    @Override
    public String toString() {
        return "PlanDeRutas{envios=" + asignaciones.size() +
               ", noAsignados=" + maletasNoAsignadas.size() +
               ", vuelos=" + vuelosMap.size() +
               ", costo=" + String.format("%.2f", costoCalculado) + "}";
    }
}
