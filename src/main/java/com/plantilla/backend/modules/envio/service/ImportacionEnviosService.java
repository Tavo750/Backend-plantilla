package com.plantilla.backend.modules.envio.service;

import com.plantilla.backend.modules.maestro.entity.Aeropuerto;
import com.plantilla.backend.modules.maestro.repository.AeropuertoRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
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

    private final ResourcePatternResolver resourceLoader;
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

    // ── Importación multi-aeropuerto ─────────────────────────────────────────

    /**
     * Importa los envíos de TODOS los archivos en classpath:data/_envios_preliminar_/
     * para el rango indicado. Antes de insertar, limpia en cascada los registros
     * existentes del rango para garantizar idempotencia.
     */
    @Transactional
    public Map<String, Object> importarTodosLosEnvios(LocalDate fechaInicio, int dias) {
        if (dias <= 0) {
            throw new IllegalArgumentException("La cantidad de días debe ser mayor a 0.");
        }

        // Rango UTC con margen de 1 día a cada lado para cubrir desfases de zona horaria
        LocalDateTime desdeUtc = fechaInicio.minusDays(1).atStartOfDay();
        LocalDateTime hastaUtc = fechaInicio.plusDays(dias + 1).atStartOfDay();

        limpiarEnviosEnRango(desdeUtc, hastaUtc);

        Resource[] archivos;
        try {
            archivos = resourceLoader.getResources(
                    "classpath:data/_envios_preliminar_/_envios_*.txt");
        } catch (IOException e) {
            throw new RuntimeException("Error al escanear archivos de envíos: " + e.getMessage(), e);
        }

        if (archivos.length == 0) {
            throw new IllegalStateException(
                    "No se encontraron archivos en classpath:data/_envios_preliminar_/");
        }

        int totalInsertados   = 0;
        int totalLeidas       = 0;
        int totalOmitidas     = 0;
        int totalFueraDeRango = 0;
        List<String> procesados = new ArrayList<>();
        List<String> omitidos   = new ArrayList<>();

        for (Resource archivo : archivos) {
            String nombre = archivo.getFilename();
            if (nombre == null) continue;
            String oaci = extraerOaci(nombre);
            if (oaci == null) continue;

            try {
                Map<String, Object> res = importarEnvios(
                        "_envios_preliminar_/" + nombre, oaci, fechaInicio, dias);
                totalInsertados   += (int) res.get("enviosInsertados");
                totalLeidas       += (int) res.get("lineasLeidas");
                totalOmitidas     += (int) res.get("lineasOmitidas");
                totalFueraDeRango += (int) res.get("fueraDeRango");
                procesados.add(oaci);
            } catch (IllegalStateException e) {
                // Aeropuerto no registrado en BD o archivo no encontrado: se omite
                omitidos.add(oaci + ": " + e.getMessage());
            }
        }

        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("fechaInicio", fechaInicio.toString());
        resultado.put("dias", dias);
        resultado.put("aeropuertosProcesados", procesados.size());
        resultado.put("aeropuertos", procesados);
        resultado.put("aeropuertosOmitidos", omitidos);
        resultado.put("totalLineasLeidas", totalLeidas);
        resultado.put("totalEnviosInsertados", totalInsertados);
        resultado.put("totalFueraDeRango", totalFueraDeRango);
        resultado.put("totalLineasOmitidas", totalOmitidas);
        return resultado;
    }

    private void limpiarEnviosEnRango(LocalDateTime desde, LocalDateTime hasta) {
        String ids = "SELECT id_envio FROM envio_maletas WHERE fecha_registro >= ? AND fecha_registro < ?";

        jdbcTemplate.update(
                "DELETE FROM envio_replanificacion WHERE id_envio IN (" + ids + ")",
                desde, hasta);
        jdbcTemplate.update(
                "DELETE FROM asignacion_vuelo WHERE id_envio IN (" + ids + ")",
                desde, hasta);
        jdbcTemplate.update(
                "DELETE FROM tramo_ruta WHERE id_plan_ruta IN " +
                "(SELECT id_plan_ruta FROM plan_ruta WHERE id_envio IN (" + ids + "))",
                desde, hasta);
        jdbcTemplate.update(
                "DELETE FROM plan_ruta WHERE id_envio IN (" + ids + ")",
                desde, hasta);
        jdbcTemplate.update(
                "DELETE FROM ubicacion_envio WHERE id_envio IN (" + ids + ")",
                desde, hasta);
        jdbcTemplate.update(
                "DELETE FROM envio_maletas WHERE fecha_registro >= ? AND fecha_registro < ?",
                desde, hasta);
    }

    private String extraerOaci(String nombre) {
        // "_envios_SBBR_.txt" → "SBBR"
        int ini = nombre.indexOf("_envios_");
        if (ini < 0) return null;
        ini += 8;
        int fin = nombre.lastIndexOf("_");
        if (fin <= ini) return null;
        return nombre.substring(ini, fin).toUpperCase();
    }
}