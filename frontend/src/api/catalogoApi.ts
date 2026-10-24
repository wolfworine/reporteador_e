import { apiClient } from './client'

/**
 * Shape mínimo común a los 5 catálogos simples (Área, Cargo, Ubicación,
 * Aplicación, Estado) tal como los devuelve el backend real — ver
 * `types/area.ts` para la justificación completa de por qué es
 * `{codigo: string, nombre: string}` y no el shape `{codigo: number, id,
 * nombre}` que se había asumido en la feature 2.
 */
export interface Catalogo {
  codigo: string
  nombre: string
}

/**
 * Body de request compartido por los 5 endpoints de catálogo simple
 * (POST/PUT) — espejo de `CatalogoRequest` del backend
 * (`../backend/src/main/java/pe/edu/cibertec/proyemp/web/dto/CatalogoRequest.java`):
 * un único campo `nombre`. El `codigo` nunca lo envía el cliente: se genera
 * en el servidor al crear, o viaja en el path de la URL al actualizar.
 */
export interface CatalogoRequest {
  nombre: string
}

/**
 * Operaciones CRUD comunes a los 5 catálogos simples, tipadas sobre la
 * entidad concreta `T` (p. ej. `Area`).
 */
export interface CatalogoApi<T extends Catalogo> {
  listar(): Promise<T[]>
  buscarPorCodigo(codigo: string): Promise<T>
  crear(request: CatalogoRequest): Promise<T>
  actualizar(codigo: string, request: CatalogoRequest): Promise<T>
  eliminar(codigo: string): Promise<void>
}

/**
 * Factoriza el cliente CRUD de un catálogo simple contra su `basePath`
 * real (p. ej. `/api/areas`). Los 5 catálogos (`AreaResource.java`,
 * `CargoResource.java`, `UbicacionResource.java`, `AplicacionResource.java`,
 * `EstadoResource.java` en `../backend/src/main/java/pe/edu/cibertec/proyemp/web/`)
 * exponen exactamente el mismo patrón de rutas
 * (`GET /`, `GET /{codigo}`, `POST /`, `PUT /{codigo}`, `DELETE /{codigo}`)
 * — factorizar aquí evita duplicar la misma lógica 5 veces en
 * `api/areas.ts`/`cargos.ts`/`ubicaciones.ts`/`aplicaciones.ts`/`estados.ts`.
 */
export function createCatalogoApi<T extends Catalogo>(
  basePath: string,
): CatalogoApi<T> {
  return {
    listar: () => apiClient<T[]>(basePath),
    buscarPorCodigo: (codigo) =>
      apiClient<T>(`${basePath}/${encodeURIComponent(codigo)}`),
    crear: (request) =>
      apiClient<T>(basePath, {
        method: 'POST',
        body: JSON.stringify(request),
      }),
    actualizar: (codigo, request) =>
      apiClient<T>(`${basePath}/${encodeURIComponent(codigo)}`, {
        method: 'PUT',
        body: JSON.stringify(request),
      }),
    eliminar: (codigo) =>
      apiClient<void>(`${basePath}/${encodeURIComponent(codigo)}`, {
        method: 'DELETE',
      }),
  }
}
