import { useState } from 'react'
import type { FormEvent } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import * as aplicacionesApi from '../../api/aplicaciones'
import type { AtributoRequest } from '../../api/atributos'
import type { CategoriaSuministro, EstadoAtributo } from '../../types/atributo'
import { ApiError } from '../../api/client'
import { CATEGORIAS_ATRIBUTO, useAtributoCategoria } from './useAtributoTabs'
import { Modal } from '../../components/ui/Modal'
import { Pagination } from '../../components/ui/Pagination'
import { usePagination } from '../../components/ui/usePagination'
import { useToast } from '../../components/ui/ToastProvider'
import { TableSkeleton } from '../../components/ui/TableSkeleton'
import { IconPlus, IconSearch } from '../../components/ui/icons'

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof ApiError ? error.message : fallback
}

const ESTADOS: EstadoAtributo[] = ['ACTIVO', 'INACTIVO']

/**
 * Clase de chip por categoría -- mismo tema de color del Reporteador
 * (`--cat-roles/grupos/inbox/default`, `ReporteTabla.tsx`), reutilizado acá
 * para la columna "Parametro" (pedido explícito del usuario 2026-08-26:
 * "la lista de detalle atributo... hay un campo parametro deberias
 * pintarlo como se estableció en la vista reporteador"). Mapeo directo --
 * `categoria` ES una de las 4 categorías del Reporteador, no solo una
 * analogía como en `RolMantenimiento.tsx`.
 */
const CHIP_POR_CATEGORIA: Record<CategoriaSuministro, string> = {
  ROLES: 'chip chip--roles',
  GRUPOS: 'chip chip--grupos',
  INBOX: 'chip chip--inbox',
  DEFAULTINBOX: 'chip chip--default',
}

type NuevoAtributoForm = Omit<AtributoRequest, 'rolId' | 'categoria'>

function formInicial(idSuministro: string): NuevoAtributoForm {
  return {
    aplicacionCodigo: '',
    suministroId: idSuministro,
    atributoCodigo: '',
    atributoNombre: '',
    estado: 'ACTIVO',
  }
}

/**
 * Componente de mantenimiento de Atributo con las 4 pestañas Roles/Grupos/
 * Inbox/DefaultInbox — replica las 4 páginas legacy separadas
 * `paginas/suministro/{mantenimiento_atributo,grupos,inbox,defaultinbox}.xhtml`.
 *
 * **Gap de "modificar"/"eliminar" (documentado, no resuelto aquí):**
 * `AtributoResource.java`/`AtributoService.java` NO exponen `PUT`/`DELETE`
 * para Atributo — únicamente `POST` (crear) y las 3 consultas de lectura.
 * No hay acción "Actualizar"/"Eliminar" por fila porque no existe el
 * endpoint que la respalde.
 *
 * **Overhaul de diseño 2026-08-20** (mismo pedido explícito del usuario que
 * `CatalogoMantenimiento.tsx`/`RolMantenimiento.tsx`/
 * `UsuarioMantenimiento.tsx`, ver `../../../handoff.md`): los filtros
 * separados Código/Nombre se unifican en un único campo de búsqueda;
 * Registrar pasa a un `Modal`; toast de éxito/error; tabla paginada de a
 * 10 filas.
 */
