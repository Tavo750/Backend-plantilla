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

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }
}
