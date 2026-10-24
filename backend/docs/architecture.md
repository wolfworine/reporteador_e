# Arquitectura — entel-role-repository-backend

## Arquitectura actual (JSF 2.2 + PrimeFaces 5.2 + Spring 4 + EclipseLink JPA sobre MySQL 5.6, Java 7/8, Tomcat 7)

- **Capas:** `managedbean/` (JSF, `@ManagedBean @SessionScoped`) →
  `service/` → `repository/` (Spring Data JPA `CrudRepository`) →
  `model/` (entidades JPA, EclipseLink).
- **Módulos/managed beans reales (13):** Area, Cargo, Ubicacion, Aplicacion,
  Rol, Suministro, Atributo, Detalle, Usuario, Login, Menu(Controller),
  Navigation, Plantilla(Controller), Reporte.
- **Flujo de datos de reportes:** `ReporteManagedBean.reporte()` arma una
  lista de `Reporte` (DTO no persistente: cargo, área, códigos,
  `List<DetalleRol>`, contadores) recorriendo `Cargo → Atributo →
  (Aplicacion, Rol, Suministro, Detalle)`; `imprimir()` renderiza esa
  estructura a PDF con iText, escribiendo en disco local del servidor.
- **Integraciones externas:** solo MySQL (JDBC directo, sin colas, sin
  cache, sin servicios de terceros).
- **Puntos de entrada:** únicamente vistas JSF (`paginas/<modulo>/*.xhtml`),
  no hay API REST.
- **Decisiones a preservar conceptualmente** (aunque cambie la tecnología):
  la agrupación de reporte por Cargo→Rol→4 categorías de suministro
  (Roles/Grupos/Inbox/DefaultInbox) es el contrato funcional central del
  sistema — cualquier feature de reportes debe reproducir esta forma, no
  inventar una nueva.

## Arquitectura objetivo (Quarkus 3.x reactivo + Java 21 + Kafka + DynamoDB/LocalStack + AWS Secrets Manager/KMS)

```
┌─────────────┐   REST/JSON    ┌────────────────────────────────────┐
│  Frontend   │ ─────────────▶ │  Quarkus API (Java 21, reactivo)     │
│  React      │ ◀───────────── │  - Endpoints REST -> Uni<Response>    │
└─────────────┘                │  - Domain services -> Uni/Multi        │
                                │  - report/ (puerto+adaptadores)         │
                                │    iText/POI (bloqueante) offload       │
                                │    a worker pool (Virtual Threads)      │
                                └───────┬────────────────┬───────────────┘
                                        │                │
                        DynamoDbAsyncClient       Kafka (reactive messaging)
                        (LocalStack en local)     report.requested / report.ready
                                        ▼                ▼
                                 ┌───────────┐    ┌────────────────────┐
                                 │ DynamoDB  │    │ Report Worker        │
                                 │ tables    │    │ (mismo servicio,      │
                                 └───────────┘    │ @Incoming, Uni/Multi,  │
                                                   │ offload bloqueante a   │
                                                   │ worker pool virtual-   │
                                                   │ thread-backed)         │
                                                   └────────────────────┘
        Secretos (endpoints DynamoDB/Kafka, credenciales) ← AWS Secrets Manager + KMS
                                (emulados en local con LocalStack)
```

- **Qué cambia:** persistencia relacional → DynamoDB (acceso async);
  síncrono → Kafka para reportes; secretos hardcodeados → AWS Secrets
  Manager/KMS; modelo bloqueante → **reactivo con SmallRye Mutiny** de
  punta a punta; sin API → API REST consumida por el frontend React.
- **Qué se mantiene:** el modelo de dominio conceptual (mismas entidades y
  relaciones, mismos nombres de campo donde sea razonable para trazabilidad
  con el legacy), la forma del reporte (Cargo→Rol→4 categorías), y Java 21
  como runtime (sus Virtual Threads se reutilizan como worker pool para el
  trabajo bloqueante que Mutiny no puede evitar — iText/POI no tienen API
  reactiva nativa).

