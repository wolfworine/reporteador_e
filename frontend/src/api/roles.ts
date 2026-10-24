import { apiClient } from './client'
import type { Rol } from '../types/rol'

/**
 * Body de request de `POST`/`PUT` de `/api/roles` — espejo de
 * `RolRequest` del backend
 * (`../backend/src/main/java/pe/edu/cibertec/proyemp/web/dto/RolRequest.java`):
 * mismos campos que `Rol` sin `id` (se asigna al crear).
 */
export interface RolRequest {
  ubicacionCodigo: string
  areaCodigo: string
  cargoCodigo: string
  nombre: string
  descripcion: string
  tipo: string
  reglaNegocio: string
  ambito: string
  propietarioRol: string
  propietarioPersona: string
  situacion: string
  rolBase: string
}

const BASE_PATH = '/api/roles'

/**
 * Cliente CRUD de Rol contra el backend real
 * (`../backend/src/main/java/pe/edu/cibertec/proyemp/web/RolResource.java`,
 * `@Path("/api/roles")`, confirmado en el código fuente). No se factoriza
 * con `createCatalogoApi` (`api/catalogoApi.ts`) porque el shape de Rol no
 * calza con el contrato `{codigo, nombre}` de los catálogos simples (PK
 * `id`, más campos, sin `buscarPorCodigo`) — sería forzar una abstracción
 * que no aplica.
 */
export function listar(): Promise<Rol[]> {
  return apiClient<Rol[]>(BASE_PATH)
}

export function buscarPorId(id: string): Promise<Rol> {
  return apiClient<Rol>(`${BASE_PATH}/${encodeURIComponent(id)}`)
}

export function crear(request: RolRequest): Promise<Rol> {
  return apiClient<Rol>(BASE_PATH, {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

export function actualizar(id: string, request: RolRequest): Promise<Rol> {
  return apiClient<Rol>(`${BASE_PATH}/${encodeURIComponent(id)}`, {
    method: 'PUT',
    body: JSON.stringify(request),
  })
}

export function eliminar(id: string): Promise<void> {
  return apiClient<void>(`${BASE_PATH}/${encodeURIComponent(id)}`, {
    method: 'DELETE',
  })
}
