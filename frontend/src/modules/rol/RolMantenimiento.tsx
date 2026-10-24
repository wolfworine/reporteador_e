import { useState } from 'react'
import type { FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import type { RolRequest } from '../../api/roles'
import type { Rol } from '../../types/rol'
import { ApiError } from '../../api/client'
import { useRolMantenimiento } from './useRolMantenimiento'
import { Modal } from '../../components/ui/Modal'
import { Pagination } from '../../components/ui/Pagination'
import { usePagination } from '../../components/ui/usePagination'
import { useToast } from '../../components/ui/ToastProvider'
import { TableSkeleton } from '../../components/ui/TableSkeleton'
import {
  IconEye,
  IconPencil,
  IconPlus,
  IconSearch,
  IconTrash,
} from '../../components/ui/icons'

/** Campos de `RolRequest` renderizados como inputs de texto en los formularios de crear/editar. */
const ROL_CAMPOS: { key: keyof RolRequest; label: string }[] = [
  { key: 'ubicacionCodigo', label: 'Ubicación' },
  { key: 'areaCodigo', label: 'Área' },
  { key: 'cargoCodigo', label: 'Cargo' },
  { key: 'nombre', label: 'Nombre' },
  { key: 'descripcion', label: 'Descripción' },
  { key: 'tipo', label: 'Tipo' },
  { key: 'reglaNegocio', label: 'Regla de negocio' },
  { key: 'ambito', label: 'Ámbito' },
  { key: 'propietarioRol', label: 'Propietario (rol)' },
  { key: 'propietarioPersona', label: 'Propietario (persona)' },
  { key: 'situacion', label: 'Situación' },
  { key: 'rolBase', label: 'Rol base' },
]

const ROL_FORM_INICIAL: RolRequest = {
  ubicacionCodigo: '',
  areaCodigo: '',
  cargoCodigo: '',
  nombre: '',
  descripcion: '',
  tipo: '',
  reglaNegocio: '',
  ambito: '',
  propietarioRol: '',
  propietarioPersona: '',
  situacion: '',
  rolBase: '',
}

function aRolRequest(rol: Rol): RolRequest {
  return {
    ubicacionCodigo: rol.ubicacionCodigo,
    areaCodigo: rol.areaCodigo,
    cargoCodigo: rol.cargoCodigo,
    nombre: rol.nombre,
    descripcion: rol.descripcion,
    tipo: rol.tipo,
    reglaNegocio: rol.reglaNegocio,
    ambito: rol.ambito,
    propietarioRol: rol.propietarioRol,
    propietarioPersona: rol.propietarioPersona,
    situacion: rol.situacion,
    rolBase: rol.rolBase,
  }
}

/**
 * Clase de chip para Tipo/Ámbito -- mismo tema de color de categorías del
 * Reporteador (`--cat-roles/grupos/inbox/default`, `ReporteTabla.tsx`),
 * reutilizado acá (pedido explícito del usuario 2026-08-26: "no cambiaste
 * los colores tipo estatico dinamico y en ambito Local Subunidad"). Un
 * valor no reconocido cae en `.chip` sin color -- no hay 5to tono
 * definido, mejor neutro que inventar uno nuevo.
 */
function chipClaseTipo(tipo: string): string {
  switch (tipo.trim().toUpperCase()) {
    case 'ESTATICO':
    case 'ESTÁTICO':
      return 'chip chip--roles'
    case 'DINAMICO':
    case 'DINÁMICO':
      return 'chip chip--grupos'
    default:
      return 'chip'
  }
}

function chipClaseAmbito(ambito: string): string {
  switch (ambito.trim().toUpperCase()) {
    case 'LOCAL':
      return 'chip chip--inbox'
    case 'SUBUNIDAD':
      return 'chip chip--default'
    default:
      return 'chip'
  }
}

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof ApiError ? error.message : fallback
}

/**
 * Campos de un formulario de Rol (crear o editar), generados a partir de
 * `ROL_CAMPOS` para no repetir 12 `<input>` a mano dos veces.
 */
function CamposRol({
  idPrefix,
  valores,
  onChange,
}: {
  idPrefix: string
  valores: RolRequest
  onChange: (valores: RolRequest) => void
}) {
  return (
    <>
      {ROL_CAMPOS.map(({ key, label }) => (
        <div key={key}>
          <label htmlFor={`${idPrefix}-${key}`}>{label}</label>
          <input
            id={`${idPrefix}-${key}`}
            type="text"
            value={valores[key]}
            onChange={(event) =>
              onChange({ ...valores, [key]: event.target.value })
            }
            required
          />
        </div>
      ))}
    </>
  )
}

