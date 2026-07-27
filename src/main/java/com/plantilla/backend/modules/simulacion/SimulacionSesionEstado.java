package com.plantilla.backend.modules.simulacion;

import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.WebSocketSession;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;

@Getter
@Setter
public class SimulacionSesionEstado {

    private static final Logger log = LoggerFactory.getLogger(SimulacionSesionEstado.class);
    private static final String CODIGO_DIAGNOSTICO = "EBCI-OAKB-20280808-0023-2047";

    public static final int MAX_CICLOS = 12; // 12 x 5 min real = 60 min total

    private final String sessionId;
    private final WebSocketSession wsSession;
    private final LocalDateTime fechaInicio;
    private final int K;
    private final int maxMaletasSC;

    private LocalDateTime punteroSim;
    private int ciclosEjecutados = 0;
    private ScheduledFuture<?> tareaScheduled;
    private boolean modoColapso = false;
    private volatile boolean colapsada = false;
    private volatile EstadoColapso estadoColapso;

    /** Capacidades fisicas reales recibidas en el snapshot inicial. */
    private final Map<String, Integer> capacidadesAeropuerto = new ConcurrentHashMap<>();

    /** Ruta vigente de cada envio, usada para evaluar ocupacion y SLA en cada ciclo. */
    private final Map<Integer, SeguimientoEnvio> seguimientosEnvio = new ConcurrentHashMap<>();
    /** Toda demanda registrada, incluso cuando ALNS aun no construyo una ruta. */
    private final Map<Integer, DemandaEnvio> demandasEnvio = new ConcurrentHashMap<>();

    /** Hora de inicio ("HH:mm") elegida por el usuario — solo para mostrar en la lista de sims activas */
    private String horaInicio = "00:00";

    /** Id de simulación compartida. Si != null, la simulación es pública y otros
     *  dispositivos pueden unirse (JOIN) para ver exactamente lo mismo. */
    private String simId;

    /** true cuando la simulación terminó (12 ciclos o colapso): se retiene un rato para joiners tardíos */
    private volatile boolean finalizada = false;

    /** Búfer de TODOS los mensajes emitidos (INIT + UPDATEs [+ FIN]) para reconstruir
     *  el estado a un dispositivo que se une en curso. */
    private final List<String> mensajesBuffer = Collections.synchronizedList(new ArrayList<>());

    /** Todas las sesiones WebSocket que están viendo esta simulación (líder + los que se unieron). */
    private final Set<WebSocketSession> suscriptores = ConcurrentHashMap.newKeySet();

    /** Instante real (wall-clock) en que el cliente empezó a reproducir: ancla del reloj de pantalla */
    private long inicioRealMs = System.currentTimeMillis();

    /** IDs de envíos no asignados en ventanas anteriores que se reintentan (arrastre) */
    private java.util.List<Integer> arrastreIds = new java.util.ArrayList<>();

    private final Set<String> ocurrenciasCanceladas = ConcurrentHashMap.newKeySet();
    private final Map<String, Set<Integer>> enviosPorOcurrencia = new ConcurrentHashMap<>();
    private final Map<Integer, Set<String>> ocurrenciasPorEnvio = new ConcurrentHashMap<>();
    private final Set<String> ocurrenciasInit = ConcurrentHashMap.newKeySet();
    private final Set<String> ocurrenciasUpdate = ConcurrentHashMap.newKeySet();

    public SimulacionSesionEstado(String sessionId, WebSocketSession wsSession,
                                   LocalDateTime fechaInicio, int K, int maxMaletasSC) {
        this.sessionId = sessionId;
        this.wsSession = wsSession;
        this.fechaInicio = fechaInicio;
        this.K = K;
        this.maxMaletasSC = maxMaletasSC;
        this.punteroSim = fechaInicio;
    }

    public int incrementarCiclo() {
        return ++ciclosEjecutados;
    }

    public boolean estaActiva() {
        // Compartida: vive mientras no haya terminado (independiente de que el líder
        // siga conectado — otros dispositivos pueden estar viéndola).
        if (colapsada) return false;
        if (simId != null) return !finalizada && ciclosEjecutados < MAX_CICLOS;
        return wsSession.isOpen() && !finalizada && ciclosEjecutados < MAX_CICLOS;
    }

