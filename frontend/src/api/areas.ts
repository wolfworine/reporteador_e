import { createCatalogoApi } from './catalogoApi'
import type { Area } from '../types/area'

/**
 * Cliente CRUD del catálogo Área contra el backend real
 * (`../backend/src/main/java/pe/edu/cibertec/proyemp/web/AreaResource.java`,
 * `@Path("/api/areas")`, confirmado en el código fuente). Construido sobre
 * `createCatalogoApi` (`api/catalogoApi.ts`) — los 5 catálogos simples
 * comparten exactamente el mismo patrón de rutas.
 */
const areaApi = createCatalogoApi<Area>('/api/areas')

export const listar = areaApi.listar
export const buscarPorCodigo = areaApi.buscarPorCodigo
export const crear = areaApi.crear
export const actualizar = areaApi.actualizar
export const eliminar = areaApi.eliminar
