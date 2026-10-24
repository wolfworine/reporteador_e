# Handoff — backend (entel-role-repository-backend)

**TRACK COMPLETO** por `feature_list.json` (13/13 `done`). Actualizado:
2026-08-19 ~17:25. `bash init.sh` sigue verde, **95/95 tests**, 0 errores,
tras 3 bugs reales encontrados tras el cierre "formal" probando la app de
verdad en un navegador real contra datos migrados reales (ver sección
dedicada abajo) -- ningún feature/test automatizado los había ejercitado.
Lee esto primero si retomas este track sin contexto previo; para
detalle histórico sesión por sesión, `progress/history.md`. Reglas de
dispatch de agentes opencode: `../OPENCODE_RULES.md`.

## 2026-08-19 — 3 bugs reales encontrados probando la demo real (no por tests automatizados)

Ningún test de los 95 (ni los 61 del frontend) ejercitaba nunca la
integración real navegador↔backend↔LocalStack con volumen real de datos
-- los tests mockean HTTP (frontend) o usan RestAssured/Dev Services
(backend), ninguno pasa por CORS real ni por más de un puñado de filas de
prueba. Los 3 bugs de abajo solo aparecieron probando `start.sh` con un
navegador real y la migración real de `../database/` corrida por primera
vez contra este backend.

1. **CORS nunca configurado.** Cero menciones en todo el código antes de
   hoy. Cualquier fetch real del frontend (puerto 5173) a este backend
   (puerto 8080) fallaba en el navegador con `TypeError: Failed to fetch`
   (bloqueado por el navegador, ni siquiera llega como error HTTP) --
   `curl` siempre funcionaba (no está sujeto a CORS), por eso pasó
   inadvertido. Corregido en `application.properties`
   (`%dev.quarkus.http.cors.enabled=true` + origins/methods/headers).
   **Ojo:** el nombre correcto de la propiedad en esta versión resuelta de
   Quarkus (3.38.1) es `quarkus.http.cors.enabled`, NO la forma corta
   `quarkus.http.cors=true` (válida en versiones viejas) -- mismo patrón
   de bug exacto que el `sdk.endpoint-override` de la feature 11: Quarkus
   ignora la key desconocida en silencio, sin fallar el build.
2. **`Scan`/`Query` sin paginar en 4 repositorios.** `CatalogoSimpleRepository`,
   `RolRepository`, `UsuarioRepository` (Scan) y `AtributoRepository`
   (Query sobre GSI1) llamaban a DynamoDB una sola vez y devolvían
   `response.items()` directo -- DynamoDB limita cada respuesta a ~1MB
   escaneado/leído, invisible con datos de prueba (unas pocas filas),
   pero con los ~17.9k items reales migrados hoy (ver
   `../database/handoff.md`) truncaba en silencio: `GET /api/areas`
   devolvía 13 de 69 filas reales, `/api/roles` 113 de 741. Corregido con
   dos utilidades nuevas de paginación real (siguen `lastEvaluatedKey`
   hasta agotar todas las páginas): `repository/DynamoScanSupport.java`
   (Scan) y `repository/DynamoQuerySupport.java` (Query). Verificado
   contra la API real tras el fix: los 4 endpoints devuelven ahora los
   conteos exactos del dump (69/695/61/1/741).
3. **Login del frontend roto por un mapeo de respuesta incorrecto.**
   `LoginPage.tsx` leía `data.usuario.email` asumiendo que
   `POST /api/auth/login` envolvía la respuesta en `{ usuario: {...} }`
   -- el endpoint real (`web/AuthResource.java`, ya `done` desde la
   feature 5) devuelve el `UsuarioResponse` plano, sin wrapper y sin
   token (decisión de diseño explícita del backend, documentada en su
   propio Javadoc). El fetch funcionaba (200 OK), pero el mapeo posterior
   tiraba un `TypeError` antes de autenticar. Bug 100% del lado
   frontend -- ver `../frontend/handoff.md` para el detalle del fix ahí.

**Verificación real, no solo tests:** ciclo completo probado en un
navegador de verdad (Claude in Chrome) contra el `start.sh` real: login
con `admin@entel.demo`/`Demo1234!` → redirección a `/` → `GET
/api/areas`/`/cargos`/`/roles`/`/ubicaciones`/`/aplicaciones`/`/atributos`
todos devolviendo datos reales y completos. `bash init.sh` reconfirmado
95/95 verde después de los 3 fixes (incluye los 15 tests nuevos de la
feature 12 y los de `AppSecretsBootstrapTest` de la feature 11, sin
regresiones).

