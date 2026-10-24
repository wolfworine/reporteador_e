import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { UseMutationResult, UseQueryResult } from '@tanstack/react-query'
import * as rolesApi from '../../api/roles'
import type { RolRequest } from '../../api/roles'
import type { Rol } from '../../types/rol'

export interface ActualizarRolInput {
  id: string
  request: RolRequest
}

export interface UseRolMantenimientoResult {
  listQuery: UseQueryResult<Rol[]>
  crearMutation: UseMutationResult<Rol, Error, RolRequest>
  actualizarMutation: UseMutationResult<Rol, Error, ActualizarRolInput>
  eliminarMutation: UseMutationResult<void, Error, string>
}

const QUERY_KEY = ['roles']

/**
 * Hook de TanStack Query para el mantenimiento de Rol: listado + crear/
 * actualizar/eliminar, invalidando el listado al éxito de cualquier
 * mutación — mismo criterio que `modules/catalogo/useCatalogoMantenimiento.ts`
 * de la feature 4, pero sin reutilizar ese hook genérico porque el shape de
 * Rol (PK `id`, sin `buscarPorCodigo`) no calza con el contrato
 * `CatalogoApi<T extends Catalogo>` (ver comentario de `api/roles.ts`).
 */
export function useRolMantenimiento(): UseRolMantenimientoResult {
  const queryClient = useQueryClient()

  const listQuery = useQuery({
    queryKey: QUERY_KEY,
    queryFn: rolesApi.listar,
  })

  function invalidate() {
    return queryClient.invalidateQueries({ queryKey: QUERY_KEY })
  }

  const crearMutation = useMutation({
    mutationFn: (request: RolRequest) => rolesApi.crear(request),
    onSuccess: invalidate,
  })

  const actualizarMutation = useMutation({
    mutationFn: ({ id, request }: ActualizarRolInput) =>
      rolesApi.actualizar(id, request),
    onSuccess: invalidate,
  })

  const eliminarMutation = useMutation({
    mutationFn: (id: string) => rolesApi.eliminar(id),
    onSuccess: invalidate,
  })

  return { listQuery, crearMutation, actualizarMutation, eliminarMutation }
}
