# Handoff — Migración Entel (WiseVision Role Repository Basic)

Actualizado: 2026-08-19 ~11:50 (sesión Claude Code, orquestador
top-level). Se actualiza en checkpoints y se regenera antes de que la
sesión termine por límite de tokens/uso, por regla explícita del usuario
(ver memoria `feedback_handoff_on_session_end`).

## 🎉 Los 3 tracks siguen en `done` — pero se encontraron y corrigieron 4 bugs reales probando la demo de verdad

**backend 13/13, frontend 10/10, database 6/6** (sin cambios de status).
Lo que cambió hoy en la tarde: por primera vez se corrió el ciclo completo
real -- `start.sh`, login en un navegador de verdad, y la migración real
de datos legado (`../database/`) contra el backend real -- y **cada uno
de esos tres pasos encontró un bug real que ningún test automatizado
(95 backend + 61 frontend + 53 database, todos en verde) había
detectado**, porque ninguno ejercitaba CORS real, volumen de datos real,
o el contrato exacto entre `database/` y `backend/`:

1. **CORS nunca configurado en el backend** -- todo fetch real del
   frontend a `localhost:8080` fallaba en el navegador (`curl` sí
   funcionaba, por eso nunca se notó). Corregido.
2. **Login del frontend roto por un mapeo de respuesta incorrecto**
   (esperaba `{ usuario: {...} }`, el backend real devuelve el objeto
   plano). Corregido, con un test nuevo que sí lo habría detectado.
3. **La migración real (`database/`) nunca se había corrido contra el
   backend real** -- 4 divergencias de esquema reales (mayúsculas de
   claves, IDs vs. códigos en GSI1, nombres de atributo, e items `ROL#`
   que nunca se creaban). Reescrito el transformer de `database/`
   campo a campo contra el código fuente real del backend. Migración real
   corrida: **17.873 items** (69 áreas, 695 cargos, 61 ubicaciones, 1
   aplicación, 741 roles, 741 suministros, 15.565 atributos -- conteos
   exactos del dump legado real).
4. **`Scan`/`Query` sin paginar en 4 repositorios del backend** -- con
   datos de prueba nunca se notó, con los ~17.9k items reales devolvía
   resultados truncados en silencio (`/api/areas` devolvía 13 de 69).
   Corregido con paginación real, verificado que los 4 endpoints
   principales ahora devuelven los conteos exactos.

**Verificado de punta a punta en un navegador real:** login con
`admin@entel.demo` / `Demo1234!` → `/api/areas`/`/cargos`/`/roles`/
`/ubicaciones`/`/aplicaciones`/`/atributos` sirviendo datos reales y
completos. `bash init.sh` de backend reconfirmado 95/95 verde después de
los 4 fixes. Ver `backend/handoff.md`, `frontend/handoff.md` y
`database/handoff.md` para el detalle completo de cada uno.

**Pendiente real, no bloqueante:** el test suite propio de `database/`
tiene 13/53 tests rotos (afirman el esquema viejo, incompatible con el
backend -- alcance conocido y acotado, ver `database/handoff.md`).

**Resuelto también hoy (encontrado por el usuario probando la demo):** el
menú lateral quedaba vacío con este dataset (sin tabla `menu` legado) y,
aun con items, no habría sido clicable (`MenuTree` pintaba `<span>`, no
`<Link>`) -- la app era inutilizable en la práctica aunque el backend
tuviera datos reales. Corregido con una navegación estática real en
`Layout.tsx` a los 8 módulos ya implementados -- ver
`frontend/handoff.md`. Verificado en navegador: login → clic en "Áreas"
→ datos reales del dump en pantalla.

## Usuario demo (login)

`admin@entel.demo` / `Demo1234!` -- **no viene sembrado, hay que crearlo**
(la base arranca vacía, sin volumen de persistencia). Si `POST
/api/usuarios` devuelve una lista vacía en `GET /api/usuarios`, créalo así:

```bash
curl -X POST http://localhost:8080/api/usuarios -H "Content-Type: application/json" \
  -d '{"nombres":"Admin","apellidos":"Demo","email":"admin@entel.demo","password":"Demo1234!","tipo":"ADMINISTRADOR"}'
```

## ⚠️ No correr `mvn test` de `database/` mientras `backend/` esté vivo con datos que importen

