package com.plantilla.backend.modules.simulacion;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;
import com.plantilla.backend.modules.envio.repository.EnvioMaletasRepository;
import com.plantilla.backend.modules.maestro.repository.AeropuertoRepository;
import com.plantilla.backend.modules.maestro.repository.VueloRepository;
import com.plantilla.backend.modules.simulacion.alns.BackendDataAdapter;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.concurrent.ScheduledFuture;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.*;

class SimulacionCancelacionTest {
    private static long ms(int dia, int hora, int minuto) {
        return LocalDateTime.of(2026, 7, dia, hora, minuto).toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    @Test
    void registraOcurrenciasYEvitaDuplicadosEnArrastre() {
        SimulacionSesionEstado estado = nuevaSesion();
        long salida = ms(15, 18, 25);
        estado.registrarResultados(List.of(Map.of("codigoVuelo", "XX001", "horaSalidaMs", salida,
                "envios", List.of(Map.of("idEnvio", 1), Map.of("idEnvio", 2)))));
        String clave = SimulacionSesionEstado.claveOcurrencia("XX001", salida);
        assertTrue(estado.conoceOcurrencia(clave));
        assertEquals(java.util.Set.of(1, 2), estado.enviosDeOcurrencia(clave));
        assertTrue(estado.getOcurrenciasCanceladas().add(clave));
        assertFalse(estado.getOcurrenciasCanceladas().add(clave));
        java.util.Set<Integer> afectados = estado.liberarOcurrencia(clave);
        assertEquals(java.util.Set.of(1, 2), afectados);
        assertFalse(estado.conoceOcurrencia(clave));
        assertTrue(estado.ocurrenciasDeEnvio(1).isEmpty());
        assertTrue(estado.ocurrenciasDeEnvio(2).isEmpty());
        estado.agregarAlArrastre(afectados);
        estado.agregarAlArrastre(List.of(1, 2, 1));
        estado.agregarAlArrastre(List.of(2));
        assertEquals(List.of(1, 2), estado.getArrastreIds());
    }

    @Test
    void registraYExcluyeSoloLaOcurrenciaFuturaAunqueNoEsteEnResultados() {
        BackendDataAdapter adapter = mock(BackendDataAdapter.class);
        VueloRepository vueloRepository = mock(VueloRepository.class);
        SimulacionPureService service = new SimulacionPureService(adapter,
                vueloRepository, mock(AeropuertoRepository.class),
                mock(EnvioMaletasRepository.class));

        long hoyMs = ms(15, 18, 25);
        long mananaMs = ms(16, 18, 25);
        var vueloHoy = new com.plantilla.backend.modules.algoritmo.alns.model.Vuelo(
                "XX001", "AAAA", "BBBB", 100, 10, 20);
        var vueloManana = new com.plantilla.backend.modules.algoritmo.alns.model.Vuelo(
                "XX001", "AAAA", "BBBB", 100, 30, 40);
        when(adapter.toLocalDateTimeUtc(10)).thenReturn(
                LocalDateTime.ofEpochSecond(hoyMs / 1000, 0, ZoneOffset.UTC));
        when(adapter.toLocalDateTimeUtc(30)).thenReturn(
                LocalDateTime.ofEpochSecond(mananaMs / 1000, 0, ZoneOffset.UTC));
        LocalDateTime mananaUtc = LocalDateTime.ofEpochSecond(mananaMs / 1000, 0, ZoneOffset.UTC);
        com.plantilla.backend.modules.maestro.entity.Vuelo vueloJpa =
                new com.plantilla.backend.modules.maestro.entity.Vuelo();
        vueloJpa.setIdVuelo(2045);
        vueloJpa.setCodigoVuelo("XX001");
        vueloJpa.setHoraSalida(mananaUtc);
        vueloJpa.setEstado(com.plantilla.backend.shared.enums.EstadoVuelo.PROGRAMADO);
        when(vueloRepository.findByCodigoVuelo("XX001")).thenReturn(java.util.Optional.of(vueloJpa));
        when(adapter.convertirVuelo(vueloJpa)).thenReturn(vueloManana);

        long afectada = mananaMs;
        assertEquals(mananaMs, afectada);
        assertTrue(service.existeOcurrencia("XX001", afectada));

        SimulacionSesionEstado estado = nuevaSesion();
        String claveManana = SimulacionSesionEstado.claveOcurrencia("XX001", afectada);
        assertFalse(estado.conoceOcurrencia(claveManana));
        estado.registrarOcurrencia(claveManana);
        assertTrue(estado.getOcurrenciasCanceladas().add(claveManana));

        List<com.plantilla.backend.modules.algoritmo.alns.model.Vuelo> disponibles =
                new ArrayList<>(List.of(vueloHoy, vueloManana));
        service.excluirOcurrenciasCanceladas(disponibles, estado.getOcurrenciasCanceladas());
        assertEquals(1, disponibles.size());
        assertSame(vueloHoy, disponibles.get(0));
    }

    @Test
    void validaCasoRealConCodigoExpuestoYTimestampDecimalConvertidoALong() {
        BackendDataAdapter adapter = mock(BackendDataAdapter.class);
        VueloRepository vueloRepository = mock(VueloRepository.class);
        SimulacionPureService service = new SimulacionPureService(adapter, vueloRepository,
                mock(AeropuertoRepository.class), mock(EnvioMaletasRepository.class));

        String codigo = "EBCI-OMDB-20260807-2112-2045";
        long salidaMs = 1786216320000L;
        Number cancelacionRecibida = 1786194504650.7678d;
        long cancelacionMs = cancelacionRecibida.longValue();
        LocalDateTime salidaUtc = LocalDateTime.ofEpochSecond(salidaMs / 1000, 0, ZoneOffset.UTC);

        com.plantilla.backend.modules.maestro.entity.Vuelo vueloJpa =
                new com.plantilla.backend.modules.maestro.entity.Vuelo();
        vueloJpa.setIdVuelo(2045);
        vueloJpa.setCodigoVuelo(codigo);
        vueloJpa.setHoraSalida(salidaUtc);
        vueloJpa.setEstado(com.plantilla.backend.shared.enums.EstadoVuelo.PROGRAMADO);
        var vueloAlns = new com.plantilla.backend.modules.algoritmo.alns.model.Vuelo(
                codigo, "EBCI", "OMDB", 100, 50, 60, 2045);

        when(vueloRepository.findByCodigoVuelo(codigo)).thenReturn(java.util.Optional.of(vueloJpa));
        when(adapter.convertirVuelo(vueloJpa)).thenReturn(vueloAlns);
        when(adapter.toLocalDateTimeUtc(50)).thenReturn(salidaUtc);

        assertTrue(cancelacionMs <= salidaMs - 3_600_000L);
        assertTrue(service.existeOcurrencia(codigo, salidaMs));
    }

    @Test
    void distingueOcurrenciasYVueloSinEnvios() {
        SimulacionSesionEstado estado = nuevaSesion();
        long hoy = ms(15, 18, 25), manana = ms(16, 18, 25);
        estado.registrarResultados(List.of(
                Map.of("codigoVuelo", "XX001", "horaSalidaMs", hoy, "envios", List.of()),
                Map.of("codigoVuelo", "XX001", "horaSalidaMs", manana, "envios", List.of(Map.of("idEnvio", 9)))));
        assertTrue(estado.conoceOcurrencia(SimulacionSesionEstado.claveOcurrencia("XX001", hoy)));
        assertTrue(estado.enviosDeOcurrencia(SimulacionSesionEstado.claveOcurrencia("XX001", hoy)).isEmpty());
        assertEquals(java.util.Set.of(9), estado.enviosDeOcurrencia(
                SimulacionSesionEstado.claveOcurrencia("XX001", manana)));
        assertFalse(estado.conoceOcurrencia(SimulacionSesionEstado.claveOcurrencia("XX404", hoy)));
    }

    @Test
    void registraVueloCargadoSinEnviosYCancelarloNoModificaArrastre() {
        BackendDataAdapter adapter = mock(BackendDataAdapter.class);
        SimulacionPureService service = new SimulacionPureService(adapter,
                mock(VueloRepository.class), mock(AeropuertoRepository.class),
                mock(EnvioMaletasRepository.class));
        long salida = ms(15, 23, 47);
        var vueloVacio = new com.plantilla.backend.modules.algoritmo.alns.model.Vuelo(
                "EBCI-SBBR-20280807-2347-0610", "EBCI", "SBBR", 100, 70, 80);
        when(adapter.toLocalDateTimeUtc(70)).thenReturn(
                LocalDateTime.ofEpochSecond(salida / 1000, 0, ZoneOffset.UTC));

        List<String> disponibles = service.clavesOcurrenciasDisponibles(List.of(vueloVacio));
        SimulacionSesionEstado estado = nuevaSesion();
        estado.registrarOcurrencias(disponibles);
        String clave = SimulacionSesionEstado.claveOcurrencia(vueloVacio.getId(), salida);

        assertTrue(estado.conoceOcurrencia(clave));
        assertTrue(estado.getOcurrenciasCanceladas().add(clave));
        java.util.Set<Integer> afectados = estado.liberarOcurrencia(clave);
        assertTrue(afectados.isEmpty());
        estado.agregarAlArrastre(afectados);
        assertTrue(estado.getArrastreIds().isEmpty());
    }

    @Test
    void vueloNoCargadoNiPersistidoPermaneceDesconocido() {
        BackendDataAdapter adapter = mock(BackendDataAdapter.class);
        VueloRepository vueloRepository = mock(VueloRepository.class);
        SimulacionPureService service = new SimulacionPureService(adapter, vueloRepository,
                mock(AeropuertoRepository.class), mock(EnvioMaletasRepository.class));
        when(vueloRepository.findByCodigoVuelo("XX404")).thenReturn(java.util.Optional.empty());

        SimulacionSesionEstado estado = nuevaSesion();
        String clave = SimulacionSesionEstado.claveOcurrencia("XX404", ms(15, 23, 47));
        assertFalse(estado.conoceOcurrencia(clave));
        assertFalse(service.existeOcurrencia("XX404", ms(15, 23, 47)));
    }

    @Test
    void claveRegistradaCoincideConClaveCalculadaAlCancelarCasoOAKB() {
        BackendDataAdapter adapter = mock(BackendDataAdapter.class);
        SimulacionPureService service = new SimulacionPureService(adapter,
                mock(VueloRepository.class), mock(AeropuertoRepository.class),
                mock(EnvioMaletasRepository.class));
        String codigo = "EBCI-OAKB-20280808-0023-2047";
        long salidaMs = LocalDateTime.of(2028, 8, 8, 22, 23)
                .toInstant(ZoneOffset.UTC).toEpochMilli();
        long simuladaMs = LocalDateTime.of(2028, 8, 8, 0, 49)
                .toInstant(ZoneOffset.UTC).toEpochMilli();
        var vuelo = new com.plantilla.backend.modules.algoritmo.alns.model.Vuelo(
                codigo, "EBCI", "OAKB", 100, 90, 100, 2047);
        when(adapter.toLocalDateTimeUtc(90)).thenReturn(
                LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(salidaMs), ZoneOffset.UTC));

        String claveRegistrada = service.clavesOcurrenciasDisponibles(List.of(vuelo)).get(0);
        assertTrue(simuladaMs <= salidaMs - 3_600_000L);
        long afectadaMs = salidaMs;
        String claveCancelacion = SimulacionSesionEstado.claveOcurrencia(codigo, afectadaMs);

        assertEquals(claveRegistrada, claveCancelacion);
    }

