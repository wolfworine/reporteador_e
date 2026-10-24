import { apiClient } from './client'
import type { Suministro } from '../types/suministro'

/**
 * Body de request de `POST`/`PUT` de
 * `/api/roles/{idRol}/suministros` — espejo de `SuministroRequest` del
 * backend
 * (`../backend/src/main/java/pe/edu/cibertec/proyemp/web/dto/SuministroRequest.java`):
 * `record SuministroRequest(String nombre, String prioridad)`.
 */
export interface SuministroRequest {
  nombre: string
  prioridad: string
}

function basePath(idRol: string): string {
  return `/api/roles/${encodeURIComponent(idRol)}/suministros`
}

/**
 * Cliente CRUD de Suministro contra el backend real
 * (`../backend/src/main/java/pe/edu/cibertec/proyemp/web/SuministroResource.java`,
 * `@Path("/api/roles/{idRol}/suministros")`), parametrizado por `idRol` en
 * cada función — el propio path del endpoint ya filtra por rol, sin
 * necesidad de filtrar en cliente (a diferencia de `atributos.ts`, ver su
 * comentario).
 */
export function listarPorRol(idRol: string): Promise<Suministro[]> {
  return apiClient<Suministro[]>(basePath(idRol))
}

export function buscarPorId(
  idRol: string,
  idSuministro: string,
): Promise<Suministro> {
  return apiClient<Suministro>(
    `${basePath(idRol)}/${encodeURIComponent(idSuministro)}`,
  )
}

export function crear(
  idRol: string,
  request: SuministroRequest,
): Promise<Suministro> {
  return apiClient<Suministro>(basePath(idRol), {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

export function actualizar(
  idRol: string,
  idSuministro: string,
  request: SuministroRequest,
): Promise<Suministro> {
  return apiClient<Suministro>(
    `${basePath(idRol)}/${encodeURIComponent(idSuministro)}`,
    {
      method: 'PUT',
      body: JSON.stringify(request),
    },
  )
}

export function eliminar(
  idRol: string,
  idSuministro: string,
): Promise<void> {
  return apiClient<void>(
    `${basePath(idRol)}/${encodeURIComponent(idSuministro)}`,
    { method: 'DELETE' },
  )
}
