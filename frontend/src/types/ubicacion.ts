/**
 * DTO real devuelto por el backend para el catálogo Ubicación.
 *
 * **Corregido 09-ago-2026** tras confirmar el contrato real del backend
 * (`../backend/src/main/java/pe/edu/cibertec/proyemp/web/UbicacionResource.java`
 * y `.../domain/Ubicacion.java`; `../backend/feature_list.json` id=3
 * `crud_catalogos_simples`, `done`): el backend expone
 * `record Ubicacion(String codigo, String nombre)`. A diferencia del
 * legacy (que nombraba el campo descriptivo `ubicacion`, columna
 * `DESCRIPCION`), el backend lo **renombra a `nombre`** para uniformidad
 * entre los 5 catálogos simples — ver comentario en
 * `../backend/.../domain/Ubicacion.java`. Ver `types/area.ts` para la
 * explicación completa de la decisión sobre `codigo`.
 *
 * Esto **reemplaza** el modelado previo de la feature 2
 * (`codigo: number` + `id: string` + `ubicacion: string`), derivado directo
 * de la entidad JPA legacy `pe.edu.cibertec.proyemp.model.Ubicacion` sin
 * haber confirmado todavía el contrato JSON real ni el renombre de campo
 * que hizo el backend.
 */
export interface Ubicacion {
  /** PK autogenerada, representada como string (ver nota de arriba). */
  codigo: string
  /** Columna `DESCRIPCION` legacy, renombrada a `nombre` por el backend. */
  nombre: string
}
