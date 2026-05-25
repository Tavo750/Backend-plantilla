package com.plantilla.backend.modules.algoritmo.alns.engine;

import java.util.Random;

/**
 * Criterio de aceptación basado en Simulated Annealing.
 *
 * Una nueva solución se acepta si:
 *  - Es mejor que la actual (Δcosto < 0), o
 *  - Con probabilidad exp(-Δcosto / T)
 *
 * La temperatura se reduce geométricamente: T = T * α
 */
public class SimulatedAnnealing {
    private double temperatura;
    private final double tasaEnfriamiento;
    private final Random random;

    public SimulatedAnnealing(double temperaturaInicial, double tasaEnfriamiento) {
        this.temperatura = temperaturaInicial;
        this.tasaEnfriamiento = tasaEnfriamiento;
        this.random = new Random();
    }

    public boolean aceptar(double costoNuevo, double costoActual) {
        double delta = costoNuevo - costoActual;

        if (delta < 0) return true;
        if (temperatura <= 0) return false;

        double probabilidad = Math.exp(-delta / temperatura);
        return random.nextDouble() < probabilidad;
    }

    public void enfriar() {
        temperatura *= tasaEnfriamiento;
    }

    public double getTemperatura() {
        return temperatura;
    }

    public boolean estaFrio() {
        return temperatura < 0.01;
    }

    @Override
    public String toString() {
        return String.format("SA{T=%.4f, α=%.4f}", temperatura, tasaEnfriamiento);
    }
}
