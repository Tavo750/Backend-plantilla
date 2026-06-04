# Cambios Backend — Sesión actual

---

## 1. `BackendApplication.java`
Flags globales configurables antes de arrancar el servidor.

```java
public static boolean GUARDAR_EN_BD = false;  // true = persiste en BD, false = solo ejecución

public static int K  = 14;   // Factor compresión temporal monitoreo (K=1 real, K=14 ~3días, K=75 colapso)
public static int SA = 5;    // Salto algoritmo: cada cuántos min REALES se lanza ALNS
public static int TA = 1;    // Tiempo estimado ejecución ALNS (min reales). Debe SA > TA
// Sc = K × SA  →  minutos de pedidos consumidos por ciclo (calculado en controller)
```

---

## 2. `application.yml`
### HikariCP (evita que AWS RDS cierre conexiones TCP inactivas)
```yaml
hikari:
  keepalive-time: 30000
  connection-timeout: 30000
  maximum-pool-size: 10
  minimum-idle: 2
  idle-timeout: 300000
  max-lifetime: 580000
  validation-timeout: 5000
```
### SSE / async (evita timeout a los 30 s en simulaciones largas)
```yaml
mvc:
  async:
    request-timeout: -1
```
### JWT (fallback si no hay variable de entorno)
```yaml
jwt:
  secret: ${JWT_SECRET:clave-secreta-super-segura-para-jwt-debe-tener-al-menos-256-bits-de-longitud-2024}
  expiration: ${JWT_EXPIRATION:86400000}
```

---

## 3. `GlobalExceptionHandler.java`
**Problema original:** `AsyncRequestTimeoutException` llegaba al handler genérico → `List.of(null)` lanzaba NPE.

**Fix 1:** Handler dedicado para SSE timeout (devuelve 204, no loguea como error):
```java
@ExceptionHandler(AsyncRequestTimeoutException.class)
public ResponseEntity<Void> handleAsyncTimeout(...) {
    log.debug("Petición async/SSE expirada — descartado silenciosamente");
    return ResponseEntity.noContent().build();
}
```
**Fix 2:** Handler genérico null-safe:
```java
String detalle = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
```

---

## 4. `AlnsSimulacionService.java`

### 4a. Flag GUARDAR_EN_BD
Todas las operaciones de persistencia (`configuracionSimulacionRepository.save()`, `planRutaRepository.save()`, `tramoRutaRepository.save()`, `asignacionVueloRepository.save()`, `resultadoSimulacionRepository.save()`) están envueltas en `if (BackendApplication.GUARDAR_EN_BD)`.

### 4b. Nuevo método `simularVentanaMonitoreo`
Para el Monitoreo Mapa. Acepta ventana datetime exacta, corre ALNS una sola vez, NO persiste, devuelve vuelos para animar.

**Lógica:**
1. Carga aeropuertos activos
2. Carga vuelos en `[ventanaInicio, ventanaFin + 2días]` (margen SLA intercontinental)
3. Carga envíos estrictamente en `[ventanaInicio, ventanaFin]` por `fechaRegistro`
4. Corre ALNS (si hay no-asignados y hay asignados, else usa plan inicial)
5. Consolida vuelos únicos con `getOrigen()` / `getDestino()` (modelo ALNS, NO entidad JPA) y tiempos via `dataAdapter.toLocalDateTimeUtc(v.getHoraSalida())`
6. Retorna: `{ ventanaInicio, ventanaFin, totalEnvios, asignados, noAsignados, vuelos[] }`

> **Ojo:** El modelo ALNS `Vuelo` usa `getOrigen()` / `getDestino()` (Strings OACI). La entidad JPA usa `getAeropuertoOrigen()` / `getAeropuertoDestino()` (objetos). No confundir.

---

## 5. `EnvioMaletasCreateDTO.java`
Eliminadas anotaciones `@JsonProperty("id_aeropuerto_origen")` y `@JsonProperty("id_aeropuerto_destino")` que esperaban snake_case pero el front envía camelCase → los campos llegaban null.

---

## 6. `EnvioMaletasServiceImpl.java` — método `crearEnvio`
Reescrito para ser null-safe cuando el front no envía aerolínea, política ni fechas:

- **Aerolínea:** si `dto.getIdAerolinea() == null` → toma la primera disponible en BD
- **Política:** si `dto.getIdPolitica() == null` → toma la activa (`findByActivaTrue`) o la primera
- **FechaRegistro:** si null → `LocalDateTime.now()`
- **FechaLímite:** si null → calcula SLA: +1 día mismo continente, +2 días diferente continente
- **HoraRegistrada:** si null → `fechaRegistro.toLocalTime()`

---

## 7. `VueloController.java`
Endpoints existentes (no modificados, referencia):
- `PATCH /maestro/vuelos/{codigo}/cancelar` → estado = CANCELADO
- `PATCH /maestro/vuelos/{codigo}/reactivar` → estado = PROGRAMADO

El ALNS excluye automáticamente vuelos CANCELADO al cargar via `BackendDataAdapter.cargarVuelos()`.

---

## 8. `MonitoreoMapaController.java` *(nuevo)*
**Ruta:** `modules/envio/controller/MonitoreoMapaController.java`

```
GET  /simulacion/monitoreo/config
     → { K, Sa, Ta, Sc }  (leídos de BackendApplication)

GET  /simulacion/monitoreo/fecha-inicio
     → { fechaInicio: "2026-01-02T00:02:00" }
     Lee la primera línea de cada _envios_XXXX_.txt en classpath:data/_envios_preliminar_/
     Devuelve la fecha-hora mínima → el frontend la usa para auto-start sin selector de fecha.
     Formato línea: 000000001-20260102-00-02-OJAI-002-0017818
     Parseo:        parts[1]=yyyyMMdd  parts[2]=HH  parts[3]=mm

POST /simulacion/monitoreo/ejecutar?ventanaInicio={ISO}&ventanaFin={ISO}
     → llama alnsService.simularVentanaMonitoreo(inicio, fin)
     → { ventanaInicio, ventanaFin, totalEnvios, asignados, noAsignados, vuelos[] }
     Cada vuelo: { codigoVuelo, origen, destino, horaSalida, horaLlegada, totalMaletas }
```

El frontend dispara `/ejecutar` con `TA` minutos de antelación para que la respuesta llegue cuando el countdown llega a 0.

---

## Dependencias entre parámetros
```
Sc = K × SA              # minutos de pedidos consumidos por ciclo
SA > TA                  # el algoritmo debe terminar antes del siguiente ciclo
Ventana siguiente = ventanaActual + Sc minutos
```
