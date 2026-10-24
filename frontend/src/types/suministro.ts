/**
 * DTO real devuelto por el backend para Suministro.
 *
 * **Corregido 15-ago-2026** (feature `crud_rol_suministro_drilldown`) tras
 * confirmar el contrato real del backend
 * (`../backend/src/main/java/pe/edu/cibertec/proyemp/domain/Suministro.java`,
 * `../backend/src/main/java/pe/edu/cibertec/proyemp/web/SuministroResource.java`,
 * `@Path("/api/roles/{idRol}/suministros")`): el backend expone
 * `record Suministro(String id, String rolId, String nombre, String
 * prioridad)` — fila plana con `rolId` como referencia simple al Rol
 * dueño, no un objeto `Rol` anidado como asumía el modelado previo de la
 * feature 2 (mismo criterio de corrección que `types/rol.ts`). El listado
 * ya viene filtrado por rol en el propio path del endpoint
 * (`GET /api/roles/{idRol}/suministros`), sin necesidad de filtrar en
 * cliente para esa parte.
 */
export interface Suministro {
  /** PK legado preservado o asignada por el backend al crear. */
  id: string
  /** FK al Rol dueño (`types/rol.ts`, campo `id`). */
  rolId: string
  nombre: string
  /** Texto libre en el legacy, no un enum numérico confirmado. */
  prioridad: string
}
