# Sesión activa

> Este archivo refleja el estado de la **sesión actual**. Cuando cierres la
> sesión, mueve el contenido relevante a `history.md` y vacía esto.

## Feature en progreso

**Feature 12: `reactive_resilience_observability`** — in_progress (inicio y
cierre de implementación 19-ago-2026). Implementer termina, deja
explícitamente `in_progress` para que un reviewer humano/orquestador
verifique en disco antes de marcar `done` (mismo criterio que la feature 11).

Título: "Health checks, logging estructurado y resiliencia básica".

Descripción (`feature_list.json`, citada tal cual): "Health checks de
Quarkus (readiness/liveness) para DynamoDB (LocalStack), Kafka y Secrets
Manager. Logging estructurado (JBoss Logging) reemplazando cualquier
System.out.println heredado. Manejo explícito de fallos de Uni (onFailure)
en los endpoints principales, incluido el stream SSE (reconexión/cierre
limpio si el cliente se desconecta)."

### Baseline confirmado ANTES de tocar nada

`./init.sh` corrido primero: **80/80 tests verdes, BUILD SUCCESS** (features
1-11 `done`, feature 12 `pending`). Confirmado también que 1-11 están
`done` en `feature_list.json` antes de empezar, tal como exige el prompt de
esta feature.

### Diseño elegido, por punto del acceptance

**1. `GET /q/health/ready` reporta DOWN si LocalStack/Kafka no están
disponibles, UP si lo están.**

- **Kafka:** ya estaba cubierto automáticamente desde la feature 1 —
  `quarkus-messaging-kafka` (SmallRye Reactive Messaging) registra un
  health check de readiness por canal (`words-in`, `report-requested-in`,
  `report-ready-out`, etc.) sin código propio. Confirmado explorando
  `/q/health/ready` con un test descartable ANTES de escribir código nuevo
  (ver metodología abajo) — el body ya traía `"SmallRye Reactive Messaging
  - readiness check"` con los 6 canales en `[OK]`.
- **DynamoDB / Secrets Manager:** no existía ningún health check (confirmado
  inspeccionando los jars `quarkus-amazon-dynamodb`/
  `quarkus-amazon-secretsmanager` 3.21.1 con `unzip -l`, sin ninguna clase
  bajo `*health*`). Se agregan `infra/health/DynamoDbReadinessCheck.java` y
  `infra/health/SecretsManagerReadinessCheck.java`, ambos:
  - Implementan `io.smallrye.health.api.AsyncHealthCheck` (NO el
    `HealthCheck` síncrono de MicroProfile) — devuelven
    `Uni<HealthCheckResponse>`, coherente con la regla dura de este
    proyecto de nunca bloquear un hilo esperando una respuesta de red
    (un `HealthCheck` síncrono forzaría exactamente eso).
  - Hacen una llamada mínima y barata (`ListTables(limit=1)` /
    `ListSecrets(maxResults=1)`) — no dependen de que ninguna tabla/secreto
    concreto exista, solo de que el servicio responda.
  - Timeout explícito de 3s (`.ifNoItem().after(TIMEOUT).recoverWithItem(...)`)
    + `.onFailure().recoverWithItem(...)` — nunca dejan que un fallo real
    (o un hang) se propague sin control fuera del health check.
  - **Decisión documentada:** solo `@Readiness`, no `@Liveness`, para
    DynamoDB/Secrets Manager (aunque la descripción de la feature mencione
    "readiness/liveness" para las tres dependencias) — atar la liveness a
    una dependencia externa haría que Kubernetes reinicie el pod ante un
    problema transitorio de LocalStack, cuando lo correcto es sacarlo del
    balanceo sin matar el proceso. Mismo criterio que ya sigue Kafka
    (también solo readiness). Documentado en `docs/architecture.md` y en el
    Javadoc de ambas clases.