    public void registrarCapacidades(List<Map<String, Object>> aeropuertos) {
        if (aeropuertos == null) return;
        for (Map<String, Object> aeropuerto : aeropuertos) {
            Object codigo = aeropuerto.get("codigoOaci");
            Object capacidad = aeropuerto.get("capacidad");
            if (codigo instanceof String c && capacidad instanceof Number n && n.intValue() > 0) {
                capacidadesAeropuerto.put(c, n.intValue());
            }
        }
    }

    public void registrarDemanda(List<Map<String, Object>> envios) {
        if (envios == null) return;
        for (Map<String, Object> envio : envios) {
            Object id = envio.get("idEnvio");
            Object origen = envio.get("origen");
            Object cantidad = envio.get("cantidad");
            Object registro = envio.get("fechaRegistroMs");
            if (!(id instanceof Number) || !(origen instanceof String)
                    || !(cantidad instanceof Number) || !(registro instanceof Number)) continue;
            int idEnvio = ((Number) id).intValue();
            Long limite = envio.get("fechaLimiteMs") instanceof Number n ? n.longValue() : null;
            demandasEnvio.computeIfAbsent(idEnvio, ignored -> new DemandaEnvio(
                    idEnvio, (String) origen, ((Number) cantidad).intValue(),
                    ((Number) registro).longValue(), limite));
        }
    }

    public static String claveOcurrencia(String codigoVuelo, long horaSalidaMs) {
        return codigoVuelo + "|" + horaSalidaMs;
    }

    public void registrarResultados(List<Map<String, Object>> vuelos) {
        if (vuelos == null) return;

        Map<Integer, Set<String>> nuevasOcurrenciasPorEnvio = new java.util.HashMap<>();
        Map<Integer, SeguimientoEnvio> nuevosSeguimientos = new java.util.HashMap<>();
        for (Map<String, Object> vuelo : vuelos) {
            Object codigo = vuelo.get("codigoVuelo");
            Object salida = vuelo.get("horaSalidaMs");
            if (!(codigo instanceof String) || !(salida instanceof Number)) continue;

            String clave = claveOcurrencia((String) codigo, ((Number) salida).longValue());
            enviosPorOcurrencia.computeIfAbsent(clave, ignored -> ConcurrentHashMap.newKeySet());
            Object enviosObj = vuelo.get("envios");
            if (!(enviosObj instanceof List<?> envios)) continue;
            for (Object envioObj : envios) {
                if (!(envioObj instanceof Map<?, ?> envio)) continue;
                Object idObj = envio.get("idEnvio");
                if (idObj instanceof Number id) {
                    nuevasOcurrenciasPorEnvio
                            .computeIfAbsent(id.intValue(), ignored -> new java.util.HashSet<>())
                            .add(clave);
                    registrarSeguimiento(nuevosSeguimientos, vuelo, envio, id.intValue());
                }
            }
        }

        for (Integer idEnvio : nuevasOcurrenciasPorEnvio.keySet()) {
            Set<String> anteriores = ocurrenciasPorEnvio.remove(idEnvio);
            if (anteriores != null) {
                anteriores.forEach(clave -> {
                    Set<Integer> ids = enviosPorOcurrencia.get(clave);
                    if (ids != null) ids.remove(idEnvio);
                });
            }
        }
        nuevasOcurrenciasPorEnvio.forEach((idEnvio, claves) -> {
            Set<String> rutaActual = ConcurrentHashMap.newKeySet();
            rutaActual.addAll(claves);
            ocurrenciasPorEnvio.put(idEnvio, rutaActual);
            claves.forEach(clave -> enviosPorOcurrencia
                    .computeIfAbsent(clave, ignored -> ConcurrentHashMap.newKeySet())
                    .add(idEnvio));
        });
        nuevosSeguimientos.forEach((id, seguimiento) -> {
            seguimiento.tramos.sort(Comparator.comparingLong(TramoSeguimiento::salidaMs));
            seguimientosEnvio.put(id, seguimiento);
        });
    }

