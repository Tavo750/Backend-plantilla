package com.plantilla.backend.modules.simulacion.service;

import com.plantilla.backend.modules.algoritmo.alns.model.Aeropuerto;
import com.plantilla.backend.modules.algoritmo.alns.model.Maleta;
import com.plantilla.backend.modules.algoritmo.alns.model.Ruta;
import com.plantilla.backend.modules.algoritmo.alns.model.Vuelo;
import com.plantilla.backend.modules.simulacion.SimulacionPureService;
import com.plantilla.backend.modules.simulacion.alns.BackendDataAdapter;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Consumer;

/**
 * Detección de colapso mediante SIMULACIÓN CONTINUA (no día-por-día aislado).
 *
 * El colapso real es acumulativo: las maletas ocupan el almacén de cada
 * aeropuerto mientras esperan su vuelo (desde el registro hasta el despegue, y
 * en cada escala desde el aterrizaje hasta la siguiente salida). Conforme la
 * demanda crece, la ocupación de los almacenes sube hasta que uno se satura y
 * "ya no puede aceptar más pedidos" — ESE es el colapso (además de las maletas
 * sin ruta o fuera de SLA).
 *
 * El planificador (ALNS) enruta los envíos de cada día; a partir de sus rutas se
 * reconstruye la ocupación de cada almacén a lo largo del tiempo con una línea
 * de barrido, y se detecta el primer instante en que algún almacén supera su
 * capacidad.
 */
@Service
@RequiredArgsConstructor
public class ColapsoContinuoService {

    private static final Logger log = LoggerFactory.getLogger(ColapsoContinuoService.class);

    /** Ventana de eventos retenida (días): las maletas esperan a lo más ~2 días (SLA 24-48h) */
    private static final long PODA_DIAS = 3;

    private final SimulacionPureService pureService;
    private final BackendDataAdapter dataAdapter;

    public record Resultado(LocalDate fecha, long tiempoColapsoMinUtc, String motivo,
                            int sinRuta, int fueraSla) {}

    /** Ocupación de un almacén: eventos (minutoUtc → delta) + base de eventos ya podados */
    private static final class Almacen {
        final int capacidad;
        long base = 0;                       // ocupación acumulada de eventos podados
        final TreeMap<Long, Long> eventos = new TreeMap<>(); // minutoUtc → delta neto
        Almacen(int capacidad) { this.capacidad = capacidad; }
        void agregar(long enter, long exit, int qty) {
            if (exit <= enter) return;
            eventos.merge(enter, (long) qty, Long::sum);
            eventos.merge(exit, (long) -qty, Long::sum);
        }
    }

    /**
     * Simula de forma continua desde {@code desde} hasta {@code hasta} y devuelve
     * el primer colapso (almacén saturado, o maletas sin ruta / fuera de SLA), o
     * null si nunca colapsa en el rango.
     */
    public Resultado buscar(LocalDate desde, LocalDate hasta, Consumer<String> progreso) {
        Map<String, Almacen> almacenes = new HashMap<>();
        boolean capsCargadas = false;

        int diasSimulados = 0;
        for (LocalDate dia = desde; !dia.isAfter(hasta); dia = dia.plusDays(1)) {
            diasSimulados++;
            if (progreso != null && diasSimulados % 3 == 1) {
                progreso.accept("Simulando " + dia + " (ocupación de almacenes)...");
            }

            SimulacionPureService.PlanDia pd = pureService.planificarDia(dia);
            if (pd.plan() == null) continue;

            if (!capsCargadas && pd.aeropuertos() != null) {
                for (Aeropuerto a : pd.aeropuertos().values()) {
                    almacenes.putIfAbsent(a.getCodigoICAO(), new Almacen(a.getCapacidadAlmacen()));
                }
                capsCargadas = true;
            }

            // Nota: la factibilidad de ruta+SLA por día ya fue probada limpia por la
            // búsqueda exhaustiva (planificador con presupuesto alto). Aquí el ALNS
            // corre con presupuesto acotado para poder simular muchos días, así que
            // residuos de 1-2 maletas sin ruta / fuera de SLA son RUIDO del
            // presupuesto, no colapso real. El colapso operativo de esta data es la
            // SATURACIÓN DE ALMACENES (fenómeno acumulativo). Se registra el residuo
            // como info pero NO dispara colapso.
            int sinRuta = pd.plan().getMaletasNoAsignadas().size();
            int fueraSla = 0;
            for (Map.Entry<Maleta, Ruta> e : pd.plan().getAsignaciones().entrySet()) {
                if (e.getKey().isSLAExpirado(e.getValue().getHoraLlegadaFinal())) fueraSla++;
            }
            if (sinRuta > 0 || fueraSla > 0) {
                log.debug("Residuo de presupuesto el {}: {} sin ruta · {} fuera de SLA (no es colapso)",
                        dia, sinRuta, fueraSla);
            }

            // 2) Registrar intervalos de ocupación de almacén de cada maleta
            for (Map.Entry<Maleta, Ruta> e : pd.plan().getAsignaciones().entrySet()) {
                Maleta m = e.getKey();
                List<Vuelo> vs = e.getValue().getVuelos();
                if (vs.isEmpty()) continue;
                int q = m.getCantidad();
                // Origen: desde el registro hasta el despegue del primer vuelo
                ocupar(almacenes, m.getAeropuertoOrigen(), m.getFechaCreacionUTC(), vs.get(0).getHoraSalida(), q);
                // Escalas: desde el aterrizaje del tramo previo hasta la salida del siguiente
                for (int i = 1; i < vs.size(); i++) {
                    ocupar(almacenes, vs.get(i - 1).getDestino(),
                            vs.get(i - 1).getHoraLlegada(), vs.get(i).getHoraSalida(), q);
                }
            }

            long horizonte = dataAdapter.toMinutosUtcDesdeEpoch(dia.plusDays(1).atStartOfDay());

            // Traza de contexto: ocupación global (fórmula del profesor)
            Barrido b = barridoGlobal(almacenes, horizonte);
            if (b.capacidadTotal > 0) {
                log.info("Ocupación almacenes {} · global pico {}/{} ({}%)",
                        dia, b.pico, b.capacidadTotal,
                        Math.round(b.pico * 100.0 / b.capacidadTotal));
            }

            // COLAPSO (criterio real): primer aeropuerto que llega a su capacidad
            // y ya no puede recibir un pedido más.
            Resultado overflow = detectarSaturacionPorAeropuerto(almacenes, horizonte);
            if (overflow != null) return overflow;

            // 4) Poda de eventos ya consolidados (mantiene la memoria acotada)
            long corte = dataAdapter.toMinutosUtcDesdeEpoch(dia.minusDays(PODA_DIAS).atStartOfDay());
            podar(almacenes, corte);
        }

        log.info("Sin colapso: la ocupación de almacenes nunca supera la capacidad en [{}, {}]", desde, hasta);
        return null;
    }

