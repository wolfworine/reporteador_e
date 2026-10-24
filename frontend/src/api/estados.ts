import { createCatalogoApi } from './catalogoApi'
import type { Estado } from '../types/estado'

/**
 * Cliente CRUD del catálogo Estado contra el backend real
 * (`../backend/src/main/java/pe/edu/cibertec/proyemp/web/EstadoResource.java`,
 * `@Path("/api/estados")`, confirmado en el código fuente). Construido
 * sobre `createCatalogoApi` (`api/catalogoApi.ts`).
 */
const estadoApi = createCatalogoApi<Estado>('/api/estados')

export const listar = estadoApi.listar
export const buscarPorCodigo = estadoApi.buscarPorCodigo
export const crear = estadoApi.crear
export const actualizar = estadoApi.actualizar
export const eliminar = estadoApi.eliminar
