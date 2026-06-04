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

    // ── Parámetros de Planificación Programada (Monitoreo Mapa) ──────────

    /**
     * K — Factor de velocidad de animación en el Monitoreo Mapa.
     * Controla cuántos segundos simulados avanzan por cada segundo real en la pantalla.
     * <ul>
     *   <li>K = 1    → 1 segundo real = 1 segundo simulado (tiempo real)</li>
     *   <li>K = 60   → 1 segundo real = 1 minuto simulado</li>
     *   <li>K = 3600 → 1 segundo real = 1 hora simulada</li>
     * </ul>
     * No tiene ningún efecto sobre cuántos pedidos procesa el algoritmo.
     */
    public static int K = 60;

    /**
     * SA — Intervalo en minutos REALES entre ejecuciones del algoritmo ALNS.
     * Cada SA minutos, el sistema lanza una nueva planificación con todos los
     * envíos pendientes desde la última ventana procesada.
     */
    public static int SA = 5;

    /**
     * CARGAR_DESDE_LOCAL — Controla la fuente de datos de envíos para el Monitoreo Mapa.
     * <ul>
     *   <li>{@code true}  — lee los envíos directamente de los archivos TXT en
     *       {@code classpath:data/_envios_preliminar_/} sin tocar la BD.</li>
     *   <li>{@code false} — lee los envíos desde la tabla {@code envio_maletas} de la BD.</li>
     * </ul>
     */
    public static boolean CARGAR_DESDE_LOCAL = true;

    // =====================================================================

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }
}
