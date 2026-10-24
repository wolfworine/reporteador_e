import { render, screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { Layout } from './Layout'
import { AuthProvider } from '../../app/AuthContext'
import * as menuApi from '../../api/menu'
import * as areasApi from '../../api/areas'
import * as aplicacionesApi from '../../api/aplicaciones'
import type { MenuNodo } from '../../types/menu'

// `modules/` (y por extensión los componentes que usan sus hooks) nunca
// llaman `fetch` directamente — se mockea `api/menu.ts` por completo, cero
// HTTP real (regla dura de `docs/architecture.md` / `docs/conventions.md`).
// `api/areas.ts`/`api/aplicaciones.ts` se mockean también porque `Layout`
// las precarga (`prefetchQuery`) al montar (ver comentario en `Layout.tsx`).
vi.mock('../../api/menu')
vi.mock('../../api/areas')
vi.mock('../../api/aplicaciones')

function renderLayoutForTipoUsuario(tipo: string, items: MenuNodo[]) {
  vi.mocked(menuApi.fetchMenu).mockResolvedValue(items)
  vi.mocked(areasApi.listar).mockResolvedValue([])
  vi.mocked(aplicacionesApi.listar).mockResolvedValue([])

  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })

  return render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider
        initialUser={{
          email: 'usuario@entel.pe',
          nombre: 'Ana',
          apellido: 'López',
          tipo,
        }}
      >
        <MemoryRouter initialEntries={['/']}>
          <Routes>
            <Route path="/" element={<Layout />}>
              <Route index element={<p>Contenido de la ruta</p>} />
            </Route>
          </Routes>
        </MemoryRouter>
      </AuthProvider>
    </QueryClientProvider>,
  )
}

describe('Layout', () => {
  it('should render the menu tree returned by the API for the simulated tipoUsuario, as real clickable links when a route exists', async () => {
    // Forma real de `GET /api/menu/{tipoUsuario}` (`domain/MenuNodo.java`):
    // árbol ya armado del lado del servidor, `codigo` string, sin
    // `tipo`/`tipoUsuario`/`codSubmenu`/`estado` -- ver `types/menu.ts`.
    const items: MenuNodo[] = [
      { codigo: '2', nombre: 'Area', action: 'areaManagedBean', hijos: [] },
      { codigo: '9', nombre: 'Usuario', action: 'usuarioManagedBean', hijos: [] },
    ]

    renderLayoutForTipoUsuario('ADMINISTRADOR', items)

    const menu = await screen.findByRole('navigation', { name: 'Menú principal' })
    const linkArea = await within(menu).findByRole('link', { name: 'Area' })
    expect(linkArea).toHaveAttribute('href', '/areas')
    const linkUsuario = within(menu).getByRole('link', { name: 'Usuario' })
    expect(linkUsuario).toHaveAttribute('href', '/usuarios')
    expect(menuApi.fetchMenu).toHaveBeenCalledWith('ADMINISTRADOR')
    expect(screen.getByText('Contenido de la ruta')).toBeInTheDocument()
  })

  it('should render an item without a matching frontend route as plain text, not a broken link', async () => {
    // Bug real corregido 2026-08-19: `Suministro`/`Detalle` del menú legado
    // real no tienen pantalla propia en el frontend nuevo (son parte del
    // drill-down anidado de Roles) -- deben mostrarse, no desaparecer ni
    // enlazar a una ruta inexistente.
    const items: MenuNodo[] = [
      { codigo: '6', nombre: 'Suministro', action: 'suministroManagedBean', hijos: [] },
    ]

    renderLayoutForTipoUsuario('ADMINISTRADOR', items)

    const menu = await screen.findByRole('navigation', { name: 'Menú principal' })
    expect(await within(menu).findByText('Suministro')).toBeInTheDocument()
    expect(within(menu).queryByRole('link', { name: 'Suministro' })).not.toBeInTheDocument()
  })

  it('should render a different, correctly-scoped menu tree when the simulated tipoUsuario is different', async () => {
    const items: MenuNodo[] = [
      { codigo: '8', nombre: 'Reporte', action: 'reporteManagedBean', hijos: [] },
    ]

    renderLayoutForTipoUsuario('OPERADOR', items)

    const menu = await screen.findByRole('navigation', { name: 'Menú principal' })
    expect(await within(menu).findByRole('link', { name: 'Reporte' })).toHaveAttribute('href', '/reportes')
    expect(screen.queryByText('Area')).not.toBeInTheDocument()
    expect(menuApi.fetchMenu).toHaveBeenCalledWith('OPERADOR')
  })

  it('should render nested children as a real nested list of links', async () => {
    const items: MenuNodo[] = [
      {
        codigo: '1',
        nombre: 'Administración',
        action: null,
        hijos: [{ codigo: '9', nombre: 'Usuario', action: 'usuarioManagedBean', hijos: [] }],
      },
    ]

    renderLayoutForTipoUsuario('ADMINISTRADOR', items)

    const menu = await screen.findByRole('navigation', { name: 'Menú principal' })
    expect(await within(menu).findByText('Administración')).toBeInTheDocument()
    expect(within(menu).getByRole('link', { name: 'Usuario' })).toHaveAttribute('href', '/usuarios')
  })

  it('should render the logged-in user name and a logout button', async () => {
    renderLayoutForTipoUsuario('ADMINISTRADOR', [])

    expect(screen.getByText(/ana lópez/i)).toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: /cerrar sesión/i }),
    ).toBeInTheDocument()
  })
})
