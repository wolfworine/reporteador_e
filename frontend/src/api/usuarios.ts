import { apiClient } from './client'
import type { CrearUsuarioRequest, Usuario } from '../types/usuario'

export type { CrearUsuarioRequest }

const BASE_PATH = '/api/usuarios'

/**
 * Cliente de Usuario contra el backend real
 * (`../backend/src/main/java/pe/edu/cibertec/proyemp/web/UsuarioResource.java`,
 * `@Path("/api/usuarios")`, confirmado leyendo el archivo fuente):
 * `listar`/`buscarPorId`/`crear`/`eliminar`.
 *
 * **Deliberadamente no se define una función `actualizar`**: el propio
 * Javadoc de `UsuarioResource.java` declara "CRUD reactivo (alta/lectura/
 * baja, sin actualización -- ver Javadoc de service/UsuarioService)" — no
 * hay `PUT /api/usuarios/{id}` que respalde una función de actualización.
 * Mismo criterio que la ausencia de `actualizar` en `api/atributos.ts`
 * (feature 6, `crud_atributo_detalle_tabs`): no se inventa un endpoint que
 * no existe en el backend real.
 */
export function listar(): Promise<Usuario[]> {
  return apiClient<Usuario[]>(BASE_PATH)
}

export function buscarPorId(id: string): Promise<Usuario> {
  return apiClient<Usuario>(`${BASE_PATH}/${encodeURIComponent(id)}`)
}

/**
 * `POST /api/usuarios` — único endpoint de escritura de Usuario, y el
 * único que recibe `password` en texto plano dentro del body HTTPS
 * (nunca en query string). El backend hashea la contraseña del lado del
 * servidor y responde con `UsuarioResponse` (`types/usuario.ts` →
 * `Usuario`), que nunca incluye password/hash.
 *
 * **Regla dura de seguridad de esta feature:** ni esta función ni ningún
 * `onError` de `modules/usuario/useUsuarioMantenimiento.ts` debe hacer
 * `console.log`/`console.error` de `request` (contendría la contraseña en
 * texto plano) — solo se loguea/expone al usuario el `Error`/`ApiError` ya
 * lanzado por `apiClient`, que nunca contiene el password de vuelta.
 */
export function crear(request: CrearUsuarioRequest): Promise<Usuario> {
  return apiClient<Usuario>(BASE_PATH, {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

export function eliminar(id: string): Promise<void> {
  return apiClient<void>(`${BASE_PATH}/${encodeURIComponent(id)}`, {
    method: 'DELETE',
  })
}
