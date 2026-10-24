# Resiliencia, snapshot en memoria e idempotencia (guía reutilizable)

Patrón implementado en este backend (Quarkus 3.38.1 + Java 21 + DynamoDB +
Kafka) y pensado para copiarse a otros servicios, p. ej. el ERP textil.
Todo lo de este documento está implementado y probado aquí; las cifras son
mediciones reales sobre `docker compose` local (LocalStack).

> Estándar: Quarkus + SmallRye (no Spring/Resilience4j) — `docs/conventions.md`.

## 1. Problemas que resuelve

| Síntoma | Causa | Solución |
|---|---|---|
| Tras levantar la app, la primera consulta tarda ~1.2 s (la precarga en frío mide ~5 s) | Sin caché: cada lectura hace Scan/Query a DynamoDB y la primera paga conexión del cliente AWS/Netty y calentamiento de la JVM | Snapshot en memoria + precarga al arrancar |
| Si DynamoDB cae, toda consulta devuelve 500/503 | Lectura directa sin respaldo | Servir el último snapshot hasta `ttl + stale-if-error` |
| Con la base caída, las peticiones se quedan colgadas decenas de segundos | Timeouts + reintentos encadenados | Espera máxima de 2 s si hay respaldo + circuit breaker |
| Cada clic en "Generar reporte" crea un reporte y un mensaje Kafka nuevo | `UUID.randomUUID()` en cada POST | Id determinístico + escritura condicional |
| `descargar?formato=excel` → HTTP 500 en Docker | `autoSizeColumn` de POI usa AWT y el contenedor no tiene fuentes (`Fontconfig head is null`) | Calcular el ancho de columna por longitud de texto |

## 2. Arquitectura

```
Resource REST ─▶ Service ─▶ SnapshotCache.obtener(clave, carga)
                               │ fresco (< ttl)?  ──▶ memoria, 0 ms
                               │ si no ──▶ LecturaResiliente.ejecutar(carga)
                               │             @Retry ▶ @CircuitBreaker ▶ @Timeout ──▶ DynamoDB
                               │ falla de infraestructura y hay snapshot
                               │   dentro de (ttl + stale-if-error)?  ──▶ snapshot (degradado)
                               └ si no ──▶ InfraestructuraNoDisponibleException ──▶ HTTP 503

Escrituras (crear/actualizar/eliminar) ──▶ snapshot.invalidar(prefijo)
                                           (marca vencido, conserva el valor como respaldo,
                                            sube generacion())

POST /api/reportes/generar ──▶ id = UUID(app|area|formato|generacion|ventana)
                               PutItem attribute_not_exists(pk) ──▶ ya existe: mismo id, sin Kafka
```

Archivos (todos bajo `src/main/java/pe/edu/cibertec/proyemp/`):

| Archivo | Rol |
|---|---|
| `infra/SnapshotCache.java` | Snapshot, ventanas de tiempo, carga compartida, respaldo ante caída |
| `infra/LecturaResiliente.java` | `@Timeout` + `@Retry` + `@CircuitBreaker` para lecturas |
| `infra/SnapshotPrecarga.java` | Calienta el snapshot en `StartupEvent` |
| `service/CatalogoSimpleService.java` (+ 5 servicios), `MenuService`, `AtributoService` | Lecturas vía snapshot; escrituras invalidan |
| `service/RolService`, `SuministroService` | Solo invalidan `atributos:` al escribir |
| `repository/ReporteEjecucionRepository.crearPendienteSiNoExiste` | Escritura condicional (idempotencia) |
| `web/ReporteGenerarResource.java` | Id determinístico + reutilización |

## 3. Snapshot en memoria (`SnapshotCache`)

API mínima:

```java
<T> Uni<T> obtener(String clave, Supplier<Uni<T>> carga)
void invalidar(String prefijo)   // marca vencido, conserva respaldo, sube generacion()
long generacion()                // contador de escrituras que invalidaron
```

Uso en un servicio (catálogos):

```java
public Multi<T> listarTodos() {
    return snapshot.obtener(namespace + "todos",
                    () -> repositorio.listarTodos().collect().asList().onItem().transform(List::copyOf))
            .onItem().transformToMulti(lista -> Multi.createFrom().iterable(lista));
}
public Uni<T> crear(String nombre) {
    return validarNombre(nombre).chain(() -> repositorio.crear(nombre)).invoke(this::invalidar);
}
```

Reglas de diseño:

- **Dos ventanas.** `ttl` (60 s): se sirve sin tocar la base. `stale-if-error`
  (2 min): si recargar falla, se sirve el último snapshot hasta
  `ttl + stale-if-error` desde que se cargó. Quien cargó justo antes de la
  caída tiene como mínimo 2 min de servicio degradado.
- **Solo se cachean éxitos.** Un "no encontrado" o un error de dominio no entra
  al snapshot ni cuenta como fallo de infraestructura.