### Modelo de datos DynamoDB — diseño canónico en el track `database/`

**El diseño de tablas DynamoDB (single-table, PKs/SKs, GSIs, generación de
IDs) se decidió y documenta en
`../database/docs/architecture.md`, no aquí** (separado a pedido del
usuario el 09-ago-2026 en un track propio con su propio agente). Este
backend es *consumidor* de esas tablas — antes de implementar cualquier
feature de dominio que lea/escriba DynamoDB, confirmar que
`../database/feature_list.json` → feature `design_dynamodb_schema` está
`done`. Si no lo está, la feature correspondiente de este backend queda
`blocked` citando esa dependencia — no se improvisa un esquema propio acá.

### Por qué no R2DBC

Se evaluó explícitamente (pedido del usuario, 09-ago-2026): R2DBC es el
estándar reactivo para bases **relacionales** (Postgres, MySQL, etc.); no
existe ni aplica un driver R2DBC para DynamoDB, que no es relacional. El
acceso reactivo correcto — y el que usa este proyecto — es
`DynamoDbAsyncClient` (AWS SDK v2) envuelto en `Uni` vía
`Uni.createFrom().completionStage(...)`. No hay ninguna dependencia de
R2DBC en `pom.xml`.

### Resolución de secretos por ambiente (feature 11, `aws_secrets_manager_integration`, implementada)

**Auditoría de `application.properties`/`docker-compose.yml` (features 1-10):**
confirmado por grep que no hay ninguna credencial/endpoint real hardcodeado
-- solo las credenciales dummy `test`/`test` que exige el AWS SDK v2 para
hablar con LocalStack (`docker-compose.yml`, comentario explícito desde la
feature 2) y nombres de propiedades Quarkus (`quarkus.secretsmanager.*`,
`quarkus.dynamodb.*`). Ningún hallazgo que corregir en ese sentido.

**Bug real encontrado y corregido en esta feature:** las claves
`%dev.quarkus.dynamodb.sdk.endpoint-override` y
`%dev.quarkus.secretsmanager.sdk.endpoint-override` (desde la feature 2)
usaban un segmento `sdk.` que no existe en `quarkus-amazon-dynamodb`/
`quarkus-amazon-secretsmanager` 3.21.1 (versión real resuelta por el BOM de
este proyecto, confirmado inspeccionando
`META-INF/quarkus-config-doc/quarkus-config-model.json` empaquetado en cada
jar de extensión) -- la propiedad real es
`quarkus.<servicio>.endpoint-override`, sin `sdk.`. Quarkus no falla el
build por una key desconocida, solo la ignora, así que el síntoma nunca se
detectó: `mvn test` usa Dev Services (que inyecta su propio
`endpoint-override` correcto en tiempo de ejecución, sin pasar por
`application.properties`) y ningún test ejercitaba el perfil `%dev`. En la
práctica esto rompía el flujo manual documentado (`docker-compose up -d
localstack` + `mvn quarkus:dev`): el cliente real habría intentado hablar
con AWS de verdad (con credenciales dummy, así que habría fallado por auth,
no habría filtrado nada, pero tampoco habría funcionado contra LocalStack).
Corregido a `quarkus.<servicio>.endpoint-override` en ambas líneas.