    private void registrarSeguimiento(
            Map<Integer, SeguimientoEnvio> nuevos, Map<String, Object> vuelo,
            Map<?, ?> envio, int idEnvio) {
        Object origen = vuelo.get("origen");
        Object destino = vuelo.get("destino");
        Object salida = vuelo.get("horaSalidaMs");
        Object llegada = vuelo.get("horaLlegadaMs");
        if (!(origen instanceof String) || !(destino instanceof String)
                || !(salida instanceof Number) || !(llegada instanceof Number)) return;

        int cantidad = envio.get("cantidad") instanceof Number n ? n.intValue() : 1;
        long registroMs = envio.get("fechaRegistroMs") instanceof Number n
                ? n.longValue() : ((Number) salida).longValue();
        Long limiteMs = envio.get("fechaLimiteMs") instanceof Number n ? n.longValue() : null;
        boolean cumpleSla = !(envio.get("cumpleSla") instanceof Boolean b) || b;

        SeguimientoEnvio seguimiento = nuevos.computeIfAbsent(idEnvio,
                ignored -> new SeguimientoEnvio(
                        idEnvio, cantidad, registroMs, limiteMs, cumpleSla, new ArrayList<>()));
        seguimiento.cumpleSla = seguimiento.cumpleSla && cumpleSla;
        seguimiento.tramos.add(new TramoSeguimiento(
                claveOcurrencia((String) vuelo.get("codigoVuelo"), ((Number) salida).longValue()),
                (String) origen, (String) destino,
                ((Number) salida).longValue(), ((Number) llegada).longValue()));
    }

    /**
     * Evalua SLA y capacidad usando el snapshot de ocupacion del mismo instante.
     */
    public EstadoColapso detectarColapso(LocalDateTime tiempoSimulado) {
        if (colapsada) return estadoColapso;
        return detectarColapso(
                tiempoSimulado, calcularOcupacionesAeropuertos(tiempoSimulado));
    }

    public EstadoColapso detectarColapso(
            LocalDateTime tiempoSimulado,
            Map<String, OcupacionAeropuerto> ocupaciones) {
        if (colapsada) return estadoColapso;
        long ahoraMs = tiempoSimulado.toInstant(ZoneOffset.UTC).toEpochMilli();

        for (SeguimientoEnvio envio : seguimientosEnvio.values()) {
            if (envio.fechaLimiteMs != null && ahoraMs > envio.fechaLimiteMs
                    && !envio.entregadoEn(ahoraMs)) {
                return EstadoColapso.sla(envio.idEnvio,
                        LocalDateTime.ofInstant(
                                Instant.ofEpochMilli(envio.fechaLimiteMs), ZoneOffset.UTC),
                        tiempoSimulado);
            }
        }
        for (DemandaEnvio envio : demandasEnvio.values()) {
            if (!seguimientosEnvio.containsKey(envio.idEnvio)
                    && envio.fechaLimiteMs != null && ahoraMs > envio.fechaLimiteMs) {
                return EstadoColapso.sla(envio.idEnvio,
                        LocalDateTime.ofInstant(
                                Instant.ofEpochMilli(envio.fechaLimiteMs), ZoneOffset.UTC),
                        tiempoSimulado);
            }
        }

        return detectarCapacidad(ocupaciones, tiempoSimulado);
    }

    static EstadoColapso detectarCapacidad(
            Map<String, Integer> ocupaciones, Map<String, Integer> capacidades,
            LocalDateTime tiempoSimulado) {
        for (Map.Entry<String, Integer> entry : ocupaciones.entrySet()) {
            Integer capacidad = capacidades.get(entry.getKey());
            if (capacidad != null && capacidad > 0 && entry.getValue() >= capacidad) {
                return EstadoColapso.capacidad(
                        entry.getKey(), entry.getValue(), capacidad, tiempoSimulado);
            }
        }
        return null;
    }

    static EstadoColapso detectarCapacidad(
            Map<String, OcupacionAeropuerto> ocupaciones,
            LocalDateTime tiempoSimulado) {
        for (Map.Entry<String, OcupacionAeropuerto> entry : ocupaciones.entrySet()) {
            OcupacionAeropuerto ocupacion = entry.getValue();
            if (ocupacion.capacidadMaxima() > 0
                    && ocupacion.ocupacionActual() >= ocupacion.capacidadMaxima()) {
                return EstadoColapso.capacidad(
                        entry.getKey(), ocupacion.ocupacionActual(),
                        ocupacion.capacidadMaxima(), tiempoSimulado);
            }
        }
        return null;
    }

