package com.plantilla.backend.modules.algoritmo.alns.engine;

import com.plantilla.backend.modules.algoritmo.alns.model.PlanDeRutas;
import com.plantilla.backend.modules.algoritmo.alns.operators.DestructionOperator;
import com.plantilla.backend.modules.algoritmo.alns.operators.GreedyInsertion;
import com.plantilla.backend.modules.algoritmo.alns.operators.RandomDestruction;
import com.plantilla.backend.modules.algoritmo.alns.operators.RegretInsertion;
import com.plantilla.backend.modules.algoritmo.alns.operators.RepairOperator;
import com.plantilla.backend.modules.algoritmo.alns.operators.SLAExpiradoDestruction;
import com.plantilla.backend.modules.algoritmo.alns.operators.ShawDestruction;
import com.plantilla.backend.modules.algoritmo.alns.operators.VueloLlenoDestruction;
import com.plantilla.backend.modules.algoritmo.alns.operators.WorstDestruction;
import com.plantilla.backend.modules.algoritmo.alns.util.CostCalculator;
import com.plantilla.backend.modules.algoritmo.alns.util.FlightIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Motor principal del algoritmo Adaptive Large Neighborhood Search (ALNS).
 *
 * Bucle:
 * 1. Solución inicial → 2. Seleccionar operadores adaptativamente → 3. Destruir
 * → 4. Reparar → 5. Evaluar con SA → 6. Actualizar pesos → 7. Repetir
 */
public class ALNSEngine {

    private static final Logger log = LoggerFactory.getLogger(ALNSEngine.class);

    private final int maxIteraciones;
    private final double porcentajeRemocionMin;
    private final double porcentajeRemocionMax;
    /** Presupuesto de tiempo real en ms (0 = sin límite, comportamiento clásico) */
    private final long presupuestoMs;
    /** Iteraciones seguidas sin mejorar el mejor global antes de parar (0 = sin límite) */
    private final int maxSinMejora;

    private final List<DestructionOperator> operadoresDestruccion;
    private final List<RepairOperator> operadoresReparacion;
    private final AdaptiveWeightManager weightManager;
    private final SimulatedAnnealing sa;
    private final FlightIndex flightIndex;
    private final Random random;

    private PlanDeRutas planActual;
    private PlanDeRutas mejorPlanGlobal;
    private final List<double[]> historialCostos;

    public ALNSEngine(int maxIteraciones, double porcentajeRemocionMin, double porcentajeRemocionMax,
                      double temperaturaInicial, double tasaEnfriamiento, double tasaReaccion,
                      int periodoActualizacionPesos, FlightIndex flightIndex) {
        this(maxIteraciones, porcentajeRemocionMin, porcentajeRemocionMax,
             temperaturaInicial, tasaEnfriamiento, tasaReaccion,
             periodoActualizacionPesos, flightIndex, 0L, 0);
    }

    /**
     * Constructor con criterios de parada adicionales para ejecuciones de alta calidad:
     * presupuesto de tiempo real y parada por estancamiento. Permite subir maxIteraciones
     * a miles sin riesgo de exceder la ventana de cómputo disponible.
     */
    public ALNSEngine(int maxIteraciones, double porcentajeRemocionMin, double porcentajeRemocionMax,
                      double temperaturaInicial, double tasaEnfriamiento, double tasaReaccion,
                      int periodoActualizacionPesos, FlightIndex flightIndex,
                      long presupuestoMs, int maxSinMejora) {
        this.maxIteraciones = maxIteraciones;
        this.porcentajeRemocionMin = porcentajeRemocionMin;
        this.porcentajeRemocionMax = porcentajeRemocionMax;
        this.presupuestoMs = presupuestoMs;
        this.maxSinMejora = maxSinMejora;
        this.flightIndex = flightIndex;
        this.random = new Random(42);
        this.historialCostos = new ArrayList<>();

        this.operadoresDestruccion = new ArrayList<>();
        operadoresDestruccion.add(new RandomDestruction());
        operadoresDestruccion.add(new SLAExpiradoDestruction());
        operadoresDestruccion.add(new VueloLlenoDestruction());
        operadoresDestruccion.add(new ShawDestruction());
        operadoresDestruccion.add(new WorstDestruction());

        this.operadoresReparacion = new ArrayList<>();
        operadoresReparacion.add(new GreedyInsertion());
        operadoresReparacion.add(new RegretInsertion());

        List<String> nombresD = new ArrayList<>();
        for (DestructionOperator op : operadoresDestruccion) nombresD.add(op.getNombre());
        List<String> nombresR = new ArrayList<>();
        for (RepairOperator op : operadoresReparacion) nombresR.add(op.getNombre());

        this.weightManager = new AdaptiveWeightManager(nombresD, nombresR,
                tasaReaccion, periodoActualizacionPesos);

        this.sa = new SimulatedAnnealing(temperaturaInicial, tasaEnfriamiento);
    }

