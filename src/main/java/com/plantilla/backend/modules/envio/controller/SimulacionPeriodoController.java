package com.plantilla.backend.modules.envio.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantilla.backend.modules.simulacion.alns.AlnsSimulacionService;
import com.plantilla.backend.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Endpoint que expone la simulación de periodo (5 días por defecto) ejecutada
 * con el algoritmo ALNS portado desde el proyecto CODIGO_ALNS.
 *
 * El servicio {@link AlnsSimulacionService} se encarga de:
 *  - cargar aeropuertos, vuelos y envíos de la BD vía JPA,
 *  - convertirlos al modelo interno del algoritmo,
 *  - ejecutar ALNS día por día,
 *  - persistir PlanRuta / TramoRuta / AsignacionVuelo y resultados,
 *  - devolver un resumen JSON.
 */
@RestController
@RequestMapping("/simulacion")
@RequiredArgsConstructor
@Tag(name = "Simulación de periodo (ALNS)", description = "Endpoint para ejecutar la simulación de 5 días usando el algoritmo ALNS")
public class SimulacionPeriodoController {

    private static final Logger log = LoggerFactory.getLogger(SimulacionPeriodoController.class);

    private final AlnsSimulacionService alnsSimulacionService;
    private final ObjectMapper objectMapper;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    private final com.plantilla.backend.modules.simulacion.SimulacionPureService simulacionPureService;
    private final com.plantilla.backend.modules.simulacion.service.ColapsoContinuoService colapsoContinuoService;
    private final com.plantilla.backend.modules.simulacion.SimulacionWebSocketHandler simulacionWebSocketHandler;

    @GetMapping("/activas")
    @Operation(summary = "Simulaciones compartidas en ejecución",
            description = "Lista las simulaciones compartidas activas para que otros dispositivos puedan unirse y ver lo mismo.")
    public ResponseEntity<ApiResponse<java.util.List<Map<String, Object>>>> simulacionesActivas() {
        java.util.List<Map<String, Object>> activas;
        try {
            activas = simulacionWebSocketHandler.listarActivas();
        } catch (Exception e) {
            // Nunca 500: la lista de simulaciones activas es informativa; si algo falla,
            // se devuelve vacía y se registra la causa para diagnóstico.
            log.error("Error listando simulaciones activas", e);
            activas = java.util.Collections.emptyList();
        }
        return ResponseEntity.ok(ApiResponse.success(activas));
    }

    @GetMapping("/colapso/continuo")
    @Operation(summary = "Diagnóstico: simulación continua de colapso sobre un rango",
            description = "Simula día a día con ALNS acumulando ocupación de almacenes; reporta el primer colapso (almacén lleno o sin ruta/fuera de SLA) en el rango.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> colapsoContinuo(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        long t0 = System.currentTimeMillis();
        var res = colapsoContinuoService.buscar(desde, hasta, null);
        Map<String, Object> r = new java.util.LinkedHashMap<>();
        r.put("desde", desde.toString());
        r.put("hasta", hasta.toString());
        if (res != null) {
            r.put("hayColapso", true);
            r.put("fecha", res.fecha().toString());
            r.put("motivo", res.motivo());
        } else {
            r.put("hayColapso", false);
        }
        r.put("ms", System.currentTimeMillis() - t0);
        return ResponseEntity.ok(ApiResponse.success(r));
    }

    @GetMapping("/colapso/chequear-dia")
    @Operation(summary = "Diagnóstico: evalúa el colapso de un solo día",
            description = "Corre el planificador sobre los envíos de la fecha dada y reporta cuántas maletas quedan sin ruta o fuera de SLA.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> chequearDia(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        long t0 = System.currentTimeMillis();
        var chequeo = simulacionPureService.chequearDia(fecha);
        Map<String, Object> r = new java.util.LinkedHashMap<>();
        r.put("fecha", fecha.toString());
        r.put("sinRuta", chequeo.sinRuta());
        r.put("fueraSla", chequeo.fueraSla());
        r.put("hayColapso", chequeo.hayViolacion());
        r.put("ms", System.currentTimeMillis() - t0);
        return ResponseEntity.ok(ApiResponse.success(r));
    }

