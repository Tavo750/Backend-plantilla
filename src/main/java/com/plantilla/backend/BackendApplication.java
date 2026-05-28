package com.plantilla.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

/**
 * Clase principal de la aplicación Spring Boot.
 * Punto de entrada del backend.
 */
@SpringBootApplication
@EnableCaching
public class BackendApplication {

    // =====================================================================
    // FLAGS DE CONFIGURACIÓN GLOBAL — modificar aquí antes de arrancar
    // =====================================================================

    /**
     * Controla si los resultados de la simulación ALNS se persisten en la base de datos.
     *
     * <ul>
     *   <li>{@code true}  — <b>(modo normal)</b> guarda ConfiguracionSimulacion, PlanRuta,
     *       TramoRuta, AsignacionVuelo y ResultadoSimulacion en cada ejecución.</li>
     *   <li>{@code false} — <b>(modo sin BD)</b> ejecuta el algoritmo normalmente pero
     *       NO escribe nada en la BD; útil para pruebas repetidas o para evitar
     *       sobrecargar la base de datos.</li>
     * </ul>
     */
    public static boolean GUARDAR_EN_BD = false;

    // =====================================================================

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }
}
