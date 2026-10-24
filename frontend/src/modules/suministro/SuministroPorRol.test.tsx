import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { SuministroPorRol } from './SuministroPorRol'
import * as suministrosApi from '../../api/suministros'
import { ApiError } from '../../api/client'
import type { Suministro } from '../../types/suministro'
import { ToastProvider } from '../../components/ui/ToastProvider'

// Cero HTTP real: se mockea `api/suministros.ts` por completo (regla dura
// de `docs/conventions.md`).
vi.mock('../../api/suministros')

function renderSuministroPorRol(idRol: string) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })

  return render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <MemoryRouter initialEntries={[`/roles/${idRol}/suministros`]}>
          <Routes>
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

describe('SuministroPorRol', () => {
  it('should list the suministros returned for the rol in the route param', async () => {
    const suministros: Suministro[] = [
      { id: 'S1', rolId: 'R1', nombre: 'Correo', prioridad: 'ALTA' },
      { id: 'S2', rolId: 'R1', nombre: 'VPN', prioridad: 'MEDIA' },
    ]
    vi.mocked(suministrosApi.listarPorRol).mockResolvedValue(suministros)

    renderSuministroPorRol('R1')

    expect(await screen.findByText('Correo')).toBeInTheDocument()
    expect(screen.getByText('VPN')).toBeInTheDocument()
    expect(suministrosApi.listarPorRol).toHaveBeenCalledWith('R1')
  })

  it('should show an inline error message when creating a suministro fails', async () => {
    vi.mocked(suministrosApi.listarPorRol).mockResolvedValue([])
    vi.mocked(suministrosApi.crear).mockRejectedValue(
      new ApiError(400, 'El suministro ya existe'),
    )
    const user = userEvent.setup()

    renderSuministroPorRol('R1')
    await waitFor(() =>
      expect(suministrosApi.listarPorRol).toHaveBeenCalled(),
    )

    await user.click(screen.getByRole('button', { name: 'Registrar' }))
    const crearForm = await screen.findByRole('form', {
      name: /crear suministro/i,
    })
    await user.type(within(crearForm).getByLabelText(/nombre/i), 'Duplicado')
    await user.type(within(crearForm).getByLabelText(/prioridad/i), 'ALTA')
    await user.click(
      within(crearForm).getByRole('button', { name: /registrar/i }),
    )

    expect(await within(crearForm).findByRole('alert')).toHaveTextContent(
      'El suministro ya existe',
    )
  })

  it('should render a "Ver Atributo" action for each suministro', async () => {
    vi.mocked(suministrosApi.listarPorRol).mockResolvedValue([
      { id: 'S1', rolId: 'R1', nombre: 'Correo', prioridad: 'ALTA' },
    ])

    renderSuministroPorRol('R1')

    expect(
      await screen.findByRole('button', { name: /ver atributo/i }),
    ).toBeInTheDocument()
  })
})
