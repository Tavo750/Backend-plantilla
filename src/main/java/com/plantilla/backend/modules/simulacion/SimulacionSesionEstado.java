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
        if (simId != null) return !finalizada && ciclosEjecutados < MAX_CICLOS;
        return wsSession.isOpen() && ciclosEjecutados < MAX_CICLOS;
    }

    public static String claveOcurrencia(String codigoVuelo, long horaSalidaMs) {
        return codigoVuelo + "|" + horaSalidaMs;
    }

    public void registrarResultados(List<Map<String, Object>> vuelos) {
        if (vuelos == null) return;

        Map<Integer, Set<String>> nuevasOcurrenciasPorEnvio = new java.util.HashMap<>();
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
        Set<Integer> afectados = enviosPorOcurrencia.remove(clave);
        if (afectados == null) return Collections.emptySet();

        Set<Integer> copia = new java.util.HashSet<>(afectados);
        for (Integer idEnvio : copia) {
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
