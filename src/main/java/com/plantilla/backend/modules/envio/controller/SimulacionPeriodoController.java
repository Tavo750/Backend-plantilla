package com.plantilla.backend.modules.envio.controller;

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
                emitter.send(SseEmitter.event().name("inicio").data(inicioData));

                // Ejecutar simulación con callback por día
                Map<String, Object> resultado = service.simularPeriodoConCallback(
                        fechaInicio, dias,
                        diaData -> {
                            try {
                                emitter.send(SseEmitter.event().name("dia").data(diaData));
                            } catch (IOException e) {
                                log.warn("Error enviando evento SSE 'dia': {}", e.getMessage());
                            }
                        }
                );

                // Evento final con resumen completo
                emitter.send(SseEmitter.event().name("fin").data(resultado));
                emitter.complete();

            } catch (Exception e) {
                log.error("Error en simulación SSE", e);
                try {
                    Map<String, Object> errorData = new LinkedHashMap<>();
                    errorData.put("mensaje", e.getMessage() != null ? e.getMessage() : "Error interno");
                    emitter.send(SseEmitter.event().name("error").data(errorData));
                } catch (IOException ioe) {
                    log.warn("No se pudo enviar evento de error SSE: {}", ioe.getMessage());
                }
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }
}
