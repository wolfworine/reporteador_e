import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import type { SuministroRequest } from '../../api/suministros'
import type { Suministro } from '../../types/suministro'
import { ApiError } from '../../api/client'
import { useSuministroMantenimiento } from './useSuministroMantenimiento'
import { Modal } from '../../components/ui/Modal'
import { Pagination } from '../../components/ui/Pagination'
import { usePagination } from '../../components/ui/usePagination'
import { useToast } from '../../components/ui/ToastProvider'
import { TableSkeleton } from '../../components/ui/TableSkeleton'
import { IconEye, IconPencil, IconPlus, IconTrash } from '../../components/ui/icons'

const SUMINISTRO_FORM_INICIAL: SuministroRequest = { nombre: '', prioridad: '' }

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof ApiError ? error.message : fallback
}

/**
 * Suministros de un Rol específico (route param `idRol`, drill-down desde
 * la acción "Ver Suministros" de `RolMantenimiento`) — replica
 * `paginas/rol/mantenimiento_suministro.xhtml` (botones Nuevo/Eliminar/
 * Actualizar/VerAtributo, columnas Codigo/Rol/Nombre/Prioridad).
 *
 * El filtrado por rol lo hace el propio backend en la URL
 * (`GET /api/roles/{idRol}/suministros`, `SuministroResource.java`
 * confirmado en el código fuente) — no hay filtrado adicional en cliente
 * para esta parte (acceptance criterio 1 de la feature), por eso esta
 * pantalla no tiene un campo de búsqueda propio como las demás.
 *
 * **Overhaul de diseño 2026-08-20** (mismo pedido explícito del usuario que
 * `CatalogoMantenimiento.tsx`/`RolMantenimiento.tsx`/
 * `UsuarioMantenimiento.tsx`/`AtributoTabs.tsx`, ver `../../../handoff.md`):
 * Registrar/Actualizar pasan a un `Modal`; eliminar pide confirmación;
 * toast de éxito/error en las 3 mutaciones; tabla paginada de a 10 filas.
 */
