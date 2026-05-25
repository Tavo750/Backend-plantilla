package com.plantilla.backend.modules.algoritmo.alns.operators;

import com.plantilla.backend.modules.algoritmo.alns.model.PlanDeRutas;
import com.plantilla.backend.modules.algoritmo.alns.util.FlightIndex;

/**
 * Interfaz para operadores de reparación del ALNS.
 * Reinserta los envíos no asignados al plan respetando restricciones de capacidad.
 */
public interface RepairOperator {

    PlanDeRutas repair(PlanDeRutas planParcial, FlightIndex flightIndex);

    String getNombre();
}
