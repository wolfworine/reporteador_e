import { useState } from 'react'
import type { FormEvent } from 'react'
import type { Catalogo, CatalogoApi } from '../../api/catalogoApi'
import { ApiError } from '../../api/client'
import { useCatalogoMantenimiento } from '../../modules/catalogo/useCatalogoMantenimiento'
import { Modal } from '../ui/Modal'
import { Pagination } from '../ui/Pagination'
import { usePagination } from '../ui/usePagination'
import { useToast } from '../ui/ToastProvider'
import { TableSkeleton } from '../ui/TableSkeleton'
import { IconPencil, IconPlus, IconSearch, IconTrash } from '../ui/icons'

export interface CatalogoMantenimientoProps<T extends Catalogo> {
  /** Título mostrado en el encabezado de la página (p. ej. "Área"). */
  titulo: string
  /** Clave de TanStack Query para el listado (p. ej. `"areas"`). */
  queryKey: string
  /** Cliente CRUD del catálogo (p. ej. `api/areas.ts` importado como `* as areasApi`). */
  api: CatalogoApi<T>
}

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof ApiError ? error.message : fallback
}

/**
 * Página de mantenimiento genérica compartida por los 5 catálogos simples
 * (Área, Cargo, Ubicación, Aplicación, Estado): listar con búsqueda,
 * crear y modificar — replica el alcance funcional de
 * `paginas/{area,cargo,ubicacion,aplicacion}/mantenimiento.xhtml`.
 *
 * **Overhaul de diseño 2026-08-20** (pedido explícito del usuario -- ver
 * `../../../handoff.md`): registrar/actualizar pasan a un `Modal` (antes
 * formularios siempre visibles inline), eliminar pide confirmación en un
 * segundo modal (antes borraba directo sin ningún paso intermedio), las
 * 3 mutaciones muestran un toast de éxito/error además del `role="alert"`
 * inline ya existente, la tabla pagina de a 10 filas (antes rendereizaba
 * TODAS las filas -- crítico para Cargo=695/Atributo=15,565), y los
 * filtros separados de Código/Nombre se unifican en un único campo de
 * búsqueda que matchea contra ambos.
 *
 * Diferencias deliberadas frente al legacy (documentadas en
 * `progress/current.md`):
 * - El filtro `p:dataTable` de PrimeFaces filtraba en servidor sobre la
 *   colección ya cargada en el managed bean `@SessionScoped`; aquí se
 *   filtra en cliente sobre el resultado completo de `useQuery` porque el
 *   backend real (`AreaResource.java` y equivalentes) no expone parámetros
 *   de query para filtrar — no hay endpoint de búsqueda parcial.
 * - El formulario "Nuevo" legacy pedía tanto "Codigo" (código de negocio
 *   corto) como "Area" (nombre); el backend real solo acepta `{nombre}` en
 *   el `POST` (`CatalogoRequest`) — el `codigo` (PK) lo asigna el
 *   servidor, así que el formulario de creación aquí solo pide `nombre`.
 *
 * Manejo de error: cada mutación maneja su propio estado de error y lo
 * muestra inline con `role="alert"` dentro del modal correspondiente
 * (nunca solo en consola, regla dura de `docs/conventions.md`) además del
 * toast.
 */
