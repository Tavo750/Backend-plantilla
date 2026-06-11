package com.plantilla.backend.modules.envio.controller;

import com.plantilla.backend.BackendApplication;
import com.plantilla.backend.modules.envio.entity.EnvioMaletas;
import com.plantilla.backend.modules.envio.repository.EnvioMaletasRepository;
import com.plantilla.backend.modules.envio.service.MonitoreoMapaService;
import com.plantilla.backend.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Endpoints del módulo Monitoreo Mapa.
 *
 * Flujo principal:
 *  1. GET  /simulacion/monitoreo/fecha-inicio  → obtener la fecha de inicio automática
 *  2. POST /simulacion/monitoreo/iniciar       → arrancar planificación continua server-side
 *  3. GET  /simulacion/monitoreo/estado        → polling para obtener resultado del ciclo actual
 *  4. POST /simulacion/monitoreo/detener       → detener planificación
 *
 * Parámetros globales (BackendApplication):
 *  K  — segundos simulados por segundo real en la animación del mapa
 *  SA — minutos reales que dura cada ciclo ALNS
 */
@RestController
@RequestMapping("/simulacion")
@RequiredArgsConstructor
@Tag(name = "Monitoreo Mapa", description = "Planificación continua para el Monitoreo Mapa")
public class MonitoreoMapaController {

    private final MonitoreoMapaService monitoreoMapaService;
    private final EnvioMaletasRepository envioMaletasRepository;

    // ──────────────────────────────────────────────────────────────────
    // Configuración y fecha de inicio
    // ──────────────────────────────────────────────────────────────────

    @GetMapping("/monitoreo/config")
    @Operation(summary = "Obtener configuración del monitoreo (K, SA)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("K",  BackendApplication.K);
        config.put("SA", BackendApplication.SA);
        return ResponseEntity.ok(ApiResponse.success("Configuración del monitoreo", config));
    }

    /**
     * Devuelve la fecha de registro del envío más antiguo en la BD.
     * El frontend la usa como punto de partida automático al llamar /iniciar sin parámetro.
     */
    @GetMapping("/monitoreo/fecha-inicio")
    @Operation(summary = "Fecha-hora más temprana de los envíos (auto-start)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getFechaInicio() {
        LocalDateTime earliest = resolverFechaInicioAutomatica();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fechaInicio", earliest.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        return ResponseEntity.ok(ApiResponse.success("Fecha de inicio del monitoreo", result));
    }

    // ──────────────────────────────────────────────────────────────────
    // Ciclo continuo
    // ──────────────────────────────────────────────────────────────────

    /**
     * Arranca la planificación continua server-side.
     *
     * IDEMPOTENTE: si el monitoreo ya está activo (cualquier fase distinta de INACTIVO/DETENIDO)
     * devuelve el estado actual sin relanzar ni reiniciar el contador.
     * El frontend puede llamar este endpoint cada vez que abre la pantalla — solo la primera vez
     * arranca el ciclo real; las demás simplemente reciben el estado en curso.
     *
     * Una vez iniciado el monitoreo continúa en el servidor aunque el cliente navegue
     * a otra pantalla o cierre la pestaña.
     */
    @PostMapping("/monitoreo/iniciar")
    @Operation(
        summary = "Iniciar planificación continua (idempotente)",
        description = "Primera llamada arranca el ciclo ALNS. Llamadas siguientes devuelven el " +
                      "estado actual sin reiniciar. El frontend hace polling a /estado."
    )
    public ResponseEntity<ApiResponse<Map<String, Object>>> iniciar(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fechaInicio
    ) {
        // fechaInicio == null → el servicio arranca desde el envío más antiguo de la BD
        Map<String, Object> estado = monitoreoMapaService.iniciar(fechaInicio);
        return ResponseEntity.ok(ApiResponse.success("Monitoreo iniciado", estado));
    }

    /**
     * Alias de /iniciar mantenido por compatibilidad con el frontend existente.
     * Mismo comportamiento idempotente.
     */
    @PostMapping("/monitoreo/ejecutar")
    @Operation(
        summary = "Alias de /iniciar (compatibilidad frontend)",
        description = "Equivalente a POST /monitoreo/iniciar. Si el monitoreo ya está activo " +
                      "devuelve el estado actual sin reiniciar."
    )
    public ResponseEntity<ApiResponse<Map<String, Object>>> ejecutar(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime ventanaInicio
    ) {
        Map<String, Object> estado = monitoreoMapaService.iniciar(ventanaInicio);
        return ResponseEntity.ok(ApiResponse.success("Monitoreo iniciado", estado));
    }

    /**
     * Snapshot completo del monitoreo: usado por el frontend para bootstrap al entrar
     * (reloj simulado actual + vuelos acumulados + paneles). Las actualizaciones en vivo
     * llegan por WebSocket; este endpoint sirve para retomar el estado al navegar de vuelta.
     */
    @GetMapping("/monitoreo/estado")
    @Operation(
        summary = "Snapshot del monitoreo (bootstrap)",
        description = "Devuelve reloj simulado actual, vuelos acumulados, paneles y contadores."
    )
    public ResponseEntity<ApiResponse<Map<String, Object>>> getEstado() {
        return ResponseEntity.ok(
                ApiResponse.success("Estado del monitoreo", monitoreoMapaService.obtenerSnapshot()));
    }

    /**
     * Detiene la planificación continua. El ciclo en curso termina normalmente
     * pero no se programa el siguiente.
     */
    @PostMapping("/monitoreo/detener")
    @Operation(summary = "Detener planificación continua")
    public ResponseEntity<ApiResponse<Void>> detener() {
        monitoreoMapaService.detener();
        return ResponseEntity.ok(ApiResponse.success("Monitoreo detenido", null));
    }

    // ──────────────────────────────────────────────────────────────────
    // Helper: fecha de inicio desde la BD
    // ──────────────────────────────────────────────────────────────────

    private LocalDateTime resolverFechaInicioAutomatica() {
        return envioMaletasRepository.findTopByOrderByFechaRegistroAsc()
                .map(EnvioMaletas::getFechaRegistro)
                .orElse(LocalDateTime.of(2026, 1, 2, 0, 0));
    }
}
