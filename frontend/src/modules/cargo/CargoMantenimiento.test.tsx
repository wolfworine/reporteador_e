import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { CargoMantenimiento } from './CargoMantenimiento'
import * as cargosApi from '../../api/cargos'
import type { Cargo } from '../../types/cargo'
import { ToastProvider } from '../../components/ui/ToastProvider'

// Cero HTTP real: se mockea `api/cargos.ts` por completo (regla dura de
// `docs/conventions.md`).
vi.mock('../../api/cargos')

function renderCargoMantenimiento() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })

  return render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <CargoMantenimiento />
      </ToastProvider>
    </QueryClientProvider>,
  )
}

describe('CargoMantenimiento', () => {
  it('should list the cargos returned by the mocked API', async () => {
    const cargos: Cargo[] = [
      { codigo: '10', nombre: 'Analista' },
      { codigo: '11', nombre: 'Gerente' },
    ]
    vi.mocked(cargosApi.listar).mockResolvedValue(cargos)

    renderCargoMantenimiento()

    expect(await screen.findByText('Analista')).toBeInTheDocument()
    expect(screen.getByText('Gerente')).toBeInTheDocument()
    expect(cargosApi.listar).toHaveBeenCalledTimes(1)
  })

  it('should call the create mutation with the entered name and show the new cargo after refetch', async () => {
    vi.mocked(cargosApi.listar)
      .mockResolvedValueOnce([{ codigo: '10', nombre: 'Analista' }])
      .mockResolvedValueOnce([
        { codigo: '10', nombre: 'Analista' },
        { codigo: '12', nombre: 'Supervisor' },
      ])
    vi.mocked(cargosApi.crear).mockResolvedValue({
      codigo: '12',
      nombre: 'Supervisor',
    })
    const user = userEvent.setup()

    renderCargoMantenimiento()
    await screen.findByText('Analista')

    await user.click(screen.getByRole('button', { name: 'Registrar' }))
    const crearForm = await screen.findByRole('form', { name: /crear cargo/i })
    await user.type(
      within(crearForm).getByLabelText(/nombre/i),
      'Supervisor',
    )
    await user.click(
      within(crearForm).getByRole('button', { name: /registrar/i }),
    )

    await waitFor(() => {
      expect(cargosApi.crear).toHaveBeenCalledWith({ nombre: 'Supervisor' })
    })
    expect(await screen.findByText('Supervisor')).toBeInTheDocument()
  })
})