export function CatalogoMantenimiento<T extends Catalogo>({
  titulo,
  queryKey,
  api,
}: CatalogoMantenimientoProps<T>) {
  const { listQuery, crearMutation, actualizarMutation, eliminarMutation } =
    useCatalogoMantenimiento(queryKey, api)
  const { mostrarExito, mostrarError } = useToast()

  const [busqueda, setBusqueda] = useState('')
  const [nuevoNombre, setNuevoNombre] = useState('')
  const [modalRegistrarAbierto, setModalRegistrarAbierto] = useState(false)
  const [editando, setEditando] = useState<{
    codigo: string
    nombre: string
  } | null>(null)
  const [eliminando, setEliminando] = useState<T | null>(null)

  const items = listQuery.data ?? []
  const termino = busqueda.trim().toLowerCase()
  const itemsFiltrados = items.filter(
    (item) =>
      item.codigo.toLowerCase().includes(termino) ||
      item.nombre.toLowerCase().includes(termino),
  )
  const { pageItems, page, totalPages, totalItems, pageSize, goToPage } =
    usePagination(itemsFiltrados)

  function handleAbrirRegistrar() {
    setNuevoNombre('')
    setModalRegistrarAbierto(true)
  }

  function handleCrear(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const nombre = nuevoNombre.trim()
    if (nombre.length === 0) {
      return
    }
    crearMutation.mutate(
      { nombre },
      {
        onSuccess: () => {
          setModalRegistrarAbierto(false)
          setNuevoNombre('')
          mostrarExito(`${titulo} "${nombre}" registrado correctamente.`)
        },
        onError: (error) =>
          mostrarError(errorMessage(error, `No se pudo crear ${titulo}.`)),
      },
    )
  }

  function handleIniciarEdicion(item: T) {
    setEditando({ codigo: item.codigo, nombre: item.nombre })
  }

  function handleCancelarEdicion() {
    setEditando(null)
  }

  function handleGuardarEdicion(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!editando) {
      return
    }
    const nombre = editando.nombre.trim()
    if (nombre.length === 0) {
      return
    }
    actualizarMutation.mutate(
      { codigo: editando.codigo, request: { nombre } },
      {
        onSuccess: () => {
          setEditando(null)
          mostrarExito(`${titulo} actualizado correctamente.`)
        },
        onError: (error) =>
          mostrarError(
            errorMessage(error, `No se pudo actualizar ${titulo}.`),
          ),
      },
    )
  }

  function handleConfirmarEliminar() {
    if (!eliminando) {
      return
    }
    eliminarMutation.mutate(eliminando.codigo, {
      onSuccess: () => {
        mostrarExito(`${titulo} "${eliminando.nombre}" eliminado.`)
        setEliminando(null)
      },
      onError: (error) =>
        mostrarError(errorMessage(error, `No se pudo eliminar ${titulo}.`)),
    })
  }

  return (
    <section>
      <h2>{titulo}</h2>

      <div className="toolbar">
        <form
          aria-label={`Filtrar ${titulo}`}
          onSubmit={(event) => event.preventDefault()}
        >
          <IconSearch className="toolbar__icono-buscar" />
          <label htmlFor={`${queryKey}-busqueda`} className="sr-only">
            Buscar
          </label>
          <input
            id={`${queryKey}-busqueda`}
            type="text"
            value={busqueda}
            onChange={(event) => setBusqueda(event.target.value)}
            placeholder="Buscar por código o nombre..."
          />
        </form>
        <span className="toolbar__resultado">
          {totalItems} de {items.length} resultados
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

      {listQuery.isLoading && <TableSkeleton columns={3} />}
      {listQuery.isError && (
        <p role="alert">
          {errorMessage(listQuery.error, `No se pudo cargar ${titulo}.`)}
        </p>
      )}

      {listQuery.data && (
        <>
          <table className="data-table">
            <colgroup>
              <col style={{ width: '20%' }} />
              <col style={{ width: '50%' }} />
              <col style={{ width: '30%' }} />
            </colgroup>
            <thead>
              <tr>
                <th>Código</th>
                <th>Nombre</th>
                <th>Acciones</th>
              </tr>
            </thead>
            <tbody>
              {pageItems.map((item) => (
                <tr key={item.codigo}>
                  <td>{item.codigo}</td>
                  <td>{item.nombre}</td>
                  <td>
                    <button
                      type="button"
                      className="icon-button"
                      onClick={() => handleIniciarEdicion(item)}
                      aria-label="Actualizar"
                      title="Actualizar"
                    >
                      <IconPencil />
                    </button>
                    <button
                      type="button"
                      className="icon-button"
                      onClick={() => setEliminando(item)}
                      disabled={
                        eliminarMutation.isPending &&
                        eliminarMutation.variables === item.codigo
                      }
                      aria-label="Eliminar"
                      title="Eliminar"
                    >
                      <IconTrash />
                    </button>
                  </td>
                </tr>
              ))}
              {pageItems.length === 0 && (
                <tr>
                  <td colSpan={3}>Sin resultados.</td>
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

      {modalRegistrarAbierto && (
        <Modal
          title={`Registrar ${titulo}`}
          onClose={() => setModalRegistrarAbierto(false)}
        >
          <form aria-label={`Crear ${titulo}`} onSubmit={handleCrear}>
            <label htmlFor={`${queryKey}-nuevo-nombre`}>Nombre</label>
            <input
              id={`${queryKey}-nuevo-nombre`}
              type="text"
              value={nuevoNombre}
              onChange={(event) => setNuevoNombre(event.target.value)}
              placeholder={`Ingresa ${titulo}...`}
              required
              autoFocus
            />
            {crearMutation.isError && (
              <p role="alert">
                {errorMessage(
                  crearMutation.error,
                  'No se pudo crear el registro.',
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

      {editando && (
        <Modal
          title={`Editar ${titulo}`}
          onClose={handleCancelarEdicion}
        >
          <form
            aria-label={`Editar ${titulo} ${editando.codigo}`}
            onSubmit={handleGuardarEdicion}
          >
            <label htmlFor={`${queryKey}-editar-nombre`}>Nombre</label>
            <input
              id={`${queryKey}-editar-nombre`}
              type="text"
              value={editando.nombre}
              onChange={(event) =>
                setEditando({
                  codigo: editando.codigo,
                  nombre: event.target.value,
                })
              }
              required
              autoFocus
            />
            {actualizarMutation.isError && (
              <p role="alert">
                {errorMessage(
                  actualizarMutation.error,
                  'No se pudo modificar el registro.',
                )}
              </p>
            )}
            <div className="modal__actions">
              <button type="button" onClick={handleCancelarEdicion}>
                Cancelar
              </button>
              <button type="submit" disabled={actualizarMutation.isPending}>
                {actualizarMutation.isPending ? 'Guardando…' : 'Guardar'}
              </button>
            </div>
          </form>
        </Modal>
      )}

      {eliminando && (
        <Modal
          title={`Eliminar ${titulo}`}
          onClose={() => setEliminando(null)}
        >
          <p className="modal__mensaje">
            ¿Seguro que querés eliminar "{eliminando.nombre}"? Esta acción no
            se puede deshacer.
          </p>
          {eliminarMutation.isError && (
            <p role="alert">
              {errorMessage(
                eliminarMutation.error,
                'No se pudo eliminar el registro.',
              )}
            </p>
          )}
          <div className="modal__actions">
            <button type="button" onClick={() => setEliminando(null)}>
              Cancelar
            </button>
            <button
              type="button"
              onClick={handleConfirmarEliminar}
              disabled={eliminarMutation.isPending}
            >
              {eliminarMutation.isPending ? 'Eliminando…' : 'Sí, eliminar'}
            </button>
          </div>
        </Modal>
      )}
    </section>
  )
}
