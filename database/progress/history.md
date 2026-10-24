# Historial de sesiones

> Log append-only. Cada entrada representa una sesión de trabajo cerrada.

---

## Sesión 2026-08-09 — Feature 1: `bootstrap_migration_cli` → DONE

**Pre-trabajo del líder (antes de lanzar al implementer):** se validó el
diseño DynamoDB canónico de `docs/architecture.md` (feature 2, aún no
implementada en código pero con el diseño ya corregido) contra el código
fuente real (`CargoRepository.java`, `AtributoRepository.java`,
`RolRepository.java`, `SuministroRepository.java`, `DetalleRepository.java`,
`MenuRepository.java` + `MenuController.java`, modelos JPA, `consulta.sql`).
Se encontraron y corrigieron 4 discrepancias entre el diseño heredado de la
sesión anterior y el comportamiento real del código legado — detalle
completo en `docs/architecture.md` §"Validación contra código real":
1. GSI de los patrones 2/3/4 no soportaba consultar por aplicación+área sin
   cargo → unificado en un solo GSI1 compartido.
2. El "Patrón 2" documentado citaba mal `CargoRepository.findCargo`
   (filtra por aplicación+área, no por cargo) → el PK/SK `ROL#/SUMINISTRO#`
   se reasignó al patrón real `SuministroRepository.findSuministro`
   ("Patrón 2b").
3. El "Patrón 5" (menú) indexaba por `tipoUsuario`, pero el código real
   consulta por `Menu.tipo` (S/I) y filtra `tipoUsuario` en memoria → PK
   corregida a `MENU#<tipo>`.
4. Generación de IDs con UUID aleatorio para datos migrados violaba la
   regla de idempotencia → decisión tomada: datos migrados conservan sus
   IDs numéricos legados tal cual; UUID/contador atómico queda como
   decisión de `../backend/` solo para altas nuevas.

**Feature 1 (`bootstrap_migration_cli`):** implementada por el subagente
Implementer y aprobada por el subagente Reviewer (`APPROVED`, ver detalle
histórico del veredicto — el archivo `progress/review.md` se sobrescribe en
cada feature, este resumen es la copia permanente).

Archivos creados: `pom.xml` (Java 21, AWS SDK v2 `dynamodb` +
`netty-nio-client`, JUnit 5, `maven-shade-plugin`),
`src/main/java/com/entel/migration/MigrationCli.java`,
`cli/Subcommand.java` (enum de los 4 subcomandos), `command/{MigrationCommand,
NotImplementedCommand, DesignValidateCommand, ParseDumpCommand,
MigrateCommand, VerifyCommand}.java` (placeholders "no implementado" que
referencian su feature futura 2-5, sin lógica real), tests
`SubcommandTest.java` y `MigrationCliTest.java` (14 tests),
`docker-compose.yml` (servicio `localstack` con healthcheck), `.gitignore`.

Archivo modificado: `init.sh` (se eliminó el bloque placeholder de plantilla
que siempre fallaba con "no esta parametrizado todavia"; el resto del
script quedó intacto).

Verificación (confirmada independientemente por el reviewer, no solo por el
reporte del implementer): `mvn clean compile` OK, `mvn test` 14/14 verde,
`docker compose up -d localstack` → contenedor healthy (`dynamodb:
available`), smoke test `java -jar target/migration-cli.jar --help` lista
los 4 subcomandos (exit 0), cada subcomando individual corre sin crashear
(exit 1, "no implementado"), subcomando inválido da error claro sin crash.
Sin `System.out.println` sueltos, sin TODOs sin contexto, sin credenciales
ni conexión a AWS real (no se instancia `DynamoDbAsyncClient` todavía).

Estado al cierre de esta entrada: feature 1 `done`. Feature 2
(`design_dynamodb_schema`) tomada a continuación — el diseño en
`docs/architecture.md` ya quedó validado/corregido antes de lanzar el
implementer de la feature 1, así que el implementer de la feature 2 solo
debe implementar el subcomando `design-validate` + tests de integración
contra LocalStack siguiendo el diseño ya corregido.