    @Test
    void registraVueloVacioDelSnapshotInitAntesDeLosUpdates() {
        SimulacionSesionEstado estado = nuevaSesion();
        String codigo = "EBCI-OAKB-20280808-0023-2047";
        long salidaMs = LocalDateTime.of(2028, 8, 8, 22, 23)
                .toInstant(ZoneOffset.UTC).toEpochMilli();
        String clave = SimulacionSesionEstado.claveOcurrencia(codigo, salidaMs);

        assertFalse(estado.conoceOcurrencia(clave));
        estado.registrarOcurrenciasInit(List.of(Map.of(
                "codigoVuelo", codigo,
                "horaSalidaMs", salidaMs,
                "envios", List.of())));

        assertTrue(estado.conoceOcurrencia(clave));
        assertEquals("INIT", estado.origenOcurrencia(clave));
        estado.registrarOcurrencias(List.of("OTRO|123"));
        assertTrue(estado.conoceOcurrencia(clave));
        assertEquals("INIT", estado.origenOcurrencia(clave));
    }

    @Test
    void unaSesionFinalizadaNoEstaActiva() {
        SimulacionSesionEstado estado = nuevaSesion();
        estado.setSimId("sim-1");
        estado.setFinalizada(true);
        assertFalse(estado.estaActiva());
    }

