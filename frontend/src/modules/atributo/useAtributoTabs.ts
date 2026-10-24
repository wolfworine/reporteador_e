import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { UseMutationResult, UseQueryResult } from '@tanstack/react-query'
import * as atributosApi from '../../api/atributos'
import type { AtributoRequest } from '../../api/atributos'
import type { Atributo, CategoriaSuministro } from '../../types/atributo'

/**
 * Las 4 categorías de suministro, en el mismo orden de pestañas del
 * legacy — `paginas/suministro/mantenimiento_atributo.xhtml` (Roles,
 * activa por defecto), `grupos.xhtml`, `inbox.xhtml`, `defaultinbox.xhtml`.
 */
export const CATEGORIAS_ATRIBUTO: {
  categoria: CategoriaSuministro
  label: string
}[] = [
  { categoria: 'ROLES', label: 'Roles' },
  { categoria: 'GRUPOS', label: 'Grupos' },
  { categoria: 'INBOX', label: 'Inbox' },
  { categoria: 'DEFAULTINBOX', label: 'DefaultInbox' },
]

export interface UseAtributoCategoriaResult {
  listQuery: UseQueryResult<Atributo[]>
  crearMutation: UseMutationResult<Atributo, Error, AtributoRequest>
}

/**
 * Hook de TanStack Query para **una sola** categoría de Atributo de un Rol
 * (`GET /api/atributos/rol/{idRol}/categoria/{categoria}`) + su mutación de
 * creación (`POST /api/atributos`, único endpoint de escritura real —
 * ver `api/atributos.ts`).
 *
 * `AtributoTabs` invoca este hook una sola vez con la `categoria` de la
 * pestaña **activa** (no las 4 a la vez) — la `queryKey` incluye la
 * categoría, así que TanStack Query cachea cada categoría por separado
 * (cambiar de pestaña y volver no repite el fetch) sin mezclar nunca datos
 * de una categoría con otra (acceptance criterio 2 de la feature
 * `crud_atributo_detalle_tabs`).
 */
export function useAtributoCategoria(
  idRol: string,
  categoria: CategoriaSuministro,
  aplicacion?: string,
): UseAtributoCategoriaResult {
  const queryClient = useQueryClient()
  const queryKey = ['atributos', idRol, 'categoria', categoria, aplicacion ?? null]

  const listQuery = useQuery({
    queryKey,
    queryFn: () => atributosApi.listarPorRolYCategoria(idRol, categoria, aplicacion),
    // `aplicacion` es obligatorio en la práctica (ver Javadoc de
    // `api/atributos.ts#listarPorRolYCategoria`) -- sin él el backend arma
    // una GSI1PK `APLICACION#null#AREA#...` que nunca matchea datos reales
    // (bug real encontrado 2026-08-25, ver `AtributoTabs.tsx`). Esperar a
    // que `aplicacion` esté resuelto evita un primer fetch garantizado
    // vacío que se autocorrige recién cuando cambia la queryKey.
    enabled: Boolean(idRol) && Boolean(aplicacion),
  })

  const crearMutation = useMutation({
    mutationFn: (request: AtributoRequest) => atributosApi.crear(request),
    onSuccess: () => queryClient.invalidateQueries({ queryKey }),
  })

  return { listQuery, crearMutation }
}
