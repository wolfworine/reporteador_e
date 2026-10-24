import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { AtributoTabs } from './AtributoTabs'
import { SuministroPorRol } from '../suministro/SuministroPorRol'
import * as aplicacionesApi from '../../api/aplicaciones'
import * as atributosApi from '../../api/atributos'
import * as suministrosApi from '../../api/suministros'
import type { Atributo, CategoriaSuministro } from '../../types/atributo'
import type { Suministro } from '../../types/suministro'
import { ApiError } from '../../api/client'
import { ToastProvider } from '../../components/ui/ToastProvider'

// Cero HTTP real: se mockean `api/atributos.ts`, `api/suministros.ts` y
// `api/aplicaciones.ts` por completo (regla dura de `docs/conventions.md`).
// `api/aplicaciones.ts` se agregó tras el bug real 2026-08-25 ("la vista de
// Atributos del Suministro no trae data"): `AtributoTabs.tsx` ahora resuelve
// `aplicacion` desde este catálogo antes de llamar a
// `listarPorRolYCategoria` (ver Javadoc en `AtributoTabs.tsx`).
vi.mock('../../api/aplicaciones')
vi.mock('../../api/atributos')
vi.mock('../../api/suministros')

beforeEach(() => {
  vi.mocked(aplicacionesApi.listar).mockResolvedValue([
    { codigo: 'APP1', nombre: 'Aplicación Uno' },
  ])
})

function atributo(overrides: Partial<Atributo>): Atributo {
  return {
    id: '1',
    idDetalleAtributo: 'D1',
    categoria: 'ROLES',
    aplicacionCodigo: 'APP1',
    areaCodigo: 'A1',
    cargoCodigo: 'C1',
    cargoNombre: 'Cargo Uno',
    rolId: 'R1',
    rolTipo: 'FIJO',
    rolNombre: 'Rol Uno',
    suministroId: 'S1',
    atributoCodigo: 'AT1',
    atributoNombre: 'Atributo Uno',
    estado: 'ACTIVO',
    ...overrides,
  }
}

/** Un dataset distinto por categoría -- cada uno con un `atributoNombre` único, para poder afirmar ausencia cruzada entre pestañas. */
function datasetsPorCategoria(): Record<CategoriaSuministro, Atributo[]> {
  return {
    ROLES: [
      atributo({ id: 'r1', atributoNombre: 'Nombre Roles Uno', suministroId: 'S1' }),
      atributo({ id: 'r2', atributoNombre: 'Nombre Roles Dos', suministroId: 'S1' }),
    ],
    GRUPOS: [
      atributo({
        id: 'g1',
        categoria: 'GRUPOS',
        atributoNombre: 'Nombre Grupos Uno',
        suministroId: 'S1',
      }),
    ],
    INBOX: [
      atributo({
        id: 'i1',
        categoria: 'INBOX',
        atributoNombre: 'Nombre Inbox Uno',
        suministroId: 'S1',
      }),
    ],
    DEFAULTINBOX: [
      atributo({
        id: 'd1',
        categoria: 'DEFAULTINBOX',
        atributoNombre: 'Nombre DefaultInbox Uno',
        suministroId: 'S1',
      }),
    ],
  }
}

const TODOS_LOS_NOMBRES = [
  'Nombre Roles Uno',
  'Nombre Roles Dos',
  'Nombre Grupos Uno',
  'Nombre Inbox Uno',
  'Nombre DefaultInbox Uno',
]

function nombresDeOtrasCategorias(propia: string[]): string[] {
  return TODOS_LOS_NOMBRES.filter((nombre) => !propia.includes(nombre))
}

function renderAtributoTabs() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })

  return render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <MemoryRouter initialEntries={['/roles/R1/suministros/S1/atributos']}>
          <Routes>
            <Route
              path="/roles/:idRol/suministros/:idSuministro/atributos"
              element={<AtributoTabs />}
            />
          </Routes>
        </MemoryRouter>
      </ToastProvider>
    </QueryClientProvider>,
  )
}