    @Test
    void capacidadIgualOSuperiorNuncaGeneraColapso() {
        LocalDateTime ahora = LocalDateTime.of(2026, 7, 27, 16, 0);
        SimulacionSesionEstado estado = nuevaSesion();
        estado.registrarCapacidades(
                List.of(Map.of("codigoOaci", "SPIM", "capacidad", 100)));
        estado.registrarDemanda(
                List.of(demanda(1, "SPIM", 101, ms(27, 15, 0))));

        OcupacionAeropuerto ocupacion =
                estado.calcularOcupacionesAeropuertos(ahora).get("SPIM");
        assertEquals(101, ocupacion.ocupacionActual());
        assertEquals(101.0, ocupacion.porcentaje());
        assertNull(estado.detectarColapso(ahora));
    }

    @Test
    void snapshotVisibleUsa419De420YNoColapsa() {
        SimulacionSesionEstado estado = nuevaSesion();
        estado.registrarCapacidades(
                List.of(Map.of("codigoOaci", "SLLP", "capacidad", 420)));
        estado.registrarDemanda(
                List.of(demanda(1, "SLLP", 419, ms(27, 9, 0))));
        LocalDateTime ahora = LocalDateTime.of(2026, 7, 27, 10, 0);

        Map<String, OcupacionAeropuerto> snapshot =
                estado.calcularOcupacionesAeropuertos(ahora);

        assertEquals(419, snapshot.get("SLLP").ocupacionActual());
        assertEquals(420, snapshot.get("SLLP").capacidadMaxima());
        assertEquals(99.76, snapshot.get("SLLP").porcentaje());
        assertNull(estado.detectarColapso(ahora, snapshot));
    }

