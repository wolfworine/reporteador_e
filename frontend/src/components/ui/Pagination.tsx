export interface PaginationProps {
  page: number
  totalPages: number
  totalItems: number
  pageSize: number
  onPageChange: (page: number) => void
}

/** Barra de paginación -- ver `usePagination` para el cálculo de las páginas. */
export function Pagination({
  page,
  totalPages,
  totalItems,
  pageSize,
  onPageChange,
}: PaginationProps) {
  if (totalItems === 0) {
    return null
  }

  const desde = (page - 1) * pageSize + 1
  const hasta = Math.min(page * pageSize, totalItems)

  return (
    <nav className="pagination" aria-label="Paginación">
      <span className="pagination__resumen">
        {desde}–{hasta} de {totalItems}
      </span>
      <button
        type="button"
        onClick={() => onPageChange(page - 1)}
        disabled={page <= 1}
      >
        Anterior
      </button>
      <span className="pagination__pagina">
        Página {page} de {totalPages}
      </span>
      <button
        type="button"
        onClick={() => onPageChange(page + 1)}
        disabled={page >= totalPages}
      >
        Siguiente
      </button>
    </nav>
  )
}
