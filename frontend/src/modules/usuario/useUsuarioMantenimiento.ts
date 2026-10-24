import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { UseMutationResult, UseQueryResult } from '@tanstack/react-query'
import * as usuariosApi from '../../api/usuarios'
import type { CrearUsuarioRequest } from '../../api/usuarios'
import type { Usuario } from '../../types/usuario'

export interface UseUsuarioMantenimientoResult {
  listQuery: UseQueryResult<Usuario[]>
  crearMutation: UseMutationResult<Usuario, Error, CrearUsuarioRequest>
  eliminarMutation: UseMutationResult<void, Error, string>
}

const QUERY_KEY = ['usuarios']

/**
 * Hook de TanStack Query para el mantenimiento de Usuario: listado +
 * crear/eliminar, invalidando el listado al éxito de cualquier mutación —
 * mismo criterio que `modules/rol/useRolMantenimiento.ts`. Sin mutación de
 * actualizar: el backend real no expone `PUT /api/usuarios/{id}` (ver
 * `api/usuarios.ts`).
 *
 * **Seguridad de la contraseña:** este hook nunca hace `console.log`/
 * `console.error` de `request`/`variables` de `crearMutation` — TanStack
 * Query no loguea automáticamente los argumentos de una mutación, y no se
 * agrega ningún `onError`/middleware aquí que lo haga. El único dato que
 * llega al componente en caso de error es `crearMutation.error` (el
 * `Error`/`ApiError` ya lanzado por `apiClient`), nunca el body de la
 * request original.
 */
export function useUsuarioMantenimiento(): UseUsuarioMantenimientoResult {
  const queryClient = useQueryClient()

  const listQuery = useQuery({
    queryKey: QUERY_KEY,
    queryFn: usuariosApi.listar,
  })

  function invalidate() {
    return queryClient.invalidateQueries({ queryKey: QUERY_KEY })
  }

  const crearMutation = useMutation({
    mutationFn: (request: CrearUsuarioRequest) => usuariosApi.crear(request),
    onSuccess: invalidate,
  })

  const eliminarMutation = useMutation({
    mutationFn: (id: string) => usuariosApi.eliminar(id),
    onSuccess: invalidate,
  })

  return { listQuery, crearMutation, eliminarMutation }
}
