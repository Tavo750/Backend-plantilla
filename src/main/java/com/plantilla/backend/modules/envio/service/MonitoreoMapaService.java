package com.plantilla.backend.modules.envio.service;

import com.plantilla.backend.BackendApplication;
import com.plantilla.backend.modules.envio.dto.EstadoMonitoreo;
import com.plantilla.backend.modules.simulacion.alns.AlnsSimulacionService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Orquestador server-side de la planificación continua del Monitoreo Mapa.
 *
 * Ciclo de vida:
 *  1. El frontend llama POST /monitoreo/iniciar → se arranca el primer ciclo ALNS.
 *  2. El ALNS tiene SA minutos reales para procesar la mayor cantidad de días posible.
 *  3. Al terminar, el resultado queda en {@link EstadoMonitoreo} (fase=LISTO).
 *  4. El scheduler espera el tiempo restante del ventana SA y arranca el siguiente ciclo
 *     desde donde terminó el anterior (ultimaFechaEnvio).
 *  5. El frontend hace polling a GET /monitoreo/estado para obtener resultados nuevos.
 *
 * La simulación es 100% server-side: no se interrumpe si el usuario cambia de pestaña.
 */
@Service
@RequiredArgsConstructor
public class MonitoreoMapaService {

    private static final Logger log = LoggerFactory.getLogger(MonitoreoMapaService.class);

    private final AlnsSimulacionService alnsSimulacionService;

    /** Evita lanzar dos ciclos simultáneos. */
    private final AtomicBoolean activo = new AtomicBoolean(false);

    /** Estado compartido con el frontend vía polling. Volatile para visibilidad entre hilos. */
    private volatile EstadoMonitoreo estado = new EstadoMonitoreo();

    /**
     * Scheduler de un solo hilo: programa el inicio de cada nuevo ciclo
     * una vez transcurrido el tiempo SA desde el inicio del ciclo anterior.
     */
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "monitoreo-scheduler");
        t.setDaemon(true);
        return t;
    });

    // ──────────────────────────────────────────────────────────────────
    // API pública
    // ──────────────────────────────────────────────────────────────────

    /**
     * Arranca la planificación continua desde {@code fechaInicio}.
     * Idempotente: si ya está activa devuelve el estado actual sin relanzar.
     */
    public EstadoMonitoreo iniciar(LocalDateTime fechaInicio) {
        if (activo.compareAndSet(false, true)) {
            log.info("Monitoreo iniciado desde {}", fechaInicio);
            EstadoMonitoreo e = new EstadoMonitoreo();
            e.setFase("INICIANDO");
            e.setVentanaActual(fechaInicio);
            estado = e;
            lanzarCiclo(fechaInicio);
        } else {
            log.info("Monitoreo ya activo — ignorando /iniciar");
        }
        return estado;
    }

    /** Estado actual para que el frontend haga polling. */
    public EstadoMonitoreo obtenerEstado() {
        return estado;
    }

    /** Detiene la planificación continua. El ciclo en curso termina normalmente. */
    public void detener() {
        log.info("Monitoreo detenido manualmente");
        activo.set(false);
        estado.setFase("DETENIDO");
    }

    // ──────────────────────────────────────────────────────────────────
    // Ciclo interno
    // ──────────────────────────────────────────────────────────────────

    private void lanzarCiclo(LocalDateTime ventanaInicio) {
        if (!activo.get()) return;

        int numeroCiclo = estado.getCiclo() + 1;
        long inicioCicloMs = System.currentTimeMillis();
        long tiempoLimiteMs = (long) BackendApplication.SA * 60_000L;

        EstadoMonitoreo nuevo = new EstadoMonitoreo();
        nuevo.setFase("PROCESANDO");
        nuevo.setCiclo(numeroCiclo);
        nuevo.setVentanaActual(ventanaInicio);
        nuevo.setInicioCicloMs(inicioCicloMs);
        nuevo.setTiempoLimiteCicloMs(tiempoLimiteMs);
        nuevo.setUltimoResultado(estado.getUltimoResultado()); // mantener resultado anterior visible
        nuevo.setUltimaActualizacion(estado.getUltimaActualizacion());
        estado = nuevo;

        log.info("Monitoreo ciclo {} | ventana desde {} | límite {} min",
                numeroCiclo, ventanaInicio, BackendApplication.SA);

        // ALNS en hilo daemon para no bloquear el scheduler
        Thread alnsThread = new Thread(() -> {
            try {
                Map<String, Object> resultado = alnsSimulacionService
                        .simularDesdeVentanaConLimite(ventanaInicio, tiempoLimiteMs);

                // Publicar resultado
                estado.setUltimoResultado(resultado);
                estado.setFase("LISTO");
                estado.setUltimaActualizacion(LocalDateTime.now());

                int vuelosCount = resultado.get("vuelos") instanceof List<?>
                        ? ((List<?>) resultado.get("vuelos")).size() : 0;
                log.info("Monitoreo ciclo {} completado | días={} | vuelos={} | asignados={}",
                        numeroCiclo,
                        resultado.get("diasProcesados"),
                        vuelosCount,
                        resultado.get("asignados"));

                // Programar siguiente ciclo respetando la ventana SA completa
                if (activo.get()) {
                    String ultimaStr = (String) resultado.get("ultimaFechaEnvio");
                    LocalDateTime siguienteVentana = ultimaStr != null
                            ? LocalDateTime.parse(ultimaStr)
                            : ventanaInicio.plusDays(1);

                    // Siempre esperar SA minutos completos (reales) antes del próximo ciclo,
                    // independientemente de cuánto tardó el ALNS.
                    // Esto garantiza que entre ciclos siempre pasen exactamente SA minutos.
                    long delay = tiempoLimiteMs; // = SA * 60_000 ms

                    log.info("Próximo ciclo en {} min desde {}", BackendApplication.SA, siguienteVentana);
                    scheduler.schedule(() -> lanzarCiclo(siguienteVentana), delay, TimeUnit.MILLISECONDS);
                }

            } catch (Exception ex) {
                log.error("Error en monitoreo ciclo {}: {}", numeroCiclo, ex.getMessage(), ex);
                estado.setFase("ERROR");

                // Reintento automático tras SA minutos desde el mismo punto
                if (activo.get()) {
                    scheduler.schedule(() -> lanzarCiclo(ventanaInicio),
                            BackendApplication.SA, TimeUnit.MINUTES);
                }
            }
        }, "monitoreo-alns-" + numeroCiclo);

        alnsThread.setDaemon(true);
        alnsThread.start();
    }
}
