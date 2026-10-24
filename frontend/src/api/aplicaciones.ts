import { createCatalogoApi } from './catalogoApi'
import type { Aplicacion } from '../types/aplicacion'

/**
 * Cliente CRUD del catálogo Aplicación contra el backend real
 * (`../backend/src/main/java/pe/edu/cibertec/proyemp/web/AplicacionResource.java`,
 * `@Path("/api/aplicaciones")`, confirmado en el código fuente). Construido
 * sobre `createCatalogoApi` (`api/catalogoApi.ts`).
 *
 * Nota de alcance: solo cubre el CRUD del catálogo (paridad con
 * `paginas/aplicacion/mantenimiento.xhtml`); el drill-down "VerRol" que
 * también tiene esa vista legacy es de la feature 5
 * (`crud_rol_suministro_drilldown`), fuera de alcance aquí.
 */
const aplicacionApi = createCatalogoApi<Aplicacion>('/api/aplicaciones')

export const listar = aplicacionApi.listar
export const buscarPorCodigo = aplicacionApi.buscarPorCodigo
export const crear = aplicacionApi.crear
export const actualizar = aplicacionApi.actualizar
export const eliminar = aplicacionApi.eliminar
