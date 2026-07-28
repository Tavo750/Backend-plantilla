package com.plantilla.backend.modules.simulacion;

import java.time.LocalDateTime;

/**
 * Estado persistente en memoria de la primera condicion de colapso detectada.
 * Los campos no aplicables al tipo de colapso permanecen en null.
 */
public record EstadoColapso(
        boolean detectado,
        String tipo,
        String mensaje,
        String codigoAeropuerto,
        Integer ocupacionActual,
        Integer capacidadMaxima,
        String idEnvio,
        LocalDateTime fechaLimiteEntrega,
        LocalDateTime fechaColapso) {

    public static final String CAPACIDAD_AEROPUERTO = "CAPACIDAD_AEROPUERTO";
    public static final String INCUMPLIMIENTO_SLA = "INCUMPLIMIENTO_SLA";

    public static EstadoColapso capacidad(
            String aeropuerto, int ocupacion, int capacidad, LocalDateTime fecha) {
        return new EstadoColapso(
                true,
                CAPACIDAD_AEROPUERTO,
                "El aeropuerto " + aeropuerto + " alcanzo su capacidad maxima de "
                        + capacidad + " maletas.",
                aeropuerto,
                ocupacion,
                capacidad,
                null,
                null,
                fecha);
    }

    public static EstadoColapso sla(
            int idEnvio, LocalDateTime fechaLimite, LocalDateTime fecha) {
        return new EstadoColapso(
                true,
                INCUMPLIMIENTO_SLA,
                "El envio " + idEnvio + " incumplio su fecha limite de entrega.",
                null,
                null,
                null,
                String.valueOf(idEnvio),
                fechaLimite,
                fecha);
    }
}