    @Test
    void porcentajeNoSeRecortaCuandoSuperaLaCapacidad() {
        SimulacionSesionEstado estado = nuevaSesion();
        estado.registrarCapacidades(
                List.of(Map.of("codigoOaci", "SLLP", "capacidad", 420)));
        estado.registrarDemanda(
                List.of(demanda(1, "SLLP", 487, ms(27, 9, 0))));

        OcupacionAeropuerto ocupacion = estado.calcularOcupacionesAeropuertos(
                LocalDateTime.of(2026, 7, 27, 10, 0)).get("SLLP");

        assertEquals(487, ocupacion.ocupacionActual());
        assertEquals(115.95, ocupacion.porcentaje());
    }

    @Test
    void slaVigenteContinuaYSlaVencidoNoEntregadoColapsa() {
        SimulacionSesionEstado estado = nuevaSesion();
        long registro = ms(27, 15, 0);
        long limite = ms(27, 16, 10);
        estado.registrarResultados(List.of(Map.of(
                "codigoVuelo", "XX-SLA",
                "origen", "SPIM",
                "destino", "SCEL",
                "horaSalidaMs", ms(27, 17, 0),
                "horaLlegadaMs", ms(27, 18, 0),
                "envios", List.of(Map.of(
                        "idEnvio", 914551,
                        "cantidad", 1,
                        "cumpleSla", false,
                        "fechaRegistroMs", registro,
                        "fechaLimiteMs", limite)))));

        assertNull(estado.detectarColapso(LocalDateTime.of(2026, 7, 27, 16, 0)));
        EstadoColapso colapso =
                estado.detectarColapso(LocalDateTime.of(2026, 7, 27, 16, 11));
        assertNotNull(colapso);
        assertEquals(EstadoColapso.INCUMPLIMIENTO_SLA, colapso.tipo());
        assertEquals("914551", colapso.idEnvio());
    }

    @Test
    void horaExactaDelLimiteNoColapsaYUnMinutoDespuesSi() {
        SimulacionSesionEstado estado = nuevaSesion();
        estado.registrarDemanda(List.of(Map.of(
                "idEnvio", 20,
                "origen", "SPIM",
                "destino", "SCEL",
                "cantidad", 1,
                "fechaRegistroMs", ms(27, 15, 0),
                "fechaLimiteMs", ms(27, 16, 0))));

        assertNull(estado.detectarColapso(LocalDateTime.of(2026, 7, 27, 16, 0)));
        assertEquals(EstadoColapso.INCUMPLIMIENTO_SLA,
                estado.detectarColapso(
                        LocalDateTime.of(2026, 7, 27, 16, 1)).tipo());
    }

    @Test
    void horizontePlanificadoNoAdelantaElRelojUsadoPorSla() {
        SimulacionSesionEstado estado = nuevaSesion();
        long inicioMs = estado.getFechaInicio()
                .toInstant(ZoneOffset.UTC).toEpochMilli();
        estado.setPunteroSim(estado.getFechaInicio().plusHours(20));
        estado.setInicioRealMs(1_000_000L);

        long ahoraRealMs = 1_000_000L
                + java.time.Duration.ofHours(14).toMillis() / estado.getK();

        assertEquals(inicioMs + java.time.Duration.ofHours(14).toMillis(),
                estado.tiempoSimuladoActualMs(ahoraRealMs));
    }

    @Test
    void relojA14NoVenceLimiteDe1755AunqueLaVentanaLlegueA20() {
        SimulacionSesionEstado estado = nuevaSesion();
        estado.registrarDemanda(List.of(Map.of(
                "idEnvio", 13408606,
                "origen", "SPIM",
                "destino", "SCEL",
                "cantidad", 1,
                "fechaRegistroMs", ms(27, 10, 0),
                "fechaLimiteMs", ms(27, 17, 55))));

        assertNull(estado.detectarColapso(ms(27, 14, 0)));
        assertEquals(EstadoColapso.INCUMPLIMIENTO_SLA,
                estado.detectarColapso(ms(27, 17, 56)).tipo());
    }