**Pendiente, no bloqueante:** el menú lateral (`Layout.tsx`/`MenuTree`)
queda vacío con este dataset (el dump real no tiene tabla `menu`, 0 items
en `RoleRepositoryMenu`, resultado correcto -- ver
`../database/handoff.md`) y **`MenuTree` renderiza `<span>`, no
`<Link>`/`useNavigate`** -- aunque hubiera items de menú, no serían
clicables todavía (gap de UI real, encontrado leyendo el componente,
no arreglado hoy porque no bloqueaba verificar la migración). Para
navegar entre pantallas hoy hay que escribir la URL a mano.

## Documento final

`backend/MEJORAS.md` es el reporte de cierre completo del track: bugs
corregidos (antes/después), versiones de stack, estado de seguridad
(arreglado vs. pendiente), cobertura de tests por feature (3→95),
recomendaciones no implementadas (8 puntos), y la coordinación
cross-track del contrato de reportes con el frontend.

## Estado a 2026-08-19 ~11:35 — features 1-12 `done`, gap DTO cross-track CERRADO (histórico, feature 13 cerró después, ver arriba)

- **Feature 12 (`reactive_resilience_observability`): `done`**, cerrada
  por el orquestador tras verificación independiente completa (los 5
  acceptance criteria confirmados uno por uno, `./init.sh` corrido por mí
  mismo → 95/95 verde — ver `progress/review.md`).
- **Coordinación cross-track backend↔frontend (gap DTO
  `Reporte`/`DetalleRol`): CERRADA, verificada end-to-end.** El
  orquestador creó `web/dto/{ReporteRespuestaDto,DetalleRolRespuestaDto,
  DetalleRespuestaDto}.java` (mapean `domain/Reporte`/`DetalleRol`/
  `Atributo` -- intactos, no tocados -- a los nombres de campo que
  `../frontend/src/types/reporte.ts` espera) y conectó
  `web/ReporteResource.java#obtener()` para devolver
  `ReporteRespuestaDto.de(lista)` en vez del dominio crudo.
  `ReporteResourceTest.java` (`should_returnReporteTreeJson_when_
  obtenerConReporteListo`) actualizado con aserciones reales sobre los
  campos nuevos (`codigoArea`/`codigoCargo`/`idRol`/`tipoRol`/
  `suministroRoles[0].atributoCodigo`/`cantidadSuministros`) contra un
  reporte generado de verdad vía Kafka+LocalStack, no solo renombrado a
  ciegas — 8/8 verde. `./init.sh` final: 95/95 verde, sin regresiones. Ver
  `../frontend/handoff.md` para el lado frontend (tipos corregidos,
  61/61 tests, `tsc -b` limpio) y `handoff.md` raíz para el resumen
  cruzado completo, incluido el hallazgo no bloqueante sobre la columna
  "Titularidad" (dead code confirmado en el legacy real).

## Estado verificado, 2026-08-19 ~10:30 (feature 11 CERRADA ~10:56, ver nota arriba)

- **Feature 11 (`aws_secrets_manager_integration`): `done`, cerrada por el
  orquestador ~10:56 tras verificación independiente completa** (los 4
  acceptance criteria confirmados en disco uno por uno: grep de
  credenciales sin hallazgos, `AppSecretsBootstrapTest` 3/3 corrido
  aislado, `docs/architecture.md` documentado, `./init.sh` 80/80 verde —
  ver `progress/review.md`, reescrito con este veredicto). El resto de
  esta sección es la nota original del implementer, dejada tal cual como
  contexto de diseño:
  - Diseño: bean `@Observes StartupEvent` (`infra/AppSecretsBootstrap.java`,
    mismo patrón que `RoleRepositoryTableInitializer`/`MenuTableInitializer`)
    que lee el secreto `app/dynamodb/table-name` de Secrets Manager, lo crea
    si no existe (con el valor de `RoleRepositoryTable.TABLE_NAME`) y
    **siempre relee** desde `GetSecretValue` real (nunca de memoria). Se
    evaluó y descartó a propósito el `SecretsManagerConfigSourceFactory`
    nativo de Quarkus (resuelve antes de CDI, exige que el secreto
    preexista — incómodo de probar de forma no frágil contra Dev Services).
  - **Bug real encontrado y corregido:** `application.properties` tenía
    `%dev.quarkus.dynamodb.sdk.endpoint-override` /
    `%dev.quarkus.secretsmanager.sdk.endpoint-override` con un segmento
    `sdk.` que no existe en la versión 3.21.1 realmente resuelta (Quarkus
    ignora keys desconocidas en vez de fallar el build — nunca se detectó
    porque `mvn test` usa Dev Services, no pasa por el archivo). Corregido.
  - 3 tests nuevos en `AppSecretsBootstrapTest.java`. Pendiente: correr
    `./init.sh` final (esperado 80/80 = 77 previos + 3 nuevos) y pasar por
    reviewer — **no marcar `done` sin verificar esto en disco primero**, el
    implementer dejó `in_progress` a propósito para que el reviewer decida.
