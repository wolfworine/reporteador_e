import { createCatalogoApi } from './catalogoApi'
import type { Ubicacion } from '../types/ubicacion'

/**
 * Cliente CRUD del catálogo Ubicación contra el backend real
 * (`../backend/src/main/java/pe/edu/cibertec/proyemp/web/UbicacionResource.java`,
 * `@Path("/api/ubicaciones")`, confirmado en el código fuente). Construido
 * sobre `createCatalogoApi` (`api/catalogoApi.ts`).
 */
const ubicacionApi = createCatalogoApi<Ubicacion>('/api/ubicaciones')

export const listar = ubicacionApi.listar
export const buscarPorCodigo = ubicacionApi.buscarPorCodigo
export const crear = ubicacionApi.crear
export const actualizar = ubicacionApi.actualizar
export const eliminar = ubicacionApi.eliminar
