import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { RolMantenimiento } from './RolMantenimiento'
import * as rolesApi from '../../api/roles'
import { ApiError } from '../../api/client'
import type { Rol } from '../../types/rol'
import { ToastProvider } from '../../components/ui/ToastProvider'

// Cero HTTP real: se mockea `api/roles.ts` por completo (regla dura de
// `docs/conventions.md`).
vi.mock('../../api/roles')

function rol(overrides: Partial<Rol>): Rol {
  return {
    id: '1',
    ubicacionCodigo: 'U1',
    areaCodigo: 'A1',
    cargoCodigo: 'C1',
    nombre: 'Administrador',
    descripcion: 'Rol de administración',
    tipo: 'FIJO',
    reglaNegocio: '',
    ambito: 'GLOBAL',
    propietarioRol: 'X',
    propietarioPersona: 'Y',
    situacion: 'ACTIVO',
    rolBase: 'NO',
    ...overrides,
  }
}

function renderRolMantenimiento() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })

  return render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <MemoryRouter initialEntries={['/roles']}>
          <RolMantenimiento />
        </MemoryRouter>
      </ToastProvider>
    </QueryClientProvider>,
  )
}

describe('RolMantenimiento', () => {
  it('should list the roles returned by the mocked API', async () => {
    const roles: Rol[] = [
      rol({ id: '1', nombre: 'Administrador' }),
      rol({ id: '2', nombre: 'Analista', areaCodigo: 'A2' }),
    ]
    vi.mocked(rolesApi.listar).mockResolvedValue(roles)

    renderRolMantenimiento()

    expect(await screen.findByText('Administrador')).toBeInTheDocument()
    expect(screen.getByText('Analista')).toBeInTheDocument()
    expect(rolesApi.listar).toHaveBeenCalledTimes(1)
  })

  it('should filter the listed roles by a single unified search field on the client side', async () => {
    const roles: Rol[] = [
      rol({ id: '1', nombre: 'Administrador', areaCodigo: 'SISTEMAS' }),
      rol({ id: '2', nombre: 'Analista', areaCodigo: 'RRHH' }),
    ]
    vi.mocked(rolesApi.listar).mockResolvedValue(roles)
    const user = userEvent.setup()

    renderRolMantenimiento()
    await screen.findByText('Administrador')

    const filtroForm = screen.getByRole('form', { name: /filtrar rol/i })
    await user.type(within(filtroForm).getByLabelText(/buscar/i), 'SISTEMAS')

    await waitFor(() => {
      expect(screen.queryByText('Analista')).not.toBeInTheDocument()
    })
    expect(screen.getByText('Administrador')).toBeInTheDocument()
  })

  it('should show an inline error message inside the modal when creating a role fails', async () => {
    vi.mocked(rolesApi.listar).mockResolvedValue([])
    vi.mocked(rolesApi.crear).mockRejectedValue(
      new ApiError(400, 'El rol ya existe'),
    )
    const user = userEvent.setup()

    renderRolMantenimiento()
    await waitFor(() => expect(rolesApi.listar).toHaveBeenCalled())

    await user.click(screen.getByRole('button', { name: 'Registrar' }))
    const crearForm = await screen.findByRole('form', { name: /crear rol/i })
    await user.type(within(crearForm).getByLabelText(/^nombre$/i), 'Duplicado')
    await user.type(within(crearForm).getByLabelText(/^área$/i), 'A1')
    await user.type(within(crearForm).getByLabelText(/^cargo$/i), 'C1')
    await user.type(within(crearForm).getByLabelText(/ubicación/i), 'U1')
    await user.type(within(crearForm).getByLabelText(/^tipo$/i), 'FIJO')
    await user.type(within(crearForm).getByLabelText(/^ámbito$/i), 'GLOBAL')
    await user.type(
      within(crearForm).getByLabelText(/regla de negocio/i),
      'RN',
    )
    await user.type(
      within(crearForm).getByLabelText(/propietario \(rol\)/i),
      'P1',
    )
    await user.type(
      within(crearForm).getByLabelText(/propietario \(persona\)/i),
      'P2',
    )
    await user.type(within(crearForm).getByLabelText(/situación/i), 'ACTIVO')
    await user.type(within(crearForm).getByLabelText(/rol base/i), 'NO')
    await user.type(within(crearForm).getByLabelText(/descripción/i), 'Desc')
    await user.click(
      within(crearForm).getByRole('button', { name: /registrar/i }),
    )

    expect(await within(crearForm).findByRole('alert')).toHaveTextContent(
      'El rol ya existe',
    )
  })

  it('should render a "Ver Suministros" action for each role', async () => {
    vi.mocked(rolesApi.listar).mockResolvedValue([
      rol({ id: '1', nombre: 'Administrador' }),
    ])

    renderRolMantenimiento()

    expect(
      await screen.findByRole('button', { name: /ver suministros/i }),
    ).toBeInTheDocument()
  })
})
