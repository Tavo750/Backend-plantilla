package com.plantilla.backend.modules.maestro.controller;

import com.plantilla.backend.modules.maestro.dto.VueloCreateDTO;
import com.plantilla.backend.modules.maestro.entity.Aeropuerto;
import com.plantilla.backend.modules.maestro.entity.PlanVueloDiario;
import com.plantilla.backend.modules.maestro.entity.Vuelo;
import com.plantilla.backend.modules.maestro.repository.AeropuertoRepository;
import com.plantilla.backend.modules.maestro.repository.PlanVueloDiarioRepository;
import com.plantilla.backend.modules.maestro.repository.VueloRepository;
import com.plantilla.backend.modules.simulacion.service.CancelacionVueloService;
import com.plantilla.backend.shared.dto.ApiResponse;
import com.plantilla.backend.shared.enums.EstadoVuelo;
import com.plantilla.backend.shared.errors.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.time.LocalDate;
import java.time.LocalDateTime;
@RestController
@RequestMapping("/maestro/vuelos")
@RequiredArgsConstructor
@Tag(name = "Vuelos", description = "Gestión de vuelos")
public class VueloController {

    private final VueloRepository vueloRepository;
    private final AeropuertoRepository aeropuertoRepository;
    private final PlanVueloDiarioRepository planVueloDiarioRepository;
    private final CancelacionVueloService cancelacionVueloService;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    // ── CRUD ─────────────────────────────────────────────────────

    @GetMapping
    @Operation(
            summary = "Listar vuelos vigentes",
            description = "Obtiene los vuelos con salida entre ayer y 3 días adelante (UTC). "
                    + "La ventana ampliada evita que cargas nuevas queden ocultas por desfase de huso."
    )
    public ResponseEntity<ApiResponse<List<Vuelo>>> listarVuelos() {

        // Ventana en UTC (todo el sistema guarda hora_salida en UTC): desde ayer (cubre
        // husos negativos y vuelos aún en el aire) hasta +3 días (cargas de la prueba).
        LocalDate fechaActual = LocalDate.now(java.time.ZoneOffset.UTC);

        LocalDateTime inicioDia =
                fechaActual.minusDays(1).atStartOfDay();

        LocalDateTime inicioDiaSiguiente =
                fechaActual.plusDays(4).atStartOfDay();

        List<Vuelo> vuelos =
                vueloRepository
                        .findByHoraSalidaGreaterThanEqualAndHoraSalidaLessThanOrderByHoraSalidaAsc(
                                inicioDia,
                                inicioDiaSiguiente
                        );

        return ResponseEntity.ok(
                ApiResponse.success(vuelos)
        );
    }

