# Historial de sesiones

> Log append-only. Cada entrada representa una sesión de trabajo cerrada.

---

## Sesión 09-ago-2026 — Feature 1: bootstrap_quarkus_reactive_project — DONE

**Veredicto:** APPROVED (ver detalle completo, ya reemplazado, en el
`progress/review.md` de esta sesión — este resumen conserva lo esencial).

**Qué se hizo:**
- Se generó el esqueleto Quarkus 3.38.1 (`quarkus-maven-plugin`), groupId
  `pe.edu.cibertec.proyemp`, artifactId `entel-role-repository-backend`,
  Java 21 (`maven.compiler.release=21` + `java.version=21`), directamente
  en la raíz de `backend/` (pom.xml, mvnw, mvnw.cmd, src/, etc.).
- Extensiones: `quarkus-rest-jackson`, `quarkus-mutiny`,
  `quarkus-amazon-dynamodb`, `quarkus-messaging-kafka` (equivalentes
  actuales de los nombres cortos legacy del enunciado
  `resteasy-reactive-jackson`/`smallrye-mutiny`/`smallrye-reactive-messaging-kafka`,
  que ya no resuelven en esta versión de la plataforma), más
  `quarkus-smallrye-health` (añadida, no estaba en el enunciado original,
  necesaria para que `/q/health` exista).
- Nuevo `src/main/java/pe/edu/cibertec/proyemp/web/StatusResource.java`:
  `GET /api/status` devuelve `Uni<Response>`, capa `web/` según
  `docs/conventions.md`.
- `init.sh` corregido: tenía un bug de plantilla (guard que siempre
  abortaba con "no esta parametrizado todavia" porque comparaba
  `BUILD_CMD` contra el mismo valor que se le acababa de asignar). Ahora
  ejecuta `mvn clean compile` → `mvn test` de verdad.
- Se eliminó el scaffold `GreetingResource*` (no encajaba en la capa
  `web/` obligatoria). Se dejaron `MyMessagingApplication.java` y
  `MyLivenessCheck.java` (demos del codestart, sin lógica de dominio,
  documentado que se reemplazan/reubican en la feature 7) — el único
  `System.out.println` de `MyMessagingApplication` se cambió a JBoss
  `Logger`.
- Tests nuevos: `HealthEndpointTest` (`/q/health` → 200),
  `StatusResourceTest` (`/api/status` → 200), más el
  `MyMessagingApplicationTest` del codestart. `mvn test`: 3/3 verdes.

**Verificación (implementer y reviewer, cada uno de forma independiente):**
`mvn clean compile` OK, `mvn test` OK (3/3, Dev Services levantó Kafka y
LocalStack vía Docker sin credenciales reales), `./init.sh` exit 0,
`mvn quarkus:dev` en vivo con `GET /q/health` → 200 UP y `GET /api/status`
→ 200 con el body esperado. Sin `System.out.println`, sin Spring/JPA/
EclipseLink, sin secretos ni credenciales AWS reales (confirmado con
grep). `../database/` y `../frontend/` no tocados.

**Observaciones menores no bloqueantes (reviewer):** clases demo del
codestart (`MyMessagingApplication`, `MyLivenessCheck`) fuera de las capas
declaradas — aceptado para bootstrap, a resolver en feature 7; propiedad
`java.version=21` en `pom.xml` es redundante junto a
`maven.compiler.release=21` (inofensivo, cumple el texto literal del
criterio de aceptación).

**Archivos clave:** `pom.xml`, `init.sh`,
`src/main/java/pe/edu/cibertec/proyemp/web/StatusResource.java`,
`src/test/java/pe/edu/cibertec/proyemp/HealthEndpointTest.java`,
`src/test/java/pe/edu/cibertec/proyemp/web/StatusResourceTest.java`.

---

## Sesión 09-ago-2026 — Feature 2: setup_localstack_dynamodb_secretsmanager — DONE

**Veredicto:** APPROVED (detalle completo en el `progress/review.md` de
esta sesión, luego sobrescrito para la siguiente feature — este resumen
conserva lo esencial).

**Qué se hizo:**
- `docker-compose.yml` nuevo: servicio `localstack`
  (`localstack/localstack:3`), `SERVICES=dynamodb,secretsmanager,kms`,
  puerto 4566, healthcheck sobre `/_localstack/health`.
- `pom.xml`: agregada extensión `io.quarkiverse.amazonservices:quarkus-amazon-secretsmanager`.
- `src/main/resources/application.properties`: config `%dev.quarkus.dynamodb.*`
  / `%dev.quarkus.secretsmanager.*` apuntando a `http://localhost:4566` con
  credenciales dummy `test`/`test` (flujo manual `docker-compose up -d` +
  `mvn quarkus:dev`). Perfil `%test` deliberadamente sin `endpoint-override`
  fijo — Quarkus Dev Services autoprovisiona un LocalStack efímero para
  `@QuarkusTest`, manteniendo `mvn test` autocontenido.
- Tests nuevos en `src/test/java/pe/edu/cibertec/proyemp/infra/`:
  `DynamoDbConnectivityTest` (crea tabla `TestConnectivityTable`,
  escribe/lee un ítem vía `DynamoDbAsyncClient` envuelto en `Uni`) y
  `SecretsManagerConnectivityTest` (crea/lee un secreto de prueba vía
  `SecretsManagerAsyncClient` envuelto en `Uni`). Ambos smoke tests de
  conectividad genérica, explícitamente NO el esquema de dominio real
  (eso depende de `../database/`).

**Incidente y fix real durante la sesión:** la primera pasada del
implementer se cortó por límite de sesión de la cuenta justo antes de
correr `mvn test`. Al retomar, el Leader corrió `./init.sh` y encontró un
fallo real de CDI: `Unsatisfied dependency` para `DynamoDbAsyncClient`/
`SecretsManagerAsyncClient`. Diagnóstico confirmado por un segundo
implementer inspeccionando los poms/jars de las extensiones Quarkiverse en
`~/.m2`: `netty-nio-client` está declarado `optional=true` en esas
extensiones (con exclusiones explícitas), así que Maven no lo arrastra
transitivamente — sin él, el deployment processor nunca registra el
productor CDI del cliente async. **Fix:** se agregó
`software.amazon.awssdk:netty-nio-client` (sin versión fija, gestionado
por el BOM ya importado) a `pom.xml`. Se confirmó que
`quarkus.<servicio>.async-client.type` ya tenía `@WithDefault("netty")`,
así que no hizo falta config adicional en `application.properties`.

**Verificación (implementer y reviewer, cada uno de forma independiente):**
`mvn clean compile` OK, `mvn test` 6/6 verdes (4 preexistentes de feature 1
+ 2 nuevos, LocalStack real vía Dev Services confirmado en el log, no
mock), `./init.sh` verde (corrido más de una vez), `docker-compose up -d
localstack` → `healthy` confirmado con `docker inspect`, `docker-compose
down` sin contenedores huérfanos, grep sin credenciales AWS reales
(solo `test`/`test` de LocalStack), sin clientes DynamoDB/SecretsManager
síncronos, sin `.await()` fuera de tests, sin `System.out.println`.
`../database/` y `../frontend/` no tocados; no se implementó esquema de
dominio.