    /**
     * Fuente unica de verdad para la ocupacion mostrada y la deteccion de colapso.
     */
    public Map<String, OcupacionAeropuerto> calcularOcupacionesAeropuertos(
            LocalDateTime tiempoSimulado) {
        long ahoraMs = tiempoSimulado.toInstant(ZoneOffset.UTC).toEpochMilli();
        Map<String, FlujoAeropuerto> flujos = new java.util.TreeMap<>();
        capacidadesAeropuerto.keySet().forEach(
                codigo -> flujos.put(codigo, new FlujoAeropuerto()));
        Set<Integer> enviosContabilizados = new java.util.HashSet<>();

        for (SeguimientoEnvio envio : seguimientosEnvio.values()) {
            enviosContabilizados.add(envio.idEnvio);
            if (envio.tramos.isEmpty()) continue;
            TramoSeguimiento primero = envio.tramos.get(0);
            registrarFlujo(flujos, primero.origen, envio.cantidad,
                    envio.fechaRegistroMs, primero.salidaMs, ahoraMs);
            for (int i = 1; i < envio.tramos.size(); i++) {
                TramoSeguimiento anterior = envio.tramos.get(i - 1);
                TramoSeguimiento actual = envio.tramos.get(i);
                registrarFlujo(flujos, actual.origen, envio.cantidad,
                        anterior.llegadaMs, actual.salidaMs, ahoraMs);
            }
        }

        for (DemandaEnvio envio : demandasEnvio.values()) {
            if (enviosContabilizados.contains(envio.idEnvio)) continue;
            if (envio.disponibleDesdeMs <= ahoraMs) {
                flujos.computeIfAbsent(
                        envio.origenActual, ignored -> new FlujoAeropuerto())
                        .entradas += envio.cantidad;
                enviosContabilizados.add(envio.idEnvio);
            }
        }

        Map<String, OcupacionAeropuerto> resultado = new java.util.LinkedHashMap<>();
        flujos.forEach((codigo, flujo) -> {
            int capacidad = capacidadesAeropuerto.getOrDefault(codigo, 0);
            int ocupacion = Math.max(0, flujo.entradas - flujo.salidas);
            double porcentaje = capacidad > 0
                    ? Math.round(ocupacion * 10_000.0 / capacidad) / 100.0
                    : 0.0;
            resultado.put(codigo, new OcupacionAeropuerto(
                    ocupacion, capacidad, porcentaje, flujo.entradas, flujo.salidas));
        });
        return resultado;
    }

    private static void registrarFlujo(
            Map<String, FlujoAeropuerto> flujos, String aeropuerto, int cantidad,
            long entradaMs, long salidaMs, long ahoraMs) {
        FlujoAeropuerto flujo = flujos.computeIfAbsent(
                aeropuerto, ignored -> new FlujoAeropuerto());
        if (entradaMs <= ahoraMs) flujo.entradas += cantidad;
        if (salidaMs <= ahoraMs) flujo.salidas += cantidad;
    }

    public boolean marcarColapsada(EstadoColapso colapso) {
        if (colapsada || colapso == null || !colapso.detectado()) return false;
        estadoColapso = colapso;
        colapsada = true;
        return true;
    }

    private record TramoSeguimiento(
            String clave, String origen, String destino, long salidaMs, long llegadaMs) {}

    private static final class FlujoAeropuerto {
        int entradas;
        int salidas;
    }

    private static final class SeguimientoEnvio {
        final int idEnvio;
        final int cantidad;
        final long fechaRegistroMs;
        final Long fechaLimiteMs;
        boolean cumpleSla;
        final List<TramoSeguimiento> tramos;

        SeguimientoEnvio(int idEnvio, int cantidad, long fechaRegistroMs,
                         Long fechaLimiteMs, boolean cumpleSla,
                         List<TramoSeguimiento> tramos) {
            this.idEnvio = idEnvio;
            this.cantidad = cantidad;
            this.fechaRegistroMs = fechaRegistroMs;
            this.fechaLimiteMs = fechaLimiteMs;
            this.cumpleSla = cumpleSla;
            this.tramos = tramos;
        }

        boolean entregadoEn(long ahoraMs) {
            return !tramos.isEmpty()
                    && tramos.get(tramos.size() - 1).llegadaMs <= ahoraMs;
        }
    }

    private static final class DemandaEnvio {
        final int idEnvio;
        final int cantidad;
        final Long fechaLimiteMs;
        String origenActual;
        long disponibleDesdeMs;

        DemandaEnvio(int idEnvio, String origen, int cantidad,
                     long fechaRegistroMs, Long fechaLimiteMs) {
            this.idEnvio = idEnvio;
            this.cantidad = cantidad;
            this.fechaLimiteMs = fechaLimiteMs;
            this.origenActual = origen;
            this.disponibleDesdeMs = fechaRegistroMs;
        }
    }

    public Set<Integer> enviosDeOcurrencia(String clave) {
        Set<Integer> ids = enviosPorOcurrencia.get(clave);
        return ids == null ? Collections.emptySet() : new java.util.HashSet<>(ids);
    }

