package com.plantilla.backend.modules.algoritmo.alns.operators;

import com.plantilla.backend.modules.algoritmo.alns.model.PlanDeRutas;

/**
 * Interfaz para operadores de destrucción del ALNS.
 * Remueve un porcentaje de envíos del plan actual, creando un plan parcial
 * que luego será reparado.
 */
public interface DestructionOperator {

    PlanDeRutas destroy(PlanDeRutas plan, double porcentajeRemocion);

    String getNombre();
}
