import { createCatalogoApi } from './catalogoApi'
import type { Cargo } from '../types/cargo'

/**
 * Cliente CRUD del catálogo Cargo contra el backend real
 * (`../backend/src/main/java/pe/edu/cibertec/proyemp/web/CargoResource.java`,
 * `@Path("/api/cargos")`, confirmado en el código fuente). Construido sobre
 * `createCatalogoApi` (`api/catalogoApi.ts`).
 */
const cargoApi = createCatalogoApi<Cargo>('/api/cargos')

export const listar = cargoApi.listar
export const buscarPorCodigo = cargoApi.buscarPorCodigo
export const crear = cargoApi.crear
export const actualizar = cargoApi.actualizar
export const eliminar = cargoApi.eliminar
