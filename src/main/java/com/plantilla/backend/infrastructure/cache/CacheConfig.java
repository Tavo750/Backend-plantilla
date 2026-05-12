package com.plantilla.backend.infrastructure.cache;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración de caché de la aplicación.
 * Principio SOLID (S): Solo gestiona configuración de caché.
 * Principio SOLID (O): Abierto a extensión (puede reemplazarse por Redis, Caffeine, etc.)
 *
 * Para usar Redis, agregar la dependencia spring-boot-starter-data-redis
 * y configurar las propiedades en application.yml:
 *   spring.cache.type: redis
 *   spring.data.redis.host: localhost
 *   spring.data.redis.port: 6379
 */
@Configuration
@EnableCaching
public class CacheConfig {
    // Configuración actual: cache simple en memoria (ConcurrentHashMap).
    // Para producción, se recomienda migrar a Redis o Caffeine.
}