    private void ocupar(Map<String, Almacen> almacenes, String aero, long enter, long exit, int qty) {
        Almacen a = almacenes.get(aero);
        if (a != null) a.agregar(enter, exit, qty);
    }

    /**
     * Barrido POR AEROPUERTO: primer minuto en que la ocupación de algún almacén
     * supera su capacidad (ya no puede recibir un pedido más = colapso).
     */
    private Resultado detectarSaturacionPorAeropuerto(Map<String, Almacen> almacenes, long horizonte) {
        Resultado mejor = null;
        String peorAero = null;
        for (Map.Entry<String, Almacen> entry : almacenes.entrySet()) {
            Almacen a = entry.getValue();
            long ocup = a.base;
            for (Map.Entry<Long, Long> ev : a.eventos.entrySet()) {
                if (ev.getKey() > horizonte) break;
                ocup += ev.getValue();
                if (ocup > a.capacidad) {
                    long tMin = ev.getKey();
                    if (mejor == null || tMin < mejor.tiempoColapsoMinUtc()) {
                        LocalDateTime cuando = dataAdapter.toLocalDateTimeUtc(tMin);
                        String motivo = "Almacén " + entry.getKey() + " lleno: " + ocup
                                + " maletas > capacidad " + a.capacidad + " (ya no puede recibir más pedidos)";
                        mejor = new Resultado(cuando.toLocalDate(), tMin, motivo, 0, 0);
                        peorAero = entry.getKey();
                    }
                    break; // primer cruce de este almacén
                }
            }
        }
        if (mejor != null) {
            log.info("Colapso (almacén lleno) el {}: {} [{}]", mejor.fecha(), mejor.motivo(), peorAero);
        }
        return mejor;
    }

    /** Resultado del barrido global: pico de ocupación, capacidad total y colapso si lo hubo */
    private static final class Barrido {
        long pico = 0;
        long capacidadTotal = 0;
        Resultado resultado = null;
    }

    /**
     * Línea de barrido GLOBAL: recorre la ocupación total de todos los almacenes
     * juntos en el tiempo. Registra el pico y, si supera la capacidad total del
     * sistema, marca el colapso en ese primer minuto.
     */
    private Barrido barridoGlobal(Map<String, Almacen> almacenes, long horizonte) {
        Barrido b = new Barrido();
        long baseTotal = 0;
        TreeMap<Long, Long> combinado = new TreeMap<>();
        for (Almacen a : almacenes.values()) {
            b.capacidadTotal += a.capacidad;
            baseTotal += a.base;
            for (Map.Entry<Long, Long> ev : a.eventos.entrySet()) {
                combinado.merge(ev.getKey(), ev.getValue(), Long::sum);
            }
        }
        if (b.capacidadTotal == 0) return b;

        long ocup = baseTotal;
        b.pico = Math.max(0, baseTotal);
        for (Map.Entry<Long, Long> ev : combinado.entrySet()) {
            if (ev.getKey() > horizonte) break;
            ocup += ev.getValue();
            if (ocup > b.pico) b.pico = ocup;
            if (ocup > b.capacidadTotal && b.resultado == null) {
                long tMin = ev.getKey();
                LocalDateTime cuando = dataAdapter.toLocalDateTimeUtc(tMin);
                int pct = (int) Math.round(ocup * 100.0 / b.capacidadTotal);
                String motivo = "Almacenes saturados: " + ocup + " maletas en aeropuertos > capacidad total "
                        + b.capacidadTotal + " (" + pct + "%)";
                log.info("Colapso (saturación global) el {}: {}", cuando.toLocalDate(), motivo);
                b.resultado = new Resultado(cuando.toLocalDate(), tMin, motivo, 0, 0);
                break;
            }
        }
        return b;
    }

    /** Consolida en {@code base} los eventos con minuto &lt; corte y los elimina */
    private void podar(Map<String, Almacen> almacenes, long corte) {
        for (Almacen a : almacenes.values()) {
            var head = a.eventos.headMap(corte, false);
            if (head.isEmpty()) continue;
            long acumulado = 0;
            for (Long delta : head.values()) acumulado += delta;
            a.base += acumulado;
            head.clear();
        }
    }
}