- **Gap cross-track DTO — ahora con diagnóstico exacto y CERRADO como no
  bloqueante por el reviewer de la feature 10** (ver `progress/review.md`,
  sección "Discusión dedicada"): campo a campo, `domain/Reporte.java` usa
  `areaCodigo/areaNombre/cargoCodigo/cargoNombre`, frontend
  `types/reporte.ts` usa `area/codigoArea/cargo/codigoCargo` (nombres
  invertidos); `domain/DetalleRol.java` usa `tiposConDatos`/
  `suministroDefaultInbox`/sin `cantidadSuministros`, frontend usa
  `tipoRol`/`suministroDefaultinbox`/con `cantidadSuministros`. El propio
  frontend documenta sus tipos como "extrapolación no confirmada" en 2
  archivos distintos (`types/reporte.ts`, `api/reportes.ts`), así que el
  reviewer decidió NO exigirle al backend copiar un placeholder no
  verificado ni reabrir el dominio ya aprobado (features 7/9). Acceptance
  criterion 3 de la feature 10 se dio por cumplido en sentido estructural.
  **Queda una acción pendiente real, explícita, NO delegable a ningún
  implementer/reviewer de un solo track:** el orquestador (yo) debe
  coordinar Leader↔Leader (backend↔frontend) para reconciliar esos nombres
  de campo antes de que el frontend intente consumir
  `GET /api/reportes/{id}` de verdad — si se integra tal cual, rompe en
  runtime. Aún no hecho.

## Estado verificado, 2026-08-18 — ¡IDs de feature RENUMERADOS!

- Features 1-6: `done` (sin cambios de contenido/orden).
- **Feature 7 (`reportes_generacion_async_kafka`) fue dividida en 4
  sub-features** — ya no es un solo item "Grande", ahora el
  `feature_list.json` tiene 13 entradas en vez de 11:
  - 7: `reportes_dominio_y_query_sin_duplicados` (`in_progress`)
  - 8: `reportes_adaptadores_pdf_excel` (`pending`)
  - 9: `reportes_generacion_async_kafka` (versión acotada, `pending`)
  - 10: `reportes_notificacion_sse_y_descarga` (`pending`, incluye el
    endpoint cross-track `GET /api/reportes/{id}` ya coordinado con
    frontend)
  - Los antiguos 9/10/11 (`aws_secrets_manager_integration`,
    `reactive_resilience_observability`, `reporte_final_mejoras`) ahora
    son **11/12/13**. **Cualquier nota vieja que diga "feature 9" o
    "feature 10" del backend puede estar refiriéndose al esquema
    anterior — verificar el `name` del feature, no solo el número.**
- **Features 7 y 8: `done`** (verificado, 65/65 tests, 2026-08-18). La 8
  (`reportes_adaptadores_pdf_excel`) usó OpenPDF (LGPL) + Apache POI
  `.xlsx`/`XSSFWorkbook`; encontró y arregló un bug real de layout PDF
  (tablas anidadas fragmentaban palabras). El reviewer verificó que los
  tests de contenido reabren los archivos generados y comprueban datos
  reales, no solo bytes no vacíos. Hay un warning cosmético de Log4j
  ("could not find a logging provider") en la salida de tests —
  investigado y confirmado que no enmascara un error real, no bloqueante.
- **Features 9 y 10: `done`.** Confirmado en `feature_list.json`
  (2026-08-18). Feature 9 cerró con 69/69 tests. Feature 10
  (`reportes_notificacion_sse_y_descarga`, incluye `GET /api/reportes/{id}`)
  cerró tras un intercambio cross-track real con frontend.