/**
 * Página de mantenimiento de Rol: listar con búsqueda, crear, modificar y
 * la acción "Ver Suministros" que navega al drill-down de Suministro del
 * rol seleccionado — replica `paginas/rol/mantenimiento.xhtml` (botones
 * Nuevo/Eliminar/Actualizar/verSuministro, columnas Codigo/Nombre/Area/
 * Cargo/Tipo/Ambito con `filterBy`).
 *
 * **Overhaul de diseño 2026-08-20** (mismo pedido explícito del usuario que
 * `CatalogoMantenimiento.tsx`, ver `../../../handoff.md`): los 4 filtros
 * separados (Área/Cargo/Tipo/Ámbito) se unifican en un único campo de
 * búsqueda que matchea contra los 4 a la vez; Registrar/Actualizar pasan a
 * un `Modal`; eliminar pide confirmación; toast de éxito/error en las 3
 * mutaciones; tabla paginada de a 10 filas.
 *
 * El filtro sigue haciéndose en cliente sobre el resultado completo de
 * `GET /api/roles` porque `RolResource.java` no expone parámetros de query
 * para filtrar (`listar()` no toma argumentos) — confirmado leyendo el
 * archivo real antes de implementar.
 */
export function RolMantenimiento() {
  const navigate = useNavigate()
  const { listQuery, crearMutation, actualizarMutation, eliminarMutation } =
    useRolMantenimiento()
  const { mostrarExito, mostrarError } = useToast()

  const [busqueda, setBusqueda] = useState('')
  const [nuevoRol, setNuevoRol] = useState<RolRequest>(ROL_FORM_INICIAL)
  const [modalRegistrarAbierto, setModalRegistrarAbierto] = useState(false)
  const [editando, setEditando] = useState<{
    id: string
    valores: RolRequest
  } | null>(null)
  const [eliminando, setEliminando] = useState<Rol | null>(null)

  const roles = listQuery.data ?? []
  const termino = busqueda.trim().toLowerCase()
  const rolesFiltrados = roles.filter((rol) => {
    if (termino === '') {
      return true
    }
    return [rol.nombre, rol.areaCodigo, rol.cargoCodigo, rol.tipo, rol.ambito]
      .join(' ')
      .toLowerCase()
      .includes(termino)
  })
  const { pageItems, page, totalPages, totalItems, pageSize, goToPage } =
    usePagination(rolesFiltrados)

  function handleAbrirRegistrar() {
    setNuevoRol(ROL_FORM_INICIAL)
    setModalRegistrarAbierto(true)
  }

  function handleCrear(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    crearMutation.mutate(nuevoRol, {
      onSuccess: () => {
        setModalRegistrarAbierto(false)
        setNuevoRol(ROL_FORM_INICIAL)
        mostrarExito(`Rol "${nuevoRol.nombre}" registrado correctamente.`)
      },
      onError: (error) =>
        mostrarError(errorMessage(error, 'No se pudo crear el rol.')),
    })
  }

  function handleIniciarEdicion(rol: Rol) {
    setEditando({ id: rol.id, valores: aRolRequest(rol) })
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
      { id: editando.id, request: editando.valores },
      {
        onSuccess: () => {
          setEditando(null)
          mostrarExito('Rol actualizado correctamente.')
        },
        onError: (error) =>
          mostrarError(errorMessage(error, 'No se pudo modificar el rol.')),
      },
    )
  }

  function handleConfirmarEliminar() {
    if (!eliminando) {
      return
    }
    eliminarMutation.mutate(eliminando.id, {
      onSuccess: () => {
        mostrarExito(`Rol "${eliminando.nombre}" eliminado.`)
        setEliminando(null)
      },
      onError: (error) =>
        mostrarError(errorMessage(error, 'No se pudo eliminar el rol.')),
    })
  }

  function handleVerSuministros(rol: Rol) {
    navigate(`/roles/${encodeURIComponent(rol.id)}/suministros`)
  }

  return (
    <section>
      <h2>Rol</h2>

      <div className="toolbar">
        <form
          aria-label="Filtrar Rol"
          onSubmit={(event) => event.preventDefault()}
        >
          <IconSearch className="toolbar__icono-buscar" />
          <label htmlFor="rol-busqueda" className="sr-only">
            Buscar
          </label>
          <input
            id="rol-busqueda"
            type="text"
            value={busqueda}
            onChange={(event) => setBusqueda(event.target.value)}
            placeholder="Buscar por nombre, área, cargo, tipo o ámbito..."
          />
        </form>
        <span className="toolbar__resultado">
          {totalItems} de {roles.length} resultados
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

      {listQuery.isLoading && <TableSkeleton columns={7} />}
      {listQuery.isError && (
        <p role="alert">
          {errorMessage(listQuery.error, 'No se pudo cargar Rol.')}
        </p>
      )}

      {listQuery.data && (
        <>
          <table className="data-table">
            <colgroup>
              <col style={{ width: '8%' }} />
              <col style={{ width: '18%' }} />
              <col style={{ width: '11%' }} />
              <col style={{ width: '11%' }} />
              <col style={{ width: '9%' }} />
              <col style={{ width: '14%' }} />
              <col style={{ width: '29%' }} />
            </colgroup>
            <thead>
              <tr>
                <th>Código</th>
                <th>Nombre</th>
                <th>Área</th>
                <th>Cargo</th>
                <th>Tipo</th>
                <th>Ámbito</th>
                <th>Acciones</th>
              </tr>
            </thead>
            <tbody>
              {pageItems.map((rol) => (
                <tr key={rol.id}>
                  <td>{rol.id}</td>
                  <td>{rol.nombre}</td>
                  <td>{rol.areaCodigo}</td>
                  <td>{rol.cargoCodigo}</td>
                  <td>
                    <span className={chipClaseTipo(rol.tipo)}>{rol.tipo}</span>
                  </td>
                  <td>
                    <span className={chipClaseAmbito(rol.ambito)}>
                      {rol.ambito}
                    </span>
                  </td>
                  <td className="acciones-cell">
                    <button
                      type="button"
                      className="icon-button"
                      onClick={() => handleIniciarEdicion(rol)}
                      aria-label="Actualizar"
                      title="Actualizar"
                    >
                      <IconPencil />
                    </button>
                    <button
                      type="button"
                      className="icon-button"
                      onClick={() => setEliminando(rol)}
                      disabled={
                        eliminarMutation.isPending &&
                        eliminarMutation.variables === rol.id
                      }
                      aria-label="Eliminar"
                      title="Eliminar"
                    >
                      <IconTrash />
                    </button>
                    <button
                      type="button"
                      className="icon-button"
                      onClick={() => handleVerSuministros(rol)}
                      aria-label="Ver Suministros"
                      title="Ver Suministros"
                    >
                      <IconEye />
                    </button>
                  </td>
                </tr>
              ))}
              {pageItems.length === 0 && (
                <tr>
                  <td colSpan={7}>Sin resultados.</td>
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
        <Modal title="Registrar Rol" onClose={() => setModalRegistrarAbierto(false)}>
          <form aria-label="Crear Rol" onSubmit={handleCrear}>
            <CamposRol
              idPrefix="rol-nuevo"
              valores={nuevoRol}
              onChange={setNuevoRol}
            />
            {crearMutation.isError && (
              <p role="alert">
                {errorMessage(crearMutation.error, 'No se pudo crear el rol.')}
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
        <Modal title="Editar Rol" onClose={handleCancelarEdicion}>
          <form
            aria-label={`Editar Rol ${editando.id}`}
            onSubmit={handleGuardarEdicion}
          >
            <CamposRol
              idPrefix={`rol-editar-${editando.id}`}
              valores={editando.valores}
              onChange={(valores) => setEditando({ id: editando.id, valores })}
            />
            {actualizarMutation.isError && (
              <p role="alert">
                {errorMessage(
                  actualizarMutation.error,
                  'No se pudo modificar el rol.',
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
        <Modal title="Eliminar Rol" onClose={() => setEliminando(null)}>
          <p className="modal__mensaje">
            ¿Seguro que querés eliminar el rol "{eliminando.nombre}"? Esta
            acción no se puede deshacer.
          </p>
          {eliminarMutation.isError && (
            <p role="alert">
              {errorMessage(
                eliminarMutation.error,
                'No se pudo eliminar el rol.',
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