export function AtributoTabs() {
  const { idRol, idSuministro } = useParams<{
    idRol: string
    idSuministro: string
  }>()

  const [activeCategoria, setActiveCategoria] =
    useState<CategoriaSuministro>('ROLES')
  const [busqueda, setBusqueda] = useState('')
  const [modalRegistrarAbierto, setModalRegistrarAbierto] = useState(false)
  const [nuevoAtributo, setNuevoAtributo] = useState<NuevoAtributoForm>(() =>
    formInicial(idSuministro ?? ''),
  )
  const { mostrarExito, mostrarError } = useToast()

  // Bug real 2026-08-25 ("la vista de Atributos del Suministro no trae
  // data"): `GET /api/atributos/rol/{idRol}/categoria/{categoria}` arma su
  // key de consulta (`AtributoRepository.listarPorRolYCategoria`) como
  // `APLICACION#<aplicacion>#AREA#<area>` -- `area` lo resuelve el backend
  // solo, pero `aplicacion` viene ÚNICAMENTE del query param opcional de
  // este endpoint (confirmado en `AtributoResource.java`/
  // `AtributoService.java`). Esta pantalla nunca lo mandaba -> la key
  // quedaba `APLICACION#null#AREA#...`, que no matchea ningún dato real
  // (confirmado con `curl` contra el backend real: mismo rol/categoría,
  // `[]` sin el param, filas reales con `?aplicacion=1`) -- SIEMPRE
  // devolvía "Sin resultados", sin importar el Rol/Suministro/categoría.
  // Ni `Rol` ni `Suministro` exponen `aplicacionCodigo` (ver Javadoc de
  // `api/atributos.ts`), así que se resuelve acá pidiendo el catálogo de
  // Aplicaciones (mismo `queryKey` que ya precarga `Layout.tsx`, sin
  // request nuevo en la práctica) y usando la primera -- hoy el sistema
  // real tiene una sola Aplicación ("PORTAL"), así que no hay ambigüedad
  // que resolver con un selector propio.
  const aplicacionesQuery = useQuery({
    queryKey: ['aplicaciones'],
    queryFn: aplicacionesApi.listar,
  })
  const aplicacionCodigo = aplicacionesQuery.data?.[0]?.codigo

  // Un solo hook, re-invocado con la `categoria` de la pestaña activa --
  // NO se llaman las 4 categorías a la vez (acceptance criterio 2: cada
  // pestaña llama a su propio endpoint/filtro sin mezclar datos de otra
  // categoría). TanStack Query cachea cada categoría por separado en su
  // propia `queryKey` (ver `useAtributoTabs.ts`), así que volver a una
  // pestaña ya visitada no repite el fetch pero tampoco mezcla datos.
  const { listQuery, crearMutation } = useAtributoCategoria(
    idRol ?? '',
    activeCategoria,
    aplicacionCodigo,
  )

  if (!idRol || !idSuministro) {
    return (
      <p role="alert">
        Falta el identificador del Rol o del Suministro en la ruta.
      </p>
    )
  }
  // Recapturados como `const` de tipo `string` (no `string | undefined`):
  // TypeScript no propaga el narrowing de `idRol`/`idSuministro` hacia los
  // closures definidos más abajo (`handleCrear`) -- mismo patrón que
  // `SuministroPorRol.tsx`.
  const idRolConfirmado: string = idRol
  const idSuministroConfirmado: string = idSuministro

  const atributosDelSuministro = (listQuery.data ?? []).filter(
    (atributo) => atributo.suministroId === idSuministroConfirmado,
  )
  const termino = busqueda.trim().toLowerCase()
  const atributos = atributosDelSuministro.filter((atributo) => {
    if (termino === '') {
      return true
    }
    return (
      atributo.atributoCodigo.toLowerCase().includes(termino) ||
      atributo.atributoNombre.toLowerCase().includes(termino)
    )
  })
  const { pageItems, page, totalPages, totalItems, pageSize, goToPage } =
    usePagination(atributos)

  function handleCambiarPestana(categoria: CategoriaSuministro) {
    setActiveCategoria(categoria)
  }

  function handleAbrirRegistrar() {
    setNuevoAtributo(formInicial(idSuministroConfirmado))
    setModalRegistrarAbierto(true)
  }

  function handleCrear(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const request: AtributoRequest = {
      ...nuevoAtributo,
      rolId: idRolConfirmado,
      categoria: activeCategoria,
    }
    crearMutation.mutate(request, {
      onSuccess: () => {
        setModalRegistrarAbierto(false)
        setNuevoAtributo(formInicial(idSuministroConfirmado))
        mostrarExito(`Atributo "${request.atributoNombre}" registrado correctamente.`)
      },
      onError: (error) =>
        mostrarError(errorMessage(error, 'No se pudo crear el atributo.')),
    })
  }

  return (
    <section>
      <p>
        <Link to={`/roles/${encodeURIComponent(idRolConfirmado)}/suministros`}>
          &larr; Volver a Suministros
        </Link>
      </p>
      <h2>Atributos del Suministro {idSuministroConfirmado}</h2>

      <div role="tablist" aria-label="Categoría de Atributo">
        {CATEGORIAS_ATRIBUTO.map(({ categoria, label }) => (
          <button
            key={categoria}
            type="button"
            role="tab"
            aria-selected={activeCategoria === categoria}
            onClick={() => handleCambiarPestana(categoria)}
          >
            {label}
          </button>
        ))}
      </div>

      <div className="toolbar">
        <form
          aria-label="Filtrar Atributo"
          onSubmit={(event) => event.preventDefault()}
        >
          <IconSearch className="toolbar__icono-buscar" />
          <label htmlFor="atributo-busqueda" className="sr-only">
            Buscar
          </label>
          <input
            id="atributo-busqueda"
            type="text"
            value={busqueda}
            onChange={(event) => setBusqueda(event.target.value)}
            placeholder="Buscar por código o nombre de atributo..."
          />
        </form>
        <span className="toolbar__resultado">
          {totalItems} de {atributosDelSuministro.length} resultados
        </span>
        <button
          type="button"
          className="icon-button"
          onClick={handleAbrirRegistrar}
          aria-label="Registrar"
          title="Registrar"
        >
          <IconPlus />
        </button>
      </div>

      <div
        role="tabpanel"
        aria-label={`Detalle Atributo ${activeCategoria}`}
      >
        {listQuery.isLoading && <TableSkeleton columns={6} />}
        {listQuery.isError && (
          <p role="alert">
            {errorMessage(listQuery.error, 'No se pudo cargar Atributos.')}
          </p>
        )}

        {listQuery.data && (
          <>
            <table className="data-table">
              <colgroup>
                <col style={{ width: '12%' }} />
                <col style={{ width: '13%' }} />
                <col style={{ width: '13%' }} />
                <col style={{ width: '17%' }} />
                <col style={{ width: '35%' }} />
                <col style={{ width: '10%' }} />
              </colgroup>
              <thead>
                <tr>
                  <th>Codigo</th>
                  <th>Aplicacion</th>
                  <th>Parametro</th>
                  <th>Atributo Codigo</th>
                  <th>Atributo Nombre</th>
                  <th>Estado</th>
                </tr>
              </thead>
              <tbody>
                {pageItems.map((atributo) => (
                  <tr key={atributo.id}>
                    <td>{atributo.id}</td>
                    <td>{atributo.aplicacionCodigo}</td>
                    <td>
                      <span className={CHIP_POR_CATEGORIA[atributo.categoria]}>
                        {atributo.categoria}
                      </span>
                    </td>
                    <td>{atributo.atributoCodigo}</td>
                    <td>{atributo.atributoNombre}</td>
                    <td>{atributo.estado}</td>
                  </tr>
                ))}
                {pageItems.length === 0 && (
                  <tr>
                    <td colSpan={6}>Sin resultados.</td>
                  </tr>
                )}
              </tbody>
            </table>
            <Pagination
              page={page}
              totalPages={totalPages}
              totalItems={totalItems}
              pageSize={pageSize}
              onPageChange={goToPage}
            />
          </>
        )}
      </div>

      {modalRegistrarAbierto && (
        <Modal
          title={`Registrar Atributo ${activeCategoria}`}
          onClose={() => setModalRegistrarAbierto(false)}
        >
          <form
            aria-label={`Crear Atributo ${activeCategoria}`}
            onSubmit={handleCrear}
          >
            <label htmlFor="atributo-nuevo-aplicacion">Aplicación</label>
            <input
              id="atributo-nuevo-aplicacion"
              type="text"
              value={nuevoAtributo.aplicacionCodigo}
              onChange={(event) =>
                setNuevoAtributo({
                  ...nuevoAtributo,
                  aplicacionCodigo: event.target.value,
                })
              }
              required
              autoFocus
            />
            <label htmlFor="atributo-nuevo-codigo">Atributo Código</label>
            <input
              id="atributo-nuevo-codigo"
              type="text"
              value={nuevoAtributo.atributoCodigo}
              onChange={(event) =>
                setNuevoAtributo({
                  ...nuevoAtributo,
                  atributoCodigo: event.target.value,
                })
              }
              required
            />
            <label htmlFor="atributo-nuevo-nombre">Atributo Nombre</label>
            <input
              id="atributo-nuevo-nombre"
              type="text"
              value={nuevoAtributo.atributoNombre}
              onChange={(event) =>
                setNuevoAtributo({
                  ...nuevoAtributo,
                  atributoNombre: event.target.value,
                })
              }
              required
            />
            <label htmlFor="atributo-nuevo-estado">Estado</label>
            <select
              id="atributo-nuevo-estado"
              value={nuevoAtributo.estado}
              onChange={(event) =>
                setNuevoAtributo({
                  ...nuevoAtributo,
                  estado: event.target.value,
                })
              }
            >
              {ESTADOS.map((estado) => (
                <option key={estado} value={estado}>
                  {estado}
                </option>
              ))}
            </select>
            {crearMutation.isError && (
              <p role="alert">
                {errorMessage(
                  crearMutation.error,
                  'No se pudo crear el atributo.',
                )}
              </p>
            )}
            <div className="modal__actions">
              <button
                type="button"
                onClick={() => setModalRegistrarAbierto(false)}
              >
                Cancelar
              </button>
              <button type="submit" disabled={crearMutation.isPending}>
                {crearMutation.isPending ? 'Registrando…' : 'Registrar'}
              </button>
            </div>
          </form>
        </Modal>
      )}
    </section>
  )
}