export function SuministroPorRol() {
  const { idRol } = useParams<{ idRol: string }>()
  const navigate = useNavigate()
  const { listQuery, crearMutation, actualizarMutation, eliminarMutation } =
    useSuministroMantenimiento(idRol ?? '')
  const { mostrarExito, mostrarError } = useToast()

  const [nuevoSuministro, setNuevoSuministro] = useState<SuministroRequest>(
    SUMINISTRO_FORM_INICIAL,
  )
  const [modalRegistrarAbierto, setModalRegistrarAbierto] = useState(false)
  const [editando, setEditando] = useState<{
    id: string
    valores: SuministroRequest
  } | null>(null)
  const [eliminando, setEliminando] = useState<Suministro | null>(null)

  if (!idRol) {
    return <p role="alert">Falta el identificador del Rol en la ruta.</p>
  }
  // Recapturado como `const` de tipo `string` (no `string | undefined`):
  // TypeScript no propaga el narrowing de `idRol` hacia los closures
  // definidos más abajo (`handleVerAtributo`), así que se recaptura aquí
  // una sola vez tras la guarda.
  const idRolConfirmado: string = idRol

  const suministros = listQuery.data ?? []
  const { pageItems, page, totalPages, totalItems, pageSize, goToPage } =
    usePagination(suministros)

  function handleAbrirRegistrar() {
    setNuevoSuministro(SUMINISTRO_FORM_INICIAL)
    setModalRegistrarAbierto(true)
  }

  function handleCrear(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    crearMutation.mutate(nuevoSuministro, {
      onSuccess: () => {
        setModalRegistrarAbierto(false)
        mostrarExito(`Suministro "${nuevoSuministro.nombre}" registrado correctamente.`)
        setNuevoSuministro(SUMINISTRO_FORM_INICIAL)
      },
      onError: (error) =>
        mostrarError(errorMessage(error, 'No se pudo crear el suministro.')),
    })
  }

  function handleIniciarEdicion(suministro: Suministro) {
    setEditando({
      id: suministro.id,
      valores: { nombre: suministro.nombre, prioridad: suministro.prioridad },
    })
  }

  function handleCancelarEdicion() {
    setEditando(null)
  }

  function handleGuardarEdicion(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!editando) {
      return
    }
    actualizarMutation.mutate(
      { idSuministro: editando.id, request: editando.valores },
      {
        onSuccess: () => {
          setEditando(null)
          mostrarExito('Suministro actualizado correctamente.')
        },
        onError: (error) =>
          mostrarError(
            errorMessage(error, 'No se pudo modificar el suministro.'),
          ),
      },
    )
  }

  function handleConfirmarEliminar() {
    if (!eliminando) {
      return
    }
    eliminarMutation.mutate(eliminando.id, {
      onSuccess: () => {
        mostrarExito(`Suministro "${eliminando.nombre}" eliminado.`)
        setEliminando(null)
      },
      onError: (error) =>
        mostrarError(
          errorMessage(error, 'No se pudo eliminar el suministro.'),
        ),
    })
  }

  function handleVerAtributo(suministro: Suministro) {
    navigate(
      `/roles/${encodeURIComponent(idRolConfirmado)}/suministros/${encodeURIComponent(suministro.id)}/atributos`,
    )
  }

  return (
    <section>
      <p>
        <Link to="/roles">&larr; Volver a Rol</Link>
      </p>
      <h2>Suministros del Rol {idRol}</h2>

      <div className="toolbar">
        <span className="toolbar__resultado" style={{ marginRight: 'auto' }}>
          {suministros.length} resultados
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

      {listQuery.isLoading && <TableSkeleton columns={5} />}
      {listQuery.isError && (
        <p role="alert">
          {errorMessage(listQuery.error, 'No se pudo cargar Suministros.')}
        </p>
      )}

      {listQuery.data && (
        <>
          <table className="data-table">
            <colgroup>
              <col style={{ width: '12%' }} />
              <col style={{ width: '14%' }} />
              <col style={{ width: '22%' }} />
              <col style={{ width: '14%' }} />
              <col style={{ width: '38%' }} />
            </colgroup>
            <thead>
              <tr>
                <th>Código</th>
                <th>Rol</th>
                <th>Nombre</th>
                <th>Prioridad</th>
                <th>Acciones</th>
              </tr>
            </thead>
            <tbody>
              {pageItems.map((suministro) => (
                <tr key={suministro.id}>
                  <td>{suministro.id}</td>
                  <td>{suministro.rolId}</td>
                  <td>{suministro.nombre}</td>
                  <td>{suministro.prioridad}</td>
                  <td className="acciones-cell">
                    <button
                      type="button"
                      className="icon-button"
                      onClick={() => handleIniciarEdicion(suministro)}
                      aria-label="Actualizar"
                      title="Actualizar"
                    >
                      <IconPencil />
                    </button>
                    <button
                      type="button"
                      className="icon-button"
                      onClick={() => setEliminando(suministro)}
                      disabled={
                        eliminarMutation.isPending &&
                        eliminarMutation.variables === suministro.id
                      }
                      aria-label="Eliminar"
                      title="Eliminar"
                    >
                      <IconTrash />
                    </button>
                    <button
                      type="button"
                      className="icon-button"
                      onClick={() => handleVerAtributo(suministro)}
                      aria-label="Ver Atributo"
                      title="Ver Atributo"
                    >
                      <IconEye />
                    </button>
                  </td>
                </tr>
              ))}
              {pageItems.length === 0 && (
                <tr>
                  <td colSpan={5}>Sin resultados.</td>
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
          title="Registrar Suministro"
          onClose={() => setModalRegistrarAbierto(false)}
        >
          <form aria-label="Crear Suministro" onSubmit={handleCrear}>
            <label htmlFor="suministro-nuevo-nombre">Nombre</label>
            <input
              id="suministro-nuevo-nombre"
              type="text"
              value={nuevoSuministro.nombre}
              onChange={(event) =>
                setNuevoSuministro({
                  ...nuevoSuministro,
                  nombre: event.target.value,
                })
              }
              required
              autoFocus
            />
            <label htmlFor="suministro-nuevo-prioridad">Prioridad</label>
            <input
              id="suministro-nuevo-prioridad"
              type="text"
              value={nuevoSuministro.prioridad}
              onChange={(event) =>
                setNuevoSuministro({
                  ...nuevoSuministro,
                  prioridad: event.target.value,
                })
              }
              required
            />
            {crearMutation.isError && (
              <p role="alert">
                {errorMessage(
                  crearMutation.error,
                  'No se pudo crear el suministro.',
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
        <Modal title="Editar Suministro" onClose={handleCancelarEdicion}>
          <form
            aria-label={`Editar Suministro ${editando.id}`}
            onSubmit={handleGuardarEdicion}
          >
            <label htmlFor="suministro-editar-nombre">Nombre</label>
            <input
              id="suministro-editar-nombre"
              type="text"
              value={editando.valores.nombre}
              onChange={(event) =>
                setEditando({
                  id: editando.id,
                  valores: { ...editando.valores, nombre: event.target.value },
                })
              }
              required
              autoFocus
            />
            <label htmlFor="suministro-editar-prioridad">Prioridad</label>
            <input
              id="suministro-editar-prioridad"
              type="text"
              value={editando.valores.prioridad}
              onChange={(event) =>
                setEditando({
                  id: editando.id,
                  valores: {
                    ...editando.valores,
                    prioridad: event.target.value,
                  },
                })
              }
              required
            />
            {actualizarMutation.isError && (
              <p role="alert">
                {errorMessage(
                  actualizarMutation.error,
                  'No se pudo modificar el suministro.',
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
        <Modal title="Eliminar Suministro" onClose={() => setEliminando(null)}>
          <p className="modal__mensaje">
            ¿Seguro que querés eliminar el suministro "{eliminando.nombre}"?
            Esta acción no se puede deshacer.
          </p>
          {eliminarMutation.isError && (
            <p role="alert">
              {errorMessage(
                eliminarMutation.error,
                'No se pudo eliminar el suministro.',
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
