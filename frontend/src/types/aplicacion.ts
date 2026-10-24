/**
 * DTO real devuelto por el backend para el catálogo Aplicación.
 *
 * **Corregido 09-ago-2026** tras confirmar el contrato real del backend
 * (`../backend/src/main/java/pe/edu/cibertec/proyemp/web/AplicacionResource.java`
 * y `.../domain/Aplicacion.java`; `../backend/feature_list.json` id=3
 * `crud_catalogos_simples`, `done`): el backend expone
 * `record Aplicacion(String codigo, String nombre)` — mismo shape uniforme
 * que los otros 4 catálogos simples. Ver `types/area.ts` para la
 * explicación completa de la decisión sobre `codigo`.
 *
 * Esto **reemplaza** el modelado previo de la feature 2
 * (`codigo: number` + `id: string`), derivado directo de la entidad JPA
 * legacy `pe.edu.cibertec.proyemp.model.Aplicacion` sin haber confirmado
 * todavía el contrato JSON real.
 */
export interface Aplicacion {
  /** PK autogenerada, representada como string (ver nota de arriba). */
  codigo: string
  /** Columna `NOMBRE` legacy, `nullable=false`. */
  nombre: string
}