**Mecanismo de resolución de al menos un secreto real de la app en tiempo de
arranque (acceptance criterion 2):** `infra/AppSecretsBootstrap.java`
(`@ApplicationScoped`, observador de `StartupEvent`, mismo patrón ya
aprobado dos veces para `repository/RoleRepositoryTableInitializer` y
`repository/MenuTableInitializer`) resuelve al arrancar Quarkus el secreto
`app/dynamodb/table-name` desde Secrets Manager: lee con `GetSecretValue`
vía `SecretsManagerAsyncClient`; si no existe todavía (primer arranque de
un LocalStack vacío), lo crea con el valor de
`RoleRepositoryTable.TABLE_NAME` (`"RoleRepository"`) y vuelve a leerlo --
el valor final expuesto siempre proviene de una llamada `GetSecretValue`
real, nunca de la constante en memoria usada para la creación. Es un
ejemplo **demostrativo** del mecanismo (el nombre de una tabla DynamoDB no
es, por sí mismo, un secreto de negocio real) -- deliberadamente no se
reemplazó `RoleRepositoryTable.TABLE_NAME` en los ~10 repositorios
existentes que ya la usan como constante, para no reabrir ocho features
previas ya aprobadas fuera del scope de esta feature. Verificado con
`infra/AppSecretsBootstrapTest.java` (`@QuarkusTest`, LocalStack real vía
Dev Services): confirma que el valor resuelto coincide con una consulta
independiente a Secrets Manager (no con un valor hardcodeado) y que una
segunda resolución es idempotente.

**Por qué un bean de arranque y no el `ConfigSourceFactory` nativo de la
extensión:** `quarkus-amazon-secretsmanager` 3.21.1 (confirmado
inspeccionando su jar, clases
`io.quarkiverse.amazon.secretsmanager.runtime.SecretsManagerConfigSourceFactory`/
`SecretsManagerConfigSource`) sí ofrece un mecanismo nativo --
`quarkus.secretsmanager.config.secrets."<propiedad>"=<nombre-del-secreto>`
-- que inyecta valores resueltos de Secrets Manager directamente como
propiedades de configuración Quarkus (consumibles con `@ConfigProperty`
normal). No se usó aquí porque ese `ConfigSourceFactory` resuelve los
valores **antes** de que arranque el contenedor CDI, durante el bootstrap
de configuración de Quarkus -- lo que exigiría que el secreto ya existiera
en Secrets Manager antes del primerísimo arranque de la app, algo
incómodo de garantizar (y de probar de forma no fragile) contra el
LocalStack efímero que Dev Services levanta en el mismo proceso que esa
misma resolución de config para `@QuarkusTest`. El bean de arranque
(`@Observes StartupEvent`) autoprovisiona el secreto si falta y lo lee de
vuelta con una llamada real, 100% verificable dentro de un único arranque
de test, sin depender de que una corrida anterior haya dejado el secreto
sembrado. Confirmado además (por análisis del bytecode del
`ConfigSourceFactory` nativo) que ese mecanismo es *fail-open*: si el
secreto no existe, registra un error y sigue sin lanzar excepción -- no
representa un riesgo si en el futuro se decide habilitarlo además de este
bean.

**Resolución por ambiente:**

| Ambiente | Endpoint Secrets Manager/DynamoDB | Credenciales | Estado |
|---|---|---|---|
| `%dev` (`docker-compose up -d` + `mvn quarkus:dev`) | `http://localhost:4566` (LocalStack manual) | Estáticas dummy `test`/`test` | ✅ Funcional (bug de `sdk.endpoint-override` corregido en esta feature). Desde 2026-08-25 `docker-compose.yml` también trae Kafka (`%dev.kafka.bootstrap.servers=localhost:9092`), así que `docker-compose up -d` levanta LocalStack + Kafka juntos y `mvn quarkus:dev` ya no dispara su propio broker efímero vía Dev Services/Testcontainers. |
| `%test` (`mvn test`) | LocalStack efímero autoprovisionado por Quarkus Dev Services | Inyectadas automáticamente por Dev Services (dummy) | ✅ Funcional, sin infraestructura manual |
| `%prod` (no configurado todavía en `application.properties`) | AWS Secrets Manager/DynamoDB reales (sin `endpoint-override`, el SDK usa los endpoints públicos de AWS por defecto) | **Pendiente de credenciales productivas** -- sin bloque `%prod.quarkus.*.aws.credentials.*` en el repo; el AWS SDK caería al `DefaultCredentialsProvider` (rol IAM/variables de entorno del entorno de despliegue real), nunca credenciales estáticas versionadas | ⚠️ Pendiente -- documentado explícitamente, no se inventan credenciales productivas (regla dura de `AGENTS.md`/`docs/conventions.md`) |

