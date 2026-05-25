package com.plantilla.backend.modules.algoritmo.alns.model;

/**
 * Representa un envío (grupo de maletas) en el sistema logístico aeroportuario.
 * Cada envío tiene un origen, destino, fecha de creación, cantidad de maletas,
 * límite de SLA y prioridad derivada.
 *
 * Modelo interno del algoritmo ALNS. Para la entidad JPA ver
 * {@code com.plantilla.backend.modules.envio.entity.EnvioMaletas}.
 */
public class Maleta {
    private final String id;                  // ID del envío (ej. ORIGEN-#########)
    private final String aeropuertoOrigen;    // Código ICAO del aeropuerto origen
    private final String aeropuertoDestino;   // Código ICAO del aeropuerto destino
    private final long fechaCreacionUTC;      // Minutos absolutos UTC desde epoch
    private final int slaLimite;              // Deadline en minutos absolutos UTC
    private final int prioridad;              // 1 = alta, 2 = media, 3 = baja (derivada de cantidad)
    private final int cantidad;               // Cantidad de maletas físicas (1-999)
    private final String idCliente;           // ID del cliente

    // Vínculo opcional con el id de la entidad JPA original (para persistencia)
    private final Integer idEnvioBackend;

    public Maleta(String id, String aeropuertoOrigen, String aeropuertoDestino,
                  long fechaCreacionUTC, int slaLimite, int prioridad,
                  int cantidad, String idCliente) {
        this(id, aeropuertoOrigen, aeropuertoDestino, fechaCreacionUTC,
                slaLimite, prioridad, cantidad, idCliente, null);
    }

    public Maleta(String id, String aeropuertoOrigen, String aeropuertoDestino,
                  long fechaCreacionUTC, int slaLimite, int prioridad,
                  int cantidad, String idCliente, Integer idEnvioBackend) {
        this.id = id;
        this.aeropuertoOrigen = aeropuertoOrigen;
        this.aeropuertoDestino = aeropuertoDestino;
        this.fechaCreacionUTC = fechaCreacionUTC;
        this.slaLimite = slaLimite;
        this.prioridad = prioridad;
        this.cantidad = cantidad;
        this.idCliente = idCliente;
        this.idEnvioBackend = idEnvioBackend;
    }

    /**
     * Verifica si el SLA del envío habría expirado dado un tiempo de entrega absoluto UTC.
     */
    public boolean isSLAExpirado(long tiempoEntregaUTC) {
        return tiempoEntregaUTC > slaLimite;
    }

    /**
     * Calcula cuántos minutos de holgura quedan antes de que el SLA expire,
     * dado un tiempo de entrega absoluto UTC.
     */
    public long getHolguraSLA(long tiempoEntregaUTC) {
        return slaLimite - tiempoEntregaUTC;
    }

    /**
     * Tiempo de transporte en minutos para una ruta dada (llegada - creación).
     */
    public long getTiempoTransporte(long horaLlegadaUTC) {
        return horaLlegadaUTC - fechaCreacionUTC;
    }

    // === Getters ===
    public String getId() { return id; }
    public String getAeropuertoOrigen() { return aeropuertoOrigen; }
    public String getAeropuertoDestino() { return aeropuertoDestino; }
    public long getFechaCreacionUTC() { return fechaCreacionUTC; }
    public int getSlaLimite() { return slaLimite; }
    public int getPrioridad() { return prioridad; }
    public int getCantidad() { return cantidad; }
    public String getIdCliente() { return idCliente; }
    public Integer getIdEnvioBackend() { return idEnvioBackend; }

    @Override
    public String toString() {
        return "Maleta{" + id + ", " + aeropuertoOrigen + "→" + aeropuertoDestino +
               ", Cant=" + cantidad + ", SLA=" + slaLimite + ", P=" + prioridad + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Maleta maleta = (Maleta) o;
        return id.equals(maleta.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
