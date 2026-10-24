# Plan de migración — entel-role-repository-database-migration

> Track dedicado a diseño de esquema DynamoDB + migración de datos reales.
> Antes vivía parcialmente dentro de `../backend/docs/`; se separó a pedido
> del usuario (09-ago-2026) para que un agente propio se enfoque solo en
> esto. `../backend/` consume las tablas que este track diseña y llena.

## 1. Qué hace el proyecto hoy

MySQL 5.6, esquema relacional (`consulta.sql` en `repositorio_entel/`).
Tablas confirmadas por DDL real: `area`, `cargo`, `ubicacion`, `aplicacion`,
`rol`, `suministro`, `atributo`, `detalle`, `detalleinbox`, `usuario`,
`login`, `estado`, `menu`, y **`sequence`** (tabla usada por EclipseLink
para generación de IDs tipo `TABLE` — no hay auto-increment nativo en
varias entidades, hay que reproducir esa semántica en DynamoDB).

**Datos reales de referencia** (dump `reposrito_roles_jsf 20150814
1214.sql`, 1.7MB). **Corrección (feature 3, `parse_legacy_mysql_dump`,
09-ago-2026):** el conteo original de este párrafo ("22 sentencias INSERT
sobre atributo, 3 sobre rol, 1 cada una sobre el resto") contaba
**sentencias `INSERT`**, no filas — la mayoría de tablas de este dump usan
`INSERT` multi-row (una sola sentencia con muchas tuplas separadas por
coma). El conteo real de filas, verificado de forma independiente tres
veces (parser Java de la feature 3, script `awk`, y script Node.js
independiente — los tres coinciden), es:

| tabla | filas reales | tabla | filas reales |
|---|---|---|---|
| `aplicacion` | 1 | `login` | 4 |
| `area` | 69 | `rol` | 741 |
| `atributo` | 15565 | `sequence` | 1 |
| `cargo` | 695 | `suministro` | 741 |
| `detalle` | 684 | `ubicacion` | 61 |
| `detalleinbox` | 0 (tabla creada, sin datos) | `usuario` | 4 |

Total: 18566 filas en 12 tablas. Este dump **no incluye** las tablas
`estado` ni `menu` (no tienen sentencia `CREATE TABLE` en este archivo
concreto) — no es un error del parser, es el estado real de esta captura;
si algún otro dump las trae, el parser (autodescriptivo, ver
`docs/conventions.md` y el código de `com.entel.migration.dump`) las
soporta igual. Este volumen tampoco es, por sí solo, el que justificaría
Kafka/DynamoDB, pero la decisión de stack ya está tomada por el usuario
independientemente del volumen.

## 2. Stack actual vs. objetivo

| | Actual | Objetivo |
|---|---|---|
| Motor | MySQL 5.6, esquema normalizado con FKs y una PK compuesta de 5 columnas en `atributo` | **DynamoDB**, diseño single-table por access patterns |
| Generación de IDs | Tabla `sequence` (EclipseLink `TABLE` generator) | Contador atómico en DynamoDB (`UpdateItem` con `ADD`) o UUID, según el caso — decidir por entidad en la feature de diseño |
| Entorno local | MySQL Server real | **LocalStack** (DynamoDB) |
| Migración de datos | N/A (no existía) | Herramienta Java 21 que lee el dump/BD MySQL real y carga DynamoDB (LocalStack en dev, AWS real en prod) |

## 3. Bugs y deuda técnica encontrados

1. La PK compuesta de 5 columnas en `atributo` (`id, id_aplicacion, id_rol,
   id_suministro, id_detalleatributo, id_estado`) no tiene equivalente
   directo en DynamoDB — exige un diseño de clave explícito (partition/sort
   key) que preserve todos los patrones de búsqueda reales, no una
   traducción mecánica.
2. La tabla `sequence` es una muleta de EclipseLink; en DynamoDB, si se
   necesita un ID numérico secuencial (compatibilidad con datos ya
   emitidos, ej. reportes que citan códigos existentes), debe modelarse
   explícitamente como contador atómico — si no hace falta, usar UUID y
   documentar el cambio de formato de ID.

## 4. Decisiones de alcance ya tomadas con el usuario (09-ago-2026)

- **Motor destino:** DynamoDB, no PostgreSQL. ✅ Decidido.
- **Entorno local:** LocalStack. ✅ Decidido.
- **R2DBC evaluado y descartado**: R2DBC es para bases relacionales; no
  aplica a DynamoDB. El acceso reactivo correcto es `DynamoDbAsyncClient`
  (AWS SDK v2) envuelto en `Uni` — ya usado por `../backend/`. No hay
  ninguna feature de R2DBC en este track ni en backend.
- **Alcance de este track:** diseño de tablas + migración de los datos
  reales existentes (dumps `.sql` de `repositorio_entel/`). No incluye
  operar la infraestructura de producción (cuenta AWS real, IAM, etc.) —
  eso queda como recomendación pendiente en el reporte final.

## 5. Plan por fases

1. **Fase 0 — Bootstrap.** Proyecto Java 21/Maven para la herramienta de
   migración, LocalStack con DynamoDB levantable.
2. **Fase 1 — Diseño de esquema.** Validar el diseño single-table contra
   cada access pattern real del código (`../backend/docs/migration-plan.md`
   documenta los mismos patrones; este track es dueño de la versión
   canónica del diseño en `docs/architecture.md`).
3. **Fase 2 — Parseo de datos legacy.** Leer los dumps `.sql` reales y
   extraer las filas como estructuras Java tipadas.
4. **Fase 3 — Carga a DynamoDB.** Transformar y escribir en LocalStack,
   idempotente (se puede correr más de una vez sin duplicar).
5. **Fase 4 — Validación.** Conteos, integridad referencial, spot-checks.
6. **Fase 5 — Reporte final.**

Ver `feature_list.json`. `../backend/` depende del diseño de esquema de
este track (feature 2) antes de poder implementar sus features de dominio
sobre DynamoDB — coordinar vía `progress/current.md` de ambos tracks si
hay bloqueos cruzados.