    @GetMapping("/origen/{idAeropuerto}")
    @Transactional(Transactional.TxType.REQUIRED)
    @Operation(summary = "Listar vuelos por aeropuerto origen")
    public ResponseEntity<ApiResponse<List<Vuelo>>> listarPorOrigen(
            @PathVariable Integer idAeropuerto
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        vueloRepository
                                .findByAeropuertoOrigenIdAeropuerto(
                                        idAeropuerto
                                )
                )
        );
    }

    @GetMapping("/{id}")
    @Transactional(Transactional.TxType.REQUIRED)
    @Operation(summary = "Obtener vuelo por ID")
    public ResponseEntity<ApiResponse<Vuelo>> obtenerVuelo(
            @PathVariable Integer id
    ) {
        Vuelo vuelo = vueloRepository.findById(id)
                .orElseThrow(() ->
                        new BusinessException(
                                "No existe el vuelo con id: " + id
                        )
                );

        return ResponseEntity.ok(
                ApiResponse.success(vuelo)
        );
    }

    // ── CREAR ────────────────────────────────────────────────────

    @PostMapping
    @Transactional
    @Operation(summary = "Crear vuelo")
    public ResponseEntity<ApiResponse<Vuelo>> crearVuelo(
            @RequestBody VueloCreateDTO dto
    ) {
        if (vueloRepository.existsByCodigoVuelo(dto.getCodigoVuelo())) {
            throw new BusinessException(
                    "Ya existe un vuelo con el código: "
                            + dto.getCodigoVuelo()
            );
        }

        Aeropuerto origen = aeropuertoRepository
                .findById(dto.getIdAeropuertoOrigen())
                .orElseThrow(() ->
                        new BusinessException(
                                "Aeropuerto origen no encontrado"
                        )
                );

        Aeropuerto destino = aeropuertoRepository
                .findById(dto.getIdAeropuertoDestino())
                .orElseThrow(() ->
                        new BusinessException(
                                "Aeropuerto destino no encontrado"
                        )
                );

        Vuelo vuelo = new Vuelo();
        vuelo.setCodigoVuelo(dto.getCodigoVuelo());
        vuelo.setAeropuertoOrigen(origen);
        vuelo.setAeropuertoDestino(destino);
        vuelo.setHoraSalida(dto.getHoraSalida());
        vuelo.setHoraLlegada(dto.getHoraLlegada());
        vuelo.setDuracionHoras(dto.getDuracionHoras());
        vuelo.setCapacidadMaxima(dto.getCapacidadMaxima());

        vuelo.setEstado(
                dto.getEstado() != null
                        ? dto.getEstado()
                        : EstadoVuelo.PROGRAMADO
        );

        vuelo.setEsIntercontinental(
                dto.getEsIntercontinental() != null
                        ? dto.getEsIntercontinental()
                        : false
        );

        /*
         * 1. Se guarda el vuelo original en la tabla vuelo.
         */
        Vuelo vueloGuardado = vueloRepository.save(vuelo);

        /*
         * 2. Con los mismos datos se crea la fila equivalente
         *    en plan_vuelo_diario.
         *
         *    No se guarda ninguna relación entre ambas tablas.
         */
        PlanVueloDiario planVueloDiario =
                construirPlanVueloDiario(
                        origen,
                        destino,
                        vueloGuardado
                );

        planVueloDiarioRepository.save(planVueloDiario);

        return ResponseEntity.ok(
                ApiResponse.created(vueloGuardado)
        );
    }

    // ── ACTUALIZAR ───────────────────────────────────────────────

    @PutMapping("/{id}")
    @Transactional
    @Operation(summary = "Actualizar vuelo")
    public ResponseEntity<ApiResponse<Vuelo>> actualizarVuelo(
            @PathVariable Integer id,
            @RequestBody VueloCreateDTO dto
    ) {
        Vuelo vuelo = vueloRepository.findById(id)
                .orElseThrow(() ->
                        new BusinessException(
                                "No existe el vuelo con id: " + id
                        )
                );

        /*
         * Guardamos los datos anteriores porque después de modificar
         * el vuelo los necesitaremos para encontrar el plan viejo.
         */
        String codigoOrigenAnterior =
                vuelo.getAeropuertoOrigen().getCodigoOaci();

        String codigoDestinoAnterior =
                vuelo.getAeropuertoDestino().getCodigoOaci();

        var horaSalidaAnterior =
                vuelo.getHoraSalida().toLocalTime();

        var horaLlegadaAnterior =
                vuelo.getHoraLlegada().toLocalTime();

        Integer capacidadAnterior =
                vuelo.getCapacidadMaxima();

        /*
         * Buscar el plan equivalente usando los valores originales.
         */
        Optional<PlanVueloDiario> planExistente =
                planVueloDiarioRepository
                        .findFirstByCodigoOrigenAndCodigoDestinoAndHoraSalidaAndHoraLlegadaAndCapacidad(
                                codigoOrigenAnterior,
                                codigoDestinoAnterior,
                                horaSalidaAnterior,
                                horaLlegadaAnterior,
                                capacidadAnterior
                        );

        if (dto.getIdAeropuertoOrigen() != null) {
            Aeropuerto nuevoOrigen = aeropuertoRepository
                    .findById(dto.getIdAeropuertoOrigen())
                    .orElseThrow(() ->
                            new BusinessException(
                                    "Aeropuerto origen no encontrado"
                            )
                    );

            vuelo.setAeropuertoOrigen(nuevoOrigen);
        }

        if (dto.getIdAeropuertoDestino() != null) {
            Aeropuerto nuevoDestino = aeropuertoRepository
                    .findById(dto.getIdAeropuertoDestino())
                    .orElseThrow(() ->
                            new BusinessException(
                                    "Aeropuerto destino no encontrado"
                            )
                    );

            vuelo.setAeropuertoDestino(nuevoDestino);
        }

        if (dto.getHoraSalida() != null) {
            vuelo.setHoraSalida(dto.getHoraSalida());
        }

        if (dto.getHoraLlegada() != null) {
            vuelo.setHoraLlegada(dto.getHoraLlegada());
        }

        if (dto.getDuracionHoras() != null) {
            vuelo.setDuracionHoras(dto.getDuracionHoras());
        }

        if (dto.getCapacidadMaxima() != null) {
            vuelo.setCapacidadMaxima(dto.getCapacidadMaxima());
        }

        if (dto.getEstado() != null) {
            vuelo.setEstado(dto.getEstado());
        }

        if (dto.getEsIntercontinental() != null) {
            vuelo.setEsIntercontinental(
                    dto.getEsIntercontinental()
            );
        }

        /*
         * Guardar cambios en vuelo.
         */
        Vuelo vueloActualizado =
                vueloRepository.save(vuelo);

        /*
         * Si se encontró el plan anterior, se actualiza esa fila.
         *
         * Si no se encontró, se crea una nueva. Esto permite editar
         * también vuelos antiguos que todavía no tenían copia en
         * plan_vuelo_diario.
         */
        PlanVueloDiario planVueloDiario =
                planExistente.orElseGet(
                        PlanVueloDiario::new
                );

        copiarDatosAlPlan(
                planVueloDiario,
                vueloActualizado
        );

        planVueloDiarioRepository.save(planVueloDiario);

        return ResponseEntity.ok(
                ApiResponse.success(vueloActualizado)
        );
    }

    // ── ELIMINAR ─────────────────────────────────────────────────

    @DeleteMapping("/{id}")
    @Transactional
    @Operation(summary = "Eliminar vuelo")
    public ResponseEntity<ApiResponse<Void>> eliminarVuelo(
            @PathVariable Integer id
    ) {
        Vuelo vuelo = vueloRepository.findById(id)
                .orElseThrow(() ->
                        new BusinessException(
                                "No existe el vuelo con id: " + id
                        )
                );

        /*
         * Buscar la fila equivalente antes de borrar el vuelo.
         */
        Optional<PlanVueloDiario> planExistente =
                planVueloDiarioRepository
                        .findFirstByCodigoOrigenAndCodigoDestinoAndHoraSalidaAndHoraLlegadaAndCapacidad(
                                vuelo.getAeropuertoOrigen()
                                        .getCodigoOaci(),

                                vuelo.getAeropuertoDestino()
                                        .getCodigoOaci(),

                                vuelo.getHoraSalida()
                                        .toLocalTime(),

                                vuelo.getHoraLlegada()
                                        .toLocalTime(),

                                vuelo.getCapacidadMaxima()
                        );

        /*
         * Eliminar el vuelo original.
         */
        vueloRepository.delete(vuelo);

        /*
         * Eliminar también la fila equivalente del plan diario.
         */
        planExistente.ifPresent(
                planVueloDiarioRepository::delete
        );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Vuelo eliminado",
                        null
                )
        );
    }

    @PostMapping("/eliminar-masivo")
    @Transactional
    @Operation(summary = "Eliminar vuelos en lote",
            description = "Elimina todos los vuelos cuyos ids se envíen en el cuerpo. "
                    + "También borra la fila equivalente del plan diario de cada uno.")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> eliminarVuelosMasivo(
            @RequestBody java.util.List<Integer> ids
    ) {
        int eliminados = 0;
        java.util.List<Integer> noEncontrados = new java.util.ArrayList<>();
        for (Integer id : (ids != null ? ids : java.util.List.<Integer>of())) {
            Optional<Vuelo> vueloOpt = vueloRepository.findById(id);
            if (vueloOpt.isEmpty()) { noEncontrados.add(id); continue; }
            Vuelo vuelo = vueloOpt.get();
            Optional<PlanVueloDiario> planExistente = planVueloDiarioRepository
                    .findFirstByCodigoOrigenAndCodigoDestinoAndHoraSalidaAndHoraLlegadaAndCapacidad(
                            vuelo.getAeropuertoOrigen().getCodigoOaci(),
                            vuelo.getAeropuertoDestino().getCodigoOaci(),
                            vuelo.getHoraSalida().toLocalTime(),
                            vuelo.getHoraLlegada().toLocalTime(),
                            vuelo.getCapacidadMaxima());
            vueloRepository.delete(vuelo);
            planExistente.ifPresent(planVueloDiarioRepository::delete);
            eliminados++;
        }
        java.util.Map<String, Object> r = new java.util.LinkedHashMap<>();
        r.put("eliminados", eliminados);
        r.put("noEncontrados", noEncontrados);
        return ResponseEntity.ok(ApiResponse.success(eliminados + " vuelo(s) eliminado(s)", r));
    }

    // ── CARGA MASIVA (misma lógica que crear un vuelo, por lote) ──

    public record CargaMasivaRequest(List<VueloCreateDTO> vuelos) {}

    @PostMapping("/carga-masiva")
    @Transactional
    @Operation(summary = "Carga masiva de vuelos",
            description = "Crea cada vuelo del CSV y lo DUPLICA para todos los días del horizonte "
                    + "(hasta donde llega la data base), igual que un plan de vuelos diario. "
                    + "Todos comparten una 'fecha_carga' que identifica la tanda para poder eliminarla "
                    + "completa después. Las horas ya vienen en UTC.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> cargaMasiva(
            @RequestBody CargaMasivaRequest body
    ) {
        List<VueloCreateDTO> patrones = body.vuelos() != null ? body.vuelos() : List.of();
        LocalDateTime fechaCarga = LocalDateTime.now();

        // 1) Insertar los vuelos patrón (una fila por vuelo del CSV) + su fila de plan diario.
        int patronesCreados = 0;
        List<String> omitidos = new ArrayList<>();
        LocalDate minSalida = null;
        for (VueloCreateDTO v : patrones) {
            if (v.getCodigoVuelo() == null || vueloRepository.existsByCodigoVuelo(v.getCodigoVuelo())) {
                if (v.getCodigoVuelo() != null) omitidos.add(v.getCodigoVuelo());
                continue;
            }
            Aeropuerto origen = aeropuertoRepository.findById(v.getIdAeropuertoOrigen()).orElse(null);
            Aeropuerto destino = aeropuertoRepository.findById(v.getIdAeropuertoDestino()).orElse(null);
            if (origen == null || destino == null) continue;

            Vuelo vuelo = new Vuelo();
            vuelo.setCodigoVuelo(v.getCodigoVuelo());
            vuelo.setAeropuertoOrigen(origen);
            vuelo.setAeropuertoDestino(destino);
            vuelo.setHoraSalida(v.getHoraSalida());
            vuelo.setHoraLlegada(v.getHoraLlegada());
            vuelo.setDuracionHoras(v.getDuracionHoras());
            vuelo.setCapacidadMaxima(v.getCapacidadMaxima());
            vuelo.setEstado(v.getEstado() != null ? v.getEstado() : EstadoVuelo.PROGRAMADO);
            vuelo.setEsIntercontinental(Boolean.TRUE.equals(v.getEsIntercontinental()));
            vuelo.setFechaCarga(fechaCarga);   // etiqueta de la tanda

            Vuelo guardado = vueloRepository.save(vuelo);
            planVueloDiarioRepository.save(construirPlanVueloDiario(origen, destino, guardado));
            if (v.getHoraSalida() != null) {
                LocalDate d = v.getHoraSalida().toLocalDate();
                if (minSalida == null || d.isBefore(minSalida)) minSalida = d;
            }
            patronesCreados++;
        }

        // 2) Duplicar toda la tanda para cada día del horizonte (set-based, rápido).
        //    Cada duplicado regenera su código con la fecha/hora LOCAL del origen (gmt),
        //    igual que el código del vuelo patrón.
        int duplicados = 0;
        if (patronesCreados > 0 && minSalida != null) {
            LocalDate maxBase = obtenerFechaMaximaBase();
            long maxN = java.time.temporal.ChronoUnit.DAYS.between(minSalida, maxBase);
            if (maxN > 0) {
                jdbcTemplate.execute("SET SESSION cte_max_recursion_depth = 100000");
                duplicados = jdbcTemplate.update(
                        "INSERT INTO vuelo (codigo_vuelo, id_aeropuerto_origen, id_aeropuerto_destino, "
                      + "  hora_salida, hora_llegada, duracion_horas, capacidad_maxima, estado, es_intercontinental, fecha_carga) "
                      + "WITH RECURSIVE n AS ( SELECT 1 AS k UNION ALL SELECT k+1 FROM n WHERE k < ? ) "
                      + "SELECT CONCAT(o.codigo_oaci,'-',dst.codigo_oaci,'-', "
                      + "  DATE_FORMAT(DATE_ADD(v.hora_salida, INTERVAL n.k DAY) + INTERVAL o.gmt HOUR,'%Y%m%d'),'-', "
                      + "  DATE_FORMAT(DATE_ADD(v.hora_salida, INTERVAL n.k DAY) + INTERVAL o.gmt HOUR,'%H%i'),'-', "
                      + "  LPAD(v.capacidad_maxima,4,'0')), "
                      + "  v.id_aeropuerto_origen, v.id_aeropuerto_destino, "
                      + "  DATE_ADD(v.hora_salida, INTERVAL n.k DAY), DATE_ADD(v.hora_llegada, INTERVAL n.k DAY), "
                      + "  v.duracion_horas, v.capacidad_maxima, v.estado, v.es_intercontinental, v.fecha_carga "
                      + "FROM vuelo v "
                      + "JOIN aeropuerto o   ON v.id_aeropuerto_origen  = o.id_aeropuerto "
                      + "JOIN aeropuerto dst ON v.id_aeropuerto_destino = dst.id_aeropuerto "
                      + "CROSS JOIN n "
                      + "WHERE v.fecha_carga = ? AND DATE(DATE_ADD(v.hora_salida, INTERVAL n.k DAY)) <= ? "
                      + "ON DUPLICATE KEY UPDATE vuelo.id_vuelo = vuelo.id_vuelo",
                        maxN, java.sql.Timestamp.valueOf(fechaCarga), java.sql.Date.valueOf(maxBase));
            }
        }

        int creados = patronesCreados + duplicados;
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("creados", creados);
        r.put("patrones", patronesCreados);
        r.put("duplicados", duplicados);
        r.put("omitidos", omitidos.size());
        r.put("fechaCarga", fechaCarga.toString());
        return ResponseEntity.ok(ApiResponse.success(patronesCreados + " vuelos creados", r));
    }

    /** Fecha máxima hasta donde llega la data base (fecha_carga NULL); horizonte de duplicación. */
    private LocalDate obtenerFechaMaximaBase() {
        java.sql.Date max = jdbcTemplate.queryForObject(
                "SELECT MAX(DATE(hora_salida)) FROM vuelo WHERE fecha_carga IS NULL", java.sql.Date.class);
        return max != null ? max.toLocalDate()
                : LocalDate.now(java.time.ZoneOffset.UTC).plusDays(365);
    }

    @GetMapping("/cargas")
    @Operation(summary = "Listar tandas de carga masiva",
            description = "Lista las tandas (fecha_carga) con su cantidad de vuelos, la más reciente primero.")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listarCargas() {
        List<Map<String, Object>> cargas = jdbcTemplate.query(
                "SELECT fecha_carga, COUNT(*) AS total, MIN(DATE(hora_salida)) AS desde, MAX(DATE(hora_salida)) AS hasta "
                + "FROM vuelo WHERE fecha_carga IS NOT NULL GROUP BY fecha_carga ORDER BY fecha_carga DESC",
                (rs, i) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("fechaCarga", rs.getTimestamp("fecha_carga").toLocalDateTime().toString());
                    m.put("total", rs.getInt("total"));
                    m.put("desde", rs.getString("desde"));
                    m.put("hasta", rs.getString("hasta"));
                    return m;
                });
        return ResponseEntity.ok(ApiResponse.success(cargas));
    }

    @DeleteMapping("/carga")
    @Transactional
    @Operation(summary = "Eliminar una tanda de carga completa",
            description = "Borra todos los vuelos de una fecha_carga (una tanda). No afecta la data base (fecha_carga NULL).")
    public ResponseEntity<ApiResponse<Map<String, Object>>> eliminarCarga(
            @RequestParam String fechaCarga
    ) {
        LocalDateTime fc = LocalDateTime.parse(fechaCarga);
        List<Vuelo> vuelos = vueloRepository.findByFechaCarga(fc);
        // Borra también la fila de plantilla de cada uno (igual que el borrado individual)
        for (Vuelo v : vuelos) {
            planVueloDiarioRepository
                    .findFirstByCodigoOrigenAndCodigoDestinoAndHoraSalidaAndHoraLlegadaAndCapacidad(
                            v.getAeropuertoOrigen().getCodigoOaci(),
                            v.getAeropuertoDestino().getCodigoOaci(),
                            v.getHoraSalida().toLocalTime(),
                            v.getHoraLlegada().toLocalTime(),
                            v.getCapacidadMaxima())
                    .ifPresent(planVueloDiarioRepository::delete);
        }
        vueloRepository.deleteAll(vuelos);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("eliminados", vuelos.size());
        return ResponseEntity.ok(ApiResponse.success(vuelos.size() + " vuelo(s) de la tanda eliminados", r));
    }

    // ── CANCELAR / REACTIVAR ─────────────────────────────────────

    @PatchMapping("/{codigoVuelo}/cancelar")
    @Operation(
            summary = "Cancelar un vuelo",
            description =
                    "Marca el vuelo como CANCELADO. Al re-ejecutar "
                            + "la simulación, el ALNS excluirá este vuelo "
                            + "y re-rutará los envíos afectados automáticamente."
    )
    public ResponseEntity<ApiResponse<Map<String, Object>>> cancelarVuelo(
            @PathVariable String codigoVuelo
    ) {
        Vuelo vuelo = vueloRepository
                .findByCodigoVuelo(codigoVuelo)
                .orElseThrow(() ->
                        new BusinessException(
                                "No existe el vuelo con código: "
                                        + codigoVuelo
                        )
                );

        EstadoVuelo estadoAnterior =
                vuelo.getEstado();

        vuelo.setEstado(EstadoVuelo.CANCELADO);
        vueloRepository.save(vuelo);

        Map<String, Object> resultado =
                new LinkedHashMap<>();

        resultado.put(
                "codigoVuelo",
                codigoVuelo
        );

        resultado.put(
                "estadoAnterior",
                estadoAnterior
        );

        resultado.put(
                "estadoActual",
                EstadoVuelo.CANCELADO
        );

        try {
            Map<String, Object> replan =
                    cancelacionVueloService
                            .cancelarVueloYReplanificar(
                                    vuelo.getIdVuelo()
                            );

            resultado.put(
                    "replanificacion",
                    replan
            );

            resultado.put(
                    "mensaje",
                    "Vuelo cancelado. Maletas marcadas para "
                            + "replanificación en el próximo ciclo."
            );

        } catch (Exception e) {
            resultado.put(
                    "mensaje",
                    "Vuelo cancelado. Re-ejecuta la simulación "
                            + "para re-rutear los envíos afectados."
            );
        }

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Vuelo cancelado correctamente",
                        resultado
                )
        );
    }

    @PatchMapping("/{codigoVuelo}/reactivar")
    @Operation(summary = "Reactivar un vuelo cancelado")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reactivarVuelo(
            @PathVariable String codigoVuelo
    ) {
        Vuelo vuelo = vueloRepository
                .findByCodigoVuelo(codigoVuelo)
                .orElseThrow(() ->
                        new BusinessException(
                                "No existe el vuelo con código: "
                                        + codigoVuelo
                        )
                );

        vuelo.setEstado(EstadoVuelo.PROGRAMADO);
        vueloRepository.save(vuelo);

        Map<String, Object> resultado =
                new LinkedHashMap<>();

        resultado.put(
                "codigoVuelo",
                codigoVuelo
        );

        resultado.put(
                "estadoActual",
                EstadoVuelo.PROGRAMADO
        );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Vuelo reactivado correctamente",
                        resultado
                )
        );
    }

    // ── MÉTODOS AUXILIARES ───────────────────────────────────────

    /**
     * Construye una nueva fila de plan_vuelo_diario usando
     * los datos del vuelo creado.
     */
    private PlanVueloDiario construirPlanVueloDiario(
            Aeropuerto origen,
            Aeropuerto destino,
            Vuelo vuelo
    ) {
        PlanVueloDiario plan =
                new PlanVueloDiario();

        plan.setCodigoOrigen(
                origen.getCodigoOaci()
        );

        plan.setCodigoDestino(
                destino.getCodigoOaci()
        );

        plan.setHoraSalida(
                vuelo.getHoraSalida().toLocalTime()
        );

        plan.setHoraLlegada(
                vuelo.getHoraLlegada().toLocalTime()
        );

        plan.setCapacidad(
                vuelo.getCapacidadMaxima()
        );

        return plan;
    }

    /**
     * Copia los valores actuales del vuelo a una fila existente
     * o nueva de plan_vuelo_diario.
     */
    private void copiarDatosAlPlan(
            PlanVueloDiario plan,
            Vuelo vuelo
    ) {
        plan.setCodigoOrigen(
                vuelo.getAeropuertoOrigen()
                        .getCodigoOaci()
        );

        plan.setCodigoDestino(
                vuelo.getAeropuertoDestino()
                        .getCodigoOaci()
        );

        plan.setHoraSalida(
                vuelo.getHoraSalida()
                        .toLocalTime()
        );

        plan.setHoraLlegada(
                vuelo.getHoraLlegada()
                        .toLocalTime()
        );

        plan.setCapacidad(
                vuelo.getCapacidadMaxima()
        );
    }
}