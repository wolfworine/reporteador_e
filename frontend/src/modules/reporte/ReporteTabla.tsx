import { useState } from 'react'
import { flexRender, getCoreRowModel, useReactTable } from '@tanstack/react-table'
import type { ColumnDef } from '@tanstack/react-table'
import type { Detalle } from '../../types/detalle'
import type { DetalleRol, Reporte } from '../../types/reporte'
import { IconChevronRight } from '../../components/ui/icons'

/**
 * Las 4 categorías de suministro del reporte, en el mismo orden en que
 * aparecen en `paginas/reportes/vista.xhtml` (ROLES, GRUPOS, INBOX,
 * DEFAULTINBOX) -- cada una es una `p:dataTable` anidada aparte, renderizada
 * solo `rendered="#{not empty detallerol.suministroX}"`. `color` es solo
 * para las chips de Titularidad/el detalle expandido (ver rediseño
 * 2026-08-25 más abajo), no tiene equivalente en el legacy.
 */
const CATEGORIAS: {
  key: keyof Pick<
    DetalleRol,
    | 'suministroRoles'
    | 'suministroGrupos'
    | 'suministroInbox'
    | 'suministroDefaultinbox'
  >
  label: string
  color: string
}[] = [
  { key: 'suministroRoles', label: 'ROLES', color: 'roles' },
  { key: 'suministroGrupos', label: 'GRUPOS', color: 'grupos' },
  { key: 'suministroInbox', label: 'INBOX', color: 'inbox' },
  { key: 'suministroDefaultinbox', label: 'DEFAULTINBOX', color: 'default' },
]

/**
 * Una de las sub-tablas de atributos de un Rol -- columnas
 * `atributo_codigo`/`atributo_nombre`, solo se renderiza si la categoría
 * tiene datos (paridad exacta con el `rendered="#{not empty ...}"` del
 * legacy). Sin cambios de estructura en el rediseño 2026-08-25 -- solo
 * cambió CUÁNDO se muestra (al expandir la fila) y CÓMO se acomodan entre
 * sí (grilla en vez de apiladas), no su forma interna.
 */
