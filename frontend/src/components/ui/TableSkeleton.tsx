export interface TableSkeletonProps {
  /** Cantidad de columnas a simular -- debe coincidir con el `<thead>` real para que el layout no salte al llegar los datos. */
  columns: number
  /** Filas de esqueleto a mostrar, default 5 (suficiente para transmitir "está cargando" sin ocupar toda la pantalla). */
  rows?: number
}

/**
 * Placeholder de tabla mientras `listQuery.isLoading` -- reemplaza el
 * `<p>Cargando {titulo}…</p>` de texto plano que tenían todas las pantallas
 * de listado (pedido explícito del usuario: "estados de carga... bien
 * diseñados"). Vive fuera de `<table>` real (no rellena `<tbody>`) para no
 * tener que inventar `key`s de datos que no existen -- se ve como una tabla
 * gracias a las mismas clases/estructura visual (`role="status"` para que
 * lectores de pantalla anuncien "cargando", no cada barra individual).
 */
export function TableSkeleton({ columns, rows = 5 }: TableSkeletonProps) {
  return (
    <table className="skeleton-table" role="status" aria-label="Cargando…">
      <tbody>
        {Array.from({ length: rows }).map((_, fila) => (
          <tr key={fila}>
            {Array.from({ length: columns }).map((_, columna) => (
              <td key={columna}>
                <span className="skeleton-bar" />
              </td>
            ))}
          </tr>
        ))}
      </tbody>
    </table>
  )
}