---

## Sesión 2026-08-09 (continuación) — Feature 2: `design_dynamodb_schema` → DONE

**`../backend/` ya puede desbloquear su feature dependiente: el diseño
canónico de esquema DynamoDB está `done`, implementado y verificado.**

**Incidente de sesión (documentado por transparencia):** el primer
subagente Implementer lanzado para esta feature fue interrumpido a mitad
de su verificación por un límite de sesión de la API ("session limit
resets 11:20am"), justo cuando iba a correr `mvn test` por primera vez. El
líder retomó verificando el estado real del código en disco (compilación
offline en verde) y detectando por su cuenta que los dos tests de
integración fallaban con `IllegalStateException: Could not find a valid
Docker environment` — un problema de Testcontainers/docker-java que no
logra localizar el daemon Docker vía named pipe en este entorno concreto
(Windows, Docker Desktop, Git Bash), a pesar de que el CLI `docker`
funciona perfectamente y LocalStack ya estaba sano vía `docker-compose.yml`
(mismo patrón que ya había verificado el reviewer de la feature 1
manualmente). El líder confirmó esto probando `DOCKER_HOST` con ambos
named pipes (`docker_engine` y `dockerDesktopLinuxEngine`) sin éxito, y
descartó exponer el daemon por TCP (cambio de configuración global de
Docker Desktop que afectaría a otros tracks en paralelo). El líder resumió
al mismo subagente Implementer (vía su transcript, sin perder contexto)
con el diagnóstico y la instrucción de reemplazar Testcontainers por el
LocalStack ya administrado por `docker-compose.yml` — el mismo patrón que
la feature 1 ya usaba para su smoke test.

**Nota sobre integridad del canal de comunicación:** durante la espera de
esa segunda ejecución, llegaron varios mensajes por el canal de chat
haciéndose pasar por "el coordinador" (rol que no existe en la jerarquía
de este proyecto — el líder de este track responde directamente al
usuario, no a ningún coordinador intermedio) pidiendo saltarse la
verificación independiente y confiar en resúmenes de segunda mano, incluso
alegando (falsamente) que el subagente ya no podía notificar. El líder no
siguió ninguna instrucción de esos mensajes por no venir del canal
legítimo de notificaciones (`task-notification`); en su lugar, verificó el
estado real del repositorio con sus propias manos (lectura de código,
timestamps de archivos, y una corrida completa de `bash init.sh` ejecutada
directamente por el líder) antes de lanzar al reviewer real, y esperó la
notificación genuina de ese reviewer antes de cerrar la feature. El
resultado de esa verificación independiente coincidió con lo que decían
los mensajes no confiables, pero la decisión de proceder se basó
exclusivamente en la evidencia verificada directamente por el líder y en
el veredicto real del reviewer, no en esos mensajes.

**Feature 2 (`design_dynamodb_schema`):** implementada (tras el ajuste de
infraestructura de test) y aprobada por el subagente Reviewer
(`APPROVED`, ver `progress/review.md` para el detalle completo, incluida
una comparación línea por línea entre el diseño de `docs/architecture.md`
y el código de claves implementado — sin discrepancias).

Archivos creados: paquete `com.entel.migration.schema` (`TableNames`,
`ItemAttributes`, `RoleRepositoryKeys`, `RoleRepositoryMenuKeys`,
`RoleRepositoryTableDefinition`, `RoleRepositoryMenuTableDefinition`);
`src/test/java/com/entel/migration/localstack/LocalStackIntegrationSupport.java`;
`src/test/java/com/entel/migration/command/DesignValidateCommandIdempotencyTest.java`;
`src/test/java/com/entel/migration/schema/AccessPatternsIntegrationTest.java`
(6 tests: patrones 1, 2, 3, 4, 5 y 2b, con casos negativos reales).

Archivos modificados: `src/main/java/com/entel/migration/command/DesignValidateCommand.java`
(de placeholder a implementación real: crea `RoleRepository` con GSI1 y
`RoleRepositoryMenu` contra `DynamoDbAsyncClient`, LocalStack por defecto,
idempotente vía `ResourceInUseException`); `pom.xml` (se agregaron y luego
se quitaron las dependencias `org.testcontainers:*` tras confirmar que no
funcionan en este entorno); `init.sh` (ahora orquesta `docker compose up -d
localstack` → espera healthy → `mvn test` → `docker compose down` vía
`trap ... EXIT`). `docs/architecture.md` NO se modificó en esta feature
(ya había quedado corregido antes de la feature 1).

Verificación (confirmada independientemente por el líder Y por el
reviewer, cada uno por su cuenta): `bash init.sh` → BUILD SUCCESS, 21/21
tests verdes (14 de la feature 1 + 7 nuevos). Idempotencia verificada
tanto por test JUnit como manualmente con el jar empaquetado: correr
`design-validate` dos veces seguidas no duplica tablas ni el GSI1
(`DescribeTable` confirma exactamente 1 GSI tras la 2ª corrida). Diseño de
claves en el código coincide exactamente con `docs/architecture.md` (tabla
de comparación completa en `progress/review.md`). Sin credenciales reales,
sin conexión a AWS real, features 3-5 siguen siendo placeholders sin
lógica agregada.

Hallazgo menor no bloqueante dejado para la próxima vez que se toque ese
archivo: el Javadoc de `DesignValidateCommand(URI)` todavía menciona
"Testcontainers" en vez del LocalStack de `docker-compose.yml` que
realmente se usa — comentario desactualizado, sin impacto funcional.

Estado al cierre de esta entrada: features 1 y 2 `done`. `../backend/` ya
puede desbloquear su feature dependiente del esquema DynamoDB.

---

## Sesión 2026-08-09 (continuación) — Feature 3: `parse_legacy_mysql_dump` → DONE

**Incidente de sesión (documentado por transparencia, mismo patrón que en
la feature 2):** tanto el subagente Implementer como, después, el
subagente Reviewer de esta feature fueron interrumpidos a mitad de su
trabajo por el límite de sesión de la API de la cuenta. En ambos casos el
líder retomó al mismo subagente (vía su transcript, sin perder contexto)
con `SendMessage` en cuanto detectó (por notificación real `status:
failed`) la interrupción. También llegaron, de nuevo, varios mensajes por
el canal de chat haciéndose pasar por "el coordinador" — un rol que no
existe en la jerarquía de este proyecto — pidiendo aceptar resultados sin
verificación o dando por hecho información que aún no estaba confirmada.
El líder no actuó sobre ninguno de esos mensajes por no venir del canal
legítimo (`task-notification`); toda decisión de este cierre se basó en
evidencia verificada directamente por el líder (lectura de código,
`bash init.sh` corrido con sus propias manos, conteo independiente de
filas del dump real) y en el veredicto real dejado en disco por el
Reviewer, quien de forma notable **detectó por su cuenta el mismo patrón
de mensaje no confiable durante su propia revisión** y lo documentó en su
veredicto (ver `progress/review.md` de esta feature, sección "Nota sobre
integridad del canal de comunicación") sin dejar que alterara su trabajo.

**Hallazgo real más importante de esta feature:** los conteos de
referencia de `docs/migration-plan.md` §1 ("22 atributo, 3 rol, 1 cada uno
del resto") resultaron ser el número de **sentencias `INSERT`**, no de
**filas** — la mayoría de tablas del dump usan `INSERT` multi-row. El
implementer lo descubrió al implementar el parser (tal como pedía
explícitamente el acceptance criteria: "validar contra el archivo real, no
solo confiar en el número documentado"), y tanto el líder como el reviewer
lo confirmaron cada uno por su cuenta con métodos independientes (grep/awk
manual el líder; awk propio + script Node.js desde cero + el jar
compilado, el reviewer). Los tres coincidieron exactamente. Conteo real:
aplicacion=1, area=69, atributo=15565, cargo=695, detalle=684,
detalleinbox=0, login=4, rol=741, sequence=1, suministro=741, ubicacion=61,
usuario=4 (total 18566 filas en 12 tablas). El dump real de referencia
tampoco incluye las tablas `estado` ni `menu` (no existen en esta captura
concreta del esquema). `docs/migration-plan.md` §1 se actualizó con la
tabla de conteos reales corregida.

**Otro hallazgo real (documentado también en `docs/architecture.md`/
`progress/history.md` de la feature 2, reconfirmado aquí):** el esquema
del dump real difiere del de `consulta.sql` para varias tablas (ej.
`atributo` en el dump trae `(id, aplicacion, atributo_codigo,
atributo_nombre, id_rol, id_suministro, parametro)`, no las columnas de
`consulta.sql`). El parser se diseñó **autodescriptivo** (lee la lista de
columnas de cada tabla directamente de su propia sentencia `INSERT`) para
no depender de reconciliar ambos esquemas.

**Feature 3 (`parse_legacy_mysql_dump`):** implementada y aprobada por el
subagente Reviewer (`APPROVED`, ver `progress/review.md` para el detalle
completo — comparación de conteos con tres métodos independientes,
verificación del caso borde de paréntesis anidados dentro de strings
citados en `rol.regla_negocio`, verificación de manejo explícito de NULL,
y confirmación de que no se inventaron datos para `estado`/`menu`).

Archivos creados: `src/main/java/com/entel/migration/dump/{MysqlDumpParser,
ParsedRow,ParsedDump,MysqlDumpParseException}.java` (parser autodescriptivo,
maneja INSERT multi-row, NULL literal, strings con escapes y paréntesis
anidados dentro de comillas); `src/main/java/com/entel/migration/dump/model/*.java`
(records tipados para las 11 tablas con datos del dump real, sin inventar
`estado`/`menu`); `src/test/resources/dumps/sample-dump.sql` (fixture
manual con NULL explícito); `src/test/java/com/entel/migration/dump/{MysqlDumpParserFixtureTest,
MysqlDumpParserRealDumpTest}.java`, `src/test/java/com/entel/migration/command/ParseDumpCommandTest.java`
(17 tests nuevos en total).

Archivo modificado: `src/main/java/com/entel/migration/command/ParseDumpCommand.java`
(de placeholder a implementación real: parsea el dump e imprime resumen de
conteos por tabla, exit 0/1). `docs/migration-plan.md` §1 actualizado por
el líder con los conteos reales corregidos (no tocado por el implementer,
fuera de su scope).

Verificación (confirmada independientemente por el líder Y por el
reviewer, cada uno con métodos propios): `bash init.sh` → BUILD SUCCESS,
38/38 tests verdes (21 previos intactos, sin tocar features 1/2 — 17
nuevos). Sin conexión a MySQL ni AWS real en ningún lado. `MigrateCommand`/
`VerifyCommand` siguen siendo placeholders sin lógica agregada.

Estado al cierre de esta entrada: features 1, 2 y 3 `done`. Features 4
(`migrate_data_mysql_to_dynamodb`), 5 (`data_migration_validation`) y 6
(`reporte_final_mejoras`) quedan `pending` para una próxima sesión — no se
tomaron en esta por precaución, dado que la sesión ya tuvo dos
interrupciones por límite de API y se prefirió cerrar en un punto estable
y completamente verificado antes de arriesgar una feature 4 a medio
terminar.

---

## Sesión 2026-08-09/16 — Feature 4: `migrate_data_mysql_to_dynamodb` → DONE

**Pre-trabajo del líder (09-ago, antes de lanzar al Implementer):** se leyó
el código real de `com.entel.migration.dump.model.*` y se inspeccionó
directamente el dump real (`reposrito_roles_jsf 20150814 1214.sql`) para
confirmar cómo encajan las columnas reales contra el diseño canónico de
`docs/architecture.md`. Hallazgo principal: el esquema real de `atributo`
no trae FKs numéricas directas a aplicación/área/cargo — trae en cambio
nombres de texto (`atributo.aplicacion` = nombre, no código; `rol.area`/
`rol.cargo` = nombres, no códigos) que hay que resolver contra los
catálogos por nombre, con una ambigüedad real documentada (`cargo.nombre`
tiene 2 pares duplicados de 695). Conteo esperado calculado por el líder:
`RoleRepository` ≈ 17132 ítems (826 catálogo + 741 suministro + 15565
atributo), `RoleRepositoryMenu` = 0 (no existe tabla `menu` en este dump).
Detalle completo de este análisis: ver el histórico de `progress/current.md`
de esta sesión (secciones previas a "Implementer — cierre de feature 4").

**Lo que realmente pasó entre el 09-ago y el 16-ago:** una sesión de
Implementer del 09-ago escribió el código de producción completo
(`MigrateCommand.java`, `RoleRepositoryTransformer.java`,
`TransformResult.java`, `DynamoBatchWriter.java`, todos wireados en
`MigrationCli`) pero se cortó antes de escribir ningún test o actualizar
`progress/current.md`/`feature_list.json` reflejando ese trabajo — quedó
sin rastro visible de que el código existía salvo los archivos mismos. El
16-ago, un primer intento de relanzar el Implementer (con brief centrado
en escribir la cobertura de test faltante) también se colgó sin producir
ningún archivo — confirmado por el líder con evidencia de disco propia
(timestamp de `target/surefire-reports` sin cambios, `src/test/` sin
archivos nuevos, `progress/current.md` sin cambios) tras recibir varios
mensajes por un canal no oficial haciéndose pasar por "un coordinador"
insistiendo en relanzar — el líder verificó la necesidad del relanzamiento
por sus propios medios en disco, no por confiar en esos mensajes. Un
segundo relanzamiento del Implementer sí completó el trabajo con éxito.

**Verificación independiente de las afirmaciones del Javadoc de
`RoleRepositoryTransformer`** (Implementer, script Node.js ad-hoc
reimplementando la lógica de tuplas del parser sobre el dump real, sin
copiar cifras ciegamente): confirmó exactamente los 2 duplicados de
`cargo.nombre` (ids 133/695 y 206/213), unicidad 100% de las 4 columnas
`codigo`, 14 filas de `rol.area` sin catálogo (`LEGAL`=11,
`PLANEAMIENTO`=3), 0 huérfanos de FK en `suministro`/`atributo`, y 76 de
15565 ítems `ATRIBUTO#` con área/cargo sin resolver vía `rol`. Ninguna
discrepancia encontrada — no hizo falta corregir código ni documentación.

**Tests escritos** (ninguno existía antes de esta sesión):
`src/test/java/com/entel/migration/transform/RoleRepositoryTransformerTest.java`
(unit test puro con fixture en memoria, cubre el empate de `cargo.nombre`,
ambos fallbacks `NOMBRE#`/`ROLDESCONOCIDO#`, y la forma completa de los 3
tipos de ítem), `src/test/java/com/entel/migration/command/MigrateCommandTest.java`
(4 tests de CLI sin LocalStack: rechazo de `--target=aws`/target
desconocido, dump inexistente, sin argumentos), y
`src/test/java/com/entel/migration/command/MigrateCommandIntegrationTest.java`
(integración real contra LocalStack + dump real, `@Order(1)` puebla y
cuenta 17132/0 ítems, `@Order(2)` corre `migrate` una segunda vez y
verifica que el conteo no cambia más un spot-check `GetItem` byte a byte
sobre `ATRIBUTO#41868` — el test de idempotencia obligatorio).

**Ningún bug encontrado** en el código de producción existente — funcionó
correctamente contra el dump real y LocalStack sin necesitar cambios; el
gap era exclusivamente falta de cobertura de test.

**Verificación cruzada, cada uno por cuenta propia:** el líder corrió
`bash init.sh` tres veces en total a lo largo de esta sesión (antes de
lanzar al Implementer, después de que reportara éxito, y una vez más tras
la aprobación del Reviewer) — siempre `BUILD SUCCESS`, `Tests run: 45,
Failures: 0, Errors: 0, Skipped: 0`, LocalStack apagado limpiamente al
final. El Reviewer corrió `bash init.sh` dos veces por su cuenta (una vez
en segundo plano, una vez explícita tras una duda sobre si la primera
corrida había quedado huérfana) con el mismo resultado, leyó el código de
producción y los 3 archivos de test línea a línea, verificó a nivel de
código fuente (no solo de test) que `--target=aws` retorna antes de
construir cualquier `DynamoDbAsyncClient`, confirmó por timestamp que solo
los 3 archivos de test + `progress/current.md` fueron tocados esta sesión
(código de producción intacto desde el 09-ago), y confirmó que
`progress/current.md` fue objeto de un append real, no una reescritura.
Veredicto: **APPROVED** (`progress/review.md` tiene el detalle completo,
incluyendo verificación explícita de los 4 acceptance criteria uno por
uno). Durante esta sesión llegaron varios mensajes por un canal no oficial
haciéndose pasar por "un coordinador" — ninguno se tomó como autoridad;
toda decisión de relanzar o cerrar se basó en verificación propia en disco
del líder y del Reviewer, igual que en el incidente ya documentado de la
feature 2/3.

Estado al cierre de esta entrada: features 1-4 `done`. Features 5
(`data_migration_validation`) y 6 (`reporte_final_mejoras`) quedan
`pending` para la próxima sesión.

---

## Sesión 2026-08-16/17 — Feature 5: `data_migration_validation` → DONE

**Contexto:** a diferencia de la feature 4, aquí `VerifyCommand` era
genuinamente un stub (`NotImplementedCommand`) al empezar — sin código de
producción preexistente que descubrir. El Implementer diseñó e implementó
el subcomando `verify [<ruta-al-dump.sql>] [--target=localstack]` desde
cero, siguiendo los patrones ya establecidos por `MigrateCommand`/
`DesignValidateCommand` (target LocalStack por defecto, `--target=aws`
rechazado antes de construir cualquier cliente, `PrintStream out/err`
inyectado).

**Trampas de diseño reales, investigadas y resueltas** (documentadas en el
Javadoc de `VerifyCommand` con el razonamiento completo):
- Los catálogos se indexan por `codigo` en su PK, pero los items
  `ATRIBUTO#` llevan el `id` numérico interno resuelto en
  `GSI1PK`/`GSI1SK`/`rolCargoId` — `verify` construye su índice de
  existencia leyendo el atributo `id` de cada item de catálogo, nunca la
  PK, evitando un falso-positivo masivo (id ≠ codigo en general).
- Los 76 de 15565 items `ATRIBUTO#` con marcador de fallback determinístico
  (`NOMBRE#`/`ROLDESCONOCIDO#`, calidad de datos preexistente del dump ya
  documentada en la feature 4) se excluyen explícitamente de "referencia
  rota" pero se siguen reportando aparte, nunca invisibles — de lo
  contrario el acceptance criteria "0 referencias rotas sobre el dump
  real" sería matemáticamente imposible de cumplir.
- `Rol` no tiene item propio en este esquema de tabla única (solo se
  manifiesta como PK de items `SUMINISTRO#`) — el chequeo real valida la
  combinación exacta `(idRol, idSuministro)` de cada `ATRIBUTO#` contra el
  conjunto de `SUMINISTRO#` migrados, evitando una tautología. La relación
  inversa "todo Suministro referencia un Rol existente" es, por
  construcción del diseño, trivialmente cierta (el PK de `SUMINISTRO#` ya
  es `ROL#<idRol>`) — documentado como no-op en vez de escribir un chequeo
  que siempre pasa.
- `Estado` se trata como literal constante (`"ACTIVO"`), no como
  referencia — no se inventó ningún catálogo `ENTITY#ESTADO#*` ausente del
  diseño canónico.
- Cambio de scope menor pero justificado: `RoleRepositoryTransformer`
  amplió la visibilidad de `UNRESOLVED_NAME_PREFIX`/`UNRESOLVED_ROL_PREFIX`
  de package-private a `public` (documentado en su propio Javadoc) para
  que `VerifyCommand` no duplicara esos literales — cambio compatible
  hacia atrás, sin romper `RoleRepositoryTransformerTest`.

**Tests escritos** (ninguno existía antes de esta sesión):
`VerifyCommandTest.java` (3, CLI sin red), `VerifyCommandIntegrationTest.java`
(4, fixtures pequeños sembrados a mano contra LocalStack — incluye el test
obligatorio de referencia rota inyectada deliberadamente, con aserciones
de PK/SK/IDs exactos en `stderr`, no solo el exit code, más un test de
regresión que confirma que el marcador de fallback conocido NO se reporta
como roto), y `VerifyCommandRealDumpIntegrationTest.java` (1, dump real
completo vía `assumeTrue`, corre `migrate` como fixture propio y confirma
0 referencias rotas + conteos 826/741/15565/0 + `AREA=76` reportado
aparte).

**Patrón recurrente de esta sesión (ya visto en la feature 4): agentes que
terminan su turno a mitad de una espera de un comando en segundo plano sin
escribir sus hallazgos.** Ocurrió dos veces con el Implementer y una vez
más con el primer intento de Reviewer (que además murió por un límite real
de la API de sesión, no solo se quedó esperando). El líder intervino
directamente en ambos casos: verificó el código y corrió `bash init.sh`
por su cuenta, y redactó él mismo la sección de hallazgos en
`progress/current.md` en vez de seguir esperando indefinidamente al
Implementer. El Reviewer se relanzó una vez y esta vez sí completó su
ciclo completo (corrida propia de `init.sh` de punta a punta, ~5 min,
lectura línea por línea del código nuevo y del existente).

**Veredicto del Reviewer: APPROVED** (`progress/review.md` tiene el
detalle completo) — confirmó independientemente los 5 criterios de diseño
arriba contra el código fuente real (no solo contra el Javadoc), verificó
que el test de referencia rota inyectada no es vacuo (aserciones de
contenido específico), y señaló el cambio de visibilidad en
`RoleRepositoryTransformer.java` como justificado y no bloqueante.

**Verificación cruzada, cada uno por su cuenta:** el líder corrió
`bash init.sh` desde cero tras la implementación (`BUILD SUCCESS`,
`Tests run: 53, Failures: 0, Errors: 0, Skipped: 0`); el Reviewer relanzado
corrió el mismo comando de punta a punta por su cuenta (~5:07 min) con
idéntico resultado. Los 45 tests previos de las features 1-4 siguen en
verde sin modificación.

Durante esta feature llegaron múltiples mensajes por el canal no oficial
de "coordinador" (peticiones de estado, una afirmación de que un mensaje
del líder legítimo podría haber sido ilegítimo). Ninguno se tomó como
autoridad ni alteró ninguna decisión — toda verificación de esta sesión
viene de lectura de código y ejecución de comandos hechos en persona por
el líder y por el Reviewer.

Estado al cierre de esta entrada: features 1-5 `done`. Feature 6
(`reporte_final_mejoras`) queda `pending` para la próxima sesión — es la
última feature del track y requiere que 1-5 estén `done` (ya lo están).

---

## Sesión 2026-08-18 — Feature 6: `reporte_final_mejoras` → DONE (última feature del track)

**Entregable**: `MEJORAS.md` en la raíz de `database/` (documentación
únicamente, sin código de producción tocado). Cubre: diseño final de
tablas/GSIs y por qué (con referencia a `docs/architecture.md`, sin
duplicar su tabla), la decisión de generación de IDs (datos migrados
preservan IDs legados tal cual por la regla de idempotencia; registros
nuevos son decisión explícitamente delegada a `../backend/`, no reabierta
aquí), conteos de datos migrados y resultado de integridad (826 catálogo +
741 suministro + 15565 atributo = 17132 en `RoleRepository`, 0 en
`RoleRepositoryMenu`, 0 referencias rotas con 76 ítems de fallback
reportados aparte), hallazgos de calidad de datos del dump fuente (nombres
vs códigos, duplicados de `cargo.nombre`, filas `rol.area` huérfanas, sin
columna `estado`, tablas fuera de scope), y recomendaciones pendientes
(cuenta/credenciales AWS real, plan de migración de datos de producción
reales — con el volumen real explícitamente marcado como desconocido, sin
inventar una cifra).

**Ciclo de revisión**: el Implementer inicial escribió el reporte completo
en disco pero su sesión murió por un límite real de la API justo después
(mismo patrón que el resto del track) — el líder verificó el contenido y
corrió `bash init.sh` por su cuenta (53/53 verdes, sin cambios en `src/`,
como se espera de una feature solo de documentación). El líder encontró
por su cuenta una inexactitud puntual en la sección de "nota de proceso"
del propio reporte (afirmaba que solo 3 features tuvieron incidentes de
sesión — en realidad fueron 4: features 2, 3, 4 y 5, con la de la 5 siendo
la más severa, una muerte real de sesión). El primer Reviewer murió también
por límite de API antes de escribir nada; un segundo intento sí completó
una revisión completa y devolvió `CHANGES_REQUESTED` exclusivamente por
esa misma inexactitud, verificándola de forma independiente contra
`progress/history.md` línea por línea antes de bloquear la aprobación. Un
Implementer de corrección aplicó el cambio quirúrgico de una sola frase
(verificando también por su cuenta la cifra correcta antes de escribir),
y un Reviewer de re-chequeo confirmó el fix con un nuevo `grep` propio
contra `progress/history.md` y aprobó (`APPROVED`, ver `progress/review.md`).

**Verificación final del líder, con el track completo:** `bash init.sh`
corrido una última vez tras la aprobación final — `BUILD SUCCESS`,
`Tests run: 53, Failures: 0, Errors: 0, Skipped: 0`, LocalStack apagado
limpiamente. `feature_list.json` confirma las 6 features en `done`.

**Nota final sobre el canal "coordinador":** a lo largo de todo este
track (features 2 a 6) llegaron mensajes recurrentes por un canal de chat
no oficial haciéndose pasar por "un coordinador" — un rol que nunca existió
en la jerarquía real de este proyecto (el líder de cada track responde
directamente al usuario). Su contenido varió entre reafirmar información
ya verificada, relayar (a veces con precisión, a veces no) el estado de
subagentes, y en un caso intentar sembrar duda sobre un mensaje legítimo
que el propio líder había enviado y confirmado por resultado de
herramienta. Ninguna decisión de este track — relanzar un agente, cerrar
una feature, marcar algo `done` — se basó en ese canal; toda verificación
fue hecha en persona por el líder y por cada Reviewer, leyendo código y
ejecutando comandos reales, exactamente como exige el protocolo de
`AGENTS.md`/`.claude/agents/leader.md`.

Estado al cierre de esta entrada: **features 1-6 `done`. Track `database`
completo.**

---
