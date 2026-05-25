package com.plantilla.backend.modules.algoritmo.alns.model;

/**
 * Representa un aeropuerto en la red logística B2B (modelo interno del algoritmo ALNS).
 * Contiene la información de ubicación, zona horaria y capacidad de almacén.
 *
 * IMPORTANTE: Esta clase es el modelo interno del algoritmo y NO la entidad JPA.
 * La entidad JPA es {@code com.plantilla.backend.modules.maestro.entity.Aeropuerto}.
 */
public class Aeropuerto {

    /**
     * Clasificación continental de los aeropuertos.
     * Determina el SLA: mismo continente = 24h, distinto = 48h.
     */
    public enum Continente {
        AMERICA_DEL_SUR("América del Sur"),
        EUROPA("Europa"),
        ASIA("Asia");

        private final String nombre;

        Continente(String nombre) {
            this.nombre = nombre;
        }

        public String getNombre() {
            return nombre;
        }
    }

    private final int numero;             // Número secuencial (01-30)
    private final String codigoICAO;      // Código ICAO de 4 letras (e.g., "SKBO")
    private final String ciudad;          // Nombre de la ciudad
    private final String pais;            // Nombre del país
    private final String alias;           // Alias corto (e.g., "bogo")
    private final Continente continente;  // Clasificación continental
    private final int gmtOffset;          // Offset GMT en horas (e.g., -5, +2)
    private final int capacidadAlmacen;   // Capacidad del almacén de tránsito

    public Aeropuerto(int numero, String codigoICAO, String ciudad, String pais,
                      String alias, Continente continente, int gmtOffset, int capacidadAlmacen) {
        this.numero = numero;
        this.codigoICAO = codigoICAO;
        this.ciudad = ciudad;
        this.pais = pais;
        this.alias = alias;
        this.continente = continente;
        this.gmtOffset = gmtOffset;
        this.capacidadAlmacen = capacidadAlmacen;
    }

    /**
     * Calcula el SLA en minutos entre este aeropuerto (origen) y otro (destino).
     * Mismo continente: 1440 min (24h), distinto continente: 2880 min (48h).
     */
    public int calcularSLA(Aeropuerto destino) {
        if (this.continente == destino.continente) {
            return 1440; // 24 horas
        } else {
            return 2880; // 48 horas
        }
    }

    // === Getters ===
    public int getNumero() { return numero; }
    public String getCodigoICAO() { return codigoICAO; }
    public String getCiudad() { return ciudad; }
    public String getPais() { return pais; }
    public String getAlias() { return alias; }
    public Continente getContinente() { return continente; }
    public int getGmtOffset() { return gmtOffset; }
    public int getCapacidadAlmacen() { return capacidadAlmacen; }

    @Override
    public String toString() {
        return "Aeropuerto{" + codigoICAO + ", " + ciudad + ", " + continente.getNombre() +
               ", GMT" + (gmtOffset >= 0 ? "+" : "") + gmtOffset + ", Cap=" + capacidadAlmacen + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Aeropuerto that = (Aeropuerto) o;
        return codigoICAO.equals(that.codigoICAO);
    }

    @Override
    public int hashCode() {
        return codigoICAO.hashCode();
    }
}