describe('AtributoTabs', () => {
  it('should show the Roles tab active by default, matching the legacy default view', async () => {
    const datasets = datasetsPorCategoria()
    vi.mocked(atributosApi.listarPorRolYCategoria).mockImplementation(
      (_idRol, categoria) => Promise.resolve(datasets[categoria]),
    )

    renderAtributoTabs()

    expect(await screen.findByText('Nombre Roles Uno')).toBeInTheDocument()
    expect(screen.getByText('Nombre Roles Dos')).toBeInTheDocument()
    for (const nombre of nombresDeOtrasCategorias([
      'Nombre Roles Uno',
      'Nombre Roles Dos',
    ])) {
      expect(screen.queryByText(nombre)).not.toBeInTheDocument()
    }

    const tabRoles = screen.getByRole('tab', { name: 'Roles' })
    expect(tabRoles).toHaveAttribute('aria-selected', 'true')
    await waitFor(() => {
      expect(atributosApi.listarPorRolYCategoria).toHaveBeenCalledWith(
        'R1',
        'ROLES',
        'APP1',
      )
    })
  })

  it('should show only the active category data when switching tabs, never mixing data from another category', async () => {
    const datasets = datasetsPorCategoria()
    vi.mocked(atributosApi.listarPorRolYCategoria).mockImplementation(
      (_idRol, categoria) => Promise.resolve(datasets[categoria]),
    )

    const user = userEvent.setup()
    renderAtributoTabs()

    // Roles (default)
    expect(await screen.findByText('Nombre Roles Uno')).toBeInTheDocument()

    // Cambiar a Grupos
    await user.click(screen.getByRole('tab', { name: 'Grupos' }))
    expect(await screen.findByText('Nombre Grupos Uno')).toBeInTheDocument()
    for (const nombre of nombresDeOtrasCategorias(['Nombre Grupos Uno'])) {
      expect(screen.queryByText(nombre)).not.toBeInTheDocument()
    }
    expect(screen.getByRole('tab', { name: 'Grupos' })).toHaveAttribute(
      'aria-selected',
      'true',
    )
    expect(screen.getByRole('tab', { name: 'Roles' })).toHaveAttribute(
      'aria-selected',
      'false',
    )

    // Cambiar a Inbox
    await user.click(screen.getByRole('tab', { name: 'Inbox' }))
    expect(await screen.findByText('Nombre Inbox Uno')).toBeInTheDocument()
    for (const nombre of nombresDeOtrasCategorias(['Nombre Inbox Uno'])) {
      expect(screen.queryByText(nombre)).not.toBeInTheDocument()
    }

    // Cambiar a DefaultInbox
    await user.click(screen.getByRole('tab', { name: 'DefaultInbox' }))
    expect(
      await screen.findByText('Nombre DefaultInbox Uno'),
    ).toBeInTheDocument()
    for (const nombre of nombresDeOtrasCategorias([
      'Nombre DefaultInbox Uno',
    ])) {
      expect(screen.queryByText(nombre)).not.toBeInTheDocument()
    }

    // Volver a Roles: los datos de Roles siguen ahí (cache por categoría),
    // sin datos de las otras 3 categorías mezclados.
    await user.click(screen.getByRole('tab', { name: 'Roles' }))
    expect(await screen.findByText('Nombre Roles Uno')).toBeInTheDocument()
    expect(screen.getByText('Nombre Roles Dos')).toBeInTheDocument()
    for (const nombre of nombresDeOtrasCategorias([
      'Nombre Roles Uno',
      'Nombre Roles Dos',
    ])) {
      expect(screen.queryByText(nombre)).not.toBeInTheDocument()
    }

    // Cada cambio de pestaña llamó al endpoint con SU categoría, nunca
    // mezclando -- acceptance criterio 2.
    expect(atributosApi.listarPorRolYCategoria).toHaveBeenCalledWith(
      'R1',
      'ROLES',
      'APP1',
    )
    expect(atributosApi.listarPorRolYCategoria).toHaveBeenCalledWith(
      'R1',
      'GRUPOS',
      'APP1',
    )
    expect(atributosApi.listarPorRolYCategoria).toHaveBeenCalledWith(
      'R1',
      'INBOX',
      'APP1',
    )
    expect(atributosApi.listarPorRolYCategoria).toHaveBeenCalledWith(
      'R1',
      'DEFAULTINBOX',
      'APP1',
    )
  })

  it('should filter the active tab data client-side by atributoCodigo/atributoNombre', async () => {
    vi.mocked(atributosApi.listarPorRolYCategoria).mockResolvedValue([
      atributo({ id: '1', atributoCodigo: 'COD1', atributoNombre: 'Correo Corporativo' }),
      atributo({ id: '2', atributoCodigo: 'COD2', atributoNombre: 'VPN Externa' }),
    ])

    const user = userEvent.setup()
    renderAtributoTabs()

    expect(await screen.findByText('Correo Corporativo')).toBeInTheDocument()
    expect(screen.getByText('VPN Externa')).toBeInTheDocument()

    await user.type(screen.getByLabelText('Buscar'), 'correo')

    expect(screen.getByText('Correo Corporativo')).toBeInTheDocument()
    expect(screen.queryByText('VPN Externa')).not.toBeInTheDocument()
  })

  it('should create an atributo on the active tab with the correct categoria, without mixing it into another tab', async () => {
    vi.mocked(atributosApi.listarPorRolYCategoria).mockResolvedValue([])
    vi.mocked(atributosApi.crear).mockResolvedValue(
      atributo({
        id: 'g99',
        categoria: 'GRUPOS',
        atributoCodigo: 'GCOD',
        atributoNombre: 'Grupo Nuevo',
      }),
    )

    const user = userEvent.setup()
    renderAtributoTabs()

    await screen.findByRole('tab', { name: 'Roles' })
    await user.click(screen.getByRole('tab', { name: 'Grupos' }))

    await user.click(screen.getByRole('button', { name: 'Registrar' }))
    await screen.findByRole('form', { name: 'Crear Atributo GRUPOS' })
    await user.type(screen.getByLabelText('Aplicación'), 'APP1')
    await user.type(screen.getByLabelText('Atributo Código'), 'GCOD')
    await user.type(screen.getByLabelText('Atributo Nombre'), 'Grupo Nuevo')

    await user.click(
      within(
        screen.getByRole('form', { name: 'Crear Atributo GRUPOS' }),
      ).getByRole('button', { name: 'Registrar' }),
    )

    await waitFor(() => {
      expect(atributosApi.crear).toHaveBeenCalledWith({
        aplicacionCodigo: 'APP1',
        suministroId: 'S1',
        atributoCodigo: 'GCOD',
        atributoNombre: 'Grupo Nuevo',
        estado: 'ACTIVO',
        rolId: 'R1',
        categoria: 'GRUPOS',
      })
    })
  })

  it('should show a visible alert (not just console) when creating an atributo fails', async () => {
    vi.mocked(atributosApi.listarPorRolYCategoria).mockResolvedValue([])
    vi.mocked(atributosApi.crear).mockRejectedValue(
      new ApiError(400, 'No se pudo crear.'),
    )

    const user = userEvent.setup()
    renderAtributoTabs()

    await user.click(screen.getByRole('button', { name: 'Registrar' }))
    const crearForm = await screen.findByRole('form', {
      name: 'Crear Atributo ROLES',
    })
    await user.type(screen.getByLabelText('Aplicación'), 'APP1')
    await user.type(screen.getByLabelText('Atributo Código'), 'COD1')
    await user.type(screen.getByLabelText('Atributo Nombre'), 'Nombre 1')
    await user.click(
      within(crearForm).getByRole('button', { name: 'Registrar' }),
    )

    expect(await within(crearForm).findByRole('alert')).toHaveTextContent(
      'No se pudo crear.',
    )
  })

  it('should navigate from Suministro (Ver Atributo) into AtributoTabs with Roles active by default, filtered client-side by suministroId', async () => {
    const suministros: Suministro[] = [
      { id: 'S1', rolId: 'R1', nombre: 'Correo', prioridad: 'ALTA' },
      { id: 'S2', rolId: 'R1', nombre: 'VPN', prioridad: 'MEDIA' },
    ]
    vi.mocked(suministrosApi.listarPorRol).mockResolvedValue(suministros)
    vi.mocked(atributosApi.listarPorRolYCategoria).mockResolvedValue([
      atributo({ id: '1', suministroId: 'S1', atributoNombre: 'Del Correo' }),
      atributo({ id: '2', suministroId: 'S2', atributoNombre: 'De la VPN' }),
    ])

    const queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false },
        mutations: { retry: false },
      },
    })
    const user = userEvent.setup()

    render(
      <QueryClientProvider client={queryClient}>
        <ToastProvider>
          <MemoryRouter initialEntries={['/roles/R1/suministros']}>
            <Routes>
              <Route
                path="/roles/:idRol/suministros"
                element={<SuministroPorRol />}
              />
              <Route
                path="/roles/:idRol/suministros/:idSuministro/atributos"
                element={<AtributoTabs />}
              />
            </Routes>
          </MemoryRouter>
        </ToastProvider>
      </QueryClientProvider>,
    )

    await screen.findByText('Correo')
    const filaS1 = screen.getByText('Correo').closest('tr')
    expect(filaS1).not.toBeNull()

    await user.click(
      within(filaS1 as HTMLElement).getByRole('button', {
        name: /ver atributo/i,
      }),
    )

    expect(
      screen.getByRole('tab', { name: 'Roles' }),
    ).toHaveAttribute('aria-selected', 'true')
    expect(await screen.findByText('Del Correo')).toBeInTheDocument()
    expect(screen.queryByText('De la VPN')).not.toBeInTheDocument()
  })
})