- **Carga compartida (single-flight).** Lecturas concurrentes de la misma clave
  comparten una sola ida a la base (`computeIfAbsent` + `memoize()`), sin
  estampida al vencer el TTL.
- **Espera acotada con respaldo.** Si hay snapshot utilizable, la lectura espera
  como máximo `max-wait-with-backup` (2 s) a la base; si no responde, sirve el
  snapshot. La carga sigue en segundo plano y lo refresca cuando la base vuelve.
- **Invalidación por prefijo** (`area:`, `menu:`, `atributos:`…). Marca vencido
  pero no borra: ante una caída posterior aún sirve de respaldo.
- **Dependencias entre cachés:** una escritura de catálogo, rol o suministro
  también invalida `atributos:` porque los atributos llevan nombres de
  cargo/rol denormalizados.
- Los fallos de infraestructura que activan el respaldo: `SdkException`,
  `TimeoutException` (FT y Mutiny), `CircuitBreakerOpenException`,
  `InfraestructuraNoDisponibleException`. Al agotar el respaldo se lanza
  `InfraestructuraNoDisponibleException`, que ya se mapea a **503**.

Límites conocidos:

- Es memoria **por instancia**: con varias réplicas cada una tiene su copia; el
  TTL corto acota la diferencia. Para caché compartido haría falta Redis/otro.
- Durante una caída se ve el dato anterior a cualquier escritura reciente.
- Un reinicio vacía el snapshot (la precarga lo vuelve a llenar).

## 4. Resiliencia (`LecturaResiliente`)

```java
@Timeout(value = 10, unit = ChronoUnit.SECONDS)
@Retry(maxRetries = 2, delay = 200, jitter = 100, retryOn = {SdkException.class, TimeoutException.class})
@CircuitBreaker(requestVolumeThreshold = 10, failureRatio = 0.5, delay = 10, delayUnit = ChronoUnit.SECONDS,
        successThreshold = 2, failOn = {SdkException.class, TimeoutException.class})
public <T> Uni<T> ejecutar(Supplier<Uni<T>> lectura) { return lectura.get(); }
```

- Dependencia: `io.quarkus:quarkus-smallrye-fault-tolerance` (versión del BOM).
- Orden de SmallRye FT: Retry envuelve a CircuitBreaker, que envuelve a
  Timeout. Cada intento reintentado cuenta contra el breaker; con el circuito
  abierto los reintentos se cortan.
- **Un solo breaker compartido** para todas las lecturas: hay una sola base.
- **Solo lecturas.** Las escrituras no se reintentan (podrían duplicar datos);
  siguen devolviendo 503 vía `UniFallosInfraestructura`.
- Solo infraestructura cuenta como fallo; las excepciones de dominio no
  reintentan ni abren el circuito.

**Lección aprendida (importante al portar):** con `@Timeout` de 3 s y umbral 6,
el arranque en frío (cliente AWS/Netty recién creado, 7 lecturas en paralelo)
excedía el timeout, el breaker se abría y la precarga fallaba por completo. Se
subió a 10 s / umbral 10. Si copias el patrón, mide primero tu arranque en frío
y no pongas el timeout por debajo.

## 5. Precarga al arrancar (`SnapshotPrecarga`)

`@Observes @Priority(3000) StartupEvent` — corre **después** de los bootstraps
de tabla/secretos (prioridad mayor = más tarde). Carga catálogos y los menús de
ambos tipos de usuario en paralelo con `Uni.join().all(...).andCollectFailures()`
y `.await().atMost(20 s)`. Si falla, solo registra un aviso: el arranque no se
cae y el snapshot se llena con las primeras consultas.

Log esperado: `Snapshot en memoria precargado en 5285 ms`.

## 6. Idempotencia de reportes

`POST /api/reportes/generar` calcula el id así:

```java
String clave = idAplicacion + "|" + idArea + "|" + formato + "|"
        + snapshot.generacion() + "|" + System.currentTimeMillis() / ventanaMs;
String id = UUID.nameUUIDFromBytes(clave.getBytes(UTF_8)).toString();
```

y crea la fila con `PutItem` condicional (`attribute_not_exists(pk)`):

- **Creó la fila** → publica `report.requested` en Kafka y devuelve el id.
- **Ya existía** (mismo reporte en la misma ventana) → **no publica nada**,
  devuelve el mismo id (PENDIENTE: ya viene; LISTO: ya está generado).
- **Existía pero está en ERROR** → reintenta con un id nuevo.
- La **generación** del snapshot entra en la clave: si se modificó un dato
  (catálogo, rol, suministro, atributo) el siguiente pedido produce un id nuevo,
  así nunca se entrega un reporte armado con datos anteriores.
- `app.reporte.idempotencia-ventana=PT0S` desactiva la idempotencia (id aleatorio).
- La condición de DynamoDB es atómica: dos clics simultáneos no crean dos filas.

