package com.plantilla.backend.modules.envio.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.plantilla.backend.BackendApplication;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;

/**
 * Estado actual del ciclo de planificación del Monitoreo Mapa.
 * Devuelto por polling desde el frontend.
 *
 * Fases:
 *  INACTIVO   — no se ha llamado a /iniciar
 *  INICIANDO  — se recibió /iniciar, arrancando primer ciclo
 *  PROCESANDO — ALNS corriendo, hay un countdown activo
 *  LISTO      — ALNS terminó el ciclo, resultado disponible para animar
 *  ERROR      — fallo en el ciclo (reintento automático programado)
 *  DETENIDO   — /detener llamado explícitamente
 */
@Getter
@Setter
public class EstadoMonitoreo {

    /** INACTIVO | INICIANDO | PROCESANDO | LISTO | ERROR | DETENIDO */
    private String fase = "INACTIVO";

    /** Número de ciclo actual (1-based). */
    private int ciclo = 0;

    /** Fecha-hora desde la que arrancó la ventana de este ciclo. */
    private LocalDateTime ventanaActual;

    /**
     * Resultado del último ciclo completado.
     * Contiene: ventanaInicio, diasProcesados, asignados, noAsignados,
     *           vuelos (lista), ultimaFechaEnvio.
     */
    private Map<String, Object> ultimoResultado = Collections.emptyMap();

    /** Cuándo se guardó el último resultado. */
    private LocalDateTime ultimaActualizacion;

    /** Factor de velocidad de animación (leído de BackendApplication.K). */
    public int getK() {
        return BackendApplication.K;
    }

    /** Intervalo entre ciclos en minutos reales (BackendApplication.SA). */
    public int getSA() {
        return BackendApplication.SA;
    }

    // ── Para calcular countdown ─────────────────────────────────────────

    @JsonIgnore
    private long inicioCicloMs = 0;

    @JsonIgnore
    private long tiempoLimiteCicloMs = 0;

    /**
     * Milisegundos que quedan en la ventana SA del ciclo actual.
     * Funciona tanto en PROCESANDO (ALNS corriendo) como en LISTO (esperando próximo ciclo).
     * Vale 0 si la simulación está INACTIVA o DETENIDA.
     */
    public long getTiempoRestanteCicloMs() {
        if ("INACTIVO".equals(fase) || "DETENIDO".equals(fase) || inicioCicloMs == 0) return 0L;
        return Math.max(0L, tiempoLimiteCicloMs - (System.currentTimeMillis() - inicioCicloMs));
    }
}