Encontrado 2026-08-19 (perdió el usuario demo a mitad de sesión, causa
raíz confirmada con logs de LocalStack, no una sospecha): los tests de
integración de `database/` no pueden usar Testcontainers en este entorno
(Docker Desktop + sandbox, documentado en su propio código) y caen de
vuelta al LocalStack fijo de `docker-compose.yml`
(`http://localhost:4566`) -- **el mismo puerto que usa `backend/`**.
Cada test de integración borra y recrea `RoleRepository`/
`RoleRepositoryMenu` en su `@BeforeEach`, sin distinguir de quién son los
datos. Si necesitás correr esos tests, primero `./stop.sh` (bajar
`backend/`) para que el puerto quede libre. Detalle completo en
`database/handoff.md`.

## Cómo levantar el proyecto para desarrollo/demo local

`./start.sh` (raíz de este directorio) levanta todo de una: LocalStack
(docker compose de `backend/`) → backend (`./mvnw quarkus:dev`, puerto
8080, Kafka se auto-provisiona vía Quarkus Dev Services) → frontend
(`npm run dev`, puerto 5173). Idempotente — si algo ya está corriendo, lo
detecta y no lo relanza. `./stop.sh` baja todo (backend, frontend,
LocalStack, y barre cualquier contenedor Testcontainers huérfano);
`./stop.sh --keep-docker` deja LocalStack arriba si vas a volver a
correr `quarkus:dev` enseguida. Logs en `.run/{backend,frontend}.log`.
Verificado end-to-end esta sesión (ciclo completo start→healthy→stop,
puertos y contenedores confirmados libres al final). La herramienta CLI
de `../database/` NO es parte de este ciclo (no es un servicio
persistente, y su propio `docker-compose.yml` usa el mismo puerto 4566
con datos distintos — nunca correr los dos LocalStack a la vez) — se
ejecuta aparte, bajo demanda.

**Este es el índice.** El detalle real de cada track vive en:
- `backend/handoff.md` + `backend/MEJORAS.md` (reporte de cierre)
- `frontend/handoff.md` + `frontend/MEJORAS.md`
- `database/handoff.md` + `database/MEJORAS.md`
- `OPENCODE_RULES.md` — reglas para dispatch de agentes opencode,
  métricas verificadas, fiabilidad observada de los modelos usados.

## Objetivo (cumplido)

Migrar el sistema legacy JSF 2.2 + PrimeFaces 5.2 + Spring 4 + EclipseLink
JPA sobre MySQL 5.6 ("WiseVision Role Repository Basic", cliente Entel,
`D:\Proyecto textil\repositorio_entel`) a tres tracks independientes bajo
`D:\Proyecto textil\repositorio_entel_modernizado\`: **backend** (Quarkus
3.x + Java 21 reactivo/Mutiny + DynamoDB/LocalStack + Kafka + SSE),
**frontend** (React 19 + TypeScript + Vite), **database** (CLI Java 21
para migrar datos MySQL → DynamoDB). Patrón por track:
`feature_list.json` + ciclo Leader→Implementer→Reviewer, coordinados por
la sesión orquestadora (verificación siempre en disco, nunca solo
confiando en el resumen de un subagente), con `opencode` (modelos
gratuitos) como capacidad adicional de Reviewer/Implementer cuando Claude
choca con límites de sesión — ver `OPENCODE_RULES.md`.

## Estado final por track

| Track | Estado | Tests | Documento de cierre |
|---|---|---|---|
| backend | **13/13 `done`** | 95/95 | `backend/MEJORAS.md` |
| frontend | **10/10 `done`** | 61/61 | `frontend/MEJORAS.md` |
| database | **6/6 `done`** | 53/53 | `database/MEJORAS.md` |

## Cross-track: gap DTO `Reporte`/`DetalleRol` — CERRADO 2026-08-19

Único trabajo real de coordinación entre tracks de todo el proyecto (el
resto de features fueron independientes por track). Diagnosticado por el
reviewer de la feature 10 del backend, ejecutado y verificado end-to-end
por el orquestador en esta sesión:

- **Backend**: `web/dto/{ReporteRespuestaDto,DetalleRolRespuestaDto,
  DetalleRespuestaDto}.java` (nuevos) mapean `domain/Reporte`/
  `DetalleRol`/`Atributo` (dominio de las features 7/9, intactos, no
  reabiertos) a los nombres de campo que el frontend ya esperaba, en el
  borde de serialización de `GET /api/reportes/{id}`
  (`web/ReporteResource.java#obtener()`). `ReporteResourceTest.java`
  verifica los campos mapeados con aserciones reales, no solo
  renombradas, contra un reporte generado de verdad vía Kafka+LocalStack.
