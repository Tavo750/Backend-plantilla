package com.plantilla.backend.modules.simulacion;

import lombok.Getter;
import lombok.Setter;
import org.springframework.web.socket.WebSocketSession;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Getter
@Setter
public class SimulacionSesionEstado {

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
}
