/**
 * DTO real devuelto por el backend para el catálogo Área.
 *
 * **Corregido 09-ago-2026** tras confirmar el contrato real del backend
 * (`../backend/src/main/java/pe/edu/cibertec/proyemp/web/AreaResource.java`
 * y `.../domain/Area.java`; `../backend/feature_list.json` id=3
 * `crud_catalogos_simples`, `done`): el backend expone
 * `record Area(String codigo, String nombre)` — **sin** un campo `id` de
 * negocio separado. `codigo` es el PK autogenerado legado (columna `ID` de
 * la tabla `area` legacy, la misma que referencian las FKs reales como
 * `Rol.b_area`) representado como `String` para encajar como literal de PK
 * de DynamoDB; el backend NO expone el código de negocio corto legacy
 * (`ID_AREA`/`id`) como campo aparte.
 *
 * Esto **reemplaza** el modelado previo de la feature 2
 * (`codigo: number` + `id: string`), que se derivó directo de la entidad
 * JPA legacy `pe.edu.cibertec.proyemp.model.Area` sin haber confirmado
 * todavía el contrato JSON real del backend. La entidad JPA legacy sigue
 * siendo referencia válida para el *comportamiento* de negocio (los
 * filtros de `paginas/area/mantenimiento.xhtml` por "Id"/"Area"), pero ya
 * no es la fuente de verdad para el shape exacto del JSON — ese rol lo
 * cumple ahora el DTO real del backend citado arriba.
 */
export interface Area {
  /** PK autogenerada, representada como string (ver nota de arriba). */
  codigo: string
  /** Columna `NOMBRE` legacy, `nullable=false`. */
  nombre: string
}