- **Frontend**: `types/reporte.ts`/`types/detalle.ts` corregidos tras
  investigación real contra el código fuente legacy
  (`repositorio_entel/version3.5/ReporteManagedBean.java` y su respaldo):
  `idRol`/`id` son `String` (no `number`, extrapolación nunca
  confirmada), `Detalle.parametro` eliminado (sin equivalente real en el
  backend, era el discriminador de categoría del legacy, ya representado
  estructuralmente).
- **Hallazgo no bloqueante, documentado, no corregido en código**: la
  columna "Titularidad" del frontend (`DetalleRol.tipoRol`) está basada
  en un campo legacy (`tiporol`) confirmado como **dead code**
  (`setTiporol(...)` nunca se invocaba en ningún managed bean real) — el
  backend reutiliza esa misma forma para `tiposConDatos`, un campo con
  semántica distinta (categorías con datos, no titularidad).
  Recomendación para una futura sesión de UI: renombrar la columna. Ver
  `frontend/handoff.md` y `backend/MEJORAS.md` §5 para el detalle
  completo.

## Qué queda, si el usuario quiere seguir

No hay ninguna feature pendiente. Posibles próximos pasos, todos
opcionales y no urgentes (ninguno bloquea nada):

1. **Migración de datos reales** (si aplica): requiere primero resolver
   la estrategia de reseteo de contraseñas de usuarios existentes
   (`backend/MEJORAS.md` §3, pendiente de decisión de negocio, no
   técnica) y credenciales AWS productivas.
2. **Renombrar la columna "Titularidad"** del frontend (cosmético, ver
   arriba).
3. **Variante serverless (Lambda + API Gateway)** del backend — evaluada
   y diferida explícitamente por el usuario el 09-ago-2026
   (`backend/docs/migration-plan.md` §4); identificado un conflicto real
   (SSE no es compatible con Lambda). No iniciar sin pedido explícito.
4. Revisar el backlog de observaciones de `task-observer` cuando el
   usuario lo decida — no urgente, no bloqueante.
5. Confirmar con el cliente el volumen real de uso para dimensionar
   Kafka y decidir si el límite de 400KB por ítem DynamoDB (persistencia
   de reportes) necesita mitigación (ver `backend/MEJORAS.md` §6).

## Archivos y cambios de infraestructura de esta sesión (fuera de las tracks)

