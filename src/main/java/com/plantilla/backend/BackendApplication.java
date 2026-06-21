package com.plantilla.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Clase principal de la aplicación Spring Boot.
 * Punto de entrada del backend.
 */
@SpringBootApplication
@EnableCaching
@EnableScheduling
public class BackendApplication {

    /** Controla si el ALNS persiste resultados en BD. */
    public static boolean GUARDAR_EN_BD = false;
    /** K = 1: monitoreo en tiempo real (sin aceleración simulada). */
    public static final int K  = 1;
    /** SA = 5: ciclo de planificación cada 5 minutos reales. */
    public static final int SA = 5;

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }
}