- **Gap cross-track detectado (abierto, no bloqueante):** el DTO real
  `domain/Reporte.java`/`DetalleRol.java` del backend no coincide
  exactamente con `../frontend/types/reporte.ts` (extrapolación propia de
  frontend, documentada desde el inicio). Frontend confirmó y encontró un
  mismatch adicional: `idRol` es `String` en backend, `number` en
  frontend. Resolución acordada: capa de mapeo DTO de respuesta en el
  backend apuntando a los tipos ya existentes de frontend — cero cambios
  de frontend, no reabre trabajo aprobado en ningún lado. Ver
  `../frontend/progress/current.md`, sección "Cross-track — gap de
  shape", para el detalle completo. Aplicar esta capa de mapeo cuando se
  retome cualquier ajuste de la feature 10, o antes de la integración
  real SSE si aún no se hizo.
- **Feature activa: 11 (`aws_secrets_manager_integration`)**.
- Nota: el Leader corrigió solo un JSON corrompido a mitad de edición
  (comilla de cierre faltante) validándolo él mismo con `node -e
  "require(...)"` antes de continuar — confirmado independientemente,
  `feature_list.json` es JSON válido con los 11 features y statuses
  correctos.
- Pendientes: 7 (`reportes_generacion_async_kafka` — leer
  `docs/kafka-reactive-guidance.md` antes de implementar), 8
  (`reportes_notificacion_sse_y_descarga`), 9
  (`aws_secrets_manager_integration`), 10
  (`reactive_resilience_observability`), 11 (`reporte_final_mejoras`).

## Cross-track para features 7/8 — RESUELTO

`GET /api/reportes/{id}` → `Reporte[]` ya está agregado explícitamente a
las acceptance criteria de la feature 8 (coordinado con el Leader de
frontend el 16-ago-2026, confirmado en disco por ambos lados). Al
implementar, igualar exactamente la forma que `../frontend/src/api/reportes.ts`
ya consume.

## Notas específicas de este track

- `domain/CategoriaSuministro.java` fue reportado en algún momento con un
  error de sintaxis por un agente de otro track — confirmado falso, es un
  enum limpio de 4 valores. No hace falta reinvestigar.
- No portar `Cripto.java`/`Encripcion.java` (cifrado reversible legacy) —
  ya decidido y confirmado por grep en vivo que no existen referencias
  activas.
- El esquema DynamoDB canónico vive en `../database/docs/architecture.md`
  — no reinventar aquí.

## Lección de esta sesión

Un Reviewer opencode fue dispatchado tarde sobre la feature 4 cuando ya
estaba `done` y el Leader ya iba en la feature 5 — sobreescribió
`progress/review.md` con una versión genérica de una revisión real que ya
existía. Sin daño real (el archivo es transitorio, se reescribe en cada
feature), pero: **antes de lanzar un Reviewer/Implementer opencode contra
este track, verificar primero el `feature_list.json` y los timestamps de
`progress/` actuales.**

## 2026-08-20 — Bug real: reportes quedaba colgado en "Generando…" para siempre

**Síntoma reportado por el usuario probando en un navegador real:** al
presionar "Reporte" en el frontend, el flujo llegaba a "Generando…" y
nunca avanzaba a "Listo, descargar" — ni éxito ni error, colgado
indefinidamente. Antes de esto ya se había corregido un bug previo en el
mismo flujo (`ReporteFiltro.tsx` nunca mandaba `formato`, ver
`../frontend/handoff.md`) — este es un bug DISTINTO, encontrado
inmediatamente después de arreglar el primero, la primera vez que el flujo
completo se probó de punta a punta contra infraestructura real (Kafka Dev
Services, no mocks).

**Root cause confirmado** (leyendo `.run/backend.log`, generando reportes
directo por curl con distintos parámetros, y esperando >10 minutos sin
ningún progreso en el log): `messaging/ReporteRequestWorker#publicarReady`
nunca lograba publicar en el tópico Kafka `report.ready` — la generación
del reporte en sí (DynamoDB + PDF/Excel) terminaba en segundos, el colgado
real estaba en la publicación del evento Kafka que dispara el puente SSE.
El productor repetía indefinidamente
`WARN [...NetworkClient] The metadata response from the cluster reported
a recoverable issue: {report.ready=UNKNOWN_TOPIC_OR_PARTITION}` sin
resolver ni fallar — el tópico no existía y la auto-creación del broker
(Kafka Dev Services, KRaft de un solo nodo) no la resolvía en un tiempo
razonable (se confirmó que SÍ terminaba resolviéndose sola eventualmente,
pero tras varios minutos — inaceptable para un flujo interactivo).
`report.requested` nunca mostró el síntoma porque ya había sido
auto-creado por intentos previos (fallidos por el bug de `formato`) antes
de llegar a esta parte del pipeline.

