package com.plantilla.backend.modules.maestro.service;

import com.plantilla.backend.modules.maestro.entity.Aeropuerto;
import com.plantilla.backend.modules.maestro.repository.AeropuertoRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ImportacionVuelosService {

    private final AeropuertoRepository aeropuertoRepository;
    private final ResourceLoader resourceLoader;
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public Map<String, Object> importarPlanesVuelo(LocalDate fechaInicio, int dias) {
        if (dias <= 0) {
            throw new IllegalArgumentException("La cantidad de días debe ser mayor a 0.");
        }

        Resource resource = resourceLoader.getResource("classpath:data/planes_vuelo.txt");

        if (!resource.exists()) {
            throw new IllegalStateException("No se encontró el archivo src/main/resources/data/planes_vuelo.txt");
        }

        Map<String, Aeropuerto> aeropuertosPorOaci = aeropuertoRepository.findAll()
                .stream()
                .collect(Collectors.toMap(
                        a -> a.getCodigoOaci().toUpperCase(),
                        a -> a
                ));

        int lineasLeidas = 0;
        int lineasOmitidas = 0;

        List<Object[]> vuelosBatch = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {

            String linea;
            int numeroLinea = 0;

            while ((linea = reader.readLine()) != null) {
                numeroLinea++;
                linea = linea.trim();

                if (linea.isBlank() || linea.startsWith("//")) {
                    continue;
                }

                lineasLeidas++;

                String[] partes = linea.split("-");

                if (partes.length != 5) {
                    lineasOmitidas++;
                    continue;
                }

                String codigoOrigen = partes[0].trim().toUpperCase();
                String codigoDestino = partes[1].trim().toUpperCase();
                LocalTime horaSalidaLocal = LocalTime.parse(partes[2].trim());
                LocalTime horaLlegadaLocal = LocalTime.parse(partes[3].trim());
                Integer capacidad = Integer.parseInt(partes[4].trim());

                Aeropuerto origen = aeropuertosPorOaci.get(codigoOrigen);
                Aeropuerto destino = aeropuertosPorOaci.get(codigoDestino);

                if (origen == null || destino == null) {
                    lineasOmitidas++;
                    continue;
                }

                for (int i = 0; i < dias; i++) {
                    LocalDate fechaOperacion = fechaInicio.plusDays(i);

                    LocalDateTime salidaUtc = convertirHoraLocalAUtc(
                            fechaOperacion,
                            horaSalidaLocal,
                            origen.getGmt()
                    );

                    LocalDateTime llegadaUtc = convertirHoraLocalAUtc(
                            fechaOperacion,
                            horaLlegadaLocal,
                            destino.getGmt()
                    );

                    while (!llegadaUtc.isAfter(salidaUtc)) {
                        llegadaUtc = llegadaUtc.plusDays(1);
                    }

                    String codigoVuelo = generarCodigoVuelo(
                            codigoOrigen,
                            codigoDestino,
                            fechaOperacion,
                            horaSalidaLocal,
                            numeroLinea
                    );

                    BigDecimal duracionHoras = calcularDuracionHoras(salidaUtc, llegadaUtc);
                    boolean esIntercontinental = !origen.getContinente().equals(destino.getContinente());

                    vuelosBatch.add(new Object[]{
                            codigoVuelo,
                            origen.getIdAeropuerto(),
                            destino.getIdAeropuerto(),
                            salidaUtc,
                            llegadaUtc,
                            duracionHoras,
                            capacidad,
                            "PROGRAMADO",
                            esIntercontinental
                    });
                }
            }

        } catch (Exception e) {
            throw new RuntimeException("Error al leer planes de vuelo: " + e.getMessage(), e);
        }

        String sql = """
                INSERT INTO vuelo (
                    codigo_vuelo,
                    id_aeropuerto_origen,
                    id_aeropuerto_destino,
                    hora_salida,
                    hora_llegada,
                    duracion_horas,
                    capacidad_maxima,
                    estado,
                    es_intercontinental
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (codigo_vuelo) DO NOTHING
                """;

        int vuelosInsertados = 0;
        int vuelosDuplicados = 0;

        try {
            int[] resultados = jdbcTemplate.batchUpdate(sql, vuelosBatch);

            for (int resultado : resultados) {
                if (resultado > 0 || resultado == Statement.SUCCESS_NO_INFO) {
                    vuelosInsertados++;
                } else {
                    vuelosDuplicados++;
                }
            }

        } catch (Exception e) {
            throw new RuntimeException("Error al insertar vuelos en lote: " + e.getMessage(), e);
        }

        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("fechaInicio", fechaInicio.toString());
        resultado.put("dias", dias);
        resultado.put("lineasLeidas", lineasLeidas);
        resultado.put("vuelosPreparados", vuelosBatch.size());
        resultado.put("vuelosInsertados", vuelosInsertados);
        resultado.put("vuelosDuplicados", vuelosDuplicados);
        resultado.put("lineasOmitidas", lineasOmitidas);

        return resultado;
    }

    private LocalDateTime convertirHoraLocalAUtc(LocalDate fecha, LocalTime horaLocal, Integer gmt) {
        LocalDateTime fechaHoraLocal = LocalDateTime.of(fecha, horaLocal);
        return fechaHoraLocal.minusHours(gmt);
    }

    private BigDecimal calcularDuracionHoras(LocalDateTime salidaUtc, LocalDateTime llegadaUtc) {
        long minutos = Duration.between(salidaUtc, llegadaUtc).toMinutes();

        return BigDecimal.valueOf(minutos)
                .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }

    private String generarCodigoVuelo(
            String origen,
            String destino,
            LocalDate fecha,
            LocalTime horaSalida,
            int numeroLinea
    ) {
        return origen + "-" +
                destino + "-" +
                fecha.format(DateTimeFormatter.BASIC_ISO_DATE) + "-" +
                horaSalida.toString().replace(":", "") + "-" +
                String.format("%04d", numeroLinea);
    }
}