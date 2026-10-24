import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { UseMutationResult, UseQueryResult } from '@tanstack/react-query'
import * as suministrosApi from '../../api/suministros'
import type { SuministroRequest } from '../../api/suministros'
import type { Suministro } from '../../types/suministro'

export interface ActualizarSuministroInput {
  idSuministro: string
  request: SuministroRequest
}

export interface UseSuministroMantenimientoResult {
  listQuery: UseQueryResult<Suministro[]>
  crearMutation: UseMutationResult<Suministro, Error, SuministroRequest>
  actualizarMutation: UseMutationResult<
    Suministro,
    Error,
    ActualizarSuministroInput
  >
  eliminarMutation: UseMutationResult<void, Error, string>
}

/**
 * Hook de TanStack Query para el mantenimiento de Suministro **de un Rol
 * específico** (`idRol` viene del route param `/roles/:idRol/suministros`
 * — ver `SuministroPorRol.tsx`). El listado ya viene filtrado por rol
 * desde el propio backend (`GET /api/roles/{idRol}/suministros`), sin
 * filtrado adicional en cliente — a diferencia del drill-down siguiente
 * (Suministro→Atributo, ver `modules/atributo/AtributoPorSuministro.tsx`).
 */
export function useSuministroMantenimiento(
  idRol: string,
): UseSuministroMantenimientoResult {
  const queryClient = useQueryClient()
  const queryKey = ['suministros', idRol]

  const listQuery = useQuery({
    queryKey,
    queryFn: () => suministrosApi.listarPorRol(idRol),
    enabled: idRol.length > 0,
  })

  function invalidate() {
    return queryClient.invalidateQueries({ queryKey })
  }

  const crearMutation = useMutation({
    mutationFn: (request: SuministroRequest) =>
      suministrosApi.crear(idRol, request),
    onSuccess: invalidate,
  })

  const actualizarMutation = useMutation({
    mutationFn: ({ idSuministro, request }: ActualizarSuministroInput) =>
      suministrosApi.actualizar(idRol, idSuministro, request),
    onSuccess: invalidate,
  })

  const eliminarMutation = useMutation({
    mutationFn: (idSuministro: string) =>
      suministrosApi.eliminar(idRol, idSuministro),
    onSuccess: invalidate,
  })

  return { listQuery, crearMutation, actualizarMutation, eliminarMutation }
}
