package com.plantilla.backend.infrastructure.database;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Configuración de la base de datos y auditoría JPA.
 * Principio SOLID (S): Solo gestiona configuración de persistencia.
 */
@Configuration
@EnableJpaAuditing
public class DatabaseConfig {
    // La configuración de datasource se maneja en application.yml
    // Esta clase habilita auditoría JPA y puede extenderse para
    // configuraciones adicionales (pools, réplicas, etc.)
}