**Archivos clave:** `docker-compose.yml`, `pom.xml`,
`src/main/resources/application.properties`,
`src/test/java/pe/edu/cibertec/proyemp/infra/DynamoDbConnectivityTest.java`,
`src/test/java/pe/edu/cibertec/proyemp/infra/SecretsManagerConnectivityTest.java`.

---

## Sesión 09-ago-2026 — Feature 3: crud_catalogos_simples — DONE

**Veredicto:** APPROVED (detalle completo en el `progress/review.md` de
esta sesión, luego sobrescrito para la siguiente feature — este resumen
conserva lo esencial).

**Dependencia:** `../database/feature_list.json` → `design_dynamodb_schema`
estaba `done` (confirmado antes de empezar), así que la feature NO quedó
`blocked`. Esquema canónico usado, sin inventar uno propio: tabla única
`RoleRepository`, catálogos simples con PK `ENTITY#<TIPO>#<codigo>`, SK
`METADATA` (Patrón 1 de `../database/docs/architecture.md`).

**Qué se hizo:** CRUD REST reactivo completo (GET listar, GET por código,
POST crear, PUT actualizar, DELETE eliminar) para los 5 catálogos simples
(`/api/areas`, `/api/cargos`, `/api/ubicaciones`, `/api/aplicaciones`,
`/api/estados`), en capas `domain/`/`repository/`/`service/`/`web/`:
- `domain/`: `TipoCatalogo`, `Area`, `Cargo`, `Ubicacion`, `Aplicacion`,
  `Estado`, `CatalogoNoEncontradoException`. Cada catálogo modelado como
  `(codigo: String, nombre: String)` — `codigo` = el ID numérico legado
  (el que usan las FKs reales, ej. `Rol.b_area` → `Area.ID`), no el código
  de negocio corto (`ID_AREA`) que se descartó por no tener integraciones
  externas conocidas.
- `repository/`: `RoleRepositoryTable` (constantes de nombres), 
  `RoleRepositoryTableInitializer` (crea la tabla `RoleRepository` con el
  esquema físico completo — pk/sk + GSI1 — en `@Observes StartupEvent` si
  no existe, idempotente, para que la feature 4 no tenga que alterarla),
  `CatalogoSimpleRepository<T>` genérico (no bean CDI) + 5 repos delgados
  por catálogo. Contador atómico DynamoDB (`PK=COUNTER#<TIPO>`,
  `UpdateItem ADD valor :1`) para generar códigos de altas nuevas.
- `service/`: `CatalogoSimpleService<T>` genérico + 5 servicios delgados.
- `web/`: 5 `@Path` resources + `dto/CatalogoRequest` +
  `exception/CatalogoExceptionMappers` (`@ServerExceptionMapper` mapea
  `CatalogoNoEncontradoException` → 404).
- Toda la cadena repository→service→web es `Uni<T>`/`Multi<T>`, sin
  clientes síncronos; el único `.await()` del proyecto está correctamente
  confinado al bootstrap (`RoleRepositoryTableInitializer`).
- Tests nuevos (20): `AreaResourceTest` (8, CRUD completo + 404),
  `AplicacionResourceTest`, `CargoResourceTest`, `EstadoResourceTest`,
  `UbicacionResourceTest` (3 cada uno) — todos crean + buscan por código
  contra LocalStack real vía Dev Services.

**Incidente de sesión:** el implementer se cortó por límite de cuenta
justo después de que sus 26 tests pasaran (antes de documentar la
verificación final). El Leader retomó, corrió `./init.sh`/grep de
credenciales él mismo para confirmar el estado real, completó
`progress/current.md`, y pasó a reviewer sin relanzar un implementer
(no hacía falta — el código y los tests ya estaban completos y verdes).

**Verificación (Leader y reviewer, cada uno de forma independiente):**
`mvn clean compile` OK, `mvn test` → 26/26 verdes (6 preexistentes de
features 1-2 + 20 nuevos), `./init.sh` verde, LocalStack real confirmado
en el log (no mock), sin `System.out.println`, sin clientes DynamoDB
síncronos, sin credenciales reales, sin Spring/JPA/EclipseLink. No se
tocó `../database/` ni `../frontend/`; no se implementó Rol/Suministro/
Atributo/Detalle/Menu/Reportes (fuera de scope, quedan para features 4 y
6-8).

**Observaciones menores no bloqueantes (reviewer):** `listarTodos()` usa
`Scan` filtrado en vez de `Query` (razonable dado el esquema canónico de
PK, vigilar si el volumen crece con las features 4/6); el mecanismo de
contador atómico para IDs nuevos no está en la tabla de diseño de
`../database/docs/architecture.md`, pero ese documento delega
explícitamente esa decisión a `../backend/`.

**Archivos clave:** `src/main/java/pe/edu/cibertec/proyemp/domain/**`,
`repository/RoleRepositoryTable.java`,
`repository/RoleRepositoryTableInitializer.java`,
`repository/CatalogoSimpleRepository.java`,
`service/CatalogoSimpleService.java`, `web/*Resource.java`.

---

## Sesión 15-ago-2026 — Feature 4: crud_rol_suministro_atributo — DONE

**Veredicto:** APPROVED (detalle completo, ya reemplazado, en el
`progress/review.md` de esta sesión — este resumen conserva lo esencial).

**Qué se hizo:** cadena de dominio Rol → Suministro → Atributo sobre la
tabla única `RoleRepository`, reutilizando exactamente los patrones
2b/2/3/4 del esquema canónico de `../database/docs/architecture.md` (sin
inventar esquema propio):
- Dominio nuevo: `Rol`, `Suministro`, `Atributo`, `CategoriaSuministro`
  (enum ROLES/GRUPOS/INBOX/DEFAULTINBOX), `EstadoAtributo` (enum
  ACTIVO/INACTIVO), `RolResumen`, `CargoConRoles`,
  `RolNoEncontradoException`, `SuministroNoEncontradoException`.
- Repositorios/servicios/recursos nuevos: `RolRepository`/`RolService`/
  `RolResource`, `SuministroRepository`/`SuministroService`/
  `SuministroResource`, `AtributoRepository`/`AtributoService`/
  `AtributoResource`, más DTOs de request y
  `web/exception/RolExceptionMappers.java`.
- `repository/RoleRepositoryTable.java` extendido (no reescrito) con los
  constructores de clave de los patrones 2b/2/3/4 (`rolPk`,
  `suministroSk`, `atributoPk`, `detalleSk`, `gsi1PkAplicacionArea`,
  `gsi1SkCargoRol`, `gsi1SkCargoPrefix`); `RoleRepositoryTableInitializer`
  no se tocó (ya crea la tabla con GSI1 completo desde la feature 3).
