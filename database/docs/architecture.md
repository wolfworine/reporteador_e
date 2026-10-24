# Arquitectura — entel-role-repository-database-migration

## Arquitectura actual (MySQL 5.6)

Esquema relacional normalizado (`repositorio_entel/consulta.sql`), FKs
explícitas, PK compuesta de 5 columnas en `atributo`. Ver
`../backend/docs/migration-plan.md` §1 para el detalle de entidades y
relaciones — no se repite aquí para no desincronizar dos copias.

## Arquitectura objetivo: diseño DynamoDB (canónico — este documento es la fuente de verdad)

**Access patterns reales identificados** (código real en
`D:\Capacitacion\IA\Migracion_Reporte\JSF_RepositorioV3.9.2_extraido\war_contenido\WEB-INF\classes\pe\edu\cibertec\proyemp\repository\`):

1. Get directo de catálogos simples por código (Área/Cargo/Ubicación/Aplicación/Estado).
2. `CargoRepository.findCargo(idAplicacion, idArea)` → lista de Cargos/Roles.
3. `AtributoRepository.findAppArea2(idAplicacion, idArea, idCargo)` → lista de Atributos.
4. Por Atributo, obtener su Detalle + las 4 categorías de suministro
   (Roles/Grupos/Inbox/DefaultInbox).
5. Menú por `tipoUsuario` (con jerarquía vía `cod_submenu`).

**Diseño propuesto (tabla única + GSIs) — validado contra código real el
09-ago-2026, ver §"Validación contra código real" más abajo para el detalle
de cada corrección:**

| Tabla | PK | SK | GSI1 | Uso |
|---|---|---|---|---|
| `RoleRepository` | `ENTITY#<tipo>#<codigo>` | `METADATA` | — | Patrón 1 |
| `RoleRepository` | `ROL#<idRol>` | `SUMINISTRO#<idSuministro>` | — | Patrón 2b (`SuministroRepository.findSuministro(idRol)`, real pero no numerado en el plan original) |
| `RoleRepository` | `ATRIBUTO#<idAtributo>` | `DETALLE#<idDetalleAtributo>#<categoria>` | GSI1PK=`APLICACION#<idAplicacion>#AREA#<idArea>` / GSI1SK=`CARGO#<idCargo>#ROL#<idRol>` | Patrones 2, 3 y 4 |
| `RoleRepositoryMenu` | `MENU#<tipo>` (`tipo` = `S`\|`I`) | `ORDEN#<codigo>` | — | Patrón 5 |

Notas de diseño derivadas de la validación:

- **GSI1 es compartido por los patrones 2, 3 y 4** porque los tres filtran
  sobre el mismo camino real `Atributo → Aplicacion / Rol(→Area,→Cargo) /
  Estado` (ver detalle abajo). Consultar solo con `GSI1PK` (sin condición de
  `GSI1SK`) resuelve los patrones 2 y 3 (sin filtro de cargo); añadir
  `begins_with(GSI1SK, "CARGO#<id>")` resuelve el patrón 4 (con cargo). El
  atributo `estado` se guarda en el ítem y se filtra con
  `FilterExpression estado = ACTIVO` (equivalente al `e.id = 1` del código
  legado) — no forma parte de la clave porque el volumen es bajo y no
  conviene fragmentar más el índice.
  Cada ítem `ATRIBUTO#` debe denormalizar `rolTipo` (`Rol.c_tipo`) para
  poder replicar el `order by r.c_tipo, d.d_parametro desc` de las
  consultas JPQL originales sin una lectura adicional.
- El diseño original (sesión previa) proponía una fila separada
  `ROL#<idRol>` / `SUMINISTRO#<idSuministro>` con GSI1=`CARGO#AREA`/`ROL`
  para el "Patrón 2". Es incorrecto: `CargoRepository.findCargo(aplicacion,
  area)` filtra por **aplicación + área**, nunca por cargo — el nombre
  "Patrón 2" en el plan original citaba mal la firma del método. Ese slot
  de PK/SK sí es necesario, pero para un patrón real distinto:
  `SuministroRepository.findSuministro(idRol)` (listar los `Suministro` de
  un `Rol`) — se conserva la fila pero se renombra "Patrón 2b" y se le
  quita el GSI1 que no aplicaba.
- **"Patrón 5" no consulta por `tipoUsuario`.** `MenuRepository.findTipo(tipo)`
  filtra por `Menu.tipo` (`"S"` = submenú, `"I"` = ítem) — ver
  `MenuController.init()` (`findTipo("S")`, `findTipo("I")`) — y el filtro
  por `tipoUsuario` ocurre **en memoria**, después de traer todos los menús
  de ese `tipo`, igual que la jerarquía por `cod_submenu`. La PK original
  (`TIPOUSUARIO#<tipo>`) apuntaba a la columna equivocada; se corrige a
  `MENU#<tipo>` (S/I), dejando `tipoUsuario` y `cod_submenu` como atributos
  del ítem para filtrar/enlazar en la capa de aplicación — igual que hace
  hoy el código legado. El volumen (referencia: 1 fila en el dump de
  prueba) no justifica una GSI adicional por `tipoUsuario`; si en
  producción el volumen crece, es trivial añadir una GSI luego.
- **"Las 4 categorías de suministro" no son categorías de la entidad
  `Suministro`.** Vienen de `DetalleRepository.ListarSuministroAtributoxParametro(idRol,
  parametro, idAplicacion)`, que filtra `Detalle.d_parametro` (valores
  literales `"ROLES"`, `"GRUPOS"`, `"INBOX"`, `"DEFAULTINBOX"`) sobre
  `Atributo` filtrado por `rol` + `aplicacion` — no hay join a `Suministro`
  en esa consulta. El nombre viene del DTO de UI `DetalleRol` (campos
  `suministroRoles`/`suministroGrupos`/`suministroInbox`/`suministroDefaultinbox`),
  no del modelo de datos. El diseño de la fila `ATRIBUTO#/DETALLE#<id>#<categoria>`
  ya modelaba esto correctamente (la SK usa `<categoria>` = el valor de
  `d_parametro`); solo se corrige la documentación para no confundir
  "categoría" con la entidad `Suministro` real (que es el Patrón 2b).

**Generación de IDs** (reemplaza la tabla `sequence` de EclipseLink) — decisión tomada:

- **Datos migrados del dump legado (alcance de este track):** se
  **preservan los IDs numéricos originales tal cual** (`Rol.id`,
  `Suministro.id`, `Atributo.id`, etc.) como parte literal de la PK/SK
  (`ROL#<id>`, `ATRIBUTO#<id>`, ...). Motivo: la regla dura de
  `docs/conventions.md` exige que la migración sea idempotente (misma
  PK/SK en cada corrida); un generador de IDs no determinístico (UUID
  aleatorio, incluido UUID v7) generaría una clave distinta en cada
  corrida del `parse-dump`/`migrate` y **rompería la idempotencia** salvo
  que se persista una tabla de mapeo id-legado→UUID, lo cual añade
  complejidad sin beneficio real aquí. Reusar el ID legado también
  preserva gratis la integridad referencial entre entidades migradas
  (`Atributo.id_rol` sigue apuntando al mismo `ROL#<id>` migrado) sin
  necesitar una fase de reescritura de FKs.
- **Registros nuevos creados por la futura aplicación sobre DynamoDB**
  (fuera del alcance de este track, decisión documentada aquí para que
  `../backend/` no la reabra): usar un ítem contador dedicado
  (`PK=COUNTER#<entidad>`) actualizado con `UpdateItem ADD contador :incr`
  (atómico, sin condiciones de carrera) **si** se quiere continuidad con
  el formato numérico existente. Se revisó el código real
  (`ReporteManagedBean.reporte()`) y los reportes PDF/Excel sí muestran el
  ID interno de `Area`/`Cargo` (`area.getId()`, `cargo.getId()`) al
  usuario final, pero es un dato generado por la propia aplicación (no una
  referencia externa persistida fuera del sistema) — no se encontró
  ninguna integración externa ni documento ya emitido que cite estos IDs
  fuera del propio repositorio. Por lo tanto **no es obligatorio** preservar
  el formato numérico para registros nuevos; se recomienda contador
  atómico solo si el equipo de negocio pide continuidad visual en reportes,
  y UUID (v7 o v4) en caso contrario. Esta decisión concreta para nuevas
  escrituras queda a cargo de `../backend/`, que es quien implementa el
  alta de entidades nuevas.

## Validación contra código real (09-ago-2026)

Se releyó el código fuente real (no solo la descripción textual del plan)
antes de aceptar el diseño de la sesión anterior:
- `repository/CargoRepository.java` (`findCargo`, `findCargoxArea`)
- `repository/AtributoRepository.java` (`findAppArea`, `findAppArea2`,
  `findParametro2`, `findParametro3`)
- `repository/RolRepository.java` (`findRol`, `findRolxArea`)
- `repository/SuministroRepository.java` (`findSuministro`)
- `repository/DetalleRepository.java` (`ListarSuministroAtributoxParametro`)
- `repository/MenuRepository.java` (`findTipo`) y
  `managedbean/MenuController.java` (uso real de `findTipo`/`tipoUsuario`)
- `model/Atributo.java`, `model/Rol.java`, `model/Suministro.java`,
  `model/Detalle.java`, `model/DetalleRol.java`, `model/Menu.java`
- `consulta.sql` (DDL real: FKs de `atributo`, `rol`, `suministro`)

Resultado: 4 discrepancias encontradas y corregidas (documentadas en las
notas de diseño arriba) — el GSI de los patrones 2/3/4 no soportaba
consultar por aplicación+área sin cargo, el "Patrón 2" apuntaba a la
consulta equivocada, el "Patrón 5" indexaba por la columna equivocada
(`tipoUsuario` en vez de `tipo`), y la generación de IDs con UUID
aleatorio para datos migrados violaba la regla de idempotencia. Ninguna de
las 4 requería una decisión humana pendiente — son correcciones de
ingeniería derivadas de leer el código real, dentro del alcance normal de
la feature `design_dynamodb_schema`.

## Reglas duras específicas de este proyecto

- Este documento (`docs/architecture.md` de `database/`) es la única
  fuente de verdad del esquema DynamoDB — `../backend/docs/architecture.md`
  debe referenciarlo, no duplicar la tabla de diseño.
- Toda escritura de la herramienta de migración es **idempotente**: correr
  el import dos veces no debe duplicar ítems (usar `PutItem` con la PK/SK
  determinística del diseño de arriba, nunca `PutItem` con clave generada
  al azar en cada corrida).
- La herramienta de migración nunca se conecta a una cuenta AWS real por
  defecto — el target por defecto es LocalStack; el endpoint de AWS real
  requiere una bandera explícita (`--target=aws`) y no se ejercita en
  tests automatizados.
- No se migra la tabla `sequence` tal cual — se reemplaza por el mecanismo
  de generación de IDs decidido arriba, documentado por entidad.