    @Test
    void aterrizajeIntermedioNoCuentaComoEntregaFinal() {
        SimulacionSesionEstado estado = nuevaSesion();
        estado.registrarDemanda(List.of(Map.of(
                "idEnvio", 21,
                "origen", "SPIM",
                "destino", "SCEL",
                "cantidad", 1,
                "fechaRegistroMs", ms(27, 12, 0),
                "fechaLimiteMs", ms(27, 16, 0))));
        estado.registrarResultados(List.of(vueloConEnvio(
                "TRAMO-1", "SPIM", "SLLP", ms(27, 13, 0), ms(27, 14, 0),
                21, 1, ms(27, 12, 0))));

        assertEquals(EstadoColapso.INCUMPLIMIENTO_SLA,
                estado.detectarColapso(
                        LocalDateTime.of(2026, 7, 27, 16, 1)).tipo());
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateSaturadoNoColapsaNiCancelaLaSimulacion() throws Exception {
        SimulacionPureService service = mock(SimulacionPureService.class);
        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("enviosDemanda",
                List.of(demanda(1, "SLLP", 420, ms(27, 0, 0))));
        resultado.put("pendientesIds", List.of(1));
        resultado.put("ocurrenciasDisponibles", List.of());
        resultado.put("nuevosVuelos", List.of());
        resultado.put("asignados", 0);
        resultado.put("noAsignados", 1);
        when(service.procesarVentanaSC(
                any(), any(), anyInt(), anyList(), anyLong(), anySet()))
                .thenReturn(resultado);

        SimulacionWebSocketHandler handler = new SimulacionWebSocketHandler(
                service,
                new ObjectMapper(),
                mock(com.plantilla.backend.modules.simulacion.service.ColapsoEstimadorService.class));
        WebSocketSession ws = mock(WebSocketSession.class);
        when(ws.isOpen()).thenReturn(true);
        SimulacionSesionEstado estado = new SimulacionSesionEstado(
                "s", ws, LocalDateTime.of(2026, 7, 27, 0, 0), 120, 5000);
        estado.registrarCapacidades(
                List.of(Map.of("codigoOaci", "SLLP", "capacidad", 420)));
        ScheduledFuture<?> tarea = mock(ScheduledFuture.class);
        estado.setTareaScheduled(tarea);

        try {
            handler.ejecutarCiclo(estado);

            assertFalse(estado.isColapsada());
            assertEquals(1, estado.getMensajesBuffer().size());
            Map<String, Object> update = new ObjectMapper().readValue(
                    estado.getMensajesBuffer().get(0), Map.class);
            assertEquals("UPDATE", update.get("type"));

            Map<String, Object> ocupaciones =
                    (Map<String, Object>) update.get("ocupacionesAeropuertos");
            Map<String, Object> sllp =
                    (Map<String, Object>) ocupaciones.get("SLLP");
            assertEquals(420, ((Number) sllp.get("ocupacionActual")).intValue());
            assertEquals(420, ((Number) sllp.get("capacidadMaxima")).intValue());
            verify(tarea, never()).cancel(false);
        } finally {
            handler.shutdown();
        }
    }

    @Test
    void envioEntregadoNoColapsaDespuesDeSuFechaLimite() {
        SimulacionSesionEstado estado = nuevaSesion();
        estado.registrarResultados(List.of(Map.of(
                "codigoVuelo", "XX-OK",
                "origen", "SPIM",
                "destino", "SCEL",
                "horaSalidaMs", ms(27, 15, 0),
                "horaLlegadaMs", ms(27, 16, 5),
                "envios", List.of(Map.of(
                        "idEnvio", 10,
                        "cantidad", 1,
                        "cumpleSla", true,
                        "fechaRegistroMs", ms(27, 14, 0),
                        "fechaLimiteMs", ms(27, 16, 10))))));

        assertNull(estado.detectarColapso(LocalDateTime.of(2026, 7, 27, 16, 11)));
    }

    @Test
    void marcarColapsoEsIdempotenteYDetieneLaSesion() {
        SimulacionSesionEstado estado = nuevaSesion();
        estado.setSimId("sim-colapso");
        EstadoColapso colapso = EstadoColapso.sla(
                10, LocalDateTime.of(2026, 7, 27, 15, 59),
                LocalDateTime.of(2026, 7, 27, 16, 0));

        assertTrue(estado.marcarColapsada(colapso));
        assertFalse(estado.marcarColapsada(colapso));
        assertFalse(estado.estaActiva());
        assertSame(colapso, estado.getEstadoColapso());
    }

    @Test
    void enviosConRutaYPendientesSaturanJuntosElOrigen() {
        SimulacionSesionEstado estado = nuevaSesion();
        estado.registrarCapacidades(List.of(Map.of("codigoOaci", "SPIM", "capacidad", 100)));
        estado.registrarDemanda(List.of(
                demanda(1, "SPIM", 70, ms(27, 9, 0)),
                demanda(2, "SPIM", 30, ms(27, 9, 0))));
        estado.registrarResultados(List.of(vueloConEnvio(
                "RUTA-1", "SPIM", "SCEL", ms(27, 11, 0), ms(27, 13, 0),
                1, 70, ms(27, 9, 0))));

        OcupacionAeropuerto ocupacion = estado.calcularOcupacionesAeropuertos(
                LocalDateTime.of(2026, 7, 27, 10, 0)).get("SPIM");
        assertEquals(100, ocupacion.ocupacionActual());
        assertNull(estado.detectarColapso(LocalDateTime.of(2026, 7, 27, 10, 0)));
    }

    @Test
    void envioEnDemandaYConRutaNoSeCuentaDosVeces() {
        SimulacionSesionEstado estado = nuevaSesion();
        estado.registrarCapacidades(List.of(Map.of("codigoOaci", "SPIM", "capacidad", 30)));
        estado.registrarDemanda(List.of(demanda(1, "SPIM", 20, ms(27, 9, 0))));
        estado.registrarResultados(List.of(vueloConEnvio(
                "RUTA-1", "SPIM", "SCEL", ms(27, 11, 0), ms(27, 13, 0),
                1, 20, ms(27, 9, 0))));

        assertNull(estado.detectarColapso(LocalDateTime.of(2026, 7, 27, 10, 0)));
    }

    @Test
    void envioFuturoTodaviaNoOcupaElOrigen() {
        SimulacionSesionEstado estado = nuevaSesion();
        estado.registrarCapacidades(List.of(Map.of("codigoOaci", "SPIM", "capacidad", 1)));
        estado.registrarDemanda(List.of(demanda(1, "SPIM", 1, ms(27, 11, 0))));

        assertNull(estado.detectarColapso(LocalDateTime.of(2026, 7, 27, 10, 0)));
    }

    @Test
    void envioCuyoPrimerVueloYaDespegoNoOcupaElOrigen() {
        SimulacionSesionEstado estado = nuevaSesion();
        estado.registrarCapacidades(List.of(Map.of("codigoOaci", "SPIM", "capacidad", 1)));
        estado.registrarDemanda(List.of(demanda(1, "SPIM", 1, ms(27, 8, 0))));
        estado.registrarResultados(List.of(vueloConEnvio(
                "RUTA-1", "SPIM", "SCEL", ms(27, 9, 0), ms(27, 11, 0),
                1, 1, ms(27, 8, 0))));

        assertNull(estado.detectarColapso(LocalDateTime.of(2026, 7, 27, 10, 0)));
    }

    @Test
    void cancelacionDevuelveMaletasAlOrigenDelTramo() {
        SimulacionSesionEstado estado = nuevaSesion();
        estado.registrarCapacidades(List.of(Map.of("codigoOaci", "SPIM", "capacidad", 20)));
        estado.registrarDemanda(List.of(demanda(1, "SPIM", 20, ms(27, 8, 0))));
        long salida = ms(27, 11, 0);
        estado.registrarResultados(List.of(vueloConEnvio(
                "RUTA-1", "SPIM", "SCEL", salida, ms(27, 13, 0),
                1, 20, ms(27, 8, 0))));
        String clave = SimulacionSesionEstado.claveOcurrencia("RUTA-1", salida);

        assertEquals(java.util.Set.of(1), estado.liberarOcurrencia(clave, ms(27, 10, 0)));
        OcupacionAeropuerto ocupacion = estado.calcularOcupacionesAeropuertos(
                LocalDateTime.of(2026, 7, 27, 10, 0)).get("SPIM");
        assertEquals(20, ocupacion.ocupacionActual());
        assertNull(estado.detectarColapso(LocalDateTime.of(2026, 7, 27, 10, 0)));
    }

    @Test
    void masDeUnaHoraYExactamenteUnaHoraCancelanLaSeleccionada() {
        BackendDataAdapter adapter = mock(BackendDataAdapter.class);
        VueloRepository repository = mock(VueloRepository.class);
        SimulacionPureService service = nuevoServicio(adapter, repository);
        var seleccionada = vueloJpa(1, "SERIE-DIA-1",
                LocalDateTime.of(2028, 8, 7, 23, 56));
        prepararConversion(adapter, seleccionada);
        when(repository.findByCodigoVuelo(seleccionada.getCodigoVuelo()))
                .thenReturn(java.util.Optional.of(seleccionada));
        when(repository.findByAeropuertoOrigen_IdAeropuertoAndAeropuertoDestino_IdAeropuertoAndHoraSalidaBetweenAndEstadoNotOrderByHoraSalidaAsc(
                eq(10), eq(20), any(LocalDateTime.class), any(LocalDateTime.class),
                eq(com.plantilla.backend.shared.enums.EstadoVuelo.CANCELADO)))
                .thenReturn(List.of(seleccionada));
        long salida = seleccionada.getHoraSalida().toInstant(ZoneOffset.UTC).toEpochMilli();
        LocalDateTime horizonte = LocalDateTime.of(2028, 8, 13, 0, 0);

        assertEquals("SERIE-DIA-1", service.resolverOcurrenciaCancelacion(
                seleccionada.getCodigoVuelo(), salida - 3_600_001, horizonte, java.util.Set.of())
                .orElseThrow().codigoVuelo());
        assertEquals(salida, service.resolverOcurrenciaCancelacion(
                seleccionada.getCodigoVuelo(), salida - 3_600_000, horizonte, java.util.Set.of())
                .orElseThrow().horaSalidaMs());
    }

    @Test
    void menosDeUnaHoraOVueloYaEnAireResuelveLaSiguienteEntidadReal() {
        BackendDataAdapter adapter = mock(BackendDataAdapter.class);
        VueloRepository repository = mock(VueloRepository.class);
        SimulacionPureService service = nuevoServicio(adapter, repository);
        var seleccionada = vueloJpa(1854, "EBCI-LOWW-20280808-0156-1854",
                LocalDateTime.of(2028, 8, 7, 23, 56));
        var siguiente = vueloJpa(2854, "EBCI-LOWW-20280809-0156-2854",
                LocalDateTime.of(2028, 8, 8, 23, 56));
        prepararConversion(adapter, seleccionada);
        prepararConversion(adapter, siguiente);
        when(repository.findByCodigoVuelo(seleccionada.getCodigoVuelo()))
                .thenReturn(java.util.Optional.of(seleccionada));
        when(repository.findByAeropuertoOrigen_IdAeropuertoAndAeropuertoDestino_IdAeropuertoAndHoraSalidaBetweenAndEstadoNotOrderByHoraSalidaAsc(
                eq(10), eq(20), any(LocalDateTime.class), any(LocalDateTime.class),
                eq(com.plantilla.backend.shared.enums.EstadoVuelo.CANCELADO)))
                .thenReturn(List.of(siguiente));
        long salidaOriginal = seleccionada.getHoraSalida().toInstant(ZoneOffset.UTC).toEpochMilli();
        long salidaSiguiente = siguiente.getHoraSalida().toInstant(ZoneOffset.UTC).toEpochMilli();
        LocalDateTime horizonte = LocalDateTime.of(2028, 8, 13, 0, 0);

        var menosDeUnaHora = service.resolverOcurrenciaCancelacion(
                seleccionada.getCodigoVuelo(), salidaOriginal - 1, horizonte, java.util.Set.of())
                .orElseThrow();
        var yaEnAire = service.resolverOcurrenciaCancelacion(
                seleccionada.getCodigoVuelo(), salidaOriginal + 2_099_992, horizonte, java.util.Set.of())
                .orElseThrow();

        assertEquals(siguiente.getCodigoVuelo(), menosDeUnaHora.codigoVuelo());
        assertEquals(salidaSiguiente, menosDeUnaHora.horaSalidaMs());
        assertEquals(menosDeUnaHora, yaEnAire);
        assertNotEquals(seleccionada.getCodigoVuelo(), menosDeUnaHora.codigoVuelo());
        assertNotEquals(salidaOriginal, menosDeUnaHora.horaSalidaMs());
    }

    @Test
    void sinSiguienteRealDevuelveVacioYNoCancelaLaSeleccionada() {
        BackendDataAdapter adapter = mock(BackendDataAdapter.class);
        VueloRepository repository = mock(VueloRepository.class);
        SimulacionPureService service = nuevoServicio(adapter, repository);
        var seleccionada = vueloJpa(1854, "EBCI-LOWW-20280808-0156-1854",
                LocalDateTime.of(2028, 8, 7, 23, 56));
        prepararConversion(adapter, seleccionada);
        when(repository.findByCodigoVuelo(seleccionada.getCodigoVuelo()))
                .thenReturn(java.util.Optional.of(seleccionada));
        when(repository.findByAeropuertoOrigen_IdAeropuertoAndAeropuertoDestino_IdAeropuertoAndHoraSalidaBetweenAndEstadoNotOrderByHoraSalidaAsc(
                anyInt(), anyInt(), any(LocalDateTime.class), any(LocalDateTime.class), any()))
                .thenReturn(List.of());
        long salida = seleccionada.getHoraSalida().toInstant(ZoneOffset.UTC).toEpochMilli();

        assertTrue(service.resolverOcurrenciaCancelacion(seleccionada.getCodigoVuelo(), salida + 1,
                LocalDateTime.of(2028, 8, 13, 0, 0), java.util.Set.of()).isEmpty());
    }

    @Test
    void exclusionUsaSoloLaClaveDeLaSiguienteOcurrenciaReal() {
        BackendDataAdapter adapter = mock(BackendDataAdapter.class);
        SimulacionPureService service = nuevoServicio(adapter, mock(VueloRepository.class));
        var original = new com.plantilla.backend.modules.algoritmo.alns.model.Vuelo(
                "ORIGINAL", "EBCI", "LOWW", 100, 1, 2);
        var siguiente = new com.plantilla.backend.modules.algoritmo.alns.model.Vuelo(
                "SIGUIENTE", "EBCI", "LOWW", 100, 3, 4);
        long originalMs = 1849305360000L;
        long siguienteMs = 1849391760000L;
        when(adapter.toLocalDateTimeUtc(1)).thenReturn(LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(originalMs), ZoneOffset.UTC));
        when(adapter.toLocalDateTimeUtc(3)).thenReturn(LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(siguienteMs), ZoneOffset.UTC));
        List<com.plantilla.backend.modules.algoritmo.alns.model.Vuelo> vuelos =
                new ArrayList<>(List.of(original, siguiente));

