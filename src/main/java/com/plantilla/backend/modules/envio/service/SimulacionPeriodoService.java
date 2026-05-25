package com.plantilla.backend.modules.envio.service;

import com.plantilla.backend.modules.simulacion.alns.AlnsSimulacionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Map;

/**
 * @deprecated Implementación greedy original sustituida por {@link AlnsSimulacionService}.
 * <p>
 * Se conserva como shim para compatibilidad con cualquier consumidor antiguo;
 * delega directamente al servicio ALNS. Marcar para eliminar tras verificar que
 * ningún cliente externo lo referencia.
 */
@Deprecated(forRemoval = true)
@Service
@RequiredArgsConstructor
public class SimulacionPeriodoService {

    private final AlnsSimulacionService alnsSimulacionService;

    /**
     * @deprecated Usar {@link AlnsSimulacionService#simularPeriodo(LocalDate, int)} en su lugar.
     */
    @Deprecated(forRemoval = true)
    public Map<String, Object> simularPeriodo(LocalDate fechaInicio, Integer dias) {
        return alnsSimulacionService.simularPeriodo(fechaInicio, dias);
    }
}
