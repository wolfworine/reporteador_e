/**
 * Espejo TypeScript de la entidad JPA legacy:
 * `pe.edu.cibertec.proyemp.model.Detalle`
 * (.../model/Detalle.java)
 *
 * Tabla legacy: `detalle` (columnas ID_DETALLE, ATRIBUTO_CODIGO,
 * ATRIBUTO_NOMBRE, PARAMETRO).
 *
 * Decisión de modelado: se omite la colección `atributo` (`@OneToMany`),
 * mismo criterio que en `types/area.ts`.
 *
 * **Corregido tras coordinación cross-track (2026-08-19):** el backend
 * (`domain/Atributo`, fila combinada Atributo+Detalle, ver
 * `../backend/docs/architecture.md`) no expone un campo equivalente a
 * `PARAMETRO`. Confirmado con `database/docs/architecture.md`:
 * `d_parametro` en el legacy era el discriminador de categoría
 * (`"ROLES"`/`"GRUPOS"`/`"INBOX"`/`"DEFAULTINBOX"`), no un valor de
 * negocio propio del detalle — el nuevo diseño ya representa esa misma
 * información estructuralmente (a través de en cuál de las 4 listas
 * paralelas de `DetalleRol` aparece cada `Detalle`, y del enum
 * `CategoriaSuministro` del backend), así que se elimina aquí en vez de
 * fabricar un valor sin respaldo real. Tampoco se usaba en
 * `ReporteTabla.tsx` (solo `id`/`atributoCodigo`/`atributoNombre` se
 * renderizan).
 */
export interface Detalle {
  /**
   * PK. Columna `ID_DETALLE` (Java: `a_id`). Corregido a `string`
   * (coordinación cross-track 2026-08-19): `domain/Atributo.id` del
   * backend es `String` (id opaco, no autonumérico) — el `number`
   * original era una extrapolación propia del frontend, nunca
   * confirmada. Solo se usa como `key` de React en `ReporteTabla.tsx`,
   * ningún cálculo numérico depende de este campo.
   */
  id: string
  /** Columna `ATRIBUTO_CODIGO`, `length=200`. Java: `b_atributo_codigo`. */
  atributoCodigo: string
  /** Columna `ATRIBUTO_NOMBRE`, `length=400`. Java: `c_atributo_nombre`. */
  atributoNombre: string
}

/**
 * Las 4 categorías de suministro que agrupan instancias de `Detalle` dentro
 * de un `DetalleRol` (ver `types/reporte.ts`). No es un campo propio de la
 * tabla `detalle` — la entidad `Detalle` no tiene una columna de
 * categoría — sino la forma en que
 * `pe.edu.cibertec.proyemp.model.DetalleRol` (.../model/DetalleRol.java)
 * separa los suministros en 4 listas paralelas
 * (`suministroRoles`, `suministroGrupos`, `suministroInbox`,
 * `suministroDefaultinbox`), reflejando las 4 pestañas/sub-tablas de las
 * vistas legacy `grupos.xhtml`, `inbox.xhtml`, `defaultinbox.xhtml` (bajo
 * `paginas/<modulo>/`) y la vista "Roles" por defecto (ver
 * `docs/architecture.md` — jerarquía Cargo→Rol→4 categorías del reporte).
 */
export type DetalleCategoria = 'ROLES' | 'GRUPOS' | 'INBOX' | 'DEFAULT_INBOX'
