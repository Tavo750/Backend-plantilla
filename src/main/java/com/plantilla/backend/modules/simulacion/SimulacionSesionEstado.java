package com.plantilla.backend.modules.simulacion;

import lombok.Getter;
import lombok.Setter;
import org.springframework.web.socket.WebSocketSession;

import java.time.LocalDateTime;
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
        return wsSession.isOpen() && ciclosEjecutados < MAX_CICLOS;
    }
}