    @GetMapping("/rango-datos")
    @Operation(
            summary = "Rango de fechas con pedidos disponibles",
            description = "Devuelve la primera y última fecha con envíos registrados en la BD, "
                    + "para habilitar el selector de fecha de la simulación."
    )
    public ResponseEntity<ApiResponse<Map<String, Object>>> rangoDatos() {
        Map<String, Object> rango = new java.util.LinkedHashMap<>();
        try {
            // MIN/MAX sobre la columna cruda usan el índice idx_envio_fecha_registro
            // (son instantáneos). Envolver en DATE() impedía usar el índice y forzaba
            // un full scan de ~10M filas → la consulta tardaba >60 s por el túnel.
            java.sql.Timestamp desde = jdbcTemplate.queryForObject(
                    "SELECT MIN(fecha_registro) FROM envio_maletas", java.sql.Timestamp.class);
            java.sql.Timestamp hasta = jdbcTemplate.queryForObject(
                    "SELECT MAX(fecha_registro) FROM envio_maletas", java.sql.Timestamp.class);
            rango.put("desde", desde != null ? desde.toLocalDateTime().toLocalDate().toString() : null);
            rango.put("hasta", hasta != null ? hasta.toLocalDateTime().toLocalDate().toString() : null);
        } catch (Exception e) {
            rango.put("desde", null);
            rango.put("hasta", null);
        }
        return ResponseEntity.ok(ApiResponse.success(rango));
    }

    @PostMapping("/periodo")
    @Operation(
            summary = "Ejecutar simulación de periodo con ALNS",
            description = "Carga envíos y vuelos del rango indicado y ejecuta el algoritmo ALNS día por día. " +
                    "Persiste la configuración, los resultados y los planes de ruta asignados."
    )
    public ResponseEntity<ApiResponse<Map<String, Object>>> simularPeriodo(
            @RequestParam(defaultValue = "2026-01-02")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate fechaInicio,

            @RequestParam(defaultValue = "5")
            Integer dias
    ) {
        Map<String, Object> resultado = alnsSimulacionService.simularPeriodo(fechaInicio, dias);

        return ResponseEntity.ok(
                ApiResponse.success("Simulación ALNS de periodo ejecutada correctamente", resultado)
        );
    }

    /**
     * Endpoint SSE: emite eventos día por día mientras el algoritmo ALNS procesa.
     * El cliente recibe eventos: "inicio", "dia" (uno por día), "fin" y opcionalmente "error".
     */
    @GetMapping(value = "/periodo/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(
            summary = "Simulación de periodo con streaming SSE",
            description = "Ejecuta ALNS en background y emite un evento SSE por cada día procesado. " +
                    "Eventos: 'inicio', 'dia', 'fin', 'error'."
    )
    public SseEmitter simularPeriodoStream(
            @RequestParam(defaultValue = "2026-01-02")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate fechaInicio,

            @RequestParam(defaultValue = "5")
            Integer dias
    ) {
        // Timeout generoso: 10 minutos para simulaciones largas
        SseEmitter emitter = new SseEmitter(600_000L);

        // Referencia final para usar dentro del lambda
        final AlnsSimulacionService service = alnsSimulacionService;

        CompletableFuture.runAsync(() -> {
            try {
                // Evento de inicio
                Map<String, Object> inicioData = new LinkedHashMap<>();
                inicioData.put("fechaInicio", fechaInicio.toString());
                inicioData.put("dias", dias);
                emitter.send(SseEmitter.event().name("inicio").data(objectMapper.writeValueAsString(inicioData)));

                // Ejecutar simulación con callback por día
                Map<String, Object> resultado = service.simularPeriodoConCallback(
                        fechaInicio, dias,
                        diaData -> {
                            try {
                                emitter.send(SseEmitter.event().name("dia").data(objectMapper.writeValueAsString(diaData)));
                            } catch (IOException e) {
                                log.warn("Error enviando evento SSE 'dia': {}", e.getMessage());
                            }
                        }
                );

                // Evento final con resumen completo
                emitter.send(SseEmitter.event().name("fin").data(objectMapper.writeValueAsString(resultado)));
                emitter.complete();

            } catch (Exception e) {
                log.error("Error en simulación SSE", e);
                try {
                    Map<String, Object> errorData = new LinkedHashMap<>();
                    errorData.put("mensaje", e.getMessage() != null ? e.getMessage() : "Error interno");
                    emitter.send(SseEmitter.event().name("error").data(objectMapper.writeValueAsString(errorData)));
                } catch (IOException ioe) {
                    log.warn("No se pudo enviar evento de error SSE: {}", ioe.getMessage());
                }
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }
}