    public boolean conoceOcurrencia(String clave) {
        return enviosPorOcurrencia.containsKey(clave);
    }

    public void registrarOcurrencia(String clave) {
        enviosPorOcurrencia.computeIfAbsent(clave, ignored -> ConcurrentHashMap.newKeySet());
    }

    public void registrarOcurrencias(Collection<String> claves) {
        if (claves == null) return;
        claves.forEach(clave -> {
            if (clave.startsWith(CODIGO_DIAGNOSTICO + "|")) {
                log.debug("OCURRENCIA CONOCIDA ANTES REGISTRO={}", conoceOcurrencia(clave));
            }
            registrarOcurrencia(clave);
            ocurrenciasUpdate.add(clave);
            if (clave.startsWith(CODIGO_DIAGNOSTICO + "|")) {
                log.debug("OCURRENCIA CONOCIDA DESPUES REGISTRO={}", conoceOcurrencia(clave));
            }
        });
        boolean contieneSolicitada = claves.stream()
                .anyMatch(clave -> clave.startsWith(CODIGO_DIAGNOSTICO + "|"));
        log.debug("REGISTRO DISPONIBLES simId={} cantidad={} contieneSolicitada={}",
                simId, claves.size(), contieneSolicitada);
    }

    public void registrarOcurrenciasInit(List<Map<String, Object>> vuelosEnAire) {
        if (vuelosEnAire == null) return;
        for (Map<String, Object> vuelo : vuelosEnAire) {
            Object codigo = vuelo.get("codigoVuelo");
            Object salida = vuelo.get("horaSalidaMs");
            if (!(codigo instanceof String) || !(salida instanceof Number)) continue;
            String clave = claveOcurrencia((String) codigo, ((Number) salida).longValue());
            if (clave.startsWith(CODIGO_DIAGNOSTICO + "|")) {
                log.debug("OCURRENCIA CONOCIDA ANTES REGISTRO={}", conoceOcurrencia(clave));
            }
            registrarOcurrencia(clave);
            ocurrenciasInit.add(clave);
            if (clave.startsWith(CODIGO_DIAGNOSTICO + "|")) {
                log.debug("OCURRENCIA CONOCIDA DESPUES REGISTRO={}", conoceOcurrencia(clave));
            }
        }
    }

    public String origenOcurrencia(String clave) {
        if (ocurrenciasInit.contains(clave)) return "INIT";
        if (ocurrenciasUpdate.contains(clave)) return "UPDATE";
        return "DESCONOCIDO";
    }

    /** Retira la ocurrencia de las rutas temporales y devuelve sus envíos afectados. */
    public Set<Integer> liberarOcurrencia(String clave) {
        return liberarOcurrencia(clave, System.currentTimeMillis());
    }

    public Set<Integer> liberarOcurrencia(String clave, long fechaCancelacionMs) {
        Set<Integer> afectados = enviosPorOcurrencia.remove(clave);
        if (afectados == null) return Collections.emptySet();

        Set<Integer> copia = new java.util.HashSet<>(afectados);
        for (Integer idEnvio : copia) {
            SeguimientoEnvio seguimiento = seguimientosEnvio.remove(idEnvio);
            DemandaEnvio demanda = demandasEnvio.get(idEnvio);
            if (seguimiento != null && demanda != null) {
                seguimiento.tramos.stream()
                        .filter(tramo -> tramo.clave.equals(clave))
                        .findFirst()
                        .ifPresent(tramo -> {
                            demanda.origenActual = tramo.origen;
                            demanda.disponibleDesdeMs = fechaCancelacionMs;
                        });
            }
            Set<String> ruta = ocurrenciasPorEnvio.get(idEnvio);
            if (ruta != null) {
                ruta.remove(clave);
                if (ruta.isEmpty()) ocurrenciasPorEnvio.remove(idEnvio, ruta);
            }
        }
        return copia;
    }

    public Set<String> ocurrenciasDeEnvio(Integer idEnvio) {
        Set<String> ruta = ocurrenciasPorEnvio.get(idEnvio);
        return ruta == null ? Collections.emptySet() : new java.util.HashSet<>(ruta);
    }

    public void agregarAlArrastre(Collection<Integer> ids) {
        java.util.LinkedHashSet<Integer> unicos = new java.util.LinkedHashSet<>(arrastreIds);
        unicos.addAll(ids);
        arrastreIds = new java.util.ArrayList<>(unicos);
    }
}
