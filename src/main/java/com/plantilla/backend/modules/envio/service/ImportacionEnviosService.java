package com.plantilla.backend.modules.envio.service;

import com.plantilla.backend.modules.maestro.entity.Aeropuerto;
import com.plantilla.backend.modules.maestro.repository.AeropuertoRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ImportacionEnviosService {

    private final ResourceLoader resourceLoader;
    private final AeropuertoRepository aeropuertoRepository;
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public Map<String, Object> importarEnvios(
            String nombreArchivo,
            String codigoOrigen,
            LocalDate fechaInicio,
            int dias
    ) {
        if (dias <= 0) {
            throw new IllegalArgumentException("La cantidad de días debe ser mayor a 0.");
        }

        Resource resource = resourceLoader.getResource("classpath:data/" + nombreArchivo);

        if (!resource.exists()) {
            throw new IllegalStateException("No se encontró el archivo src/main/resources/data/" + nombreArchivo);
        }

        Map<String, Aeropuerto> aeropuertosPorOaci = aeropuertoRepository.findAll()
                .stream()
                .collect(Collectors.toMap(
                        a -> a.getCodigoOaci().toUpperCase(),
                        a -> a
                ));

        Aeropuerto origen = aeropuertosPorOaci.get(codigoOrigen.toUpperCase());

        if (origen == null) {
            throw new IllegalStateException("No existe el aeropuerto origen: " + codigoOrigen);
        }

        Integer idAerolinea = obtenerIdAerolineaDemo();
        Integer idPolitica = obtenerIdPoliticaActiva();

        LocalDate fechaFin = fechaInicio.plusDays(dias);

        int lineasLeidas = 0;
        int lineasOmitidas = 0;
        int fueraDeRango = 0;

        List<Object[]> enviosBatch = new ArrayList<>();

        DateTimeFormatter formatterFecha = DateTimeFormatter.BASIC_ISO_DATE;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {

            String linea;

            while ((linea = reader.readLine()) != null) {
                linea = linea.trim();

                if (linea.isBlank() || linea.startsWith("//")) {
                    continue;
                }

                lineasLeidas++;

                String[] partes = linea.split("-");

                if (partes.length != 7) {
                    lineasOmitidas++;
                    continue;
                }

                LocalDate fechaLocal = LocalDate.parse(partes[1].trim(), formatterFecha);

                if (fechaLocal.isBefore(fechaInicio) || !fechaLocal.isBefore(fechaFin)) {
                    fueraDeRango++;
                    continue;
                }

                int hora = Integer.parseInt(partes[2].trim());
                int minuto = Integer.parseInt(partes[3].trim());
                String codigoDestino = partes[4].trim().toUpperCase();
                int cantidad = Integer.parseInt(partes[5].trim());

                Aeropuerto destino = aeropuertosPorOaci.get(codigoDestino);

                if (destino == null) {
                    lineasOmitidas++;
                    continue;
                }

                LocalTime horaRegistrada = LocalTime.of(hora, minuto);
                LocalDateTime fechaRegistroUtc = convertirHoraLocalAUtc(fechaLocal, horaRegistrada, origen.getGmt());

                int diasSla = origen.getContinente().equals(destino.getContinente()) ? 1 : 2;
                LocalDateTime fechaLimiteEntrega = fechaRegistroUtc.plusDays(diasSla);

                enviosBatch.add(new Object[]{
                        idAerolinea,
                        origen.getIdAeropuerto(),
                        destino.getIdAeropuerto(),
                        idPolitica,
                        cantidad,
                        1,
                        "REGISTRADA",
                        fechaRegistroUtc,
                        fechaLimiteEntrega,
                        horaRegistrada
                });
            }

        } catch (Exception e) {
            throw new RuntimeException("Error al leer envíos: " + e.getMessage(), e);
        }

        String sql = """
                INSERT INTO envio_maletas (
                    id_aerolinea,
                    id_aeropuerto_origen,
                    id_aeropuerto_destino,
                    id_politica,
                    cantidad,
                    prioridad,
                    estado,
                    fecha_registro,
                    fecha_limite_entrega,
                    hora_registrada
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        int enviosInsertados = 0;

        try {
            int[] resultados = jdbcTemplate.batchUpdate(sql, enviosBatch);

            for (int resultado : resultados) {
                if (resultado > 0 || resultado == Statement.SUCCESS_NO_INFO) {
                    enviosInsertados++;
                }
            }

        } catch (Exception e) {
            throw new RuntimeException("Error al insertar envíos en lote: " + e.getMessage(), e);
        }

        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("archivo", nombreArchivo);
        resultado.put("origen", codigoOrigen);
        resultado.put("fechaInicio", fechaInicio.toString());
        resultado.put("dias", dias);
        resultado.put("lineasLeidas", lineasLeidas);
        resultado.put("enviosPreparados", enviosBatch.size());
        resultado.put("enviosInsertados", enviosInsertados);
        resultado.put("fueraDeRango", fueraDeRango);
        resultado.put("lineasOmitidas", lineasOmitidas);

        return resultado;
    }

    private Integer obtenerIdAerolineaDemo() {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT id_aerolinea FROM aerolinea WHERE codigo = 'AERO_DEMO' LIMIT 1",
                    Integer.class
            );
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalStateException("No existe la aerolínea AERO_DEMO. Ejecuta primero el INSERT base.");
        }
    }

    private Integer obtenerIdPoliticaActiva() {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT id_politica FROM politica_entrega WHERE activa = true ORDER BY id_politica LIMIT 1",
                    Integer.class
            );
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalStateException("No existe una política de entrega activa. Ejecuta primero el INSERT base.");
        }
    }

    private LocalDateTime convertirHoraLocalAUtc(LocalDate fecha, LocalTime horaLocal, Integer gmt) {
        LocalDateTime fechaHoraLocal = LocalDateTime.of(fecha, horaLocal);
        return fechaHoraLocal.minusHours(gmt);
    }
}