### Reportes: asíncrono de punta a punta, notificación por SSE (corregido 09-ago-2026)

**No existe un endpoint síncrono de descarga directa como flujo principal.**
El único punto de entrada público es asíncrono, y la notificación al
frontend es **push (SSE)**, no polling:

```
Frontend --POST /api/reportes/generar (202 Accepted + id)--> Backend
                                                                  │
                                                          publica report.requested (Kafka)
                                                                  │
                                                     Worker consume, genera PDF/Excel
                                                     (offload bloqueante a worker pool),
                                                     guarda resultado, publica report.ready
                                                                  │
Frontend <--GET /api/reportes/eventos (SSE, Multi<ReporteEvento>)--┘
       │ (recibe evento report.ready para su id)
       ▼
GET /api/reportes/{id}/descargar → binario
```

- `POST /api/reportes/generar` devuelve `202 Accepted` + `id` de
  seguimiento — nunca el archivo directamente.
- `GET /api/reportes/eventos` es un endpoint SSE (`Multi<ReporteEvento>`,
  `@RestStreamElementType(MediaType.SERVER_SENT_EVENTS)`) donde el backend
  empuja el evento en cuanto el reporte pasa a `LISTO`/`ERROR` — el
  frontend no hace polling. **Implementado en la feature 10
  (`reportes_notificacion_sse_y_descarga`): el endpoint SSE NO consume
  Kafka directamente por request** (crearía un consumer group nuevo por
  cada conexión abierta, ineficiente y sin necesidad, ver
  `docs/kafka-reactive-guidance.md` §"SSE — relación con Kafka"). En vez de
  eso, `messaging/ReporteRequestWorker` (el único consumidor real de
  `report.requested`/publicador de `report.ready`/`report.failed`, feature
  9) alimenta un puente interno per-proceso
  (`messaging/ReporteEventoBroadcaster`, un `BroadcastProcessor<ReporteEvento>`
  de Mutiny) inmediatamente después de confirmar cada publicación Kafka;
  `web/ReporteResource#eventos()` expone ese mismo `Multi` en multicast a
  todas las conexiones SSE activas. Kafka sigue siendo la fuente de verdad
  asíncrona entre servicios — el `Multi` interno es solo el puente hacia
  las conexiones SSE de este mismo proceso. **Limitación conocida:** si el
  backend escala a múltiples instancias, un cliente SSE conectado a la
  instancia A no vería un evento generado por la instancia B (el
  `BroadcastProcessor` vive en memoria de un solo proceso) — fuera de
  scope de la feature 10, documentado también en
  `docs/kafka-reactive-guidance.md`.
- **Por qué SSE y no WebSocket:** se eligió SSE sobre WebSocket porque el
  canal es unidireccional (servidor → cliente) — el frontend nunca necesita
  enviar datos de vuelta por este canal, solo recibir la notificación de
  que un reporte cambió de estado. WebSocket exige un protocolo
  bidireccional full-duplex (handshake `Upgrade`, framing propio, manejo
  explícito de ambos sentidos) que aquí sería complejidad sin beneficio: no
  hay ningún dato que el cliente necesite empujar por este mismo canal
  (la única entrada del cliente, `POST /api/reportes/generar`, ya es un
  endpoint REST normal, separado). SSE además reconecta automáticamente
  vía el propio `EventSource` del navegador (backoff nativo) y viaja sobre
  HTTP/1.1 plano, sin infraestructura adicional de proxy/load balancer que
  WebSocket suele requerir. Si en el futuro se necesita que el cliente
  empuje datos por el mismo canal (no el caso hoy), revisar esta decisión.

#### Tópicos Kafka (feature 9, `reportes_generacion_async_kafka`, implementada)

Tres tópicos, todos con el `id` (UUID) del reporte como **clave de
partición** (nunca un valor aleatorio ni el id del usuario) — garantiza que
los eventos de un mismo reporte (`requested`→`ready`/`failed`) se procesen
en orden en la misma partición (ver `docs/kafka-reactive-guidance.md`):

