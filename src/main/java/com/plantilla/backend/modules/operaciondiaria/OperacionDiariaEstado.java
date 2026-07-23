package com.plantilla.backend.modules.operaciondiaria;

import lombok.Getter;
import lombok.Setter;
import org.springframework.web.socket.WebSocketSession;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;

/**
 * Estado de la OPERACIÓN DIARIA en continuo (una sola instancia global compartida
 * por todos los dispositivos conectados). No tiene fin: el planificador corre
 * indefinidamente. Aislado del flujo de simulación.
 */
@Getter
@Setter
public class OperacionDiariaEstado {

    /** Puntero de la ventana de planificación (avanza en tiempo real). */
    private LocalDateTime punteroSim;

    /** Instante real (wall-clock) en que arrancó la operación: ancla del reloj. */
    private long inicioRealMs = System.currentTimeMillis();

    /** IDs de envíos ya reflejados en el mapa (no se reprocesan). */
    private final Set<Integer> reflejados = ConcurrentHashMap.newKeySet();

    /** Vuelos del plan enviados en el INIT (se muestran volando aunque vayan vacíos). */
    private List<Map<String, Object>> snapshotVuelos = new CopyOnWriteArrayList<>();

    /** Aeropuertos enviados en el INIT. */
    private List<Map<String, Object>> snapshotAeropuertos = new CopyOnWriteArrayList<>();

    /** Estado acumulado de asignaciones: clave vuelo → payload con maletas mergeadas. */
    private final Map<String, Map<String, Object>> acumulados = new ConcurrentHashMap<>();

    /** Sesiones WebSocket que están viendo la operación. */
    private final Set<WebSocketSession> suscriptores = ConcurrentHashMap.newKeySet();

    /** Ocurrencias de vuelo canceladas (clave = codigoVuelo|horaSalidaMs). El vuelo no
     *  se planifica ni despega para esa fecha; sus maletas se replanifican. */
    private final Set<String> ocurrenciasCanceladas = ConcurrentHashMap.newKeySet();

    private ScheduledFuture<?> tareaScheduled;
    private int ciclos = 0;

    public int incrementarCiclo() { return ++ciclos; }

    public static String claveOcurrencia(String codigoVuelo, long horaSalidaMs) {
        return codigoVuelo + "|" + horaSalidaMs;
    }
}