**Fix:** nueva clase `infra/KafkaTopicBootstrap.java` — crea explícitamente
los 3 tópicos (`report.requested`/`report.ready`/`report.failed`) al
arranque vía `org.apache.kafka.clients.admin.Admin`, idempotente (tolera
`TopicExistsException`), mismo patrón que
`repository/RoleRepositoryTableInitializer`/`MenuTableInitializer` usan
para las tablas DynamoDB. Esto elimina la dependencia de la auto-creación
implícita del broker, que es la causa raíz real.

**Verificado:** tras el fix, `POST /api/reportes/generar` (formato `pdf` Y
`excel` probados por separado) + escuchar `GET /api/reportes/eventos` con
curl en paralelo → evento `LISTO` recibido en ~5 segundos para ambos
formatos. `mvn compile` limpio.

## 2026-08-20 (continuación) — SEGUNDO bug real en el mismo flujo: el payload SSE no era JSON

Con el bug de arriba corregido, el usuario reportó que el botón "Reporte"
seguía quedándose colgado en "Generando…" en su navegador real. La
verificación por curl (arriba) mostraba el evento `LISTO` llegando bien
por SSE, así que el backend parecía sano — pero **curl nunca intenta
parsear el payload como JSON, solo lo imprime crudo**, por eso ese primer
chequeo no detectó nada raro.

**Root cause real, confirmado inyectando un wrapper de `EventSource` y
`fetch` en la página real vía `javascript_tool` (no solo curl) y
comparando el `id` devuelto por `POST /generar` contra el `data` crudo
recibido por el mismo `EventSource` que usa la app:** el payload SSE
llegaba como `ReporteEvento[id=..., estado=LISTO, mensaje=null]` (el
`toString()` de Java) en vez de JSON
(`{"id":"...","estado":"LISTO","mensaje":null}`). `web/ReporteResource#eventos()`
tenía `@RestStreamElementType(MediaType.SERVER_SENT_EVENTS)` — esa
anotación declara el media type de **cada elemento individual** del
stream (no el del stream en conjunto, eso ya lo cubre
`@Produces(MediaType.SERVER_SENT_EVENTS)` en la línea de abajo); al
decirle "cada elemento es `text/event-stream`" en vez de
`application/json`, RESTEasy Reactive no usaba el proveedor Jackson para
serializar `ReporteEvento` y caía a un `toString()` por defecto.