        service.excluirOcurrenciasCanceladas(vuelos, java.util.Set.of(
                SimulacionSesionEstado.claveOcurrencia("SIGUIENTE", siguienteMs)));

        assertEquals(List.of(original), vuelos);
    }

    private SimulacionPureService nuevoServicio(BackendDataAdapter adapter, VueloRepository repository) {
        return new SimulacionPureService(adapter, repository, mock(AeropuertoRepository.class),
                mock(EnvioMaletasRepository.class));
    }

    private com.plantilla.backend.modules.maestro.entity.Vuelo vueloJpa(
            int id, String codigo, LocalDateTime salida) {
        var origen = new com.plantilla.backend.modules.maestro.entity.Aeropuerto();
        origen.setIdAeropuerto(10);
        origen.setCodigoOaci("EBCI");
        var destino = new com.plantilla.backend.modules.maestro.entity.Aeropuerto();
        destino.setIdAeropuerto(20);
        destino.setCodigoOaci("LOWW");
        var vuelo = new com.plantilla.backend.modules.maestro.entity.Vuelo();
        vuelo.setIdVuelo(id);
        vuelo.setCodigoVuelo(codigo);
        vuelo.setAeropuertoOrigen(origen);
        vuelo.setAeropuertoDestino(destino);
        vuelo.setHoraSalida(salida);
        vuelo.setHoraLlegada(salida.plusHours(2));
        vuelo.setCapacidadMaxima(100);
        vuelo.setEstado(com.plantilla.backend.shared.enums.EstadoVuelo.PROGRAMADO);
        return vuelo;
    }

    private void prepararConversion(BackendDataAdapter adapter,
            com.plantilla.backend.modules.maestro.entity.Vuelo vuelo) {
        long minutos = vuelo.getHoraSalida().toEpochSecond(ZoneOffset.UTC) / 60;
        var alns = new com.plantilla.backend.modules.algoritmo.alns.model.Vuelo(
                vuelo.getCodigoVuelo(), "EBCI", "LOWW", 100, minutos, minutos + 120,
                vuelo.getIdVuelo());
        when(adapter.convertirVuelo(vuelo)).thenReturn(alns);
        when(adapter.toLocalDateTimeUtc(minutos)).thenReturn(vuelo.getHoraSalida());
    }

    private Map<String, Object> demanda(
            int id, String origen, int cantidad, long fechaRegistroMs) {
        return Map.of(
                "idEnvio", id,
                "origen", origen,
                "cantidad", cantidad,
                "fechaRegistroMs", fechaRegistroMs);
    }

    private Map<String, Object> vueloConEnvio(
            String codigo, String origen, String destino,
            long salidaMs, long llegadaMs, int idEnvio,
            int cantidad, long fechaRegistroMs) {
        return Map.of(
                "codigoVuelo", codigo,
                "origen", origen,
                "destino", destino,
                "horaSalidaMs", salidaMs,
                "horaLlegadaMs", llegadaMs,
                "envios", List.of(Map.of(
                        "idEnvio", idEnvio,
                        "cantidad", cantidad,
                        "cumpleSla", true,
                        "fechaRegistroMs", fechaRegistroMs)));
    }

    private SimulacionSesionEstado nuevaSesion() {
        return new SimulacionSesionEstado("s", mock(WebSocketSession.class),
                LocalDateTime.of(2026, 7, 15, 2, 0), 120, 5000);
    }
}
