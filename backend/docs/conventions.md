# Convenciones — entel-role-repository-backend

## Estilo de código

- Paquete raíz: `pe.edu.cibertec.proyemp` (se conserva del legacy para
  trazabilidad; no usar `com.tallertextil` ni ningún otro inventado por
  documentos previos).
- Capas: `domain/` (modelos), `repository/` (acceso DynamoDB), `service/`
  (lógica de negocio, reactivo), `web/` (`@Path` REST resources), `report/`
  (puerto + adaptadores PDF/Excel).
- **Todo tipo de retorno público en `service/` y `web/` es `Uni<T>` o
  `Multi<T>`** (SmallRye Mutiny) — nunca un tipo bloqueante ni `void`
  fire-and-forget sin suscripción gestionada por Quarkus.
- Manejo de errores: excepciones de dominio propias (`RolNoEncontradoException`,
  etc.) mapeadas a respuestas HTTP vía `@ServerExceptionMapper`. Prohibido
  `catch` genérico que trague la excepción y devuelva `null`/vacío en
  silencio (ese patrón causaba bugs difíciles de rastrear en el legacy,
  ver `ReporteManagedBean.imprimir()` con sus múltiples `catch (Exception ex)
  { System.out.println(...) }`).
- Inyección de dependencias: constructor injection (`@Inject` en
  constructor), no field injection — facilita testear con mocks.
- Logging: JBoss Logging (estándar Quarkus), niveles `WARN`/`ERROR` para
  fallos reales, `DEBUG` para trazas de queries DynamoDB. Prohibido
  `System.out.println` (presente en el legacy, no se migra).

## Reactivo (Mutiny) — reglas específicas

- `DynamoDbAsyncClient` es el único cliente DynamoDB permitido; envolver
  sus `CompletableFuture` con `Uni.createFrom().completionStage(...)`.
- Componer operaciones con `.chain()`, `.onItem().transform()`, no anidar
  `.await().indefinitely()` dentro de otro `Uni` (bloquea el hilo del event
  loop — es el equivalente reactivo de un deadlock potencial).
- `.await()` solo se permite en tests o en el `main`/bootstrap, nunca en
  código de request.
- Trabajo bloqueante (iText `Document`, Apache POI `Workbook`) se ejecuta
  con `.emitOn(Infrastructure.getDefaultWorkerPool())` antes de tocar esas
  APIs, y se vuelve a `.emitOn` al pool reactivo si hace falta continuar
  componiendo. Documentar con un comentario breve por qué ese punto exige
  offload (no es obvio para quien no conoce iText/POI).
- Kafka: `@Incoming`/`@Outgoing` de SmallRye Reactive Messaging, payloads
  como `Uni<Void>`/`Multi<T>` según corresponda — no mezclar con llamadas
  bloqueantes directas al broker.

## Tests

- **Unit:** JUnit 5 + Mutiny test helpers (`UniAssertSubscriber`), sin
  infraestructura externa, para lógica de dominio pura (ej. armado de la
  estructura `Reporte`/`DetalleRol`).
- **Integración:** `@QuarkusTest` + Quarkus Dev Services / Testcontainers
  apuntando a **LocalStack** (DynamoDB, Secrets Manager, KMS) y a Kafka
  embebido — nunca a AWS real ni a un broker Kafka real.
- Nomenclatura: `should_<resultado>_when_<condicion>` (ej.
  `should_return404_whenAreaCodeDoesNotExist`).
- **Regla dura de migración:** todo fix de bug incluye un test de
  regresión que falla contra el comportamiento viejo y pasa contra el
  nuevo (ej. el bug de deduplicación con `HashSet` de
  `ReporteManagedBean` — el test nuevo debe probar que la query no genera
  duplicados en origen, no que un `Set` los limpia después).
- Prohibido cualquier test que dependa de credenciales AWS reales, de que
  una cuenta AWS esté disponible, o de un cluster Kafka real.

## Dependencias

- BOM de Quarkus fija la versión de la plataforma; no fijar versiones de
  librerías individuales que el BOM ya gestiona.
- Reemplazos respecto al legacy:
  - Spring Data JPA + EclipseLink → `quarkus-amazon-dynamodb` (cliente
    async) + repositorios propios (no hay un "Spring Data para DynamoDB"
    oficial en Quarkus, se escribe el repositorio a mano por tabla).
  - `Cripto.java` (AES/ECB propio) → `at.favre.lib:bcrypt` o
    `quarkus-elytron-security-common` para hashing de contraseñas.
  - iText 5 → puede mantenerse iText 5/7 (licencia AGPL en v7, revisar
    antes de actualizar) o OpenPDF (fork LGPL de iText 4) si hay
    restricción de licencia — decisión pendiente, documentar la elegida
    en la feature correspondiente.
  - Apache POI 3.7 → Apache POI vigente (5.x), API compatible en lo
    esencial usado por el legacy (`HSSFWorkbook`/`XSSFWorkbook`).

## Qué NO hacer

- No introducir Spring (ni JPA/Hibernate) — es un salto completo a
  DynamoDB, no una migración híbrida.
- No reintroducir `Cripto.java` ni ningún cifrado reversible de
  contraseñas, aunque parezca "más simple" para una feature puntual.
- No escribir reportes a un path de disco del servidor ni usar
  `Runtime.exec(...)` para abrir archivos — el patrón que tenía el legacy
  queda explícitamente prohibido.
- No agregar Kafka *ad hoc* fuera de los tópicos ya definidos en
  `docs/architecture.md` sin actualizar ese documento primero.
