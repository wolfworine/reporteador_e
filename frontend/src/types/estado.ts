/**
 * DTO real devuelto por el backend para el catálogo Estado.
 *
 * **Corregido 09-ago-2026** tras confirmar el contrato real del backend
 * (`../backend/src/main/java/pe/edu/cibertec/proyemp/web/EstadoResource.java`
 * y `.../domain/Estado.java`; `../backend/feature_list.json` id=3
 * `crud_catalogos_simples`, `done`): el backend expone
 * `record Estado(String codigo, String nombre)`, el mismo shape uniforme
 * `(codigo, nombre)` que usa para los 5 catálogos simples, aunque el
 * legacy (`model/Estado.java`) solo tenía un único campo identificador
 * (`id`, `Long` autogenerado) sin el split codigo de negocio/id que sí
 * tenían Area/Cargo/Ubicacion/Aplicacion — el backend decidió encajarlo en
 * el mismo shape `(codigo, nombre)` para uniformidad entre los 5.
 *
 * Esto **reemplaza** el modelado previo de la feature 2 (`id: number` +
 * `nombre: string`, sin campo `codigo`), derivado directo de la entidad
 * JPA legacy sin haber confirmado todavía el contrato JSON real que el
 * backend terminó exponiendo.
 *
 * Nota de alcance: igual que en la feature 2, `Estado` no tenía managed
 * bean propio en el legacy (era catálogo de apoyo de `Atributo`), pero es
 * uno de los 5 catálogos simples que expone el backend con CRUD completo
 * (`EstadoResource.java`) y que `docs/migration-plan.md` (fase 4) enumera
 * junto a Área/Cargo/Ubicación/Aplicación — se mantiene su mantenimiento
 * propio en esta feature por paridad con los otros 4, no es scope creep.
 */
export interface Estado {
  /** PK autogenerada, representada como string (ver nota de arriba). */
  codigo: string
  /** Columna `NOMBRE` legacy, `nullable=false`, `length=45`. */
  nombre: string
}
