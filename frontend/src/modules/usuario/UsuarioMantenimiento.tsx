import { useState } from 'react'
import type { FormEvent } from 'react'
import type { CrearUsuarioRequest } from '../../api/usuarios'
import type { TipoUsuario, Usuario } from '../../types/usuario'
import { ApiError } from '../../api/client'
import { useUsuarioMantenimiento } from './useUsuarioMantenimiento'
import { Modal } from '../../components/ui/Modal'
import { Pagination } from '../../components/ui/Pagination'
import { usePagination } from '../../components/ui/usePagination'
import { useToast } from '../../components/ui/ToastProvider'
import { TableSkeleton } from '../../components/ui/TableSkeleton'
import { IconPlus, IconSearch, IconTrash } from '../../components/ui/icons'

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof ApiError ? error.message : fallback
}

/** Paridad con `domain/TipoUsuario.java` + `NombreTipo(String)` del legacy (`e_tipo`: "A" = Administrador, otro = Operador). */
const TIPOS: { valor: TipoUsuario; label: string }[] = [
  { valor: 'ADMINISTRADOR', label: 'Administrador' },
  { valor: 'OPERADOR', label: 'Operador' },
]

function nombreTipo(tipo: TipoUsuario): string {
  return TIPOS.find((t) => t.valor === tipo)?.label ?? tipo
}

/**
 * Clase de chip para Tipo -- mismo tema de color del Reporteador
 * (`--cat-roles/inbox`, `ReporteTabla.tsx`), reutilizado acá (pedido
 * explícito del usuario 2026-08-26: "establece colores tambien en usuario
 * y tipo administrador o operador"; Operador en verde claro, pedido
 * explícito de seguimiento -- `--cat-inbox` es el verde ya usado para
 * "Local" en `RolMantenimiento.tsx`).
 */
function chipClaseTipoUsuario(tipo: TipoUsuario): string {
  return tipo === 'ADMINISTRADOR' ? 'chip chip--roles' : 'chip chip--inbox'
}

const USUARIO_FORM_INICIAL: CrearUsuarioRequest = {
  nombres: '',
  apellidos: '',
  email: '',
  password: '',
  tipo: 'OPERADOR',
}

/**
 * Página de mantenimiento de Usuario — replica
 * `paginas/usuario/mantenimiento.xhtml` (tabla con columnas Nombre/
 * Apellido/Email/Tipo, cada una con `filterBy`, botones Nuevo/Eliminar/
 * Actualizar) + `paginas/usuario/editar.xhtml` (formulario de creación,
 * bean `usuarioManagedBean.registrar`: campos Nombre/Apellido/Email/
 * Password/Tipo, todos requeridos, select Administrador/Operador).
 * Ambas vistas legacy confirmadas leyendo el archivo fuente directamente
 * antes de implementar.
 *
 * **Decisión de alcance — sin UI de edición (gap real de backend,
 * documentado también en `progress/current.md`):** el backend real
 * (`UsuarioResource.java`) no expone `PUT /api/usuarios/{id}` — no se
 * inventa un endpoint de edición que no existe.
 *
 * **Overhaul de diseño 2026-08-20** (mismo pedido explícito del usuario que
 * `CatalogoMantenimiento.tsx`/`RolMantenimiento.tsx`, ver
 * `../../../handoff.md`): los 4 filtros separados (Nombre/Apellido/Email/
 * Tipo) se unifican en un único campo de búsqueda; Registrar pasa a un
 * `Modal`; eliminar pide confirmación; toast de éxito/error; tabla
 * paginada de a 10 filas.
 *
 * **Seguridad de la contraseña (sin cambios frente a la versión anterior,
 * ver acceptance criterios 1 y 2 de la feature original):** el listado
 * nunca tiene un campo password/hash, el input usa `type="password"`, y
 * viaja únicamente dentro del body HTTPS de `crearMutation.mutate`.
 */