| Tópico            | Productor                              | Consumidor                              | Payload                                              |
|--------------------|-----------------------------------------|-------------------------------------------|-------------------------------------------------------|
| `report.requested` | `web/ReporteGenerarResource` (`POST /api/reportes/generar`) | `messaging/ReporteRequestWorker` (`@Incoming("report-requested-in")`) | `{reporteId, idAplicacion, idArea, formato}` |
| `report.ready`     | `messaging/ReporteRequestWorker` (tras persistir `estado=LISTO`) | Sin consumidor Kafka dedicado (feature 10): el mismo `ReporteRequestWorker` que publica también alimenta `messaging/ReporteEventoBroadcaster` (puente SSE interno), expuesto por `web/ReporteResource#eventos()` | `{reporteId}` |
| `report.failed`    | `messaging/ReporteRequestWorker` (tras agotar reintentos, `estado=ERROR`) | Idem — `ReporteEventoBroadcaster` (feature 10) | `{reporteId, motivo}` |

Cada tópico tiene su propio par de nombres de canal Quarkus/SmallRye
entrada/salida (`report-requested-out`/`report-requested-in`,
`report-ready-out`, `report-failed-out` — ver `application.properties`),
mismo patrón que el demo `words-in`/`words-out`: un proceso no puede tener
`@Incoming` y `@Outgoing` sobre el mismo nombre de canal sin ambigüedad,
aunque ambos apunten al mismo tópico físico.

**Garantía de entrega — at-least-once + consumidor idempotente** (decisión
explícita, no exactly-once transaccional, ver
`docs/kafka-reactive-guidance.md` para el razonamiento completo):
productor con `acks=all` + `enable.idempotence=true` explícitos en los tres
tópicos; consumidor de `report.requested` verifica el `estado` persistido
antes de regenerar (dedupe ante redelivery); `commit-strategy=throttled`
(nunca auto-commit/`latest`); reintentos acotados a 3 (contador `intentos`
persistido en DynamoDB, sobrevive a un crash-restart del worker) antes de
`estado=ERROR` + `report.failed`, y el offset siempre avanza tras un fallo
de negocio manejado (la partición no se atasca) — un fallo de
infraestructura real sí se propaga sin capturar, para que ahí Kafka
reintregue genuinamente.

**Persistencia del resultado** (tabla única `RoleRepository`,
`PK=REPORTE#<id>`/`SK=METADATA`, ver
`repository/ReporteEjecucionRepository.java`): binario generado (atributo
`B` nativo de DynamoDB) + el árbol `List<Reporte>` que lo originó
(serializado a JSON en `reporteJson`) + `estado`
(PENDIENTE/LISTO/ERROR), recuperables por `id` sin volver a ensamblar ni
regenerar nada — contrato que la feature 10 (`GET /api/reportes/{id}`,
descarga, SSE) consume tal cual, sin rediseñar la persistencia.
**Limitación conocida:** un ítem DynamoDB no puede superar 400KB totales;
un reporte cuyo binario+JSON exceda ese límite fallaría al persistirse. No
se resuelve en esta feature (mover el binario a S3 está fuera de scope de
`docs/migration-plan.md`) — documentado como limitación conocida.

#### Feature 10 (`reportes_notificacion_sse_y_descarga`, implementada) — endpoints de consulta

`web/ReporteResource` (coexiste con `web/ReporteGenerarResource` en el
mismo `@Path("/api/reportes")`, sin colisión de rutas) expone los tres
endpoints que cierran el flujo asíncrono:

- `GET /api/reportes/eventos` — SSE, ver el punto de arriba sobre el
  puente `ReporteEventoBroadcaster`.
- `GET /api/reportes/{id}` — deserializa `reporteJson` (ya persistido por
  la feature 9) a `List<Reporte>` y lo devuelve tal cual; `404` si el id no
  existe (`ReporteEjecucionNoEncontradaException`) o si `estado != LISTO`
  (`ReporteNoListoException`, nueva en esta feature) — ambas mapeadas a 404
  en `web/exception/ReporteExceptionMappers`.
