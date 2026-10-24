import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { UseMutationResult, UseQueryResult } from '@tanstack/react-query'
import type { Catalogo, CatalogoApi, CatalogoRequest } from '../../api/catalogoApi'

export interface ActualizarCatalogoInput {
  codigo: string
  request: CatalogoRequest
}

export interface UseCatalogoMantenimientoResult<T extends Catalogo> {
  listQuery: UseQueryResult<T[]>
  crearMutation: UseMutationResult<T, Error, CatalogoRequest>
  actualizarMutation: UseMutationResult<T, Error, ActualizarCatalogoInput>
  eliminarMutation: UseMutationResult<void, Error, string>
}

/**
 * Hook de TanStack Query compartido por los 5 catálogos simples
 * (Área/Cargo/Ubicación/Aplicación/Estado): listado (`useQuery`) + crear/
 * actualizar/eliminar (`useMutation`), invalidando el listado al éxito de
 * cualquier mutación. Todo dato de servidor pasa por TanStack Query — sin
 * `useEffect`/`useState` manual (regla dura de `docs/architecture.md`).
 *
 * Vive en `modules/catalogo/` (no en un módulo de una entidad concreta)
 * porque es lógica reutilizada tal cual por los 5 módulos
 * (`modules/area`, `modules/cargo`, etc.) — evita duplicar la misma lógica
 * de wiring de queries/mutaciones 5 veces.
 */
export function useCatalogoMantenimiento<T extends Catalogo>(
  queryKey: string,
  api: CatalogoApi<T>,
): UseCatalogoMantenimientoResult<T> {
  const queryClient = useQueryClient()

  const listQuery = useQuery({
    queryKey: [queryKey],
    queryFn: api.listar,
  })

  function invalidate() {
    return queryClient.invalidateQueries({ queryKey: [queryKey] })
  }

  const crearMutation = useMutation({
    mutationFn: (request: CatalogoRequest) => api.crear(request),
    onSuccess: invalidate,
  })

  const actualizarMutation = useMutation({
    mutationFn: ({ codigo, request }: ActualizarCatalogoInput) =>
      api.actualizar(codigo, request),
    onSuccess: invalidate,
  })

  const eliminarMutation = useMutation({
    mutationFn: (codigo: string) => api.eliminar(codigo),
    onSuccess: invalidate,
  })

  return { listQuery, crearMutation, actualizarMutation, eliminarMutation }
}