- **Cómo se probó el caso DOWN sin romper el arranque de la app:** apuntar
  `quarkus.dynamodb.endpoint-override`/`quarkus.secretsmanager.endpoint-override`
  a un puerto inválido para TODA la app rompería el arranque completo —
  `RoleRepositoryTableInitializer`/`MenuTableInitializer`/
  `AppSecretsBootstrap` (`@Observes StartupEvent`, features previas ya
  aprobadas) usan `.await().atMost(...)` sin ningún fallback si DynamoDB no
  responde en absoluto, así que `@QuarkusTest` ni siquiera arrancaría. En
  vez de eso, `DynamoDbReadinessCheckTest`/`SecretsManagerReadinessCheckTest`
  construyen un cliente AWS SDK **standalone** (fuera de CDI, con su propio
  `DynamoDbAsyncClient`/`SecretsManagerAsyncClient` apuntado a
  `http://127.0.0.1:1`, un puerto que nadie escucha) e instancian la clase
  de health check directamente con ese cliente roto — prueba el
  comportamiento real de la clase sin arriesgar el resto de la suite. El
  caso UP se prueba en la misma clase inyectando el bean real (CDI, contra
  el LocalStack de Dev Services). Además, `ReadinessHealthEndpointTest`
  confirma a nivel de endpoint HTTP real que `/q/health/ready` agrega los
  tres checks (DynamoDB, Secrets Manager, Kafka) todos en `UP`.

**2. Cero `System.out.println` en `src/main`.**

Confirmado por `grep -rn "System.out.println" src/main` (sin resultados)
ANTES de empezar y de nuevo al terminar — ya estaba así desde features
anteriores, ninguna clase nueva de esta feature lo introduce (todas usan
`org.jboss.logging.Logger`, igual que `ReporteRequestWorker`/
`AppSecretsBootstrap` ya establecían).

**3. Endpoints de reportes y de escritura (POST/PUT): manejo de error
explícito, código HTTP y mensaje claros ante fallo de DynamoDB/Kafka.**

- `domain/InfraestructuraNoDisponibleException.java` (nueva): excepción de
  dominio para "el sistema no pudo responder porque DynamoDB/Kafka no
  disponible" — deliberadamente distinta de las excepciones de negocio
  existentes (`CatalogoNoEncontradoException`, `ReporteNoListoException`,
  etc., que son 4xx porque el sistema SÍ respondió, solo que el dato no
  existe/no está listo).
- `web/UniFallosInfraestructura.java` (nueva): helper con
  `manejarFallosInfraestructura(Uni<T>, String contexto)` — aplica
  `.onFailure(predicado).transform(...)` **explícito** (literal de la
  descripción de la feature) envolviendo SOLO fallos de
  `software.amazon.awssdk.core.exception.SdkException` (DynamoDB/Secrets
  Manager) u `org.apache.kafka.common.KafkaException` (Kafka) —
  deliberadamente NO envuelve cualquier otro `Throwable` (un bug real,
  ej. `NullPointerException`) para no disfrazarlo de "reintente en unos
  segundos" (503) cuando en realidad es un 500 que alguien debe arreglar.
  Aplicado explícitamente en los tres endpoints que la descripción de la
  feature nombra por su rol ("endpoints de reportes"):
  `web/ReporteGenerarResource#generar` (`POST /api/reportes/generar` —
  cubre tanto el fallo de `crearPendiente` en DynamoDB como el nack de
  Kafka al publicar `report.requested`, ya que `Emitter.send` + `withNack`
  puede fallar con cualquier `KafkaException`), `web/ReporteResource#obtener`
  y `#descargar` (`GET /api/reportes/{id}` y `GET
  /api/reportes/{id}/descargar`).
- `web/exception/InfraestructuraExceptionMappers.java` (nueva,
  `@ServerExceptionMapper`, mismo patrón ya usado por
  `CatalogoExceptionMappers`/`RolExceptionMappers`/`ReporteExceptionMappers`)
  mapea `InfraestructuraNoDisponibleException` a 503, **y además** mapea
  `SdkException`/`KafkaException` sin envolver directamente a 503 con un
  mensaje claro. Esta segunda parte es la decisión de diseño no trivial de
  esta feature: en vez de repetir `UniFallosInfraestructura` en cada uno de
  los ~10 recursos REST de catálogos/dominio (Área, Cargo, Ubicación,
  Aplicación, Estado, Rol, Suministro, Atributo, Usuario, Menú) para
  obtener el mismo resultado, se centraliza en el mapper: RESTEasy Reactive
  despacha una falla de `Uni` a un `@ServerExceptionMapper` que matchee su
  tipo exactamente igual que si la excepción se hubiera lanzado de forma
  síncrona (es el mismo mecanismo que YA usan `CatalogoNoEncontradoException`
  et al., que tampoco pasan por ningún `.onFailure()` explícito en cada
  recurso — se lanzan dentro de `.onItem().transform()` y el mapper las
  atrapa). Evaluado y descartado repetir el wrapper 10 veces: sería
  boilerplate idéntico sin ganar nada, ya que el resultado HTTP final es el
  mismo. Documentado explícitamente en `docs/architecture.md` para que quede
  claro que es una decisión deliberada, no un olvido.
