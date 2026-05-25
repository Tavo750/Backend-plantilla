package com.plantilla.backend.modules.simulacion.alns;

import com.plantilla.backend.modules.algoritmo.alns.model.Aeropuerto;
import com.plantilla.backend.modules.algoritmo.alns.model.Maleta;
import com.plantilla.backend.modules.algoritmo.alns.model.Vuelo;
import com.plantilla.backend.modules.algoritmo.alns.util.FlightIndex;
import com.plantilla.backend.modules.algoritmo.alns.util.TimeUtils;
import com.plantilla.backend.modules.envio.entity.EnvioMaletas;
import com.plantilla.backend.modules.envio.repository.EnvioMaletasRepository;
import com.plantilla.backend.modules.maestro.repository.AeropuertoRepository;
import com.plantilla.backend.modules.maestro.repository.VueloRepository;
import com.plantilla.backend.shared.enums.Continente;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapta las entidades JPA del backend (Aeropuerto, Vuelo, EnvioMaletas)
 * al modelo interno del algoritmo ALNS (com.plantilla.backend.modules.algoritmo.alns.model.*).
 *
 * Encapsula toda la conversión de fechas {@link LocalDateTime} a minutos UTC
 * absolutos desde el epoch del ALNS (2026-01-01 00:00 UTC).
 */
@Component
@RequiredArgsConstructor
public class BackendDataAdapter {

    private static final Logger log = LoggerFactory.getLogger(BackendDataAdapter.class);

    private final AeropuertoRepository aeropuertoRepository;
    private final VueloRepository vueloRepository;
    private final EnvioMaletasRepository envioMaletasRepository;

    /**
     * Carga todos los aeropuertos activos y los convierte al modelo interno del ALNS.
     *
     * @return Mapa código OACI → Aeropuerto interno
     */
    public Map<String, Aeropuerto> cargarAeropuertos() {
        List<com.plantilla.backend.modules.maestro.entity.Aeropuerto> aeropuertos =
                aeropuertoRepository.findByActivoTrue();

        Map<String, Aeropuerto> mapa = new LinkedHashMap<>();
        for (com.plantilla.backend.modules.maestro.entity.Aeropuerto a : aeropuertos) {
            mapa.put(a.getCodigoOaci(), convertirAeropuerto(a));
        }
        log.debug("Aeropuertos cargados desde BD: {}", mapa.size());
        return mapa;
    }

    /**
     * Carga los vuelos cuyo horario de salida está dentro del rango especificado
     * y los convierte al modelo interno del ALNS.
     *
     * El rango se interpreta en UTC (ya que la BD almacena hora_salida en UTC,
     * vía {@code ImportacionVuelosService}).
     */
    public List<Vuelo> cargarVuelos(LocalDateTime desdeUtc, LocalDateTime hastaUtc) {
        List<com.plantilla.backend.modules.maestro.entity.Vuelo> vuelos =
                vueloRepository.findByHoraSalidaBetween(desdeUtc, hastaUtc);

        List<Vuelo> resultado = new java.util.ArrayList<>(vuelos.size());
        for (com.plantilla.backend.modules.maestro.entity.Vuelo v : vuelos) {
            resultado.add(convertirVuelo(v));
        }
        log.debug("Vuelos cargados desde BD entre {} y {}: {}", desdeUtc, hastaUtc, resultado.size());
        return resultado;
    }

    /**
     * Construye un {@link FlightIndex} a partir de los vuelos del rango.
     */
    public FlightIndex construirFlightIndex(List<Vuelo> vuelos) {
        return new FlightIndex(vuelos);
    }

    /**
     * Carga los envíos cuya fecha_registro cae dentro del rango y los convierte
     * al modelo interno del ALNS.
     *
     * @param aeropuertos Mapa código OACI → Aeropuerto interno (para calcular SLA)
     */
    public List<Maleta> cargarEnvios(LocalDateTime desdeUtc, LocalDateTime hastaUtc,
                                     Map<String, Aeropuerto> aeropuertos) {
        List<EnvioMaletas> envios = envioMaletasRepository.findByFechaRegistroBetween(desdeUtc, hastaUtc);

        List<Maleta> resultado = new java.util.ArrayList<>(envios.size());
        for (EnvioMaletas e : envios) {
            Maleta m = convertirEnvio(e, aeropuertos);
            if (m != null) resultado.add(m);
        }
        log.debug("Envíos cargados desde BD entre {} y {}: {}", desdeUtc, hastaUtc, resultado.size());
        return resultado;
    }

    // ──────────────────────────────────────────────────────────────────
    // Conversiones unitarias
    // ──────────────────────────────────────────────────────────────────

    /**
     * Convierte una entidad JPA Aeropuerto al modelo interno del ALNS.
     */
    public Aeropuerto convertirAeropuerto(com.plantilla.backend.modules.maestro.entity.Aeropuerto a) {
        Aeropuerto.Continente continenteAlns = mapearContinente(a.getContinente());
        return new Aeropuerto(
                a.getIdAeropuerto() != null ? a.getIdAeropuerto() : 0,
                a.getCodigoOaci(),
                a.getCiudad(),
                a.getPais(),
                a.getCodigo(),
                continenteAlns,
                a.getGmt() != null ? a.getGmt() : 0,
                a.getCapacidad() != null ? a.getCapacidad() : 0
        );
    }