function CategoriaAtributos({
  label,
  detalles,
}: {
  label: string
  detalles: Detalle[]
}) {
  if (detalles.length === 0) {
    return null
  }
  return (
    <table aria-label={`Categoría ${label}`}>
      <caption>{label}</caption>
      <thead>
        <tr>
          <th>Atributo Código</th>
          <th>Atributo Nombre</th>
        </tr>
      </thead>
      <tbody>
        {detalles.map((detalle) => (
          <tr key={detalle.id}>
            <td>{detalle.atributoCodigo}</td>
            <td>{detalle.atributoNombre}</td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}

/**
 * Panel de detalle de un Rol, visible solo cuando su fila está expandida --
 * grilla de hasta 4 columnas (una por categoría con datos), cada una su
 * propia `CategoriaAtributos`.
 */
function AtributosDetalle({ detalle }: { detalle: DetalleRol }) {
  const categoriasConDatos = CATEGORIAS.filter(({ key }) => detalle[key].length > 0)
  if (categoriasConDatos.length === 0) {
    return (
      <div className="rol-row__detalle rol-row__detalle--vacio">
        Sin datos de suministro para este rol.
      </div>
    )
  }
  return (
    <div className="rol-row__detalle">
      {categoriasConDatos.map(({ key, label }) => (
        <CategoriaAtributos key={key} label={label} detalles={detalle[key]} />
      ))}
    </div>
  )
}

/**
 * Chips de "Titularidad" -- una por categoría de suministro con datos, en
 * vez de la lista vertical anterior. Mismo criterio de qué categorías
 * mostrar (`detalle[key].length > 0`) y mismo `<ul aria-label>` accesible
 * que antes (`getByRole('list', {name: 'Titularidad <rol>'})` en los tests
 * sigue encontrándola), solo cambia la presentación visual.
 */
function TitularidadChips({ detalle }: { detalle: DetalleRol }) {
  const items = CATEGORIAS.filter(({ key }) => detalle[key].length > 0)
  return (
    <ul className="titularidad-chips" aria-label={`Titularidad ${detalle.rol}`}>
      {items.map(({ key, label, color }) => (
        <li key={key} className={`chip chip--${color}`}>
          {label}
        </li>
      ))}
    </ul>
  )
}

const columns: ColumnDef<DetalleRol>[] = [
  {
    id: 'rol',
    header: 'Rol',
    accessorFn: (row) => row.rol,
    cell: (info) => (
      <span className="rol-row__nombre">{info.getValue<string>()}</span>
    ),
  },
  {
    id: 'tipo',
    header: 'Tipo',
    accessorFn: (row) => row.tipo,
    cell: (info) => (
      <span className="badge-tipo">{info.getValue<string>()}</span>
    ),
  },
  {
    id: 'aplicacion',
    header: 'Aplicacion',
    accessorFn: (row) => row.aplicacion,
    cell: (info) => (
      <span className="rol-row__aplicacion">{info.getValue<string>()}</span>
    ),
  },
  {
    id: 'titularidad',
    header: 'Titularidad',
    cell: ({ row }) => <TitularidadChips detalle={row.original} />,
  },
]

/**
 * Tabla de Rol de un Cargo específico dentro de un `Reporte`, armada con
 * TanStack Table (`docs/conventions.md` la exige explícitamente para las
 * tablas anidadas de Reportes) -- headless por diseño, así que el "row
 * model" que da la librería se sigue usando tal cual, pero cada fila se
 * pinta como una línea de encabezado colapsable en vez de un `<tr>` de una
 * tabla HTML literal.
 *
 * **Rediseño 2026-08-25 (Dirección A del canvas "Rediseño del
 * Reporteador", elegida por el usuario tras revisar 3 direcciones
 * inspiradas en guías de NN/g/Pencil & Paper sobre progressive disclosure
 * en tablas densas):** antes, la columna "Atributos" de cada fila
 * mostraba SIEMPRE sus 4 sub-tablas apiladas -- con reportes de varios
 * roles por cargo la página se volvía enorme por defecto, sin forma de
 * escanear solo Rol/Tipo/Aplicación/Titularidad. Ahora cada fila arranca
 * colapsada (solo el resumen); un clic (o Enter/Espacio, `role="button"`
 * propio ya que no es un `<button>` nativo -- iría a contener un `<ul>`,
 * contenido de flujo no permitido dentro de `<button>`) expande
 * `AtributosDetalle` debajo. El dato ya está 100% cargado en memoria desde
 * el único `GET obtenerReporte` que dispara `ReporteVista` -- expandir no
 * dispara ningún request nuevo, es un toggle de estado local.
 */
function CargoSubTabla({ reporte }: { reporte: Reporte }) {
  const [expandidos, setExpandidos] = useState<Record<string, boolean>>({})
  const table = useReactTable({
    data: reporte.detalleRol,
    columns,
    getCoreRowModel: getCoreRowModel(),
  })

  function alternar(idRol: string) {
    setExpandidos((prev) => ({ ...prev, [idRol]: !prev[idRol] }))
  }

  return (
    <section aria-label={`Cargo ${reporte.codigoCargo}`} className="cargo-card">
      <header>
        <p>
          Área: {reporte.codigoArea} - {reporte.area}
        </p>
        <p>
          Cargo: {reporte.codigoCargo} - {reporte.cargo}
        </p>
      </header>

      <div className="cargo-card__encabezado" aria-hidden="true">
        <span />
        <span>Rol</span>
        <span>Tipo</span>
        <span>Aplicación</span>
        <span>Titularidad</span>
      </div>

      <div className="cargo-card__filas">
        {table.getRowModel().rows.map((row) => {
          const detalle = row.original
          const abierto = !!expandidos[detalle.idRol]
          return (
            <div className="rol-row" key={row.id}>
              <div
                className="rol-row__cabecera"
                role="button"
                tabIndex={0}
                aria-expanded={abierto}
                aria-label={`Ver detalle de ${detalle.rol}`}
                onClick={() => alternar(detalle.idRol)}
                onKeyDown={(event) => {
                  if (event.key === 'Enter' || event.key === ' ') {
                    event.preventDefault()
                    alternar(detalle.idRol)
                  }
                }}
              >
                <IconChevronRight
                  className={`rol-row__chevron${abierto ? ' rol-row__chevron--abierto' : ''}`}
                />
                {row.getVisibleCells().map((cell) => (
                  <span
                    key={cell.id}
                    className={`rol-row__celda rol-row__celda--${cell.column.id}`}
                  >
                    {flexRender(cell.column.columnDef.cell, cell.getContext())}
                  </span>
                ))}
              </div>
              {abierto && <AtributosDetalle detalle={detalle} />}
            </div>
          )
        })}
      </div>
    </section>
  )
}

export interface ReporteTablaProps {
  reportes: Reporte[]
}

/**
 * Reproduce la jerarquía real Cargo→Rol→(Roles/Grupos/Inbox/DefaultInbox)
 * de `paginas/reportes/vista.xhtml` (confirmado leyendo el archivo
 * directamente: `p:dataTable value="reportes"` → `p:subTable
 * value="reportes.detallerol"`, una sub-tabla por Cargo, con columnas
 * Rol/Tipo/Aplicacion/Titularidad/Atributos). Cada elemento de `reportes`
 * es un grupo Área+Cargo (mismo criterio del legacy) -- ver acceptance
 * criterio 1 de la feature. Presentación de la columna Atributos rediseñada
 * 2026-08-25, ver Javadoc de `CargoSubTabla`.
 */
export function ReporteTabla({ reportes }: ReporteTablaProps) {
  if (reportes.length === 0) {
    return <p>No hay datos de reporte para mostrar.</p>
  }
  return (
    <div>
      {reportes.map((reporte) => (
        <CargoSubTabla
          key={`${reporte.codigoArea}-${reporte.codigoCargo}`}
          reporte={reporte}
        />
      ))}
    </div>
  )
}
