package com.plantilla.backend.shared.helpers;

/**
 * Utilidades para manejo de cadenas de texto.
 * Principio SOLID (S): Solo responsable de operaciones con strings.
 */
public final class StringHelper {

    private StringHelper() {
        // Clase utilitaria, no instanciable
    }

    /**
     * Verifica si una cadena es nula o vacía (incluyendo espacios en blanco).
     */
    public static boolean isBlank(String str) {
        return str == null || str.isBlank();
    }

    /**
     * Capitaliza la primera letra de cada palabra.
     * Ejemplo: "juan perez" → "Juan Perez"
     */
    public static String capitalize(String str) {
        if (isBlank(str)) return str;

        String[] words = str.trim().toLowerCase().split("\\s+");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1))
                        .append(" ");
            }
        }
        return result.toString().trim();
    }

    /**
     * Genera un nombre completo a partir de nombre y apellidos.
     */
    public static String buildNombreCompleto(String nombre, String apellidoPaterno, String apellidoMaterno) {
        StringBuilder sb = new StringBuilder();
        if (!isBlank(nombre)) sb.append(nombre.trim());
        if (!isBlank(apellidoPaterno)) sb.append(" ").append(apellidoPaterno.trim());
        if (!isBlank(apellidoMaterno)) sb.append(" ").append(apellidoMaterno.trim());
        return sb.toString().trim();
    }
}