    /**
     * Convierte una entidad JPA Vuelo al modelo interno del ALNS.
     * Asume que {@code horaSalida} y {@code horaLlegada} están almacenadas en UTC.
     */
    public Vuelo convertirVuelo(com.plantilla.backend.modules.maestro.entity.Vuelo v) {
        long salidaMin = toMinutosUtcDesdeEpoch(v.getHoraSalida());
        long llegadaMin = toMinutosUtcDesdeEpoch(v.getHoraLlegada());
        return new Vuelo(
                v.getCodigoVuelo(),
                v.getAeropuertoOrigen().getCodigoOaci(),
                v.getAeropuertoDestino().getCodigoOaci(),
                v.getCapacidadMaxima() != null ? v.getCapacidadMaxima() : 0,
                salidaMin,
                llegadaMin,
                v.getIdVuelo()
        );
    }

    /**
     * Convierte un envío JPA al modelo interno {@link Maleta} del ALNS.
     * Devuelve {@code null} si el destino no existe en el mapa de aeropuertos cargados.
     */
    public Maleta convertirEnvio(EnvioMaletas e, Map<String, Aeropuerto> aeropuertos) {
        String origen = e.getAeropuertoOrigen().getCodigoOaci();
        String destino = e.getAeropuertoDestino().getCodigoOaci();

        Aeropuerto aeroOrigen = aeropuertos.get(origen);
        Aeropuerto aeroDestino = aeropuertos.get(destino);
        if (aeroOrigen == null || aeroDestino == null) {
            log.warn("Envío {} omitido: aeropuerto origen/destino no en mapa ({} → {})",
                    e.getIdEnvio(), origen, destino);
            return null;
        }

        long fechaCreacionUtcMin = toMinutosUtcDesdeEpoch(e.getFechaRegistro());

        // SLA en minutos (mismo continente 1440, distinto 2880)
        int slaDuracionMin = aeroOrigen.calcularSLA(aeroDestino);
        int slaDeadlineMin = (int) (fechaCreacionUtcMin + slaDuracionMin);

        int cantidad = e.getCantidad() != null ? e.getCantidad() : 1;

        // Derivar prioridad de la cantidad (igual que el código original ALNS)
        int prioridad;
        if (cantidad >= 5) {
            prioridad = 1;
        } else if (cantidad >= 3) {
            prioridad = 2;
        } else {
            prioridad = 3;
        }

        String idCompuesto = "E-" + e.getIdEnvio();
        String idCliente = e.getAerolinea() != null && e.getAerolinea().getIdAerolinea() != null
                ? "A" + e.getAerolinea().getIdAerolinea()
                : "0000000";

        return new Maleta(
                idCompuesto,
                origen,
                destino,
                fechaCreacionUtcMin,
                slaDeadlineMin,
                prioridad,
                cantidad,
                idCliente,
                e.getIdEnvio()
        );
    }

    // ──────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────

    /**
     * Convierte un {@link LocalDateTime} (asumido UTC) a minutos absolutos
     * desde el epoch del ALNS (2026-01-01 00:00 UTC).
     */
    public long toMinutosUtcDesdeEpoch(LocalDateTime utc) {
        if (utc == null) return 0L;
        LocalDateTime epoch = LocalDate.of(
                TimeUtils.EPOCH_YEAR, TimeUtils.EPOCH_MONTH, TimeUtils.EPOCH_DAY).atStartOfDay();
        long segundos = utc.toEpochSecond(ZoneOffset.UTC) - epoch.toEpochSecond(ZoneOffset.UTC);
        return segundos / 60L;
    }

    /**
     * Convierte minutos absolutos UTC ALNS → {@link LocalDateTime} (UTC).
     */
    public LocalDateTime toLocalDateTimeUtc(long minutosUtc) {
        LocalDateTime epoch = LocalDate.of(
                TimeUtils.EPOCH_YEAR, TimeUtils.EPOCH_MONTH, TimeUtils.EPOCH_DAY).atStartOfDay();
        return epoch.plusMinutes(minutosUtc);
    }

    /**
     * Mapea el enum {@link Continente} del backend al enum del modelo ALNS.
     * AMERICA → AMERICA_DEL_SUR (asunción del proyecto: los aeropuertos americanos son sudamericanos).
     */
    public Aeropuerto.Continente mapearContinente(Continente continente) {
        if (continente == null) return Aeropuerto.Continente.AMERICA_DEL_SUR;
        return switch (continente) {
            case AMERICA -> Aeropuerto.Continente.AMERICA_DEL_SUR;
            case EUROPA -> Aeropuerto.Continente.EUROPA;
            case ASIA -> Aeropuerto.Continente.ASIA;
        };
    }
}
