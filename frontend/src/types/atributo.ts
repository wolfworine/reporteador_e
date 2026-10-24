/**
 * DTO real devuelto por el backend para Atributo (fila combinada
 * Atributo+Detalle).
 *
 * **Corregido 15-ago-2026** (feature `crud_rol_suministro_drilldown`) tras
 * confirmar el contrato real del backend
 * (`../backend/src/main/java/pe/edu/cibertec/proyemp/domain/Atributo.java`,
 * `../backend/src/main/java/pe/edu/cibertec/proyemp/web/AtributoResource.java`,
 * `@Path("/api/atributos")`): el backend expone una fila plana y
 * denormalizada —
 * `record Atributo(String id, String idDetalleAtributo, CategoriaSuministro
 * categoria, String aplicacionCodigo, String areaCodigo, String
 * cargoCodigo, String cargoNombre, String rolId, String rolTipo, String
 * rolNombre, String suministroId, String atributoCodigo, String
 * atributoNombre, EstadoAtributo estado)` — **no** con `Aplicacion`/`Rol`/
 * `Suministro`/`Detalle`/`Estado` anidados como asumía el modelado previo
 * de la feature 2 (mismo criterio de corrección que `types/rol.ts`/
 * `types/suministro.ts`). El backend no expone (todavía) un filtro
 * server-side por `suministroId` — ver `modules/atributo/AtributoPorSuministro.tsx`
 * para la decisión de filtrar por `suministroId` en cliente, documentada
 * también en `progress/current.md`.
 */

/** Las 4 sub-vistas de Detalle del legacy — paridad con `CategoriaSuministro` del backend. */
export type CategoriaSuministro = 'ROLES' | 'GRUPOS' | 'INBOX' | 'DEFAULTINBOX'

/** Estado activo/inactivo — paridad con `EstadoAtributo` del backend. */
export type EstadoAtributo = 'ACTIVO' | 'INACTIVO'

export interface Atributo {
  id: string
  idDetalleAtributo: string
  categoria: CategoriaSuministro
  /** FK al catálogo Aplicación (`types/aplicacion.ts`, campo `codigo`). */
  aplicacionCodigo: string
  /** FK al catálogo Área (`types/area.ts`, campo `codigo`). */
  areaCodigo: string
  /** FK al catálogo Cargo (`types/cargo.ts`, campo `codigo`). */
  cargoCodigo: string
  /** Nombre de Cargo denormalizado (evita una lectura extra). */
  cargoNombre: string
  /** FK al Rol (`types/rol.ts`, campo `id`). */
  rolId: string
  /** `tipo` de Rol denormalizado. */
  rolTipo: string
  /** Nombre de Rol denormalizado. */
  rolNombre: string
  /** FK al Suministro (`types/suministro.ts`, campo `id`) — sin filtro server-side, ver nota arriba. */
  suministroId: string
  atributoCodigo: string
  atributoNombre: string
  estado: EstadoAtributo
}
