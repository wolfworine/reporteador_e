import { useState } from 'react'

export interface UsePaginationResult<T> {
  pageItems: T[]
  page: number
  totalPages: number
  totalItems: number
  pageSize: number
  goToPage: (page: number) => void
}

const DEFAULT_PAGE_SIZE = 10

/**
 * Paginación client-side genérica -- pedido explícito del usuario
 * ("deberias cargar los 10 primeros"): sin esto, tablas como Cargo (695
 * filas) o Atributo (15,565 filas) rendereizaban TODAS las filas de una
 * sola vez en el DOM.
 *
 * Vuelve a la página 1 cuando cambia el tamaño de la lista filtrada (nueva
 * búsqueda, alta, baja) -- ajuste de estado en tiempo de render, mismo
 * patrón ya usado en `useReportEvents`/`useReporteFlujo` de este proyecto
 * para "resetear estado cuando cambia una prop"
 * (https://react.dev/learn/you-might-not-need-an-effect#adjusting-some-state-when-a-prop-changes),
 * en vez de un `useEffect` aparte.
 */
export function usePagination<T>(
  items: T[],
  pageSize: number = DEFAULT_PAGE_SIZE,
): UsePaginationResult<T> {
  const [page, setPage] = useState(1)
  const totalPages = Math.max(1, Math.ceil(items.length / pageSize))

  const [totalItemsObservado, setTotalItemsObservado] = useState(items.length)
  if (items.length !== totalItemsObservado) {
    setTotalItemsObservado(items.length)
    if (page !== 1) {
      setPage(1)
    }
  }

  const paginaEfectiva = Math.min(page, totalPages)
  const inicio = (paginaEfectiva - 1) * pageSize
  const pageItems = items.slice(inicio, inicio + pageSize)

  return {
    pageItems,
    page: paginaEfectiva,
    totalPages,
    totalItems: items.length,
    pageSize,
    goToPage: setPage,
  }
}