- `GET /api/reportes/{id}/descargar` — sirve `ejecucion.binario()` con
  `Content-Disposition: attachment` y el `Content-Type` según
  `ejecucion.formato()` (`application/pdf` o
  `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` para
  `.xlsx`, coherente con `ExcelReportAdapter`); mismos 404 que arriba.

### Health checks, logging estructurado y resiliencia básica (feature 12, `reactive_resilience_observability`, implementada)

**Health checks (`GET /q/health`, `/q/health/ready`, `/q/health/live`, expuestos por `quarkus-smallrye-health` desde la feature 1):**

| Dependencia | Tipo de check | Clase | Cómo verifica |
|---|---|---|---|
| Kafka (todos los canales `words-*`/`report-*`) | Readiness | Automático (SmallRye Reactive Messaging, `quarkus-messaging-kafka`) | Cada canal `@Incoming`/`@Outgoing` reporta si está suscrito/conectado al broker — sin código propio, confirmado explorando `/q/health/ready` antes de escribir código nuevo (ver `progress/current.md`) |
| DynamoDB (LocalStack) | Readiness | `infra/health/DynamoDbReadinessCheck` (nueva) | `AsyncHealthCheck` (SmallRye Health, `Uni<HealthCheckResponse>`) que llama `ListTables` con `limit(1)` contra `DynamoDbAsyncClient`; `ifNoItem().after(3s)` + `onFailure()` recuperan a `DOWN` con el mensaje de la causa en `data.detalle` |
| AWS Secrets Manager (LocalStack) | Readiness | `infra/health/SecretsManagerReadinessCheck` (nueva) | Mismo diseño, `ListSecrets` con `maxResults(1)` contra `SecretsManagerAsyncClient` |
| Proceso Quarkus | Liveness | `MyLivenessCheck` (ya existía desde la feature 1) | Siempre `up("alive")` — liveness solo refleja que el proceso sigue vivo |

**Por qué readiness y no liveness para DynamoDB/Secrets Manager** (aunque la
descripción de la feature mencione "readiness/liveness" para las tres
dependencias): atar la liveness a una dependencia externa haría que
Kubernetes reinicie el pod cada vez que LocalStack/DynamoDB tenga un
problema transitorio — lo correcto es sacar el pod del balanceo (readiness)
sin matar el proceso, que sigue vivo y puede recuperarse solo si la
dependencia vuelve. Kafka ya sigue este mismo patrón (solo readiness,
registrado automáticamente por la extensión) — DynamoDB/Secrets Manager se
alinean a ese mismo criterio en vez de inventar uno distinto.

Ambos checks nuevos implementan `io.smallrye.health.api.AsyncHealthCheck`
(no el `HealthCheck` síncrono de MicroProfile) para devolver
`Uni<HealthCheckResponse>` y no bloquear ningún hilo esperando la respuesta
de DynamoDB/Secrets Manager — coherente con la regla dura de este proyecto
de no bloquear el event loop. Verificados con
`infra/health/DynamoDbReadinessCheckTest`/`SecretsManagerReadinessCheckTest`:
caso UP contra el LocalStack real de Quarkus Dev Services (bean inyectado
tal cual lo usa la app), caso DOWN con un cliente AWS SDK standalone
apuntado a un puerto inalcanzable (`http://127.0.0.1:1`) construido a mano
en el test — **deliberadamente sin** apuntar
`quarkus.dynamodb.endpoint-override`/`quarkus.secretsmanager.endpoint-override`
a un endpoint inválido para toda la app, porque `RoleRepositoryTableInitializer`/
`MenuTableInitializer`/`AppSecretsBootstrap` (`@Observes StartupEvent`, ya
aprobados en features previas) usan `.await().atMost(...)` sin fallback si
DynamoDB no responde en absoluto — eso rompería el arranque de la app y de
toda la suite de tests, no solo el escenario que se quiere probar.