export function UsuarioMantenimiento() {
  const { listQuery, crearMutation, eliminarMutation } =
    useUsuarioMantenimiento()
  const { mostrarExito, mostrarError } = useToast()

  const [busqueda, setBusqueda] = useState('')
  const [nuevoUsuario, setNuevoUsuario] = useState<CrearUsuarioRequest>(
    USUARIO_FORM_INICIAL,
  )
  const [modalRegistrarAbierto, setModalRegistrarAbierto] = useState(false)
  const [eliminando, setEliminando] = useState<Usuario | null>(null)

  const usuarios = listQuery.data ?? []
  const termino = busqueda.trim().toLowerCase()
  const usuariosFiltrados = usuarios.filter((usuario) => {
    if (termino === '') {
      return true
    }
    return [
      usuario.nombres,
      usuario.apellidos,
      usuario.email,
      nombreTipo(usuario.tipo),
    ]
      .join(' ')
      .toLowerCase()
      .includes(termino)
  })
  const { pageItems, page, totalPages, totalItems, pageSize, goToPage } =
    usePagination(usuariosFiltrados)

  function handleAbrirRegistrar() {
    setNuevoUsuario(USUARIO_FORM_INICIAL)
    setModalRegistrarAbierto(true)
  }

  function handleCrear(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    crearMutation.mutate(nuevoUsuario, {
      onSuccess: () => {
        setModalRegistrarAbierto(false)
        mostrarExito(`Usuario "${nuevoUsuario.nombres}" registrado correctamente.`)
        setNuevoUsuario(USUARIO_FORM_INICIAL)
      },
      onError: (error) =>
        mostrarError(errorMessage(error, 'No se pudo crear el usuario.')),
    })
  }

  function handleConfirmarEliminar() {
    if (!eliminando) {
      return
    }
    eliminarMutation.mutate(eliminando.id, {
      onSuccess: () => {
        mostrarExito(`Usuario "${eliminando.nombres}" eliminado.`)
        setEliminando(null)
      },
      onError: (error) =>
        mostrarError(errorMessage(error, 'No se pudo eliminar el usuario.')),
    })
  }

  return (
    <section>
      <h2>Usuario</h2>

      <div className="toolbar">
        <form
          aria-label="Filtrar Usuario"
          onSubmit={(event) => event.preventDefault()}
        >
          <IconSearch className="toolbar__icono-buscar" />
          <label htmlFor="usuario-busqueda" className="sr-only">
            Buscar
          </label>
          <input
            id="usuario-busqueda"
            type="text"
            value={busqueda}
            onChange={(event) => setBusqueda(event.target.value)}
            placeholder="Buscar por nombre, apellido, email o tipo..."
          />
        </form>
        <span className="toolbar__resultado">
          {totalItems} de {usuarios.length} resultados
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
          {errorMessage(listQuery.error, 'No se pudo cargar Usuario.')}
        </p>
      )}

      {listQuery.data && (
        <>
          <table className="data-table">
            <colgroup>
              <col style={{ width: '18%' }} />
              <col style={{ width: '18%' }} />
              <col style={{ width: '32%' }} />
              <col style={{ width: '14%' }} />
              <col style={{ width: '18%' }} />
            </colgroup>
            <thead>
              <tr>
                <th>Nombre</th>
                <th>Apellido</th>
                <th>Email</th>
                <th>Tipo</th>
                <th>Acciones</th>
              </tr>
            </thead>
            <tbody>
              {pageItems.map((usuario) => (
                <tr key={usuario.id}>
                  <td>{usuario.nombres}</td>
                  <td>{usuario.apellidos}</td>
                  <td>{usuario.email}</td>
                  <td>
                    <span className={chipClaseTipoUsuario(usuario.tipo)}>
                      {nombreTipo(usuario.tipo)}
                    </span>
                  </td>
                  <td>
                    <button
                      type="button"
                      className="icon-button"
                      onClick={() => setEliminando(usuario)}
                      disabled={
                        eliminarMutation.isPending &&
                        eliminarMutation.variables === usuario.id
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
          title="Registrar Usuario"
          onClose={() => setModalRegistrarAbierto(false)}
        >
          <form aria-label="Crear Usuario" onSubmit={handleCrear}>
            <label htmlFor="usuario-nuevo-nombres">Nombre</label>
            <input
              id="usuario-nuevo-nombres"
              type="text"
              value={nuevoUsuario.nombres}
              onChange={(event) =>
                setNuevoUsuario({
                  ...nuevoUsuario,
                  nombres: event.target.value,
                })
              }
              placeholder="Ingresa Nombre..."
              required
              autoFocus
            />

            <label htmlFor="usuario-nuevo-apellidos">Apellido</label>
            <input
              id="usuario-nuevo-apellidos"
              type="text"
              value={nuevoUsuario.apellidos}
              onChange={(event) =>
                setNuevoUsuario({
                  ...nuevoUsuario,
                  apellidos: event.target.value,
                })
              }
              placeholder="Ingresa Apellido..."
              required
            />

            <label htmlFor="usuario-nuevo-email">Email</label>
            <input
              id="usuario-nuevo-email"
              type="email"
              value={nuevoUsuario.email}
              onChange={(event) =>
                setNuevoUsuario({ ...nuevoUsuario, email: event.target.value })
              }
              placeholder="Ingresa Email"
              required
            />

            <label htmlFor="usuario-nuevo-password">Password</label>
            <input
              id="usuario-nuevo-password"
              type="password"
              value={nuevoUsuario.password}
              onChange={(event) =>
                setNuevoUsuario({
                  ...nuevoUsuario,
                  password: event.target.value,
                })
              }
              placeholder="Ingresa password"
              autoComplete="new-password"
              required
            />

            <label htmlFor="usuario-nuevo-tipo">Tipo</label>
            <select
              id="usuario-nuevo-tipo"
              value={nuevoUsuario.tipo}
              onChange={(event) =>
                setNuevoUsuario({
                  ...nuevoUsuario,
                  tipo: event.target.value as TipoUsuario,
                })
              }
            >
              {TIPOS.map(({ valor, label }) => (
                <option key={valor} value={valor}>
                  {label}
                </option>
              ))}
            </select>

            {crearMutation.isError && (
              <p role="alert">
                {errorMessage(
                  crearMutation.error,
                  'No se pudo crear el usuario.',
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

      {eliminando && (
        <Modal title="Eliminar Usuario" onClose={() => setEliminando(null)}>
          <p className="modal__mensaje">
            ¿Seguro que querés eliminar a "{eliminando.nombres}{' '}
            {eliminando.apellidos}"? Esta acción no se puede deshacer.
          </p>
          {eliminarMutation.isError && (
            <p role="alert">
              {errorMessage(
                eliminarMutation.error,
                'No se pudo eliminar el usuario.',
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