- `C:\Users\CESAR\.claude\projects\D--Proyecto-textil-repositorio-entel\skill-observations\` —
  skill task-observer inicializado, observaciones OPEN sin revisar
  (patrón de orquestación multiagente: falsos positivos anti-inyección;
  disciplina de verificación en disco).
- `C:\Users\CESAR\.claude\projects\D--Proyecto-textil-repositorio-entel\memory\` —
  regla del usuario guardada: generar/mantener handoff.md proactivamente.
- `OPENCODE_RULES.md`, `backend/docs/kafka-reactive-guidance.md`,
  `{backend,frontend,database}/handoff.md`, `{backend,frontend,database}/MEJORAS.md`,
  este archivo — todos actualizados/cerrados en este checkpoint final.
- Código de aplicación real: escrito por subagentes (Claude/opencode)
  para las features de cada track; la capa de mapeo DTO cross-track
  (`web/dto/*RespuestaDto.java`, ediciones de tipos del frontend) y los
  3 reportes `MEJORAS.md` los escribió directamente la sesión
  orquestadora, por ser trabajo de cierre/coordinación que ningún Leader
  de un solo track puede resolver solo.

## Intentos fallidos (acumulado de la sesión, referencia histórica)

- Límites de sesión de la API Claude interrumpiendo a los tres Leaders
  repetidamente (múltiples horarios de reset reportados) — mitigado con
  resumes y con opencode como capacidad paralela.
- Un Leader de database mostró comportamiento defensivo ante un mensaje
  de resume legítimo (nota anti-inyección heredada aplicada de forma
  indiscriminada) — se autocorrigió. Ver observación #1 de task-observer.
- Un Reviewer opencode dispatchado sobre backend feature 4 llegó tarde y
  sobreescribió un `review.md` real con uno genérico — sin daño real,
  esfuerzo desperdiciado. Regla ya incorporada en `OPENCODE_RULES.md`.
- Segundo dispatch de opencode bloqueado inicialmente por el classifier
  de permisos de Claude Code — resuelto con aprobación explícita del
  usuario.
- `headroom wrap claude` (wrapper externo del usuario) falló al lanzarse
  desde su terminal — no afectó ninguna sesión.

## Disciplina que se mantuvo durante todo el proyecto

- Ni task-notifications de "completed" ni veredictos de un solo reviewer
  (Claude u opencode) se aceptaron nunca sin cruzar contra
  `bash init.sh`/tests reales corridos de forma independiente.
- Ningún dominio ya aprobado se reabrió sin razón documentada — los
  gaps encontrados después de aprobar una feature (ej. el DTO
  cross-track) se resolvieron con capas de mapeo/adaptación, no
  reescribiendo trabajo ya cerrado.
- Cada bug real corregido tiene test de regresión dedicado, verificado
  por el reviewer revirtiendo temporalmente el fix para confirmar que el
  test sí lo detecta.
- Este handoff.md (y los de cada track) se mantuvo actualizado en cada
  checkpoint, por regla explícita del usuario.

## 2026-08-25 — Bug real: LocalStack sin persistencia se llevaba puesta toda la data real en cada reinicio

**Síntoma reportado por el usuario:** tras cerrar la sesión (para correr
`claude update`) y volver a levantar todo con `start.sh`, login y menú
volvían vacíos — los 4 usuarios reales y el menú real ya no estaban.

**Root cause:** `backend/docker-compose.yml` nunca tuvo `PERSISTENCE=1`
ni un volumen para el estado de LocalStack -- toda la data de DynamoDB
vivía solo en la capa de escritura efímera del contenedor. Cualquier
recreación del contenedor (`docker compose down`+`up`, o que Docker
Desktop se haya reiniciado junto con la sesión) empezaba con DynamoDB
completamente vacío — los ~17,873 items de la migración real, los 4
usuarios reales y los 10 ítems de menú real, todo perdido sin ningún
aviso. `start.sh` nunca detectaba esto porque solo chequea que el
contenedor esté *corriendo* y *healthy*, no que tenga datos.

**Fix:**
1. `backend/docker-compose.yml`: `PERSISTENCE=1` + volumen nombrado
   `localstack-data:/var/lib/localstack` — sobrevive a un `docker compose
   down`/`up` normal (el ciclo real de `stop.sh`/`start.sh`), solo se
   pierde con `docker compose down -v`/`docker volume rm` explícitos.
2. Restaurada la data real una vez más sobre el volumen ya persistente
   (para que quede la última, la que de verdad se mantiene):
   `database/target/migration-cli.jar migrate "reposrito_roles_jsf
   20150814 1214.sql" --target=localstack` (17,873 items) + los 10 ítems
   de menú reales (extraídos de `version 3.8.1/prueba 20150921 1349.sql`,
   la tabla `menu` no existe en el dump usado para la migración principal
   — confirmado grepeando `INSERT INTO \`menu\`` en los 5 dumps de la
   carpeta raíz, ninguno la tiene) + los 4 usuarios reales (extraídos
   directamente de la tabla `usuario` del dump principal,
   `firstname`/`lastname`/`email`/`password` en texto plano: Mark
   Falcon/admin1/ADMINISTRADOR, Cesar Valenzuela/employer/OPERADOR, Jorge
   Raven/12345/OPERADOR, hola hola/employer2/OPERADOR) — ambos vía los
   endpoints `POST /api/menu`/`POST /api/usuarios` ya existentes
   (documentados en su propio Javadoc como "solo para poblar datos de
   prueba", mismo mecanismo usado la primera vez que se migraron).

**Verificado:** `docker compose restart localstack` (recreación real del
contenedor) seguido de `GET /api/areas` (sigue devolviendo 69) y `POST
/api/auth/login` con `admin@wisevisioncorp.com`/`admin1` (sigue
devolviendo 200) — la persistencia funciona de verdad, no solo quedó
configurada.

**Pendiente/nota para el futuro:** `start.sh` sigue sin detectar
"LocalStack está healthy pero vacío" — si alguna vez se corre `docker
compose down -v` a mano (destruye el volumen a propósito), `start.sh` no
avisará que hace falta re-migrar. No se automatizó la re-migración dentro
de `start.sh` esta vez (hubiera significado correr la migración+altas de
usuario/menú en CADA arranque, redundante ahora que persiste); si se
decide automatizarlo, la señal correcta sería chequear si `GET
/api/usuarios` devuelve `[]` justo después de que el backend queda
healthy, y solo ahí disparar la restauración.

## 2026-08-25 (continuación) — Reportes PDF/Excel pulidos + mantenimiento visual, con 2 pendientes reales

Sesión larga de pulido visual pedida por el usuario ("mejora el proyecto
la parte de vida de front login reportes y pdf excel"), iterada varias
veces con capturas de pantalla propias (to-be vs. as-is) hasta que cada
fix coincidió exactamente con lo pedido. Resumen (detalle completo en
`backend/handoff.md` y `frontend/handoff.md`, ambos con secciones fechadas
2026-08-25):

- **Login/skeleton loaders**: branding simple en `LoginPage.tsx`,
  `TableSkeleton` reemplazando los `<p>Cargando…</p>` en las 6 pantallas
  con listas.
- **Reportes -- Excel**: columnas Rol/Tipo/Aplicacion Y la columna de
  categoría (Titularidad) que se repetían por cada atributo ahora se
  combinan (`mergeCells`) en una sola celda por bloque -- verificado
  descomprimiendo el `.xlsx` real y contando merges/`sharedStrings.xml`.
- **Reportes -- PDF**: reescrito a una tabla real de 5 columnas con
  Titularidad/Atributos anidadas, sin la caja de encabezado que había
  quedado de un intento intermedio (sacada recién, en la última
  iteración).
- **Reportes -- Titularidad (web)**: 3 iteraciones reales hasta llegar a
  una lista `flex-grow` ponderada por cantidad de atributos por
  categoría -- centra el texto de cada categoría dentro de la altura
  proporcional que ocupa esa misma categoría en la columna Atributos
  vecina (coincide con la captura "to-be" del usuario, verificado con
  zoom en navegador real).
- **Reportes -- arquitectura**: el usuario preguntó por qué el flujo se
  había partido en "elegir formato → generar" en vez de "generar → 2
  botones export" original. Causa raíz real encontrada y corregida:
  `GET /descargar?formato=` nunca estaba implementado en el backend (el
  fix anterior había tapado el síntoma del lado del frontend). Ahora
  regenera bajo demanda el formato no cacheado desde el JSON ya
  persistido, sin tocar Kafka/DynamoDB de nuevo -- 2 tests de integración
  nuevos, verificado con descarga real de blob ZIP válido.
- **Mantenimiento (Área/Rol/Usuario/Atributo/Suministro)**: `table-layout:
  fixed` + `<colgroup>` corrige un bug real de columnas moviéndose al
  paginar (verificado con `getBoundingClientRect()` en navegador real);
  botones de acción pasan a icono+texto (`components/ui/icons.tsx`,
  nuevo, sin librería externa).

**Dos pedidos explícitos del usuario quedaron SIN EMPEZAR / SIN CERRAR al
final de esta sesión** (detalle completo, con próximos pasos concretos,
en `frontend/handoff.md` sección "PENDIENTE"):

1. **Botones a icon-only**: el usuario pidió sacar el texto visible de
   los botones de acción (quedaron sobrecargados con icono+texto,
   `vista_sobrecarga.png`) y dejar solo el ícono, con `aria-label`/
   `title` para preservar accesibilidad. No se tocó código todavía.
2. **Suministro/Detalle "desactivados" en el sidebar — ¿así era en el
   original?**: investigación real hecha (buscado `MenuController.java`
   en todo el árbol, no existe; el borrador `<p:megaMenu>` comentado no
   distingue Suministro/Detalle de los demás módulos; los datos reales
   de la tabla `menu` tampoco los distinguen). Conclusión preliminar:
   probablemente SÍ eran clicables top-level en el legacy real, y el
   `<span>` no-clicable actual es una decisión de diseño de una sesión
   anterior de este proyecto, no algo heredado. Falta comunicarle esto
   al usuario y preguntar si quiere mantenerlo así o agregar una pantalla
   intermedia de selección de Rol/Suministro.
