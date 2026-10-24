# MEJORAS.md — Reporte final del track `database`

> Proyecto: `entel-role-repository-database-migration` (WiseVision Role
> Repository Basic, cliente Entel). Este documento resume el trabajo del
> track completo (features 1-6 de `feature_list.json`, todas `done` antes de
> escribir este reporte) para quien retome el proyecto después — incluido
> `../backend/`, que consume el esquema DynamoDB diseñado aquí.

---

## 1. Diseño final de tablas y GSIs

Fuente de verdad completa: `docs/architecture.md` (no se duplica aquí la
tabla de diseño, solo se resume el resultado y el porqué).

Dos tablas DynamoDB, patrón de tabla única (single-table design):

- **`RoleRepository`**
  - Catálogos simples (Área/Cargo/Ubicación/Aplicación) como
    `ENTITY#<tipo>#<codigo>` / `METADATA` — acceso directo por código
    (patrón 1).
  - Relación Rol→Suministro como `ROL#<idRol>` / `SUMINISTRO#<idSuministro>`
    (patrón 2b — `SuministroRepository.findSuministro(idRol)`).
  - Atributos y su detalle como `ATRIBUTO#<idAtributo>` /
    `DETALLE#<idDetalleAtributo>#<categoria>`, con
    `GSI1PK=APLICACION#<idAplicacion>#AREA#<idArea>` /
    `GSI1SK=CARGO#<idCargo>#ROL#<idRol>`, cubriendo los patrones 2, 3 y 4
    (consultar solo por `GSI1PK` resuelve 2/3; añadir
    `begins_with(GSI1SK, "CARGO#<id>")` resuelve 4).
- **`RoleRepositoryMenu`**
  - `MENU#<tipo>` (`tipo` = `S`|`I`) / `ORDEN#<codigo>`, para el patrón 5
    (`MenuRepository.findTipo`), con `tipoUsuario`/`cod_submenu` como
    atributos del ítem para filtrar/enlazar en memoria, igual que hace hoy
    el código legado.

**Por qué este diseño y no el propuesto originalmente:** el diseño que
llegó a la feature 2 tenía 4 discrepancias reales frente al código fuente
legado (releído directamente, no solo la descripción textual del plan),
corregidas y documentadas en `docs/architecture.md` §"Validación contra
código real" (09-ago-2026):

1. El GSI de los patrones 2/3/4 no soportaba consultar por
   aplicación+área sin cargo → se unificó en el GSI1 compartido descrito
   arriba.
2. El "Patrón 2" original citaba mal `CargoRepository.findCargo`
   (filtra por aplicación+área, no por cargo); la fila `ROL#/SUMINISTRO#`
   se reasignó al patrón real que sí necesitaba esa clave
   (`SuministroRepository.findSuministro`, renombrado "Patrón 2b").
3. El "Patrón 5" (menú) indexaba por `tipoUsuario`, pero el código real
   consulta por `Menu.tipo` (S/I) y filtra `tipoUsuario` en memoria → la PK
   se corrigió de `TIPOUSUARIO#<tipo>` a `MENU#<tipo>`.
4. Generación de IDs con UUID aleatorio para datos migrados violaba la
   regla de idempotencia del proyecto (ver §2).

Ninguna de las 4 requirió una decisión humana — fueron correcciones de
ingeniería normales dentro del scope de la feature 2, verificadas contra
el código real en
`.../pe/edu/cibertec/proyemp/repository/{CargoRepository,AtributoRepository,RolRepository,SuministroRepository,DetalleRepository,MenuRepository}.java`,
los modelos JPA y `consulta.sql`.

**Sobre `RoleRepositoryMenu` con 0 ítems:** el dump real usado en todo el
track (`reposrito_roles_jsf 20150814 1214.sql`) no incluye las tablas
`estado` ni `menu` — no existen en esta captura concreta del esquema, no
es que la migración las omita por error. `verify` (feature 5) confirma
0 ítems en `RoleRepositoryMenu` como resultado correcto para este dump, no
como un defecto del diseño o del migrador.

---

## 2. Decisión de generación de IDs

Detalle completo y razonamiento en `docs/architecture.md` §"Generación de
IDs" (esta sección solo resume):

- **Datos migrados del dump legado (alcance de este track):** se
  preservan los IDs numéricos originales tal cual (`Rol.id`,
  `Suministro.id`, `Atributo.id`, etc.) como parte literal de la PK/SK
  (`ROL#<id>`, `ATRIBUTO#<id>`, ...). No se usó UUID ni contador atómico
  para estos datos. Motivo: la regla dura de idempotencia del proyecto
  exige la misma PK/SK en cada corrida de `migrate`; un generador no
  determinístico (UUID v4 o v7) produciría una clave distinta cada vez y
  rompería esa garantía, salvo que se mantuviera una tabla de mapeo
  id-legado→UUID — complejidad sin beneficio real para datos que ya tienen
  un identificador estable. Reusar el ID legado también preserva gratis la
  integridad referencial entre entidades migradas sin una fase de
  reescritura de FKs.