**Manejo explícito de fallos de `Uni` (endpoints de reportes y de
escritura, acceptance 3):**

- `web/UniFallosInfraestructura` (nueva): helper con
  `manejarFallosInfraestructura(Uni<T>, String contexto)` que aplica
  `.onFailure(predicado).transform(...)` explícito, envolviendo únicamente
  fallos de `software.amazon.awssdk.core.exception.SdkException` (DynamoDB/
  Secrets Manager) u `org.apache.kafka.common.KafkaException` (Kafka) en
  `domain/InfraestructuraNoDisponibleException` (nueva). Cualquier otra
  excepción (bugs reales, excepciones de dominio ya mapeadas aparte como
  `CatalogoNoEncontradoException`/`ReporteNoListoException`) pasa sin tocar
  — no se disfraza un bug de programación como "reintente en unos
  segundos".
- Aplicado explícitamente con `.onFailure()` en los tres endpoints
  "principales" que la descripción de la feature nombra: `POST
  /api/reportes/generar` (`web/ReporteGenerarResource`, cubre tanto el fallo
  de `crearPendiente` en DynamoDB como el nack de Kafka al publicar
  `report.requested`), `GET /api/reportes/{id}` y `GET
  /api/reportes/{id}/descargar` (`web/ReporteResource`).
- `web/exception/InfraestructuraExceptionMappers` (nueva,
  `@ServerExceptionMapper`, mismo patrón ya establecido por
  `CatalogoExceptionMappers`/`RolExceptionMappers`/`ReporteExceptionMappers`)
  mapea `InfraestructuraNoDisponibleException` **y también** cualquier
  `SdkException`/`KafkaException` sin envolver directamente a HTTP 503 con
  un mensaje claro en JSON (`{"mensaje": "..."}"`) — esto cubre de forma
  centralizada, sin repetir el wrapper en cada uno de los ~10 recursos REST
  de catálogos/dominio (Área, Cargo, Ubicación, Aplicación, Estado, Rol,
  Suministro, Atributo, Usuario, Menú), cualquier endpoint de escritura
  (POST/PUT) cuyo `Uni` falle por DynamoDB/Kafka: RESTEasy Reactive
  despacha una falla de `Uni` al `@ServerExceptionMapper` que matchee su
  tipo exactamente igual que si se hubiera lanzado la excepción de forma
  síncrona, así que no hace falta repetir `.onFailure()` en cada uno de
  esos recursos para obtener el mismo 503 claro en vez del 500 genérico por
  defecto. Verificado con `InfraestructuraExceptionMappersTest` (unit test
  puro, sin `@QuarkusTest`) y `UniFallosInfraestructuraTest` (unit test
  puro sobre el helper, incluida la prueba de que un fallo NO relacionado a
  infraestructura pasa sin envolver).

**SSE — cierre limpio ante desconexión del cliente (acceptance 4):**
`web/ReporteResource#eventos()` agrega `.onCancellation().invoke(...)`
(log en `DEBUG` cuando RESTEasy Reactive cancela la suscripción al `Multi`
porque el cliente cerró la conexión) y `.onFailure().invoke(...)`
defensivo (log en `WARN` si el puente `ReporteEventoBroadcaster` alguna vez
fallara, hoy no lo hace). Verificado con
`web/ReporteResourceSseDisconnectTest`: se suscribe directo al `Multi` que
devuelve `eventos()` (el mismo objeto que expone el endpoint HTTP,
incluidos estos operadores) y cancela la suscripción — simula exactamente
lo que hace RESTEasy Reactive cuando el socket SSE se cierra del lado del
cliente — confirmando que no queda una excepción no controlada y que el
`BroadcastProcessor` compartido sigue sano para otras conexiones activas
tras la desconexión de una.