- Endpoints: CRUD de Rol y de Suministro-bajo-Rol; `GET
  /api/roles/cargos?aplicacion=&area=` (replica `CargoRepository.findCargo`);
  `GET /api/atributos?aplicacion=&area=[&cargo=]` (sin `cargo` replica
  `findAppArea`, con `cargo` replica `findAppArea2` exacto, patrón 4); `GET
  /api/atributos/rol/{idRol}/categoria/{categoria}?aplicacion=` (las 4
  categorías, un único endpoint parametrizado); `POST /api/atributos`
  (alta, para poblar datos de prueba — Atributo no tiene CRUD completo,
  decisión documentada, ningún acceptance lo exige).
- 15 tests nuevos `@QuarkusTest`: `RolResourceTest` (5),
  `SuministroResourceTest` (4), `AtributoResourceTest` (6).

**Verificación (implementer, Leader y reviewer, cada uno de forma
independiente):** `mvn clean compile` OK, `mvn test` → **41/41 verdes**
(26 preexistentes de features 1-3 + 15 nuevos), `./init.sh` verde,
LocalStack real + Kafka embebido confirmados en el log (no mocks). Sin
`System.out.println`, sin `.await()` nuevo fuera del bootstrap
preexistente, sin `synchronized`, sin clientes DynamoDB síncronos, sin
credenciales reales, sin Spring/JPA/EclipseLink. Toda la cadena
repository→service→web es `Uni`/`Multi` de punta a punta. No se tocó
`../database/` ni `../frontend/`, ni ninguna feature 5-8
(Usuario/Login/Menu/Reportes).

**Observaciones menores no bloqueantes (reviewer):** `RolRepository.
listarTodos()` usa `Scan` filtrado en vez de `Query` (mismo patrón ya
aceptado en la feature 3, vigilar si el volumen crece); `Atributo` no
tiene actualización/borrado (asimetría intencional, documentada, ningún
acceptance lo exige); `EstadoAtributo` es un enum propio en vez de
reusar el catálogo `Estado` genérico de la feature 3 (razonado en su
Javadoc, no bloquea).

**Archivos clave:**
`src/main/java/pe/edu/cibertec/proyemp/domain/{Rol,Suministro,Atributo,
CategoriaSuministro,EstadoAtributo,RolResumen,CargoConRoles,
RolNoEncontradoException,SuministroNoEncontradoException}.java`,
`repository/{RolRepository,SuministroRepository,AtributoRepository,
RoleRepositoryTable}.java`,
`service/{RolService,SuministroService,AtributoService}.java`,
`web/{RolResource,SuministroResource,AtributoResource}.java`,
`web/dto/{RolRequest,SuministroRequest,AtributoRequest}.java`,
`web/exception/RolExceptionMappers.java`,
`src/test/java/pe/edu/cibertec/proyemp/web/{RolResourceTest,
SuministroResourceTest,AtributoResourceTest}.java`.

---

## Sesión 15-ago-2026 — Feature 5: usuario_login_password_hash_fix — DONE

**Veredicto:** APPROVED (detalle completo, ya reemplazado, en el
`progress/review.md` de esta sesión — este resumen conserva lo esencial).

**Qué se hizo:** reemplazo del hashing de contraseñas legacy (SHA-256 sin
sal, único camino de login realmente ejecutado por
`LoginManagedBean.iniciarSesion()`) por BCrypt de una vía. `Cripto.java`
(AES/ECB reversible) y `Encripcion.java` (código muerto, solo referenciado
en un bloque comentado del legacy) **no se portaron**, ni siquiera como
código muerto.
- **Diseño de dominio:** `Login`/`LoginRepository` del legacy NO se migran
  como entidad separada — se fusionaron en una única entidad
  `domain/Usuario.java` (decisión documentada: el camino de login vivo
  solo usaba `UsuarioRepository`, `LoginRepository` era tabla espejo sin
  FK, nunca tocada por el flujo real). Nuevas clases: `Usuario`,
  `TipoUsuario`, `UsuarioNoEncontradoException`, `EmailDuplicadoException`,
  `CredencialesInvalidasException`.
- **Diseño DynamoDB propio** (el esquema canónico de `../database/` no
  cubre Usuario/Login, mismo criterio que la feature 4 usó para Rol, sin
  tocar `RoleRepositoryTableInitializer`): fila principal
  `PK=USUARIO#<id>/SK=METADATA` + fila puntero
  `PK=EMAIL#<email-minusculas>/SK=METADATA` para lookup directo de login
  sin Scan; alta/baja vía `TransactWriteItems` (dos filas atómicas,
  `attribute_not_exists(pk)`) para unicidad de email sin filas huérfanas.
- **Hashing:** `service/PasswordHasher.java`, envoltorio de
  `at.favre.lib:bcrypt:0.10.2` (cost factor 12, dependencia añadida a
  `pom.xml`, no gestionada por el BOM). El hash ocurre exclusivamente en
  `UsuarioService.crear()` antes de persistir — `UsuarioRequest`
  deliberadamente no tiene `aDominio()`, no existe camino a texto plano
  persistido.
- **Servicios/endpoints:** `service/UsuarioService.java` (alta/lectura/
  baja), `service/AuthService.java` (login: compara hash contra hash vía
  `PasswordHasher.matches`, nunca texto plano). `web/UsuarioResource.java`
  (`/api/usuarios`, CRUD alta/lectura/baja, respuestas siempre
  `UsuarioResponse` sin `passwordHash`), `web/AuthResource.java`
  (`POST /api/auth/login`, 200 + datos básicos o 401 genérico tanto para
  email inexistente como password incorrecto, evita filtrar por
  temporización/mensaje). `web/exception/UsuarioExceptionMappers.java`
  (404/409/401).