- **Registros nuevos creados por la futura aplicación sobre DynamoDB**
  (fuera del alcance de este track): la decisión concreta —contador
  atómico (`PK=COUNTER#<entidad>`, `UpdateItem ADD contador :incr`) vs
  UUID— queda documentada en `docs/architecture.md` como responsabilidad
  de `../backend/`, con una recomendación explícita: contador atómico solo
  si el negocio pide continuidad visual del formato numérico en reportes
  (se confirmó, revisando `ReporteManagedBean.reporte()`, que los reportes
  PDF/Excel sí muestran el ID interno al usuario, pero no se encontró
  ninguna integración externa que dependa de ese formato); UUID en caso
  contrario. Este track no reabre esa decisión ni la implementa.

---

## 3. Conteos de datos migrados y validación de integridad

### 3.1 Conteo real del dump fuente (feature 3)

El conteo de referencia original de `docs/migration-plan.md` §1 ("22
atributo, 3 rol, 1 cada uno del resto") resultó ser el número de
sentencias `INSERT` multi-row, no de filas. Conteo real confirmado por
tres métodos independientes (implementer, líder y reviewer, cada uno con
su propio script/comando):

| Tabla | Filas |
|---|---|
| area | 69 |
| cargo | 695 |
| ubicacion | 61 |
| aplicacion | 1 |
| rol | 741 |
| suministro | 741 |
| atributo | 15565 |
| detalle | 684 |
| detalleinbox | 0 |
| usuario | 4 |
| login | 4 |
| sequence | 1 |

Las tablas `estado` y `menu` no existen en esta captura del dump.

### 3.2 Conteo migrado a DynamoDB (feature 4)

- Catálogo (`ENTITY#...`): **826** ítems = 69 área + 695 cargo + 61
  ubicación + 1 aplicación.
- `SUMINISTRO#` (relación Rol→Suministro): **741** ítems.
- `ATRIBUTO#`: **15565** ítems.
- **Total `RoleRepository`: 17132** ítems.
- **Total `RoleRepositoryMenu`: 0** ítems (dump sin tablas `menu`/`estado`,
  ver §1).

Estos números fueron calculados de antemano por el líder a partir de la
inspección del dump real y confirmados exactamente por el resultado del
`migrate` real contra LocalStack.

**Prueba de idempotencia:** `migrate` se corrió dos veces seguidas contra
el mismo dump real. El conteo de ítems no cambió entre corridas, y un
spot-check `GetItem` sobre `ATRIBUTO#41868` fue byte-idéntico antes y
después de la segunda corrida (test `MigrateCommandIntegrationTest`,
`@Order(1)`/`@Order(2)`).

### 3.3 Resultado de `verify` (feature 5)

`verify` sobre los datos migrados del dump real reporta **0 referencias
rotas**. Esto incluye:

- Toda combinación `(idRol, idSuministro)` referenciada por un `ATRIBUTO#`
  existe como ítem `SUMINISTRO#` migrado.
- La relación inversa (todo `SUMINISTRO#` referencia un `Rol` existente)
  es válida por construcción del diseño (el PK de `SUMINISTRO#` ya es
  `ROL#<idRol>`) — documentada como no-op en `VerifyCommand`, no como un
  chequeo vacuo que siempre pasa.
- `Estado` se trata como literal constante (`"ACTIVO"`), no como
  referencia a un catálogo — el dump no tiene tabla `estado`.

**76 de 15565 ítems `ATRIBUTO#` con marcador de fallback determinístico**
(`NOMBRE#`/`ROLDESCONOCIDO#`) se excluyen explícitamente de "referencia
rota" pero se reportan aparte (`AREA=76` en la salida de `verify`), nunca
de forma invisible. Esto **no es un defecto de la migración**: es
consecuencia directa de un gap de calidad de datos preexistente en el
dump fuente (ver §4) — 14 filas de `rol.area` sin fila de catálogo
correspondiente. Si `verify` los contara como "rotos", el criterio de
aceptación "0 referencias rotas sobre el dump real" sería matemáticamente
imposible de cumplir sin inventar datos de catálogo que no existen en el
dump.

`verify` también fue probado con una referencia rota inyectada
deliberadamente en datos de prueba (no en el dump real), confirmando que
detecta el caso y termina con exit code distinto de 0 y mensaje claro de
qué ítem y qué referencia falta (`VerifyCommandIntegrationTest`).

---

## 4. Hallazgos de calidad de datos en el dump fuente

Esta sección se documenta aparte porque sorprendió a cada sesión que tocó
el dump — vale la pena que quien mire producción después lo sepa de
antemano:

- **`atributo.aplicacion`, `rol.area` y `rol.cargo` son nombres de texto,
  no códigos.** El esquema real del dump no trae FKs numéricas directas;
  hay que resolverlas contra los catálogos por nombre (descubierto en la
  feature 4, releyendo el dump real, no `consulta.sql`).
- **`cargo.nombre` tiene 2 pares de duplicados reales** de 695 filas (ids
  133/695 y 206/213). Se resolvieron de forma determinística tomando el id
  más bajo de cada par — confirmado independientemente con un script
  Node.js ad-hoc que reimplementó la lógica de resolución desde cero sobre
  el dump real, sin copiar cifras del código de producción.
- **14 filas de `rol.area` no tienen catálogo `area` correspondiente**
  (`LEGAL`=11 filas, `PLANEAMIENTO`=3 filas) — son el origen directo de los
  76 ítems `ATRIBUTO#` con marcador de fallback en `verify` (§3.3).
- **`atributo` no tiene columna `estado`** en el dump real (a diferencia
  de lo que sugeriría `consulta.sql`) — se defaulteó a `"ACTIVO"` en la
  transformación, sin inventar un catálogo de estados que no existe en los
  datos.
- **`detalle`, `usuario`, `login` y `sequence` quedaron explícitamente
  fuera de scope** de la migración de datos: `sequence` es reemplazada por
  la decisión de IDs de §2; `usuario`/`login` no forman parte de ningún
  access pattern del repositorio de roles (pertenecen a autenticación, no
  a este dominio); `detalle` se consume indirectamente vía la SK
  `DETALLE#<id>#<categoria>` de los ítems `ATRIBUTO#`, no como ítems
  propios.
- **`estado` y `menu` están ausentes de esta captura del dump por
  completo** (no hay tablas, no solo filas vacías) — de ahí que
  `RoleRepositoryMenu` termine con 0 ítems (§1) y que `verify` trate
  `estado` como literal en vez de referencia (§3.3).
- **El esquema del dump real difiere del de `consulta.sql`** para varias
  tablas (ej. `atributo` en el dump trae columnas distintas a las que
  describe `consulta.sql`). El parser (feature 3) se diseñó
  autodescriptivo — lee la lista de columnas de cada tabla directamente de
  su propia sentencia `INSERT` — precisamente para no depender de
  reconciliar ambos esquemas.

---

## 5. Recomendaciones pendientes

1. **Cuenta y credenciales AWS reales para producción.** Este track, por
   regla dura del proyecto, nunca se conectó a una cuenta AWS real ni
   ejercitó `--target=aws` en ningún test automatizado — todo el trabajo
   (features 1-5) se validó exclusivamente contra LocalStack vía
   `docker-compose.yml`. Antes de un despliegue real hace falta: una
   cuenta AWS de destino, credenciales gestionadas (no hardcodeadas),
   y una corrida real (no simulada) de `design-validate` contra DynamoDB
   real para confirmar que la creación de tablas/GSI funciona igual fuera
   de LocalStack (LocalStack emula la API pero no es una garantía
   absoluta de paridad 1:1 en todos los detalles operativos, como límites
   de capacidad o comportamiento exacto de throttling).
2. **Plan de migración de datos de producción reales**, más allá del dump
   de prueba usado en este track. Este track validó todo el pipeline
   (`parse-dump` → `migrate` → `verify`) contra **una captura específica**
   del dump (`reposrito_roles_jsf 20150814 1214.sql`, 18566 filas en 12
   tablas). Un cutover de producción necesitaría, como mínimo:
   - Correr el mismo pipeline (`parse-dump` → `migrate` → `verify`) contra
     el export real de producción, sin asumir que los conteos serán los
     mismos que aquí.
   - Decidir explícitamente qué hacer con las tablas `estado`, `menu`,
     `usuario` y `login` **si el sistema de producción real tiene datos en
     ellas** — este dump de prueba no las tenía (o estaban vacías/ausentes),
     así que el pipeline actual nunca ejercitó una migración con datos
     reales en esas tablas. En particular, si producción sí tiene datos de
     `menu`, `RoleRepositoryMenu` dejaría de estar vacía y valdría la pena
     revisar si el volumen sigue sin justificar una GSI por `tipoUsuario`
     (la nota de diseño en `docs/architecture.md` ya anticipa esta
     posibilidad).
   - Revalidar si los hallazgos de calidad de datos de §4 (duplicados de
     `cargo.nombre`, filas `rol.area` sin catálogo) se repiten en el dump
     de producción real o son específicos de esta captura de prueba — no
     hay forma de saberlo sin correr el pipeline contra los datos reales.
   - El volumen de datos de producción real es **desconocido** en este
     momento (no se debe asumir que será similar a las 18566 filas de este
     dump de prueba) — a determinar antes de planificar tiempos de
     migración o costos de capacidad de DynamoDB.

---

## 6. Nota de proceso

Cuatro de las seis features de este track (2, 3, 4 y 5) requirieron más de
una ejecución de Implementer y/o Reviewer, por límites de sesión de la API
o por agentes que terminaron su turno a mitad de una espera de un comando
en segundo plano sin escribir sus hallazgos — no por defectos en el código
entregado. Solo la feature 1 no tuvo ningún incidente documentado. El
líder retomó cada caso verificando el estado real en disco (lectura de
código, timestamps, corridas propias de `bash init.sh`) antes de continuar
o cerrar cualquier feature. Detalle completo de cada incidente en
`progress/history.md`, sesión por sesión.
