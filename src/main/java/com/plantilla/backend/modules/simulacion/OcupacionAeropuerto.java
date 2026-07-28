package com.plantilla.backend.modules.simulacion;

/**
 * Snapshot logistico de un aeropuerto para un instante simulado.
 */
public record OcupacionAeropuerto(
        int ocupacionActual,
        int capacidadMaxima,
        double porcentaje,
        int entradas,
        int salidas) {
}
