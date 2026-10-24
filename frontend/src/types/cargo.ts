/**
 * DTO real devuelto por el backend para el catálogo Cargo.
 *
 * **Corregido 09-ago-2026** tras confirmar el contrato real del backend
 * (`../backend/src/main/java/pe/edu/cibertec/proyemp/web/CargoResource.java`
 * y `.../domain/Cargo.java`; `../backend/feature_list.json` id=3
 * `crud_catalogos_simples`, `done`): el backend expone
 * `record Cargo(String codigo, String nombre)` — mismo shape uniforme que
 * los otros 4 catálogos simples, sin campo `id` de negocio separado. Ver
 * `types/area.ts` para la explicación completa de la decisión (`codigo`
 * como PK legado representado como string, sin el código de negocio corto
 * legacy `ID_CARGO`/`id` como campo aparte).
 *
 * Esto **reemplaza** el modelado previo de la feature 2 (`codigo: number` +
 * `id: string` + `nombre: string | null`), derivado directo de la entidad
 * JPA legacy `pe.edu.cibertec.proyemp.model.Cargo` sin haber confirmado
 * todavía el contrato JSON real. Nota: el backend real no modela `nombre`
 * como nullable (a diferencia del legacy) — se refleja aquí como
 * obligatorio, fiel al DTO real.
 */
export interface Cargo {
  /** PK autogenerada, representada como string (ver nota de arriba). */
  codigo: string
  /** Columna `NOMBRE` legacy. */
  nombre: string
}
