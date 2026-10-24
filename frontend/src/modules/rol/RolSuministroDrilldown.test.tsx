import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { RolMantenimiento } from './RolMantenimiento'
import { SuministroPorRol } from '../suministro/SuministroPorRol'
import * as rolesApi from '../../api/roles'
import * as suministrosApi from '../../api/suministros'
import type { Rol } from '../../types/rol'
import type { Suministro } from '../../types/suministro'
import { ToastProvider } from '../../components/ui/ToastProvider'

// Cero HTTP real: se mockean `api/roles.ts` y `api/suministros.ts` por
// completo (regla dura de `docs/conventions.md`).
vi.mock('../../api/roles')
vi.mock('../../api/suministros')

function rol(overrides: Partial<Rol>): Rol {
  return {
    id: '1',
    ubicacionCodigo: 'U1',
    areaCodigo: 'A1',
    cargoCodigo: 'C1',
    nombre: 'Administrador',
    descripcion: '',
    tipo: 'FIJO',
    reglaNegocio: '',
    ambito: 'GLOBAL',
    propietarioRol: '',
    propietarioPersona: '',
    situacion: 'ACTIVO',
    rolBase: 'NO',
    ...overrides,
  }
}

function suministro(overrides: Partial<Suministro>): Suministro {
  return {
    id: 'S1',
    rolId: 'R1',
    nombre: 'Suministro',
    prioridad: 'ALTA',
    ...overrides,
  }
}

function renderDrilldown() {
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
          <Routes>
            <Route path="/roles" element={<RolMantenimiento />} />
            <Route
              path="/roles/:idRol/suministros"
              element={<SuministroPorRol />}
            />
          </Routes>
        </MemoryRouter>
      </ToastProvider>
    </QueryClientProvider>,
  )
}

describe('Rol -> Suministro drilldown (Ver Suministros)', () => {
  it('should navigate to VerSuministros and show only the suministros of the selected rol, by id_rol', async () => {
    const roles: Rol[] = [
      rol({ id: 'R1', nombre: 'Rol Uno' }),
      rol({ id: 'R2', nombre: 'Rol Dos' }),
    ]
    vi.mocked(rolesApi.listar).mockResolvedValue(roles)

    // El backend real filtra por idRol en la URL
    // (`GET /api/roles/{idRol}/suministros`) — el mock replica ese
    // contrato devolviendo listas distintas por idRol.
    vi.mocked(suministrosApi.listarPorRol).mockImplementation((idRol) => {
      if (idRol === 'R1') {
        return Promise.resolve([
          suministro({ id: 'S1', rolId: 'R1', nombre: 'Suministro de R1' }),
        ])
      }
      if (idRol === 'R2') {
        return Promise.resolve([
          suministro({ id: 'S2', rolId: 'R2', nombre: 'Suministro de R2' }),
        ])
      }
      return Promise.resolve([])
    })

    const user = userEvent.setup()
    renderDrilldown()

    await screen.findByText('Rol Uno')
    const filaRol1 = screen.getByText('Rol Uno').closest('tr')
    expect(filaRol1).not.toBeNull()

    await user.click(
      within(filaRol1 as HTMLElement).getByRole('button', {
        name: /ver suministros/i,
      }),
    )

    expect(await screen.findByText('Suministro de R1')).toBeInTheDocument()
    expect(screen.queryByText('Suministro de R2')).not.toBeInTheDocument()
    await waitFor(() => {
      expect(suministrosApi.listarPorRol).toHaveBeenCalledWith('R1')
    })
    expect(suministrosApi.listarPorRol).not.toHaveBeenCalledWith('R2')
    expect(
      screen.getByRole('heading', { name: /suministros del rol r1/i }),
    ).toBeInTheDocument()
  })
})
