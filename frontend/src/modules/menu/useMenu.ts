import { useQuery } from '@tanstack/react-query'
import { fetchMenu } from '../../api/menu'
import type { MenuNodo } from '../../types/menu'
import type { UseQueryResult } from '@tanstack/react-query'

/**
 * Hook de TanStack Query que trae el árbol de menú de
 * `GET /api/menu/{tipoUsuario}` -- el árbol ya viene armado del lado del
 * servidor (ver `types/menu.ts`), este hook no arma nada del lado del
 * cliente.
 *
 * `tipoUsuario` puede ser `undefined` mientras el usuario todavía no inició
 * sesión (`Layout` solo se renderiza tras autenticación, pero el tipo se
 * modela como opcional para no forzar un valor falso antes de tiempo); la
 * query queda deshabilitada (`enabled: false`) hasta tener un valor.
 */
export function useMenu(
  tipoUsuario: string | undefined,
): UseQueryResult<MenuNodo[]> {
  return useQuery({
    queryKey: ['menu', tipoUsuario],
    queryFn: () => fetchMenu(tipoUsuario as string),
    enabled: tipoUsuario !== undefined && tipoUsuario.length > 0,
  })
}
