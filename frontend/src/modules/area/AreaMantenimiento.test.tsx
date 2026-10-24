import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AreaMantenimiento } from './AreaMantenimiento'
import * as areasApi from '../../api/areas'
import type { Area } from '../../types/area'
import { ToastProvider } from '../../components/ui/ToastProvider'

// Cero HTTP real: se mockea `api/areas.ts` por completo (regla dura de
// `docs/conventions.md`).
vi.mock('../../api/areas')

function renderAreaMantenimiento() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })

  return render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <AreaMantenimiento />
      </ToastProvider>
    </QueryClientProvider>,
  )
}

describe('AreaMantenimiento', () => {
  it('should list the areas returned by the mocked API', async () => {
    const areas: Area[] = [
      { codigo: '1', nombre: 'Sistemas' },
      { codigo: '2', nombre: 'Recursos Humanos' },
    ]
    vi.mocked(areasApi.listar).mockResolvedValue(areas)

    renderAreaMantenimiento()

    expect(await screen.findByText('Sistemas')).toBeInTheDocument()
    expect(screen.getByText('Recursos Humanos')).toBeInTheDocument()
    expect(areasApi.listar).toHaveBeenCalledTimes(1)
  })

  it('should filter the listed areas by a single unified search field on the client side', async () => {
    const areas: Area[] = [
      { codigo: '1', nombre: 'Sistemas' },
      { codigo: '2', nombre: 'Recursos Humanos' },
    ]
    vi.mocked(areasApi.listar).mockResolvedValue(areas)
    const user = userEvent.setup()

    renderAreaMantenimiento()
    await screen.findByText('Sistemas')

    const filtroForm = screen.getByRole('form', { name: /filtrar área/i })
    await user.type(within(filtroForm).getByLabelText(/buscar/i), 'Sistemas')

    await waitFor(() => {
      expect(screen.queryByText('Recursos Humanos')).not.toBeInTheDocument()
    })
    expect(screen.getByText('Sistemas')).toBeInTheDocument()
  })

  it('should call the create mutation with the entered name and refresh the list when the modal form is submitted', async () => {
    vi.mocked(areasApi.listar)
      .mockResolvedValueOnce([{ codigo: '1', nombre: 'Sistemas' }])
      .mockResolvedValueOnce([
        { codigo: '1', nombre: 'Sistemas' },
        { codigo: '2', nombre: 'Logística' },
      ])
    vi.mocked(areasApi.crear).mockResolvedValue({
      codigo: '2',
      nombre: 'Logística',
    })
    const user = userEvent.setup()

    renderAreaMantenimiento()
    await screen.findByText('Sistemas')

    await user.click(screen.getByRole('button', { name: 'Registrar' }))
    const crearForm = await screen.findByRole('form', { name: /crear área/i })
    await user.type(within(crearForm).getByLabelText(/nombre/i), 'Logística')
    await user.click(
      within(crearForm).getByRole('button', { name: /registrar/i }),
    )

    await waitFor(() => {
      expect(areasApi.crear).toHaveBeenCalledWith({ nombre: 'Logística' })
    })
    expect(await screen.findByText('Logística')).toBeInTheDocument()
    // El modal se cierra solo tras un alta exitosa.
    expect(
      screen.queryByRole('form', { name: /crear área/i }),
    ).not.toBeInTheDocument()
  })

  it('should show an inline error message inside the modal when creating an area fails', async () => {
    const { ApiError } = await import('../../api/client')
    vi.mocked(areasApi.listar).mockResolvedValue([])
    vi.mocked(areasApi.crear).mockRejectedValue(
      new ApiError(400, 'El nombre ya existe'),
    )
    const user = userEvent.setup()

    renderAreaMantenimiento()
    await waitFor(() => expect(areasApi.listar).toHaveBeenCalled())

    await user.click(screen.getByRole('button', { name: 'Registrar' }))
    const crearForm = await screen.findByRole('form', { name: /crear área/i })
    await user.type(within(crearForm).getByLabelText(/nombre/i), 'Duplicada')
    await user.click(
      within(crearForm).getByRole('button', { name: /registrar/i }),
    )

    expect(await within(crearForm).findByRole('alert')).toHaveTextContent(
      'El nombre ya existe',
    )
  })

  it('should complete the full crear -> listar -> editar lifecycle for an area in a single flow', async () => {
    // Paso 1: listado inicial vacío.
    vi.mocked(areasApi.listar)
      .mockResolvedValueOnce([])
      // Paso 2: tras crear, el refetch devuelve la nueva área.
      .mockResolvedValueOnce([{ codigo: '3', nombre: 'Logística' }])
      // Paso 3: tras editar, el refetch devuelve el nombre actualizado.
      .mockResolvedValueOnce([{ codigo: '3', nombre: 'Logística Norte' }])
    vi.mocked(areasApi.crear).mockResolvedValue({
      codigo: '3',
      nombre: 'Logística',
    })
    vi.mocked(areasApi.actualizar).mockResolvedValue({
      codigo: '3',
      nombre: 'Logística Norte',
    })
    const user = userEvent.setup()

    renderAreaMantenimiento()
    // Nota: no se afirma un número exacto de llamadas acumuladas a
    // `listar` aquí -- el mock es compartido por todo el archivo y otros
    // `it` anteriores ya lo invocaron, así que `toHaveBeenCalledTimes`
    // reflejaría el total acumulado del archivo, no de este test. Basta
    // con esperar a que el listado inicial (vacío) haya cargado.
    await waitFor(() => {
      expect(areasApi.listar).toHaveBeenCalled()
      expect(screen.queryByText('Cargando Área…')).not.toBeInTheDocument()
    })

    // Crear: abre el modal de registro primero.
    await user.click(screen.getByRole('button', { name: 'Registrar' }))
    const crearForm = await screen.findByRole('form', { name: /crear área/i })
    await user.type(within(crearForm).getByLabelText(/nombre/i), 'Logística')
    await user.click(
      within(crearForm).getByRole('button', { name: /registrar/i }),
    )
    await waitFor(() => {
      expect(areasApi.crear).toHaveBeenCalledWith({ nombre: 'Logística' })
    })

    // Listar: la nueva área aparece en la tabla tras el refetch.
    expect(await screen.findByText('Logística')).toBeInTheDocument()

    // Editar: click en "Actualizar" abre el modal de edición de esa fila.
    const filaLogistica = screen.getByText('Logística').closest('tr')
    if (!filaLogistica) {
      throw new Error('No se encontró la fila de "Logística" en la tabla.')
    }
    await user.click(
      within(filaLogistica).getByRole('button', { name: /actualizar/i }),
    )

    const editarForm = await screen.findByRole('form', {
      name: 'Editar Área 3',
    })
    const inputNombre = within(editarForm).getByLabelText(/nombre/i)
    await user.clear(inputNombre)
    await user.type(inputNombre, 'Logística Norte')
    await user.click(within(editarForm).getByRole('button', { name: /guardar/i }))

    await waitFor(() => {
      expect(areasApi.actualizar).toHaveBeenCalledWith('3', {
        nombre: 'Logística Norte',
      })
    })

    // Listar de nuevo: la tabla refleja el nombre actualizado, ya no el viejo.
    expect(await screen.findByText('Logística Norte')).toBeInTheDocument()
    expect(screen.queryByText('Logística')).not.toBeInTheDocument()
  })
})