**Aislamiento del puente SSE respecto al resultado real del worker Kafka**
(nota no bloqueante heredada de la review de la feature 11,
`aws_secrets_manager_integration`, resuelta aquí):
`messaging/ReporteRequestWorker#publicarReady`/`publicarFailed` ahora
llaman a `emitirEventoSeguro(...)` (try/catch alrededor de
`ReporteEventoBroadcaster#emitir`, log en `WARN` si falla) en vez de
invocar `emitir(...)` directo dentro de `.invoke(...)` — antes, una
excepción del puente SSE (side-effect puramente aditivo de la feature 10)
habría contaminado el `Uni<Void>` que devuelve `publicarReady`/
`publicarFailed`, haciendo que `procesar()` interpretara como fallo de
generación un reporte que en realidad ya se había generado y persistido
correctamente en Kafka/DynamoDB. Verificado con
`web/ReporteRequestWorkerSseIsolationTest`: construye un
`ReporteRequestWorker` con un `ReporteEventoBroadcaster` que siempre lanza
y confirma que el reporte igual termina `LISTO` con `intentos=1` (sin
reintento espurio).

**Logging estructurado (acceptance 2):** confirmado por grep que
`src/main` no contiene ningún `System.out.println` (ya estaba así antes de
esta feature; las clases nuevas de esta feature usan JBoss Logging
(`org.jboss.logging.Logger`) igual que el resto del proyecto).

## Reglas duras específicas de este proyecto

- `web/` (controllers REST) no accede a DynamoDB directamente — siempre a
  través de `service/`.
- `report/ReportGeneratorPort` es la única interfaz que conoce `service/`
  para generar reportes; iText/POI viven exclusivamente detrás de sus
  adaptadores (`PdfReportAdapter`, `ExcelReportAdapter`).
- Ningún secreto (credenciales DynamoDB/Kafka, ARNs, tokens) en
  `application.properties` versionado — siempre vía AWS Secrets Manager/KMS
  o variables de entorno inyectadas en runtime.
- Prohibido reintroducir `Cripto.java` o cualquier cifrado reversible de
  contraseñas.
- **Modelo reactivo obligatorio**: controllers REST devuelven `Uni<T>`/
  `Multi<T>` (SmallRye Mutiny), no tipos bloqueantes. Prohibido `.await()`
  bloqueante dentro de un endpoint reactivo (rompe el event loop).
- El acceso a DynamoDB usa `DynamoDbAsyncClient` (no el cliente síncrono),
  envuelto en `Uni` (`Uni.createFrom().completionStage(...)`).
- Todo trabajo bloqueante inevitable (iText, POI — sin API reactiva) debe
  offloadearse explícitamente con `.emitOn(Infrastructure.getDefaultWorkerPool())`
  o equivalente; prohibido ejecutarlo directamente en el event loop de
  Vert.x. Ese worker pool puede configurarse respaldado por Virtual Threads
  (Java 21) para no agotar hilos de plataforma bajo carga.
- Prohibido `synchronized` en código que pueda ejecutarse sobre un hilo
  virtual (causa *pinning*) — usar `java.util.concurrent.locks.ReentrantLock`
  si se necesita exclusión mutua.
- Ningún test depende de AWS/DynamoDB/Kafka reales — todo contra LocalStack
  (DynamoDB + Secrets Manager + KMS) / Kafka embebido (Quarkus Dev Services).

### Snapshot en memoria, resiliencia e idempotencia de reportes

Implementado y documentado (patrón reutilizable para otros servicios, p. ej.
el ERP textil) en [`docs/resiliencia-snapshot-idempotencia.md`](resiliencia-snapshot-idempotencia.md):
`infra/SnapshotCache` (ttl 60 s + respaldo ante caída de BD de 2 min),
`infra/LecturaResiliente` (SmallRye Fault Tolerance: timeout/retry/circuit
breaker solo en lecturas), `infra/SnapshotPrecarga` (calentamiento al arrancar)
e idempotencia de `POST /api/reportes/generar` (id determinístico +
`attribute_not_exists`). También documenta el fix del HTTP 500 del Excel en
Docker (`autoSizeColumn` requería fuentes AWT).
