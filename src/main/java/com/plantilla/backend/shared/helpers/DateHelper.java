package com.plantilla.backend.shared.helpers;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Utilidades para manejo de fechas.
 * Principio SOLID (S): Solo responsable de operaciones con fechas.
 */
public final class DateHelper {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private DateHelper() {
        // Clase utilitaria, no instanciable
    }

    /**
     * Formatea un LocalDate a String (dd/MM/yyyy).
     */
    public static String formatDate(LocalDate date) {
        return date != null ? date.format(DATE_FORMAT) : null;
    }

    /**
     * Formatea un LocalDateTime a String (dd/MM/yyyy HH:mm:ss).
     */
    public static String formatDateTime(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(DATETIME_FORMAT) : null;
    }

    /**
     * Parsea un String (dd/MM/yyyy) a LocalDate.
     */
    public static LocalDate parseDate(String dateStr) {
        return dateStr != null ? LocalDate.parse(dateStr, DATE_FORMAT) : null;
    }
}
