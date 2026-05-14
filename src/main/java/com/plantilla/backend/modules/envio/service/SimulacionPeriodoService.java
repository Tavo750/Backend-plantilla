package com.plantilla.backend.modules.envio.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SimulacionPeriodoService {

    private final JdbcTemplate jdbcTemplate;

    public Map<String, Object> simularPeriodo(LocalDate fechaInicio, Integer dias) {
        LocalDateTime inicioFiltro = fechaInicio.atStartOfDay().minusDays(1);
        LocalDateTime finFiltro = fechaInicio.plusDays(dias + 1).atStartOfDay();

        List<Map<String, Object>> envios = jdbcTemplate.queryForList("""
                SELECT 
                    e.id_envio,
                    e.id_aeropuerto_origen,
                    e.id_aeropuerto_destino,
                    e.cantidad,
                    e.fecha_registro,
                    e.fecha_limite_entrega,
                    ao.codigo_oaci AS origen,
                    ad.codigo_oaci AS destino
                FROM envio_maletas e
                JOIN aeropuerto ao ON ao.id_aeropuerto = e.id_aeropuerto_origen
                JOIN aeropuerto ad ON ad.id_aeropuerto = e.id_aeropuerto_destino
                WHERE e.fecha_registro >= ?
                  AND e.fecha_registro < ?
                ORDER BY e.fecha_registro
                """, inicioFiltro, finFiltro);

        Map<Integer, Integer> ocupacionPorVuelo = new HashMap<>();
        List<Map<String, Object>> eventos = new ArrayList<>();

        int totalEnvios = envios.size();
        int asignados = 0;
        int noAsignados = 0;
        int totalMaletas = 0;
        int maletasAsignadas = 0;
        int maletasNoAsignadas = 0;

        for (Map<String, Object> envio : envios) {
            Integer idEnvio = getInt(envio.get("id_envio"));
            Integer idOrigen = getInt(envio.get("id_aeropuerto_origen"));
            Integer idDestino = getInt(envio.get("id_aeropuerto_destino"));
            Integer cantidad = getInt(envio.get("cantidad"));

            String origen = String.valueOf(envio.get("origen"));
            String destino = String.valueOf(envio.get("destino"));

            LocalDateTime fechaRegistro = toLocalDateTime(envio.get("fecha_registro"));
            LocalDateTime fechaLimite = toLocalDateTime(envio.get("fecha_limite_entrega"));

            totalMaletas += cantidad;

            List<Map<String, Object>> vuelosCandidatos = jdbcTemplate.queryForList("""
                    SELECT 
                        v.id_vuelo,
                        v.codigo_vuelo,
                        v.hora_salida,
                        v.hora_llegada,
                        v.capacidad_maxima,
                        ao.codigo_oaci AS origen,
                        ad.codigo_oaci AS destino
                    FROM vuelo v
                    JOIN aeropuerto ao ON ao.id_aeropuerto = v.id_aeropuerto_origen
                    JOIN aeropuerto ad ON ad.id_aeropuerto = v.id_aeropuerto_destino
                    WHERE v.id_aeropuerto_origen = ?
                      AND v.id_aeropuerto_destino = ?
                      AND v.hora_salida >= ?
                      AND v.hora_llegada <= ?
                      AND v.estado = 'PROGRAMADO'
                    ORDER BY v.hora_llegada ASC
                    """, idOrigen, idDestino, fechaRegistro, fechaLimite);

            Map<String, Object> vueloElegido = null;

            for (Map<String, Object> vuelo : vuelosCandidatos) {
                Integer idVuelo = getInt(vuelo.get("id_vuelo"));
                Integer capacidadMaxima = getInt(vuelo.get("capacidad_maxima"));
                Integer ocupacionActual = ocupacionPorVuelo.getOrDefault(idVuelo, 0);

                if (ocupacionActual + cantidad <= capacidadMaxima) {
                    vueloElegido = vuelo;
                    ocupacionPorVuelo.put(idVuelo, ocupacionActual + cantidad);
                    break;
                }
            }

            if (vueloElegido != null) {
                asignados++;
                maletasAsignadas += cantidad;

                Map<String, Object> evento = new LinkedHashMap<>();
                evento.put("idEnvio", idEnvio);
                evento.put("origen", origen);
                evento.put("destino", destino);
                evento.put("cantidad", cantidad);
                evento.put("estado", "ASIGNADO");
                evento.put("codigoVuelo", vueloElegido.get("codigo_vuelo"));
                evento.put("horaSalida", vueloElegido.get("hora_salida"));
                evento.put("horaLlegada", vueloElegido.get("hora_llegada"));

                eventos.add(evento);
            } else {
                noAsignados++;
                maletasNoAsignadas += cantidad;

                Map<String, Object> evento = new LinkedHashMap<>();
                evento.put("idEnvio", idEnvio);
                evento.put("origen", origen);
                evento.put("destino", destino);
                evento.put("cantidad", cantidad);
                evento.put("estado", "NO_ASIGNADO");
                evento.put("motivo", "No se encontró vuelo directo disponible dentro del SLA");

                eventos.add(evento);
            }
        }

        Map<String, Object> resumen = new LinkedHashMap<>();
        resumen.put("fechaInicio", fechaInicio.toString());
        resumen.put("dias", dias);
        resumen.put("totalEnvios", totalEnvios);
        resumen.put("enviosAsignados", asignados);
        resumen.put("enviosNoAsignados", noAsignados);
        resumen.put("totalMaletas", totalMaletas);
        resumen.put("maletasAsignadas", maletasAsignadas);
        resumen.put("maletasNoAsignadas", maletasNoAsignadas);
        resumen.put("vuelosUtilizados", ocupacionPorVuelo.size());

        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("resumen", resumen);
        respuesta.put("eventos", eventos);

        return respuesta;
    }

    private Integer getInt(Object value) {
        return value == null ? 0 : ((Number) value).intValue();
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }

        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }

        throw new IllegalArgumentException("No se pudo convertir fecha: " + value);
    }
}