    /**
     * Ejecuta el algoritmo ALNS completo y devuelve el mejor plan encontrado.
     */
    public PlanDeRutas ejecutar(PlanDeRutas planInicial) {
        this.planActual = planInicial;
        this.mejorPlanGlobal = planInicial.copiar();

        double costoActual = CostCalculator.calcularCosto(planActual);
        double costoMejorGlobal = costoActual;

        log.debug("ALNS iniciado | Iter máx: {} | Costo inicial: {}", maxIteraciones, costoActual);

        final long inicioMs = System.currentTimeMillis();
        int sinMejoraGlobal = 0;

        for (int iter = 1; iter <= maxIteraciones; iter++) {

            // Criterios de parada de alta calidad (solo si fueron configurados)
            if (presupuestoMs > 0 && System.currentTimeMillis() - inicioMs >= presupuestoMs) {
                log.debug("ALNS detenido por presupuesto de tiempo ({} ms) en iter {}", presupuestoMs, iter);
                break;
            }
            if (maxSinMejora > 0 && sinMejoraGlobal >= maxSinMejora) {
                log.debug("ALNS detenido por estancamiento ({} iter sin mejora) en iter {}", maxSinMejora, iter);
                break;
            }

            int idxDestruccion = weightManager.seleccionarDestruccion();
            int idxReparacion = weightManager.seleccionarReparacion();

            DestructionOperator opDestruccion = operadoresDestruccion.get(idxDestruccion);
            RepairOperator opReparacion = operadoresReparacion.get(idxReparacion);

            double porcentaje = porcentajeRemocionMin +
                    random.nextDouble() * (porcentajeRemocionMax - porcentajeRemocionMin);

            PlanDeRutas planParcial = opDestruccion.destroy(planActual, porcentaje);
            PlanDeRutas nuevoPlan = opReparacion.repair(planParcial, flightIndex);

            double costoNuevo = CostCalculator.calcularCosto(nuevoPlan);

            int sigma = 0;
            if (costoNuevo < costoMejorGlobal) {
                mejorPlanGlobal = nuevoPlan.copiar();
                costoMejorGlobal = costoNuevo;
                sigma = AdaptiveWeightManager.SIGMA_1;
                sinMejoraGlobal = 0;
            } else {
                sinMejoraGlobal++;
                if (costoNuevo < costoActual) {
                    sigma = AdaptiveWeightManager.SIGMA_2;
                }
            }

            if (sa.aceptar(costoNuevo, costoActual)) {
                planActual = nuevoPlan;
                costoActual = costoNuevo;
                if (sigma == 0) {
                    sigma = AdaptiveWeightManager.SIGMA_3;
                }
            }

            weightManager.registrarRendimiento(idxDestruccion, idxReparacion, sigma);
            sa.enfriar();

            historialCostos.add(new double[]{iter, costoActual, costoMejorGlobal});

            if (log.isTraceEnabled() && (iter % imprimirCada() == 0 || iter == maxIteraciones || iter <= 5)) {
                log.trace("ALNS iter={} | costoActual={} | mejorGlobal={} | T={} | D={} R={}",
                        iter, costoActual, costoMejorGlobal, sa.getTemperatura(),
                        opDestruccion.getNombre(), opReparacion.getNombre());
            }
        }

        log.debug("ALNS terminado | Costo final: {} | Mejora: {}%",
                costoMejorGlobal,
                costoActual > 0 ? Math.round((1 - costoMejorGlobal / Math.max(costoActual, 1)) * 1000) / 10.0 : 0);

        return mejorPlanGlobal;
    }

    private int imprimirCada() {
        if (maxIteraciones <= 100) return 10;
        if (maxIteraciones <= 1000) return 50;
        return 200;
    }

    public PlanDeRutas getMejorPlanGlobal() { return mejorPlanGlobal; }
    public List<double[]> getHistorialCostos() { return historialCostos; }
    public AdaptiveWeightManager getWeightManager() { return weightManager; }
}
