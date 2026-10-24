import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { UsuarioMantenimiento } from './UsuarioMantenimiento'
import * as usuariosApi from '../../api/usuarios'
import { ApiError } from '../../api/client'
import type { Usuario } from '../../types/usuario'
import { ToastProvider } from '../../components/ui/ToastProvider'

// Cero HTTP real: se mockea `api/usuarios.ts` por completo (regla dura de
// `docs/conventions.md`).
vi.mock('../../api/usuarios')

function usuario(overrides: Partial<Usuario>): Usuario {
  return {
    id: '1',
    nombres: 'Ana',
    apellidos: 'Gómez',
    email: 'ana.gomez@example.com',
    tipo: 'OPERADOR',
    ...overrides,
  }
}

function renderUsuarioMantenimiento() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })

  return render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <UsuarioMantenimiento />
      </ToastProvider>
    </QueryClientProvider>,
  )
}

describe('UsuarioMantenimiento', () => {
  it('should list the usuarios returned by the mocked API', async () => {
    const usuarios: Usuario[] = [
      usuario({ id: '1', nombres: 'Ana', apellidos: 'Gómez' }),
      usuario({
        id: '2',
        nombres: 'Luis',
        apellidos: 'Pérez',
        tipo: 'ADMINISTRADOR',
      }),
    ]
    vi.mocked(usuariosApi.listar).mockResolvedValue(usuarios)

    renderUsuarioMantenimiento()

    expect(await screen.findByText('Ana')).toBeInTheDocument()
    expect(screen.getByText('Luis')).toBeInTheDocument()
    // "Administrador" también aparece como opción del <select> del
    // formulario de creación — se acota la búsqueda a la tabla de listado.
    const tabla = screen.getByRole('table')
    expect(within(tabla).getByText('Administrador')).toBeInTheDocument()
    expect(usuariosApi.listar).toHaveBeenCalledTimes(1)
  })

  // Acceptance criterio 1 y 3: el listado nunca muestra ni recibe del
  // backend el campo password/hash. Se simula deliberadamente una
  // respuesta "hostil" del backend (un objeto con un campo `password` en
  // runtime, algo que TypeScript no puede impedir si el backend real
  // llegara a filtrarlo) para probar que el componente JAMÁS renderiza ese
  // valor ni ningún campo/etiqueta relacionado a password en la tabla de
  // listado — la garantía viene del código del componente (que nunca lee
  // `usuario.password`), no solo del tipo estático.
  it('should never render any password/hash field in the listing table, even if the mocked API response contains one', async () => {
    const usuarioConPasswordFiltrado = {
      ...usuario({ id: '1', nombres: 'Ana', apellidos: 'Gómez' }),
      // Campo hostil que no debería existir en `UsuarioResponse` real —
      // simula una regresión de backend para probar que el frontend no lo
      // expondría igualmente.
      password: 'SuperSecreta123',
      passwordHash: '$2b$10$hashvaluehashvaluehashvalue',
    } as unknown as Usuario
    vi.mocked(usuariosApi.listar).mockResolvedValue([
      usuarioConPasswordFiltrado,
    ])

    renderUsuarioMantenimiento()
    await screen.findByText('Ana')

    const tabla = screen.getByRole('table')
    // El valor de la contraseña filtrada nunca aparece en la tabla.
    expect(within(tabla).queryByText('SuperSecreta123')).not.toBeInTheDocument()
    expect(
      within(tabla).queryByText('$2b$10$hashvaluehashvaluehashvalue'),
    ).not.toBeInTheDocument()
    // Ninguna columna/celda de la tabla menciona "password"/"contraseña".
    expect(within(tabla).queryByText(/password/i)).not.toBeInTheDocument()
    expect(within(tabla).queryByText(/contraseña/i)).not.toBeInTheDocument()
    // La tabla no tiene ningún input de tipo password (el único existente
    // en la página vive en el formulario de creación, fuera de la tabla).
    expect(tabla.querySelector('input[type="password"]')).toBeNull()
  })

  it('should filter the listed usuarios by a single unified search field on the client side', async () => {
    const usuarios: Usuario[] = [
      usuario({
        id: '1',
        nombres: 'Ana',
        apellidos: 'Gómez',
        email: 'ana@example.com',
        tipo: 'OPERADOR',
      }),
      usuario({
        id: '2',
        nombres: 'Luis',
        apellidos: 'Pérez',
        email: 'luis@example.com',
        tipo: 'ADMINISTRADOR',
      }),
    ]
    vi.mocked(usuariosApi.listar).mockResolvedValue(usuarios)
    const user = userEvent.setup()

    renderUsuarioMantenimiento()
    await screen.findByText('Ana')

    const filtroForm = screen.getByRole('form', { name: /filtrar usuario/i })
    await user.type(within(filtroForm).getByLabelText(/buscar/i), 'Ana')

    await waitFor(() => {
      expect(screen.queryByText('Luis')).not.toBeInTheDocument()
    })
    expect(screen.getByText('Ana')).toBeInTheDocument()
  })

  it('should create a usuario sending the password only inside the mutation request body, and refetch the list', async () => {
    vi.mocked(usuariosApi.listar)
      .mockResolvedValueOnce([])
      .mockResolvedValueOnce([
        usuario({ id: '9', nombres: 'Nuevo', apellidos: 'Usuario' }),
      ])
    vi.mocked(usuariosApi.crear).mockResolvedValue(
      usuario({ id: '9', nombres: 'Nuevo', apellidos: 'Usuario' }),
    )
    const user = userEvent.setup()

    renderUsuarioMantenimiento()
    await waitFor(() => expect(usuariosApi.listar).toHaveBeenCalled())

    await user.click(screen.getByRole('button', { name: 'Registrar' }))
    const crearForm = await screen.findByRole('form', { name: /crear usuario/i })
    await user.type(within(crearForm).getByLabelText(/^nombre$/i), 'Nuevo')
    await user.type(
      within(crearForm).getByLabelText(/^apellido$/i),
      'Usuario',
    )
    await user.type(
      within(crearForm).getByLabelText(/^email$/i),
      'nuevo@example.com',
    )
    const passwordInput = within(crearForm).getByLabelText(/^password$/i)
    expect(passwordInput).toHaveAttribute('type', 'password')
    await user.type(passwordInput, 'ClaveSegura1')
    await user.click(
      within(crearForm).getByRole('button', { name: /registrar/i }),
    )

    await waitFor(() =>
      expect(usuariosApi.crear).toHaveBeenCalledWith({
        nombres: 'Nuevo',
        apellidos: 'Usuario',
        email: 'nuevo@example.com',
        password: 'ClaveSegura1',
        tipo: 'OPERADOR',
      }),
    )
    // El listado se refresca tras crear (invalidación de query).
    expect(await screen.findByText('Nuevo')).toBeInTheDocument()
  })

  // Acceptance criterio 2: la contraseña nunca se loguea en consola. Se
  // espía `console.log`/`console.error`/`console.warn` durante todo el
  // flujo de creación (incluido un fallo de la mutación, el camino más
  // propenso a un `console.error` de depuración) y se verifica que ninguna
  // llamada haya incluido el valor de la contraseña.
  describe('password never logged to console', () => {
    const logSpy = vi.spyOn(console, 'log')
    const errorSpy = vi.spyOn(console, 'error')
    const warnSpy = vi.spyOn(console, 'warn')

    beforeEach(() => {
      logSpy.mockClear()
      errorSpy.mockClear()
      warnSpy.mockClear()
    })

    afterEach(() => {
      logSpy.mockReset()
      errorSpy.mockReset()
      warnSpy.mockReset()
    })

    it('should not log the password anywhere, including when creation fails', async () => {
      vi.mocked(usuariosApi.listar).mockResolvedValue([])
      vi.mocked(usuariosApi.crear).mockRejectedValue(
        new ApiError(409, 'El email ya está registrado'),
      )
      const user = userEvent.setup()
      const CLAVE = 'NuncaEnConsola9'

      renderUsuarioMantenimiento()
      await waitFor(() => expect(usuariosApi.listar).toHaveBeenCalled())

      await user.click(screen.getByRole('button', { name: 'Registrar' }))
      const crearForm = await screen.findByRole('form', {
        name: /crear usuario/i,
      })
      await user.type(within(crearForm).getByLabelText(/^nombre$/i), 'X')
      await user.type(within(crearForm).getByLabelText(/^apellido$/i), 'Y')
      await user.type(
        within(crearForm).getByLabelText(/^email$/i),
        'x@example.com',
      )
      await user.type(
        within(crearForm).getByLabelText(/^password$/i),
        CLAVE,
      )
      await user.click(
        within(crearForm).getByRole('button', { name: /registrar/i }),
      )

      expect(await within(crearForm).findByRole('alert')).toHaveTextContent(
        'El email ya está registrado',
      )

      const todasLasLlamadas = [
        ...logSpy.mock.calls,
        ...errorSpy.mock.calls,
        ...warnSpy.mock.calls,
      ]
      for (const llamada of todasLasLlamadas) {
        for (const argumento of llamada) {
          expect(JSON.stringify(argumento)).not.toContain(CLAVE)
        }
      }
    })
  })

  it('should show an inline error message when deleting a usuario fails', async () => {
    vi.mocked(usuariosApi.listar).mockResolvedValue([
      usuario({ id: '1', nombres: 'Ana', apellidos: 'Gómez' }),
    ])
    vi.mocked(usuariosApi.eliminar).mockRejectedValue(
      new ApiError(500, 'No se pudo eliminar el usuario.'),
    )
    const user = userEvent.setup()

    renderUsuarioMantenimiento()
    await screen.findByText('Ana')

    await user.click(screen.getByRole('button', { name: /eliminar/i }))
    const confirmarModal = await screen.findByRole('dialog', {
      name: 'Eliminar Usuario',
    })
    await user.click(
      within(confirmarModal).getByRole('button', { name: /sí, eliminar/i }),
    )

    expect(
      await within(confirmarModal).findByRole('alert'),
    ).toHaveTextContent('No se pudo eliminar el usuario.')
  })
})
