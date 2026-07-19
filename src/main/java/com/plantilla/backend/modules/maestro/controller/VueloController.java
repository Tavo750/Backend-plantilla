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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/maestro/vuelos")
@RequiredArgsConstructor
@Tag(name = "Vuelos", description = "Gestión de vuelos")
public class VueloController {

    private final VueloRepository vueloRepository;
    private final AeropuertoRepository aeropuertoRepository;
    private final PlanVueloDiarioRepository planVueloDiarioRepository;
    private final CancelacionVueloService cancelacionVueloService;

    // ── CRUD ─────────────────────────────────────────────────────

    @GetMapping
    @Transactional(Transactional.TxType.REQUIRED)
    @Operation(
            summary = "Listar vuelos",
            description = "Obtiene la lista de todos los vuelos"
    )
    public ResponseEntity<ApiResponse<List<Vuelo>>> listarVuelos() {
        return ResponseEntity.ok(
                ApiResponse.success(vueloRepository.findAll())
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