- **`docs/migration-plan.md` §4 no se tocó** — decisión explícita y
  aceptada por el reviewer: el criterio es condicional ("si se define el
  mecanismo final de reseteo") y esta feature no definió ningún mecanismo
  de reseteo distinto al ya documentado como `⚠️ Pendiente`; solo
  implementó BCrypt para altas nuevas.
- **Tests nuevos (13):** `service/PasswordHasherTest` (4, unit puro, sin
  LocalStack), `web/UsuarioResourceTest` (6, incluye verificación directa
  contra el ítem crudo de DynamoDB de que el hash guardado empieza con
  `$2` y que dos usuarios con la misma contraseña tienen hashes distintos),
  `web/AuthResourceTest` (3: login correcto, 401 password incorrecto, 401
  email inexistente).

**Verificación (implementer, Leader y reviewer, cada uno de forma
independiente):** `mvn clean compile` OK, `mvn test` → **54/54 verdes**
(41 preexistentes de features 1-4 + 13 nuevos), `./init.sh` verde,
LocalStack real + Kafka embebido confirmados en el log (no mocks). Grep en
vivo confirma cero clases `Cripto`/`Encripcion` y cero
`Cipher.getInstance` (solo Javadoc explicando la decisión de no
portarlos), sin `System.out.println` nuevo, sin credenciales
hardcodeadas nuevas. Toda la cadena repository→service→web es
`Uni`/`Multi` de punta a punta, constructor injection, excepciones de
dominio mapeadas explícitamente. No se tocó `../database/` ni
`../frontend/`, ni ninguna feature 6-11.

**Archivos clave:**
`pom.xml` (dependencia bcrypt),
`src/main/java/pe/edu/cibertec/proyemp/domain/{Usuario,TipoUsuario,
UsuarioNoEncontradoException,EmailDuplicadoException,
CredencialesInvalidasException}.java`,
`repository/{UsuarioRepository,RoleRepositoryTable}.java`,
`service/{PasswordHasher,UsuarioService,AuthService}.java`,
`web/{UsuarioResource,AuthResource}.java`,
`web/dto/{UsuarioRequest,UsuarioResponse,LoginRequest}.java`,
`web/exception/UsuarioExceptionMappers.java`,
`src/test/java/pe/edu/cibertec/proyemp/{service/PasswordHasherTest,
web/UsuarioResourceTest,web/AuthResourceTest}.java`.

---

## Sesión 15/16-ago-2026 — Feature 6: menu_dinamico_por_tipo_usuario — DONE

**Veredicto:** APPROVED (detalle completo, ya reemplazado, en el
`progress/review.md` de esta sesión — este resumen conserva lo esencial).

**Qué se hizo:** endpoint `GET /api/menu/{tipoUsuario}`, reemplazando
`MenuController.java` del legacy, **corrigiendo dos bugs de origen
confirmados por grep sobre el árbol fuente legado real** (no replicados,
mismo principio ya aplicado al bug de dedup de `ReporteManagedBean`,
`docs/migration-plan.md` §3.5):
1. **Filtro asimétrico por tipoUsuario.** El legacy solo filtraba de
   verdad los ítems/submenús cuando el usuario en sesión era `"O"`
   (Operador); para cualquier otro valor (incluido `"A"`, Administrador) el
   filtro se saltaba por completo. Corregido: filtro simétrico para
   cualquier valor recibido.
2. **Jerarquía por producto cruzado.** `cod_submenu` (campo de
   `model/Menu.java`) nunca se usaba para enlazar nada en todo el árbol
   fuente legado — cada submenú recibía TODOS los ítems que pasaban el
   filtro de tipo, no solo sus hijos reales. Corregido: `MenuService.
   armarArbol()` enlaza estrictamente `codSubmenu == codigo` del padre.
   Test de regresión (`should_notCrossJoinItemsAcrossDifferentSubmenus_...`)
   verificado línea por línea, tanto por el implementer (revirtiendo
   temporalmente la lógica) como de forma independiente por el reviewer —
   confirmado que detecta el bug real.

**Esquema DynamoDB:** tabla **separada** `RoleRepositoryMenu` (Patrón 5 del
esquema canónico `../database/docs/architecture.md`, a diferencia de
Usuario en la feature 5 que no tenía patrón canónico) — `PK=MENU#<tipo>`
(`tipo`="S"|"I"), `SK=ORDEN#<codigo>`, sin GSI. Nuevo
`repository/MenuTableInitializer.java` (análogo a
`RoleRepositoryTableInitializer` de la feature 3, `@Observes StartupEvent`,
`CreateTable` idempotente) — el backend no la creaba todavía.

**Dominio/repositorio/servicio/web nuevos:**
`domain/{MenuTipo,EstadoMenu,MenuItem,MenuNodo}.java` (reutiliza el enum
`TipoUsuario` de la feature 5, no lo duplica),
`repository/{MenuTable,MenuTableInitializer,MenuRepository}.java`
(`Query` por `PK=MENU#<tipo>` + `FilterExpression` tipoUsuario+estado,
nunca `Scan`), `service/MenuService.java` (arma el árbol, incluye `crear`
para poblar datos de prueba), `web/MenuResource.java` (`GET
/api/menu/{tipoUsuario}`, `POST /api/menu` para seed).

**Incidente y autocorrección durante la sesión:** la primera versión de
`MenuResource` inyectaba `MenuRepository` directamente para el endpoint de
seed, violando la regla dura "`web/` no accede a DynamoDB directamente".
El implementer lo detectó y corrigió él mismo antes de cerrar (movió
`crear` a `MenuService`), confirmado en el código final por el reviewer.

**Tests nuevos (5)** en `web/MenuResourceTest.java`: jerarquía básica,
regresión de cross-join, exclusión de inactivos/tipoUsuario distinto, 2
tipoUsuario distintos con menús que difieren, 400 por tipoUsuario
inválido.

**Verificación (implementer y reviewer, cada uno de forma
independiente):** `mvn clean compile` OK, `mvn test` → **59/59 verdes**
(54 preexistentes de features 1-5 + 5 nuevos), `./init.sh` verde,
LocalStack real + Kafka embebido confirmados en el log. Toda la cadena
repository→service→web es `Uni`/`Multi` de punta a punta, constructor
injection, sin `.await()` nuevo fuera del bootstrap. No se tocó
`../database/` ni `../frontend/`, ni ninguna feature 7-11.

**Nota operativa de esta sesión:** el primer reviewer lanzado murió a
mitad de camino por límite de sesión, dejando `progress/review.md` con
solo el esqueleto de ejemplo del formato de veredicto (no una review
real) — detectado por el Leader al leer el archivo antes de confiar en
él, se relanzó un reviewer nuevo desde cero que sí completó el veredicto
real.

**Archivos clave:**
`src/main/java/pe/edu/cibertec/proyemp/domain/{MenuTipo,EstadoMenu,
MenuItem,MenuNodo}.java`,
`repository/{MenuTable,MenuTableInitializer,MenuRepository}.java`,
`service/MenuService.java`, `web/MenuResource.java`,
`web/dto/MenuRequest.java`,
`src/test/java/pe/edu/cibertec/proyemp/web/MenuResourceTest.java`.

---

## Sesión 17/18-ago-2026 — Feature 7: reportes_dominio_y_query_sin_duplicados — DONE

**Veredicto:** APPROVED (detalle completo, ya reemplazado, en el
`progress/review.md` de esta sesión — este resumen conserva lo esencial).

**Contexto:** primera de tres sub-features resultantes de dividir la
antigua feature 7 única (`reportes_generacion_async_kafka`) el 16-ago-2026
por regla 4 de `docs/plan-to-features.md` (mezclaba ensamblado de datos +
adaptadores PDF/Excel + orquestación Kafka en una sola feature). Esta
feature es **solo** el ensamblado de datos: sin Kafka, sin PDF/Excel, sin
endpoint HTTP propio.

**Qué se hizo:** `service/ReporteService.generar(idAplicacion, idArea)` →
`Uni<List<Reporte>>` con la estructura Cargo→Rol→4 categorías
(Roles/Grupos/Inbox/DefaultInbox), componiendo únicamente servicios/
repositorios ya existentes de las features 3 (`AreaRepository`,
`AplicacionRepository`) y 4 (`AtributoService.listarCargosConRoles` +
`AtributoService.listarPorCategoria`) — cero acceso directo a DynamoDB
desde esta clase, cero `.await()` fuera de tests. Nuevo dominio
`domain/{Reporte,DetalleRol}.java` (records, reutilizan `Atributo`/
`CategoriaSuministro` de la feature 4 en vez de reinventar tipos; se omite
a propósito el campo `cantidadSuministros` del legacy, dead code nunca
poblado). Corrige en origen el bug de duplicados de
`ReporteManagedBean.reporte()/imprimir()` (`docs/migration-plan.md`
§3.5): la no-duplicación viene del agrupado `LinkedHashMap`/`putIfAbsent`
ya existente en `AtributoService.agruparPorCargo` (feature 4) y de que
cada `Atributo` es un ítem DynamoDB distinto — nunca de un `Set` posterior
a la query.

**Tests nuevos (3)** en `src/test/java/pe/edu/cibertec/proyemp/web/ReporteServiceTest.java`
(en `web/`, no `service/` — ver incidente abajo): estructura completa con
datos reales de features 3/4, no-duplicados con el mismo escenario que
forzaba el `HashSet` del legacy (2 cargos/3 roles/6 atributos, verificado
por el reviewer línea por línea que sí detectaría una regresión), lista
vacía cuando el área no tiene cargos con atributos activos.

**Incidente de sesión (misma feature, dos rondas):** una primera sesión
implementó los 4 archivos completos pero murió por límite de cuenta antes
de arreglar un error de compilación real: `ReporteServiceTest` vivía en el
paquete `service` pero llamaba a los helpers estáticos package-private de
`RolResourceTest` (paquete `web`) — error de visibilidad de paquete, no de
diseño. El Leader lo confirmó corriendo `./init.sh` él mismo antes de
relanzar; una segunda sesión movió el archivo de test a `web/` (mismo
paquete que `RolResourceTest`/`AtributoResourceTest`/`SuministroResourceTest`,
consistente con el patrón ya aprobado en la feature 4), sin tocar
`domain/Reporte.java`, `domain/DetalleRol.java` ni
`service/ReporteService.java` — confirmado por el reviewer comparando
timestamps de archivo.

**Verificación (implementer x2 y reviewer, cada uno de forma
independiente):** `mvn clean compile` OK, `mvn test` → **62/62 verdes**
(59 preexistentes de features 1-6 + 3 nuevos), `./init.sh` verde,
LocalStack real + Kafka embebido confirmados en el log. Sin endpoint HTTP
nuevo bajo `/api/reportes/*` (ausencia deliberada, documentada, evita
colisión de rutas con las features 9/10 futuras). No se tocó
`../database/` ni `../frontend/`, ni ninguna otra feature 8-13.

**Archivos clave:**
`src/main/java/pe/edu/cibertec/proyemp/domain/{Reporte,DetalleRol}.java`,
`service/ReporteService.java`,
`src/test/java/pe/edu/cibertec/proyemp/web/ReporteServiceTest.java`.

---

## Sesión 18-ago-2026 — Feature 8: reportes_adaptadores_pdf_excel — DONE

**Veredicto:** APPROVED (detalle completo, ya reemplazado, en el
`progress/review.md` de esta sesión — este resumen conserva lo esencial).

**Contexto:** segunda de tres sub-features resultantes de dividir la
antigua feature 7 única (`reportes_generacion_async_kafka`) el 16-ago-2026.
Solo los adaptadores PDF/Excel: sin Kafka, sin endpoint HTTP, sin
persistencia (eso es la feature 9). Reutiliza `domain/Reporte.java`/
`DetalleRol.java` de la feature 7 (ya `done`) sin tocarlos.

**Decisiones de dependencias:** **OpenPDF** `com.github.librepdf:openpdf:1.3.30`
(LGPL 2.1) en vez de iText 5 (dual LGPL/MPL, ambiguo) o iText 7 (AGPL) —
mismo paquete `com.lowagie.text.*` que el iText 4/5 clásico que ya usaba
el legacy, API casi idéntica. **Apache POI** `org.apache.poi:poi-ooxml:5.2.5`
con `XSSFWorkbook` (`.xlsx`), no `HSSFWorkbook` (`.xls` legacy). Ninguna
gestionada por el BOM de Quarkus, ambas con versión explícita en `pom.xml`.

**Qué se hizo:** `report/ReportGeneratorPort.java` (interfaz, un solo
método `Uni<byte[]> generar(List<Reporte>)`), implementado por
`report/PdfReportAdapter.java` y `report/ExcelReportAdapter.java` como
beans CDI `@Named("pdf")`/`@Named("excel")` diferenciados por nombre (en
vez de dos interfaces separadas) para que la feature 9 pueda inyectar "el
generador de PDF"/"el generador de Excel" sin dos tipos Java distintos.
Ambos adaptadores reproducen el contenido real de
`ReporteManagedBean.imprimir()`/`excel()` del legacy (tabla cabecera
Área+Cargo, filas/bloques por cada `DetalleRol` con las 4 categorías de
`Atributo`), generando en `ByteArrayOutputStream` — cero
`FileOutputStream` a ruta fija, cero `Runtime.exec`. Trabajo bloqueante
offloadeado con `.emitOn(Infrastructure.getDefaultWorkerPool())` antes de
tocar OpenPDF/POI en ambos adaptadores, documentado con comentario.

**Bug real encontrado y corregido durante la sesión:** el layout PDF
original replicaba la estructura anidada del legacy (tabla de atributos
angosta dentro de una celda de la tabla de 5 columnas), lo que hacía que
OpenPDF cortara palabras letra por letra ("Atributo" → "Atribut"/"o") por
falta de ancho — confirmado empíricamente generando el PDF fuera de Maven
e inspeccionando el texto extraído. Se rediseñó a una tabla de atributos a
ancho completo (sin anidamiento), documentado en el Javadoc de
`PdfReportAdapter`. El reviewer verificó el fix leyendo el código final y
confirmando que el test que lo cubre (`contenido.contains("Atributo Roles")`
sin fragmentar) sí detectaría una regresión al layout viejo.

**Test nuevo:** `src/test/java/pe/edu/cibertec/proyemp/report/ReportGeneratorAdaptersTest.java`
(3 tests, unit puro sin `@QuarkusTest`/LocalStack). Reabre el PDF generado
con `PdfReader`+`PdfTextExtractor` y el Excel con `XSSFWorkbook` de
lectura, verificando 9 datos reconocibles en cada uno (códigos/nombres de
área y cargo, nombre del rol, los 4 `atributoCodigo` de las 4 categorías)
— no un test débil de "bytes no vacíos".

**Verificación (implementer y reviewer, cada uno de forma independiente):**
`mvn clean compile` OK, `mvn test` → **65/65 verdes** (62 preexistentes de
features 1-7 + 3 nuevos), `./init.sh` verde. Un mensaje cosmético
`ERROR Log4j API could not find a logging provider` durante la corrida fue
investigado por el reviewer (`mvn dependency:tree`, confirmado transitivo
único de `poi-ooxml`, sin `log4j-core` en el classpath, no cuenta como
fallo de Surefire, ningún `catch` genérico en el código nuevo que pudiera
enmascarar un error real) — confirmado ruido cosmético, no un problema
real. Cero imports de `com.lowagie`/`org.apache.poi` fuera de `report/`
(grep en todo `src/main`). No se tocó `domain/Reporte.java`,
`domain/DetalleRol.java`, `service/ReporteService.java` (feature 7),
`../database/`, `../frontend/`, ni ninguna otra feature 9-13.

**Archivos clave:**
`report/{ReportGeneratorPort,PdfReportAdapter,ExcelReportAdapter}.java`,
`src/test/java/pe/edu/cibertec/proyemp/report/ReportGeneratorAdaptersTest.java`,
`pom.xml` (dependencias OpenPDF/POI).

---

## Sesión 18-ago-2026 — Feature 9: reportes_generacion_async_kafka — DONE

**Veredicto:** APPROVED (detalle completo, ya reemplazado, en el
`progress/review.md` de esta sesión — este resumen conserva lo esencial).

**Contexto:** tercera y última de las tres sub-features en que se dividió
la antigua feature 7 única (16-ago-2026). La pieza de mayor riesgo:
orquestación Kafka real con garantías de entrega, verificada contra el
checklist obligatorio de `docs/kafka-reactive-guidance.md` (escrito
específicamente para que el reviewer de esta feature y la 10 lo use
literal, no como preferencia de estilo). Reutiliza `service/ReporteService`
(feature 7) y `report/ReportGeneratorPort` con sus adaptadores
`@Named("pdf")`/`@Named("excel")` (feature 8) sin tocarlos.

**Qué se hizo:** `POST /api/reportes/generar` (`idAplicacion`, `idArea`,
`formato=pdf|excel`) persiste fila `PENDIENTE` en DynamoDB (antes de
publicar, orden estricto) → publica `report.requested` (key=id del
reporte) → responde `202 Accepted` + `id` (nunca el binario).
`messaging/ReporteRequestWorker` (`@Incoming("report-requested-in")`)
consume, verifica el `estado` persistido (dedupe ante redelivery: si ya es
`LISTO`/`ERROR`, no regenera), reutiliza `ReporteService.generar` +
`ReportGeneratorPort` según el `formato` guardado, persiste binario (tipo
`B` nativo DynamoDB) + `List<Reporte>` serializado a JSON + `estado=LISTO`,
publica `report.ready`. Reintentos acotados a 3 vía contador **persistido**
en DynamoDB (no en memoria — sobrevive a un crash-restart del worker a
media generación) antes de `estado=ERROR` + `motivoError` + publica
`report.failed`.

**Checklist de `docs/kafka-reactive-guidance.md` — los 6 puntos
verificados por el reviewer contra código real, no por el relato del
implementer:**
1. Productor `acks=all` + `enable.idempotence=true` explícitos en los tres
   canales de salida (`report-requested-out`/`report-ready-out`/
   `report-failed-out`).
2. Consumer verifica `estado` persistido antes de regenerar — confirmado
   en vivo con el log real de la corrida de test.
3. `commit-strategy=throttled`, no auto-commit/`latest`.
4. Límite de reintentos (`MAX_INTENTOS=3`) + camino a `ERROR`/
   `report.failed` — confirmado ejecutando el test real de agotamiento
   (log muestra exactamente 3 intentos), y evaluada explícitamente la
   robustez ante crash-restart a media generación (el contador vive en
   DynamoDB, el offset solo avanza tras completar exitosamente).
5. SSE correctamente ausente (confirmado por grep, scope de la feature 10).
6. Partition key = id del reporte en los tres tópicos, nunca el id de
   usuario ni un valor aleatorio.

**Decisiones de diseño notables:** persistencia sobre la tabla única
`RoleRepository` (`PK=REPORTE#<id>/SK=METADATA`, id=UUID), sin tocar
`RoleRepositoryTableInitializer`; limitación de 400KB por ítem DynamoDB
documentada explícitamente en tres lugares (no omisión silenciosa,
S3 fuera de scope); nuevo paquete `messaging/` (eventos Kafka + worker,
no encajaban en ninguna capa existente); `Emitter<String>.send(Message)`
es `void` en este BOM de Quarkus (no devuelve `CompletionStage`
directamente) — resuelto con el patrón estándar
`Message.withAck()/withNack()` completando un `CompletableFuture` propio
envuelto en `Uni.createFrom().completionStage(...)`, verificado como uso
correcto de la spec de MicroProfile Reactive Messaging, no una invención.

**Investigación específica del reviewer:** un stack trace largo de AWS
SDK (`AsyncRetryableStage`) que aparecía en el log de test fue rastreado
hasta confirmar que eran frames del hilo completando el `CompletableFuture`
de DynamoDB en el momento exacto en que el test de poison-pill lanzaba su
`CatalogoNoEncontradoException` de aplicación (área inexistente a
propósito) — no una excepción originada en el SDK ni una falla de
infraestructura real, correctamente capturada por
`.onFailure().recoverWithUni(...)`.

**Verificación (implementer y reviewer, cada uno de forma independiente,
más el Leader corriendo `./init.sh` una tercera vez):** `mvn clean compile`
OK, `mvn test` → **69/69 verdes** (65 preexistentes de features 1-8 + 4
nuevos en `ReporteGenerarResourceTest`), `./init.sh` verde, LocalStack +
Kafka reales vía Quarkus Dev Services. Sin `FileOutputStream`/
`Runtime.exec`/`synchronized`/`System.out.println` nuevos. No se tocó
`service/ReporteService.java`, `domain/Reporte.java`,
`domain/DetalleRol.java` (feature 7), `report/*.java` (feature 8),
`../database/`, `../frontend/`, ni ninguna otra feature 10-13.

**Nota operativa de esta sesión:** el primer reviewer lanzado pareció
quedarse a medias (su notificación inicial mencionaba estar "esperando un
monitor de corrida de tests" — malentendido de herramientas, no existe tal
notificación para un reviewer) y el Leader relanzó un segundo reviewer
creyendo que el primero no terminaría; el primero en realidad sí continuó
y completó una review real y completa poco después, entregando el
`APPROVED` que se usó para cerrar esta feature. El Leader no pudo detener
al segundo reviewer (restricción de permisos entre agentes) pero actuó de
inmediato sobre el veredicto ya verificado del primero para evitar una
condición de carrera sobre `progress/review.md`.

**Archivos clave:**
`domain/{EstadoReporte,ReporteEjecucion,ReporteEjecucionNoEncontradaException}.java`,
`repository/ReporteEjecucionRepository.java`,
`repository/RoleRepositoryTable.java` (editado),
`messaging/{ReportRequestedEvent,ReportReadyEvent,ReportFailedEvent,ReporteRequestWorker}.java`,
`web/ReporteGenerarResource.java`,
`web/dto/{ReporteGenerarRequest,ReporteGenerarResponse}.java`,
`src/main/resources/application.properties` (editado),
`docs/architecture.md` (editado),
`src/test/java/pe/edu/cibertec/proyemp/web/ReporteGenerarResourceTest.java`.

---

## Sesión 18-ago-2026 — Feature 10: reportes_notificacion_sse_y_descarga — DONE

**Veredicto:** APPROVED (detalle completo, ya reemplazado, en el
`progress/review.md` de esta sesión — este resumen conserva lo esencial).

**Contexto:** cierra la cadena de reportes async iniciada en las features
7/8/9. Expone `GET /api/reportes/eventos` (SSE), `GET /api/reportes/{id}`
(árbol JSON) y `GET /api/reportes/{id}/descargar` (binario), reutilizando
`ReporteEjecucionRepository` (feature 9) tal cual, sin rediseñar
persistencia.

**Qué se hizo:** `messaging/ReporteEventoBroadcaster` (`@ApplicationScoped`,
envuelve `BroadcastProcessor<ReporteEvento>` de Mutiny) es el puente
per-proceso que `docs/kafka-reactive-guidance.md` exige en vez de que el
SSE consuma Kafka directamente — `GET /eventos` solo expone
`broadcaster.stream()`, cero `@Incoming`/consumer Kafka propio por
conexión (punto 5 del checklist de la guía, el único que esta feature
activa por primera vez). `messaging/ReporteRequestWorker.java` (feature 9,
ya aprobada) fue tocado de forma **aditiva pura**: `publicarReady`/
`publicarFailed` encadenan `.invoke(() -> eventoBroadcaster.emitir(...))`
DESPUÉS del ack real de Kafka — dedupe, límite de reintentos
(`MAX_INTENTOS=3`), commit-strategy y persistencia quedaron byte a byte
intactos, verificado por el reviewer comparando contra la review de la
feature 9 y confirmado indirectamente por los 4/4 tests preexistentes de
`ReporteGenerarResourceTest` sin cambios. `web/ReporteResource.java`
(`GET /{id}`, `GET /{id}/descargar`) usa dos excepciones de dominio
(`ReporteNoListoException` nueva, `ReporteEjecucionNoEncontradaException`
ya existía sin mapper) → 404 vía `ReporteExceptionMappers`.
`docs/architecture.md` corregido (el SSE no consume Kafka, es el worker
quien lo hace) y ampliado (justificación SSE-vs-WebSocket, tabla de
tópicos, nueva subsección de endpoints).

**Tests nuevos (8)** en `web/ReporteResourceTest.java`: entrega de evento
al suscriptor, circuito SSE real de punta a punta (`POST /generar` →
worker → `report.ready` → broadcaster → suscriptor, no solo aislado), 404
por id inexistente/reporte pendiente (x2, descarga y consulta), descarga
con binario+header `Content-Disposition` correctos, consulta de árbol
`Reporte[]` con forma jerárquica real verificada.

**Discrepancia de campos con `../frontend/` — decisión razonada del
reviewer, no bloqueante para esta feature:** `domain/Reporte`/
`domain/DetalleRol` (ya aprobados en la feature 7) usan nombres distintos
a los que `../frontend/src/types/reporte.ts` espera (`areaCodigo` vs
`area`/`codigoArea`, `tiposConDatos` vs `tipoRol`, etc.). El reviewer falló
a favor de serializar el dominio real tal cual (cumple "misma forma
estructural", no "mismos nombres de campo byte a byte"), citando que el
propio archivo del frontend se autodocumenta tres veces como
"extrapolación propia no confirmada" y que igualar nombres exactos exigiría
reabrir dominio ya aprobado fuera de scope. **Acción pendiente explícita
para el Leader:** coordinar con el Leader de `../frontend/` para reconciliar
esos nombres antes de integrar de verdad el endpoint — sin eso, la
integración real rompería en runtime.

**Verificación (implementer y reviewer, cada uno de forma independiente,
más el Leader corriendo `./init.sh` una tercera vez):** `mvn clean compile`
OK, `mvn test` → **77/77 verdes** (69 preexistentes de features 1-9 + 8
nuevos), `./init.sh` verde, LocalStack + Kafka reales vía Dev Services. Sin
`FileOutputStream`/`Runtime.exec`/`synchronized`/`System.out.println`
nuevos. No se tocó `service/ReporteService.java`, `domain/Reporte.java`,
`domain/DetalleRol.java` (feature 7), `report/*.java` (feature 8), la
lógica de negocio de la feature 9 más allá del side-effect declarado,
`../database/`, `../frontend/`, ni ninguna otra feature 11-13.

**Notas no bloqueantes dejadas por el reviewer para fases futuras:** (1)
coordinación Leader↔Leader pendiente sobre nombres de campo (ver arriba);
(2) considerar en la feature 12 (resiliencia) envolver
`eventoBroadcaster.emitir(...)` con manejo de error explícito para que una
falla del puente SSE nunca contamine el resultado del worker Kafka; (3) el
patrón de `web/` inyectando `repository/` directamente sin pasar por
`service/` (ya presente desde la feature 9 en `ReporteGenerarResource`, se
repite aquí) vale la pena decidir explícitamente como regla o excepción
documentada al cerrar el proyecto (feature 13).

**Archivos clave:**
`domain/ReporteNoListoException.java`,
`messaging/{ReporteEvento,ReporteEventoBroadcaster}.java` (nuevos),
`messaging/ReporteRequestWorker.java` (editado, aditivo),
`web/exception/ReporteExceptionMappers.java`, `web/ReporteResource.java`,
`src/test/java/pe/edu/cibertec/proyemp/web/ReporteResourceTest.java`,
`docs/architecture.md` (editado).

---

## Sesión 19-ago-2026 — Feature 11: aws_secrets_manager_integration — DONE

**Veredicto:** APPROVED (detalle completo, ya reemplazado, en el
`progress/review.md` de esta sesión — este resumen conserva lo esencial).

**Qué se hizo:** auditoría de `application.properties`/`docker-compose.yml`
(features 1-10) sin hallazgos de credenciales reales nuevas. Bean
`@Observes StartupEvent` (`infra/AppSecretsBootstrap.java`, mismo patrón
ya aprobado que `RoleRepositoryTableInitializer`/`MenuTableInitializer`):
lee el secreto `app/dynamodb/table-name` de Secrets Manager, lo crea si no
existe (valor de `RoleRepositoryTable.TABLE_NAME`) y **siempre relee**
desde `GetSecretValue` real, nunca de memoria. Se evaluó y descartó a
propósito el `SecretsManagerConfigSourceFactory` nativo de Quarkus
(resuelve antes de CDI, exige que el secreto preexista — incómodo de
probar de forma no frágil contra Dev Services).

**Bug real encontrado y corregido:** `application.properties` tenía
`%dev.quarkus.dynamodb.sdk.endpoint-override` /
`%dev.quarkus.secretsmanager.sdk.endpoint-override` con un segmento `sdk.`
que no existe en la versión 3.21.1 realmente resuelta (confirmado en
`quarkus-config-model.json` de los jars vía `mvn dependency:tree` +
extracción) — Quarkus ignora keys desconocidas en vez de fallar el build,
nunca se detectó porque `mvn test` usa Dev Services y no pasa por el
archivo. Corregido en ambas líneas.

**Tests nuevos (3)** en `infra/AppSecretsBootstrapTest.java`: el bean
resolvió el secreto durante `StartupEvent` antes del test; el valor
coincide con una consulta independiente a Secrets Manager (no hardcodeado
en memoria); una segunda resolución manual es idempotente.

**Verificación (orquestador top-level, sesión sin Leader/Reviewer
dedicado — el implementer dejó la feature `in_progress` a propósito para
que se revisara, ver `progress/current.md` de esta sesión):** los 4
acceptance criteria verificados uno por uno en disco: grep de
credenciales sin hallazgos reales fuera de los dummy `test`/`test` de
LocalStack; `AppSecretsBootstrapTest` corrido aislado (3/3) además de en
la suite completa; `docs/architecture.md` documenta la resolución de
secretos por ambiente; `./init.sh` → **80/80 verdes** (77 preexistentes
de features 1-10 + 3 nuevos), `BUILD SUCCESS`. Sin regresiones.

**Archivos clave:** `infra/AppSecretsBootstrap.java`,
`src/main/resources/application.properties` (bug corregido),
`docs/architecture.md` (editado),
`src/test/java/pe/edu/cibertec/proyemp/infra/AppSecretsBootstrapTest.java`.

---

## Sesión 19-ago-2026 — Feature 12: reactive_resilience_observability — DONE

**Veredicto:** APPROVED (detalle completo, ya reemplazado, en el
`progress/review.md` de esta sesión — este resumen conserva lo esencial).

**Qué se hizo:** health checks reactivos de readiness para DynamoDB
(`infra/health/DynamoDbReadinessCheck.java`) y Secrets Manager
(`SecretsManagerReadinessCheck.java`), ambos `AsyncHealthCheck` de
SmallRye Health (`Uni<HealthCheckResponse>`, nunca bloqueante) — Kafka ya
tenía readiness automático vía `quarkus-messaging-kafka` desde la feature
1, confirmado explorando `/q/health/ready` antes de escribir código en vez
de asumir que hacía falta un check propio. Manejo explícito de fallos de
infraestructura: `web/UniFallosInfraestructura.java` (helper aplicado en
los 4 endpoints "principales" de reportes: generar/obtener/descargar/SSE)
+ `web/exception/InfraestructuraExceptionMappers.java`
(`@ServerExceptionMapper` centralizado para `SdkException`/
`KafkaException` sin envolver, cubre de forma centralizada el resto de
~10 endpoints de escritura sin repetir el wrapper en cada uno) — ambos
caminos terminan en HTTP 503 con mensaje claro, nunca el 500 genérico.
SSE (`web/ReporteResource.java#eventos()`): `.onCancellation()` (log
DEBUG, sin relanzar) + `.onFailure()` defensivo para cierre limpio ante
desconexión del cliente. `messaging/ReporteRequestWorker.java`: nuevo
`emitirEventoSeguro` aísla el puente SSE del resultado del worker Kafka —
resuelve la nota no bloqueante que había quedado pendiente desde la
review de la feature 10.

**Tests nuevos (15)** en 7 archivos: `DynamoDbReadinessCheckTest`/
`SecretsManagerReadinessCheckTest` (casos UP reales contra LocalStack vía
Dev Services, casos DOWN con un cliente standalone apuntado a un puerto
inalcanzable, sin arriesgar el arranque de la suite completa),
`ReadinessHealthEndpointTest` (`/q/health/ready` completo, los 3 checks),
`InfraestructuraExceptionMappersTest`, `UniFallosInfraestructuraTest`,
`ReporteResourceSseDisconnectTest` (simula desconexión real vía
`AssertSubscriber.cancel()`), `ReporteRequestWorkerSseIsolationTest`.

**Verificación (orquestador top-level, tras un implementer dispatchado en
background — mismo patrón que la feature 11):** los 5 acceptance
criteria verificados uno por uno en disco: grep sin `System.out.println`
en `src/main`; `./init.sh` corrido por el orquestador → **95/95 verdes**
(80 preexistentes de features 1-11 + 15 nuevos), `BUILD SUCCESS`. Sin
regresiones — `messaging/ReporteRequestWorker.java` modificado de forma
aditiva (dedupe/reintentos/commit-strategy de las features 9/10 sin
tocar).

**Archivos clave:**
`domain/InfraestructuraNoDisponibleException.java`,
`web/UniFallosInfraestructura.java`,
`web/exception/InfraestructuraExceptionMappers.java`,
`infra/health/{DynamoDbReadinessCheck,SecretsManagerReadinessCheck}.java`,
`web/{ReporteGenerarResource,ReporteResource}.java` (editados),
`messaging/ReporteRequestWorker.java` (editado, aditivo).

---

## Sesión 19-ago-2026 — Coordinación cross-track: gap DTO Reporte/DetalleRol (backend↔frontend) — CERRADO

No es una feature de `feature_list.json` (trabajo explícito del
orquestador top-level, ningún Leader de un solo track podía resolverlo).
Cierra el gap diagnosticado y dejado abierto por el reviewer de la
feature 10: `domain/Reporte`/`DetalleRol` (backend) no coincidían en
nombres/tipos de campo con `../frontend/src/types/reporte.ts`.

**Decisión (confirmada explícitamente con el usuario):** el backend
agrega una capa de mapeo de respuesta en vez de reabrir el dominio ya
aprobado (features 7/9) o forzar al frontend a adivinar de nuevo.
`web/dto/{ReporteRespuestaDto,DetalleRolRespuestaDto,
DetalleRespuestaDto}.java` (nuevos) traducen `domain/Reporte`/
`DetalleRol`/`Atributo` a los nombres que el frontend ya esperaba.
`web/ReporteResource.java#obtener()` ahora devuelve
`ReporteRespuestaDto.de(lista)`. `ReporteResourceTest.java` actualizado
con aserciones reales (no solo renombradas) sobre los campos mapeados
contra un reporte generado de verdad vía Kafka+LocalStack.

**Investigación real que resolvió una ambigüedad de diseño:** antes de
mapear `tiposConDatos`→`tipoRol` a ciegas, se confirmó contra el código
fuente legacy real (`repositorio_entel/version3.5/
ReporteManagedBean.java` y su respaldo `CopyOfReporteManagedBean.java`,
los dos únicos lugares donde se construye un `DetalleRol`) que
`setTiporol(...)` **nunca se invocaba** — la columna "Titularidad" del
legacy siempre renderizó una tabla vacía (dead code), así que no había
semántica original que preservar; `tiposConDatos` (categorías con datos)
es un reemplazo razonable, no una regresión. También se confirmó que
`Detalle.parametro` (frontend) no tiene equivalente real en `domain/
Atributo` porque `d_parametro` (legacy) era el discriminador de
categoría, ya representado estructuralmente — se eliminó del tipo
frontend en vez de fabricar un valor sin respaldo.

**Verificación:** `./init.sh` del backend → 95/95 verde (incluye 8/8 de
`ReporteResourceTest` con las aserciones nuevas). Frontend: `npx tsc -b`
limpio, `npx vitest run` → 61/61 verde. Ver `../frontend/handoff.md` y
`handoff.md` raíz para el detalle completo del lado frontend.

**Archivos clave (backend):**
`web/dto/{ReporteRespuestaDto,DetalleRolRespuestaDto,
DetalleRespuestaDto}.java` (nuevos), `web/ReporteResource.java` (editado),
`src/test/java/pe/edu/cibertec/proyemp/web/ReporteResourceTest.java`
(editado).

---