El frontend (`modules/reporte/useReportEvents.ts`) hace `JSON.parse(message.data)`
dentro de un `try/catch` que descarta silenciosamente cualquier error de
parseo (decisión documentada explícitamente en su propio Javadoc: "dato
malformado del stream del servidor, no requiere `role=\"alert\"`") — así
que el evento SIEMPRE fallaba al parsear y SIEMPRE se descartaba en
silencio, dejando el estado de UI trabado en `GENERANDO` para SIEMPRE,
sin importar cuán rápido o bien funcionara el resto del pipeline.

**Por qué ningún test lo agarró:** `ReporteResourceSseDisconnectTest`
(el único test que ejercita `eventos()`) llama al método Java
`reporteResource.eventos()` **directamente** y se suscribe al
`Multi<ReporteEvento>` en memoria con `AssertSubscriber` — nunca pasa por
una request HTTP real ni por el serializador de RESTEasy Reactive, así
que la anotación incorrecta es estructuralmente invisible para ese test.
Mismo patrón de "atajo que evita la frontera real" que causó casi todos
los demás bugs de esta sesión.

**Fix:** una sola línea —
`@RestStreamElementType(MediaType.SERVER_SENT_EVENTS)` →
`@RestStreamElementType(MediaType.APPLICATION_JSON)` en
`web/ReporteResource.java#eventos()`.

**Verificado:**
- Payload SSE crudo por curl tras el fix: `data:{"estado":"LISTO","id":"...","mensaje":null}` (JSON real).
- `ReporteResourceTest` (8 tests) + `ReporteResourceSseDisconnectTest` (2 tests) — 10/10 verdes, sin regresión.
- **En navegador real** (no solo curl/tests): login real, generar reporte
  real (`POST /generar` → `id` real), la pantalla pasa de "Generando…" a
  "Listo, descargar" con la tabla del reporte completa, y "Exportar PDF"
  descarga un blob real (`6585 bytes`, `application/pdf`) — confirmado
  interceptando `URL.createObjectURL` en la página real.

## 2026-08-20 (continuación) — PDF/Excel sin estilo alguno ("no tienen forma esta todo descuadrado")

El usuario comparó una captura de la vista web (bien maquetada tras el
overhaul de diseño, ver `../frontend/handoff.md`) contra el PDF generado
y señaló que este último no tenía ninguna forma reconocible. Correcto:
`PdfReportAdapter`/`ExcelReportAdapter` (features 8/`reportes_view_export_sse`)
nunca tuvieron ni un solo `Font`/color/ancho de columna explícito desde
que se escribieron — `new Paragraph(texto)`/`cell.setCellValue(texto)` a
secas. OpenPDF reparte el ancho de tabla en columnas IGUALES por defecto
(la columna "Rol", con nombres largos, quedaba tan angosta como "Tipo",
4 letras) y POI deja cada columna en el ancho default (~8 caracteres) sin
`autoSizeColumn` — de ahí el "descuadrado".

**Fix (ambos adaptadores, sin tocar el contenido/dato mostrado, ver Javadoc
de cada clase para el detalle):**
- `PdfReportAdapter.java`: anchos relativos explícitos por tabla
  (`18/82` para Área/Cargo, `30/70` para código/nombre de atributo),
  fuentes Helvetica-Bold para título/etiquetas/encabezados de categoría,
  fondo oscuro (`#171a21`, mismo tono que el header de la web) en las
  celdas de etiqueta "Área"/"Cargo", fondo gris claro en los encabezados
  ROLES/GRUPOS/INBOX/DEFAULT INBOX, padding en todas las celdas.
- `ExcelReportAdapter.java`: fila de encabezados (Rol/Tipo/Aplicacion/...)
  con fondo oscuro + texto blanco en negrita; filas "Area"/"Cargo" con
  fondo gris claro + negrita; borde fino en todas las celdas de datos;
  `sheet.autoSizeColumn(...)` sobre las 6 columnas al final (con un piso
  mínimo de ancho para columnas de contenido corto como "Tipo").

**Verificado:**
- `ReportGeneratorAdaptersTest` (3 tests) — verde, sin regresión.
- PDF real generado y descargado por curl, extraído con `pdftotext
  -layout`: título, Área/Cargo, nombre de rol y encabezados de categoría
  (ROLES/GRUPOS/INBOX) aparecen ahora como bloques claramente separados
  con las columnas código/descripción bien proporcionadas (antes todo
  corrido en una sola columna angosta).
- Excel real generado y descargado por curl, `unzip`eado y su
  `xl/styles.xml` inspeccionado directamente: confirma el fill sólido
  `#171A21` con fuente blanca en negrita para el encabezado, el fill gris
  (`indexed=22`) en negrita para las etiquetas Área/Cargo, y borde fino
  completo (`borderId` con los 4 lados) aplicado a todas las celdas de
  datos — prueba estructural de que el estilo realmente quedó embebido en
  el archivo, no solo que el código compila.
- **No se pudo tomar un screenshot pixel-a-pixel del PDF/Excel
  renderizado esta sesión** (el visor de PDF de Chrome corre en una URL
  interna que la herramienta de automatización bloquea por seguridad, y
  no había `pdftoppm`/`poppler` disponible para rasterizar a imagen) — la
  verificación de arriba (texto extraído + XML de estilos embebido) es la
  evidencia real disponible; si el usuario ve algo raro en el PDF/Excel
  real, es el primer lugar donde mirar.

## 2026-08-25 — Excel: columna Rol repetida en cada fila de atributo

El usuario mandó una captura real del `.xlsx` abierto en Excel: la
columna "Rol" repetía el mismo nombre en 7+ filas seguidas (una por cada
atributo del rol) — "unifica en una columna". Bug real: `ExcelReportAdapter`
escribía `Rol`/`Tipo`/`Aplicacion` en CADA fila de atributo (mismo defecto
que tenía el legacy, `ReporteManagedBean.excel()`, nunca corregido al
migrar).

**Fix:** `escribirBloqueAtributos` ya no escribe las columnas 0-2 (Rol/
Tipo/Aplicacion), solo Titularidad/AtributoCodigo/AtributoNombre.
`escribirRolUnaVez` (nuevo) escribe Rol/Tipo/Aplicacion UNA sola vez en
la primera fila del bloque de un `DetalleRol`, y combina esa celda
(`sheet.addMergedRegion`, `CellRangeAddress`) hacia abajo hasta la última
fila que ese rol generó -- igual que Área/Cargo, que ya se mostraban una
sola vez por bloque. El estilo `dato` ahora centra verticalmente
(`VerticalAlignment.CENTER`) para que el nombre del rol quede centrado
dentro de la celda combinada alta, no pegado al borde inferior.

**Verificado:** generé un Excel real, lo descomprimí y confirmé
directamente en `xl/worksheets/sheet1.xml` que existen 27 regiones
combinadas (`<mergeCells count="27">`, ej. `A5:A11` -- 7 filas en una
celda) y en `xl/sharedStrings.xml` que el nombre del rol aparece **una
sola vez** (antes aparecía repetido). `ReportGeneratorAdaptersTest`
(3 tests) sigue verde.

## 2026-08-25 (continuación) — PDF no reproducía la tabla real, Excel seguía repitiendo la columna de categoría

El usuario insistió con más precisión ("corrige la parte vista de excel
en pdf en titularidad y atributos, en excel se repiten columnas
repetidas") -- dos fixes anteriores (Titularidad de la web, Rol/Tipo/
Aplicacion del Excel) no habían cubierto esto:

1. **`PdfReportAdapter.java` reescrito por completo:** el PDF anterior
   NUNCA reproducía la tabla de 5 columnas (Rol/Tipo/Aplicacion/
   Titularidad/Atributos) que sí tiene la vista web
   (`ReporteTabla.tsx#CargoSubTabla`) -- "Titularidad" era texto suelto
   embebido junto con Rol/Tipo/Aplicación en una sola línea, y
   "Atributos" era una tabla aparte sin ninguna columna real que la
   alinee. Ahora arma UNA `PdfPTable` de 5 columnas por `Reporte` (mismo
   criterio que `CargoSubTabla`), con encabezado de columnas real y, en
   cada fila, "Titularidad"/"Atributos" como tablas ANIDADAS dentro de su
   celda -- misma jerarquía que la web: caja "Titularidad" con
   ROLES/GRUPOS/INBOX/DEFAULT INBOX centrados, y caja por categoría con
   pares código/nombre de atributo.
2. **`ExcelReportAdapter.java`:** la columna "Titularidad" (categoría:
   ROLES/GRUPOS/INBOX/DEFAULTINBOX) seguía repitiéndose una vez por cada
   fila de atributo de esa categoría (el fix anterior solo cubrió Rol/
   Tipo/Aplicacion). Mismo patrón de celda combinada:
   `escribirBloqueAtributos` ahora escribe la categoría una sola vez en
   la primera fila de su propio bloque y la combina hacia abajo
   (`CellRangeAddress`) -- jerarquía de 2 niveles: la celda de Rol
   combinada abarca TODAS las filas del detalle, la celda de categoría
   combinada abarca solo las filas de ESA categoría dentro de ese bloque.

**Verificado:** `ReportGeneratorAdaptersTest` (3 tests) sigue verde,
`mvn compile` limpio. Generé un PDF y un Excel reales por curl:
- PDF: `pdftotext -layout` confirma el encabezado real "Rol Tipo
  Aplicacion Titularidad Atributos" y las cajas "Titularidad"/"ROLES"/
  "GRUPOS"/"INBOX" presentes en cada fila (la salida de texto se ve
  entreverada porque `pdftotext` interpola por posición absoluta cuando
  las celdas de una fila tienen alturas muy distintas -- limitación
  conocida de la herramienta, no evidencia de un problema real; sin
  `pdftoppm`/`poppler` para rasterizar a imagen no se pudo confirmar
  pixel a pixel esta vez tampoco).
- Excel: descomprimido y confirmado en XML -- 45 regiones combinadas
  (`<mergeCells count="45">`, incluye merges de categoría de solo 2-4
  filas anidados dentro del merge de Rol de todo el bloque) y "ROLES"/
  "GRUPOS"/"INBOX" aparecen **una sola vez cada uno** en
  `xl/sharedStrings.xml` (antes se repetían por cada atributo de esa
  categoría).

## 2026-08-25 (continuación) — `/descargar?formato=` por fin implementado: regeneración bajo demanda

El usuario preguntó por qué el flujo de Reportes había quedado partido en
"dos consultas" (elegir formato ANTES de generar, un solo botón de
exportar después) en vez de la UX original (un filtro Aplicación+Área,
dos botones de exportar siempre). La causa raíz: `GET
/{id}/descargar?formato=...` tenía el query param desde el diseño
original de la feature 10, pero **nunca se implementó** -- siempre servía
el único binario ya generado, sin importar qué formato pidiera el
cliente. El fix del frontend del 2026-08-20 (un solo botón, el del
formato ya generado) fue tapar el síntoma del lado equivocado.

**Fix real:** `ReporteResource#descargar` ahora usa el query param de
verdad. Si el formato pedido coincide con el ya persistido, sirve el
binario cacheado (camino rápido, sin cambios). Si pide el OTRO formato,
deserializa `reporteJson` (el árbol `List<Reporte>` que ya está
persistido, mismo que usa `GET /{id}`) y corre el adaptador
(`ReportGeneratorPort`, `@Named("pdf")`/`@Named("excel")`, inyectados
ambos) sobre esos datos ya en memoria -- sin volver a tocar DynamoDB ni
pasar por Kafka/el worker. El binario regenerado se sirve directo, no se
persiste de vuelta (mantiene el modelo de datos simple: un solo
`binario`/`formato` por ejecución; el costo de regenerar en cada
descarga del formato "no cacheado" es aceptable, un reporte es
información acotada). `formato` inválido (ni pdf ni excel) → 400.

**Verificado con tests de integración reales** (Kafka + LocalStack Dev
Services, no mocks) -- 2 tests nuevos en `ReporteResourceTest`
(10/10 total, verde):
- `should_regenerateOnDemand_when_descargarPideElFormatoContrarioAlYaGenerado`:
  genera en pdf, pide `?formato=excel` → 200, `Content-Disposition`
  `reporte-{id}.xlsx`, content-type Excel real, binario no vacío.
- `should_return400_when_descargarConFormatoInvalido`: `?formato=word` → 400.

Frontend correspondiente (ver `../frontend/handoff.md`): `ReporteFiltro.tsx`
volvió a ser solo Aplicación+Área (se manda `formato: "pdf"` fijo, ya no
le importa al usuario cuál se generó primero), `ReporteVista.tsx` vuelve
a mostrar los 2 botones "Exportar PDF"/"Exportar Excel" siempre.
Verificado en navegador real: generé un reporte (sin elegir formato),
clickeé "Exportar Excel" -- el blob descargado tiene magic bytes ZIP
reales (`50 4b 03 04`) pese a que el reporte se generó como PDF,
confirmando la regeneración al vuelo funcionando de punta a punta.

## 2026-08-25 (continuación) — Titularidad en PDF: se sacó el encabezado, alineado vertical

Pedido explícito tras un tercer round de comparación visual con capturas
"to-be" del usuario: `PdfReportAdapter.java#tablaTitularidad()` todavía
tenía la llamada a `celdaCategoria("Titularidad")` (la caja con
encabezado gris) que ya se había sacado del lado web -- se me había
olvidado replicar el mismo fix en el PDF la primera vez. Se sacó la
llamada, se agregó `fila.setVerticalAlignment(Element.ALIGN_MIDDLE)` a
cada fila, y se borró el helper `celdaCategoria(String)` (quedaba sin
otro llamador). El Javadoc del método documenta explícitamente que el
centrado PROPORCIONAL (peso por cantidad de atributos, como en la web
con `flex-grow`) es una limitación conocida y aceptada del PDF -- OpenPDF
calcula el alto de fila DESPUÉS de poner el contenido, no hay forma
simple de reservarle a esta tabla anidada el mismo alto proporcional que
va a terminar ocupando la tabla de Atributos vecina antes de que ambas
se rendericen. Ver `../frontend/handoff.md` para el detalle completo del
lado web (mismo pedido, mismo commit visual).

Tests backend sin cambios de comportamiento (`ReportGeneratorAdaptersTest`
3/3 verde, no hay aserción de texto/layout del encabezado ahí).