- Verificación: `InfraestructuraExceptionMappersTest` (unit test puro, sin
  `@QuarkusTest`, instancia el mapper directo y confirma 503 + mensaje para
  los 3 métodos) y `UniFallosInfraestructuraTest` (unit test puro sobre el
  helper con `Uni.createFrom().failure(...)`, incluye la prueba de que un
  fallo NO relacionado a infraestructura — `IllegalStateException` — pasa
  sin envolver). Se evitó deliberadamente romper DynamoDB/Kafka reales a
  mitad de la suite de integración por el mismo motivo que el punto 1
  (rompería el arranque de los bootstrap beans) — los tests unitarios
  prueban la lógica de clasificación/mapeo de forma determinista sin ese
  riesgo.

**4. El endpoint SSE cierra la conexión limpiamente sin excepción no
controlada cuando el cliente se desconecta (test que simula la
desconexión).**

- `web/ReporteResource#eventos()` agrega `.onCancellation().invoke(() ->
  LOG.debug(...))` (dispara cuando RESTEasy Reactive cancela la suscripción
  al `Multi` porque el cliente cerró la conexión SSE) y
  `.onFailure().invoke(failure -> LOG.warn(...))` defensivo (hoy
  `ReporteEventoBroadcaster` nunca falla, pero si en el futuro lo hiciera,
  queda logueado en vez de perderse silenciosamente).
- `web/ReporteResourceSseDisconnectTest` (nueva): se suscribe directo al
  `Multi` que devuelve `reporteResource.eventos()` (el MISMO objeto que
  RESTEasy Reactive expone en `GET /api/reportes/eventos`, con los
  operadores de arriba ya aplicados) usando `AssertSubscriber`, y llama
  `.cancel()` — exactamente lo que hace RESTEasy Reactive cuando el socket
  SSE se cierra del lado del cliente, sin necesidad de un socket HTTP real
  para simularlo. Dos tests: (a) la cancelación no deja
  `subscriber.getFailure()` con nada (ninguna excepción no controlada);
  (b) el `BroadcastProcessor` compartido sigue sano para un segundo
  cliente conectado DESPUÉS de que el primero se desconectó (la
  desconexión de uno no corrompe el stream para los demás).

**Nota no bloqueante heredada de la feature 11 (resuelta en esta feature,
no exigida por el acceptance de la 12 pero explícitamente pedida en el
prompt de esta sesión):** `messaging/ReporteRequestWorker#publicarReady`/
`publicarFailed` invocaban `eventoBroadcaster.emitir(...)` directo dentro
de `.invoke(...)` — si esa llamada lanzara, la excepción habría contaminado
el `Uni<Void>` que devuelve el método, y `procesar()` habría interpretado
como fallo de generación un reporte que en realidad ya se había generado y
persistido correctamente en Kafka/DynamoDB (el `ackFuture` de
`report.ready`/`report.failed` ya había completado antes de llegar a esa
línea). Se agregó `emitirEventoSeguro(...)` (try/catch alrededor de
`emitir`, log en `WARN`, nunca relanza) y se usa en ambos métodos.
Verificado con `web/ReporteRequestWorkerSseIsolationTest` (nueva):
construye un `ReporteRequestWorker` con un `ReporteEventoBroadcaster` que
siempre lanza `IllegalStateException` en `emitir(...)` y confirma que el
reporte de todas formas termina `estado=LISTO` con `intentos=1` (sin
reintento espurio) — visible en el log de la corrida real
(`WARN [...ReporteRequestWorker] Fallo al emitir el evento SSE LISTO ...
-- ignorado, no afecta el resultado ya confirmado en Kafka/DynamoDB`),
confirmando que el fix funciona exactamente como se diseñó.

