/**
 * DTO real devuelto por el backend para Rol.
 *
 * **Corregido 15-ago-2026** (feature `crud_rol_suministro_drilldown`) tras
 * confirmar el contrato real del backend
 * (`../backend/src/main/java/pe/edu/cibertec/proyemp/domain/Rol.java`,
 * `../backend/src/main/java/pe/edu/cibertec/proyemp/web/RolResource.java`,
 * `@Path("/api/roles")`): el backend expone
 * `record Rol(String id, String ubicacionCodigo, String areaCodigo, String
 * cargoCodigo, String nombre, String descripcion, String tipo, String
 * reglaNegocio, String ambito, String propietarioRol, String
 * propietarioPersona, String situacion, String rolBase)` — una fila plana,
 * **no** con `Ubicacion`/`Area`/`Cargo` anidados como asumía el modelado
 * previo de la feature 2 (derivado directo de la entidad JPA legacy
 * `model.Rol` sin haber confirmado todavía el contrato JSON real). Solo se
 * envían los códigos de los catálogos relacionados
 * (`ubicacionCodigo`/`areaCodigo`/`cargoCodigo`, mismo `codigo` de
 * `types/area.ts`/`types/cargo.ts`/`types/ubicacion.ts`), sin resolver los
 * objetos completos — igual criterio que ya se confirmó para los 5
 * catálogos simples en la feature 4.
 */
export interface Rol {
  /** PK legado preservado o asignado por el backend al crear. */
  id: string
  /** FK al catálogo Ubicación (`types/ubicacion.ts`, campo `codigo`). */
  ubicacionCodigo: string
  /** FK al catálogo Área (`types/area.ts`, campo `codigo`). */
  areaCodigo: string
  /** FK al catálogo Cargo (`types/cargo.ts`, campo `codigo`). */
  cargoCodigo: string
  nombre: string
  descripcion: string
  tipo: string
  reglaNegocio: string
  ambito: string
  propietarioRol: string
  propietarioPersona: string
  situacion: string
  rolBase: string
}