Complementos que ya existían: el worker Kafka es idempotente por estado
(at-least-once, no regenera un reporte LISTO) y `descargar?formato=` reutiliza
el árbol persistido en `reporteJson`.

Límite: si el proceso muere entre crear la fila y publicar en Kafka, la fila
queda PENDIENTE hasta que cambie la ventana (≤ 5 min). Si se necesita más
rigor, el patrón es un outbox.

Recomendación de frontend: deshabilitar el botón mientras haya un reporte en curso.

## 7. Configuración (`application.properties`)

```properties
app.snapshot.ttl=PT60S                    # fresco
app.snapshot.stale-if-error=PT2M          # respaldo extra ante caída
app.snapshot.max-wait-with-backup=PT2S    # espera máx. a la base si hay respaldo (default en código)
app.reporte.idempotencia-ventana=PT5M     # PT0S = sin idempotencia
%test.app.snapshot.ttl=PT0S               # tests ven siempre el dato real
```

En `%test` el TTL fresco es 0 para que los tests que escriben por repositorio y
leen por REST no vean datos viejos; el respaldo ante caída sigue activo y se
prueba aparte.

## 8. Fix relacionado: Excel en Docker

`ExcelReportAdapter` usaba `XSSFSheet.autoSizeColumn`, que mide con
`java.awt.Font`. El contenedor no tiene fontconfig ni fuentes →
`RuntimeException: Fontconfig head is null` → HTTP 500 en
`GET /api/reportes/{id}/descargar?formato=excel` (solo ese formato, porque es el
que se regenera al pedirlo). Ahora `ajustarAnchoColumnas` calcula el ancho por
longitud de texto (piso 3000, tope 255 caracteres), sin AWT. Funcionaba en
Windows y por eso los tests no lo detectaron: **cualquier librería que use AWT
fallará en una imagen sin fuentes**.

## 9. Pruebas

- `infra/SnapshotCacheTest` (10 tests, unitarios, reloj controlado): hit dentro
  del TTL, recarga al vencer, respaldo con la base caída dentro de `ttl + 2 min`,
  503 fuera de esa ventana o sin snapshot, invalidación (recarga + respaldo),
  prefijos independientes, errores de dominio sin cachear, carga compartida,
  respuesta rápida con la base colgada.
- `web/ReporteGenerarIdempotenciaTest` (2 tests de integración): mismo id para
  peticiones idénticas; ids distintos si cambia formato o área.
- Suite completa: 109 tests, 0 fallos (`mvn test`, requiere Docker).

Prueba manual de caída (reproducible):

```bash
curl .../api/areas                       # recarga con la base arriba
docker stop entel-role-repository-localstack
sleep 65                                 # vence el TTL
curl .../api/areas                       # 200 con snapshot (primera ~2 s, luego ~50 ms)
sleep 125                                # supera ttl + 2 min
curl .../api/areas                       # 503
docker start entel-role-repository-localstack   # se recupera sola
```

## 10. Resultados medidos

| Prueba | Antes | Después |
|---|---|---|
| Consultas de tablas tras arrancar | ~1.2 s | 7–116 ms |
| 3 POST `/generar` idénticos | 3 reportes distintos | mismo `id` las 3 veces |
| Base caída, 1.ª petición con TTL vencido | sin respaldo (en el primer intento: 19 s) | 200 en 2.1 s con snapshot |
| Base caída, siguientes peticiones | sin respaldo | 40–100 ms |
| Base caída más de `ttl + 2 min` | sin respaldo | 503 |
| Base de vuelta | — | recuperación automática |

## 11. Cómo portarlo al ERP textil

1. Añade `quarkus-smallrye-fault-tolerance` al `pom.xml`.
2. Copia `SnapshotCache`, `LecturaResiliente` y `SnapshotPrecarga`; ajusta en
   `SnapshotPrecarga` la lista de servicios a calentar.
3. En cada servicio de **lectura frecuente y cambio raro** (catálogos, menús,
   maestros de artículos/colores/tallas, listas de precios): envuelve la lectura
   con `snapshot.obtener("<ns>:<clave>", () -> ...)`.
4. En cada **escritura** de esa entidad: `.invoke(() -> snapshot.invalidar("<ns>:"))`.
   Si otras entidades guardan copia denormalizada, invalida también su prefijo.
5. No caches lo que debe ser siempre exacto (stock en línea, saldos, login).
6. Para operaciones costosas disparadas por el usuario (reportes, cierres,
   exportaciones): id determinístico + escritura condicional + `generacion()` en
   la clave.
7. Define `app.snapshot.*` en `application.properties` y `%test.app.snapshot.ttl=PT0S`.
8. Mide tu arranque en frío antes de fijar el `@Timeout` del breaker (§4).
9. Copia `SnapshotCacheTest` y adapta las claves.