**5. `mvn test` sin errores.**

Ver "Ejecución real" abajo.

### Metodología: exploración antes de escribir código (por qué no se
adivinó nada)

- El baseline de `/q/health/ready` (qué checks ya existían) se confirmó con
  un test descartable temporal (`ScratchHealthProbeTest`, borrado antes de
  este commit) que imprimía el body real via `System.err` (permitido en
  `src/test`, la regla de "cero `System.out.println`" es solo sobre
  `src/main`) — reveló que Kafka ya estaba cubierto y que no había ningún
  check de DynamoDB/Secrets Manager, evitando escribir un check redundante
  para Kafka.
- Se inspeccionaron con `unzip -l`/`javap` los jars reales
  (`smallrye-health-api-4.3.0.jar`, `mutiny-3.3.0.jar`,
  `smallrye-reactive-messaging-kafka-4.36.0.jar`,
  `quarkus-amazon-dynamodb-3.21.1.jar`) para confirmar, antes de escribir
  código: que `AsyncHealthCheck` existe y su firma exacta
  (`Uni<HealthCheckResponse> call()`), que `Multi.onCancellation()`
  devuelve `MultiOnCancel` (no `MultiOnCancellation`, un nombre que no
  existe), que `Uni.ifNoItem().after(Duration)` devuelve `UniOnTimeout` con
  `recoverWithItem(...)`, y que los adaptadores AWS Quarkiverse no traen
  ningún health check propio.
- **Bug de diseño propio detectado y corregido durante la implementación
  (no llegó a `git`):** el primer borrador de `DynamoDbReadinessCheck`
  intentaba mezclar un `String` y un `HealthCheckResponse` en la misma
  cadena `Uni` (`onFailure().recoverWithItem(UniHealthFailures::mensaje)`
  seguido de un `transform` con `instanceof`) — no compilaba tipo-seguro de
  forma limpia. Se simplificó a transformar primero
  `ListTablesResponse -> HealthCheckResponse` y recién ENTONCES aplicar
  `ifNoItem()`/`onFailure()` sobre `Uni<HealthCheckResponse>` consistente,
  sin necesidad de una clase auxiliar.
- **Bug de CDI detectado en la primera corrida de `mvn test` (sí llegó a
  compilar, falló en el build de Arc):** `@Inject
  DynamoDbReadinessCheck readinessCheck;` en el test fallaba con
  `Unsatisfied dependency ... qualifiers [@Default]` — `@Readiness` de
  MicroProfile Health es un **qualifier CDI real** (no solo un marcador),
  así que el bean queda con qualifiers `{@Any, @Readiness}` (sin
  `@Default`) y el punto de inyección debe repetir `@Readiness` para que
  Arc lo resuelva. Corregido en ambos tests de health check. Documentado
  con un comentario en el propio test para que no se repita el error.

### Archivos nuevos

- `src/main/java/pe/edu/cibertec/proyemp/domain/InfraestructuraNoDisponibleException.java`
- `src/main/java/pe/edu/cibertec/proyemp/web/UniFallosInfraestructura.java`
- `src/main/java/pe/edu/cibertec/proyemp/web/exception/InfraestructuraExceptionMappers.java`
- `src/main/java/pe/edu/cibertec/proyemp/infra/health/DynamoDbReadinessCheck.java`
- `src/main/java/pe/edu/cibertec/proyemp/infra/health/SecretsManagerReadinessCheck.java`
- `src/test/java/pe/edu/cibertec/proyemp/infra/health/DynamoDbReadinessCheckTest.java`
- `src/test/java/pe/edu/cibertec/proyemp/infra/health/SecretsManagerReadinessCheckTest.java`
- `src/test/java/pe/edu/cibertec/proyemp/ReadinessHealthEndpointTest.java`
- `src/test/java/pe/edu/cibertec/proyemp/web/exception/InfraestructuraExceptionMappersTest.java`
- `src/test/java/pe/edu/cibertec/proyemp/web/UniFallosInfraestructuraTest.java`
- `src/test/java/pe/edu/cibertec/proyemp/web/ReporteResourceSseDisconnectTest.java`
- `src/test/java/pe/edu/cibertec/proyemp/web/ReporteRequestWorkerSseIsolationTest.java`

### Archivos modificados

- `src/main/java/pe/edu/cibertec/proyemp/web/ReporteGenerarResource.java`
  (`.onFailure()` explícito en `generar()`)
- `src/main/java/pe/edu/cibertec/proyemp/web/ReporteResource.java`
  (`.onFailure()` explícito en `obtener()`/`descargar()`;
  `.onCancellation()`/`.onFailure()` en `eventos()`)
- `src/main/java/pe/edu/cibertec/proyemp/messaging/ReporteRequestWorker.java`
  (`emitirEventoSeguro(...)` aislando el puente SSE)
- `feature_list.json` (feature 12: `pending` -> `in_progress`)
- `docs/architecture.md` (nueva subsección "Health checks, logging
  estructurado y resiliencia básica")

### Ejecución real (confirmado en disco, no asumido)

1. **Baseline:** `./init.sh` ANTES de tocar nada — 80/80 tests verdes,
   BUILD SUCCESS (confirmado en el log real de esta sesión).
2. `mvn -q clean compile` tras cada tanda de cambios — sin errores.
3. `mvn -q test-compile` — un error real detectado y corregido (ver bug de
   CDI arriba, y el error de visibilidad de paquete de
   `ReporteRequestWorkerSseIsolationTest` al intentar usar los helpers
   `static` package-private de `RolResourceTest` desde el paquete
   `messaging` — se movió el test al paquete `web`, donde ya viven
   `ReporteGenerarResourceTest`/`ReporteResourceTest` que usan los mismos
   helpers).
4. `mvn test` completo — **95/95 tests verdes, BUILD SUCCESS**
   (80 preexistentes + 15 nuevos de esta feature: 2 + 2 +
   1 + 3 + 4 + 2 + 1 = 15, ver desglose de archivos nuevos arriba).
5. `./init.sh` completo de nuevo (build + test desde cero) para la
   verificación final exigida por el prompt de esta sesión — **95/95
   tests verdes, BUILD SUCCESS, "Total time: 58.515 s"** (log completo
   guardado durante la sesión). Sin regresiones en los 80 tests
   preexistentes — mismos nombres, mismos conteos por clase que el
   baseline.
6. Ruido esperado en el log (ya documentado como benigno en reviews
   anteriores): poison-pill de `ReporteGenerarResourceTest` (área
   inexistente, 2 reintentos con `CatalogoNoEncontradoException` +
   `UNKNOWN_TOPIC_OR_PARTITION` de arranque de Kafka), warning de Log4j de
   Apache POI. **Ruido NUEVO esperado de esta feature** (no es un fallo):
   la línea `WARN [...ReporteRequestWorker] Fallo al emitir el evento SSE
   LISTO ... -- ignorado, no afecta el resultado ya confirmado en
   Kafka/DynamoDB: java.lang.IllegalStateException: fallo simulado del
   puente SSE` — es exactamente lo que produce
   `ReporteRequestWorkerSseIsolationTest` a propósito, prueba visible en el
   log de que el aislamiento funciona.
7. Grep final de `System.out.println` en `src/main` — sin resultados,
   confirmado de nuevo tras todos los cambios.

### Próximos pasos

1. Feature 12 queda `in_progress`, lista para revisión — no marcada `done`
   por el implementer (mismo criterio que la feature 11).
2. Reviewer: confirmar en disco `./init.sh` (95/95 esperado) y revisar la
   decisión de centralizar el manejo de fallos de escritura en
   `InfraestructuraExceptionMappers` en vez de repetir
   `UniFallosInfraestructura` en los ~10 recursos de catálogos/dominio —
   documentada como deliberada en `docs/architecture.md`, pero es la
   decisión de diseño más discutible de esta feature y merece una segunda
   opinión.
3. Sigue pendiente, heredado, fuera de scope de esta feature: coordinación
   Leader↔Leader backend↔frontend sobre los nombres de campo de
   `domain/Reporte`/`domain/DetalleRol` (feature 10) — ver `handoff.md`.
4. Feature 13 (`reporte_final_mejoras`, `MEJORAS.md`) queda como la única
   `pending` restante tras esta feature, condicionada a que 1-12 estén
   `done`.
