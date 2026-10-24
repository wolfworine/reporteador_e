import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ReporteVista } from './ReporteVista'
import * as reportesApi from '../../api/reportes'
import * as areasApi from '../../api/areas'
import * as aplicacionesApi from '../../api/aplicaciones'
import { ApiError } from '../../api/client'
import type { Area } from '../../types/area'
import type { Aplicacion } from '../../types/aplicacion'
import type { Reporte, ReporteEvento } from '../../types/reporte'
import { ToastProvider } from '../../components/ui/ToastProvider'

// Cero HTTP/SSE real: se mockea `api/reportes.ts`, `api/areas.ts` y
// `api/aplicaciones.ts` por completo, y `EventSource` con un doble de test
// (regla dura de `docs/conventions.md`).
vi.mock('../../api/reportes')
vi.mock('../../api/areas')
vi.mock('../../api/aplicaciones')

class MockEventSource {
  static instances: MockEventSource[] = []
  url: string
  listeners: Record<string, ((event: MessageEvent<string>) => void)[]> = {}

  constructor(url: string) {
    this.url = url
    MockEventSource.instances.push(this)
  }

  addEventListener(
    type: string,
    listener: (event: MessageEvent<string>) => void,
  ) {
    this.listeners[type] = [...(this.listeners[type] ?? []), listener]
  }

  removeEventListener() {
    // no-op: suficiente para este test, no se verifica remoción.
  }

  close() {
    // no-op
  }

  emitMessage(data: ReporteEvento) {
    const messageEvent = { data: JSON.stringify(data) } as MessageEvent<string>
    for (const listener of this.listeners['message'] ?? []) {
      listener(messageEvent)
    }
  }
}

const AREAS: Area[] = [{ codigo: 'A1', nombre: 'Sistemas' }]
const APLICACIONES: Aplicacion[] = [{ codigo: 'APP1', nombre: 'SAP' }]

const REPORTE_LISTO: Reporte[] = [
  {
    area: 'Sistemas',
    codigoArea: 'A1',
    cargo: 'Analista',
    codigoCargo: 'C1',
    cantidadRol: 1,
    cantidadSuministro: 1,
    detalleRol: [
      {
        idRol: 'R1',
        rol: 'Rol Uno',
        tipo: 'FIJO',
        aplicacion: 'SAP',
        tipoRol: ['Directo'],
        suministroRoles: [
          { id: '1', atributoCodigo: 'COD1', atributoNombre: 'Nombre 1' },
        ],
        suministroGrupos: [],
        suministroInbox: [],
        suministroDefaultinbox: [],
        cantidadSuministroRol: 1,
        cantidadSuministros: 1,
      },
    ],
  },
]

function renderReporteVista() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <ReporteVista />
      </ToastProvider>
    </QueryClientProvider>,
  )
}

async function seleccionarYEnviarFiltro(user: ReturnType<typeof userEvent.setup>) {
  const form = screen.getByRole('form', { name: 'Filtrar Reporte' })
  // Las opciones de los selects llegan de `useQuery` (async) -- se espera a
  // que carguen antes de seleccionar, si no `selectOptions` falla porque la
  // opción todavía no existe en el DOM.
  await screen.findByRole('option', { name: 'SAP' })
  await screen.findByRole('option', { name: 'Sistemas' })
  await user.selectOptions(screen.getByLabelText('Aplicacion'), 'APP1')
  await user.selectOptions(screen.getByLabelText('Area'), 'A1')
  await user.click(screen.getByRole('button', { name: 'Reporte' }))
  return form
}

describe('ReporteVista', () => {
  const originalEventSource = globalThis.EventSource

  beforeEach(() => {
    MockEventSource.instances = []
    // @ts-expect-error -- doble de test de EventSource, ver useReportEvents.test.ts.
    globalThis.EventSource = MockEventSource
    vi.mocked(areasApi.listar).mockResolvedValue(AREAS)
    vi.mocked(aplicacionesApi.listar).mockResolvedValue(APLICACIONES)
  })

  afterEach(() => {
    globalThis.EventSource = originalEventSource
    vi.restoreAllMocks()
  })

  it('should go from Generando… to Listo, descargar without a page reload when the matching SSE event arrives', async () => {
    vi.mocked(reportesApi.generar).mockResolvedValue({ id: 'REP-1' })
    vi.mocked(reportesApi.obtenerReporte).mockResolvedValue(REPORTE_LISTO)
    const user = userEvent.setup()

    renderReporteVista()
    await screen.findByLabelText('Aplicacion')
    await seleccionarYEnviarFiltro(user)

    expect(await screen.findByText('Generando…')).toBeInTheDocument()
    expect(reportesApi.generar).toHaveBeenCalledWith({
      idArea: 'A1',
      idAplicacion: 'APP1',
      formato: 'pdf',
    })

    // La suscripción SSE es genuinamente EventSource, no polling -- ver
    // useReportEvents.test.ts para la prueba dedicada de esa parte.
    expect(MockEventSource.instances).toHaveLength(1)
    expect(MockEventSource.instances[0].url).toContain('/api/reportes/eventos')

    act(() => {
      MockEventSource.instances[0].emitMessage({ id: 'REP-1', estado: 'LISTO' })
    })

    expect(await screen.findByText('Listo, descargar')).toBeInTheDocument()
    expect(screen.queryByText('Generando…')).not.toBeInTheDocument()

    // Los dos botones de exportar están siempre disponibles -- el backend
    // regenera al vuelo (`GET /descargar?formato=`) el formato que no
    // coincide con el ya generado (ver `../../../backend/handoff.md`
    // 2026-08-25), así que no hace falta elegir formato por adelantado.
    expect(
      await screen.findByRole('button', { name: 'Exportar PDF' }),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: 'Exportar Excel' }),
    ).toBeInTheDocument()

    // La tabla anidada se pobló con el detalle del reporte ya "listo".
    expect(await screen.findByText('Rol Uno')).toBeInTheDocument()
  })

  it('should show a visible alert (not just console) when the SSE event reports estado FALLIDO', async () => {
    vi.mocked(reportesApi.generar).mockResolvedValue({ id: 'REP-2' })
    const user = userEvent.setup()

    renderReporteVista()
    await screen.findByLabelText('Aplicacion')
    await seleccionarYEnviarFiltro(user)
    await screen.findByText('Generando…')

    act(() => {
      MockEventSource.instances[0].emitMessage({
        id: 'REP-2',
        estado: 'FALLIDO',
        mensaje: 'Timeout tras reintentos',
      })
    })

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Timeout tras reintentos',
    )
    expect(screen.queryByText('Listo, descargar')).not.toBeInTheDocument()
  })

  it('should trigger a real blob download against /api/reportes/{id}/descargar when clicking Exportar PDF', async () => {
    vi.mocked(reportesApi.generar).mockResolvedValue({ id: 'REP-3' })
    vi.mocked(reportesApi.obtenerReporte).mockResolvedValue(REPORTE_LISTO)
    const blob = new Blob(['contenido-pdf'], { type: 'application/pdf' })
    vi.mocked(reportesApi.descargar).mockResolvedValue(blob)

    const createObjectURLSpy = vi
      .spyOn(URL, 'createObjectURL')
      .mockReturnValue('blob:mock-url')
    const revokeObjectURLSpy = vi
      .spyOn(URL, 'revokeObjectURL')
      .mockImplementation(() => {})
    const clickSpy = vi
      .spyOn(HTMLAnchorElement.prototype, 'click')
      .mockImplementation(() => {})

    const user = userEvent.setup()
    renderReporteVista()
    await screen.findByLabelText('Aplicacion')
    await seleccionarYEnviarFiltro(user)
    await screen.findByText('Generando…')

    act(() => {
      MockEventSource.instances[0].emitMessage({ id: 'REP-3', estado: 'LISTO' })
    })

    const botonPdf = await screen.findByRole('button', { name: 'Exportar PDF' })
    await user.click(botonPdf)

    await waitFor(() => {
      expect(reportesApi.descargar).toHaveBeenCalledWith('REP-3', 'pdf')
    })
    await waitFor(() => {
      expect(createObjectURLSpy).toHaveBeenCalledWith(blob)
    })
    expect(clickSpy).toHaveBeenCalled()
    expect(revokeObjectURLSpy).toHaveBeenCalledWith('blob:mock-url')
  })

  it('should trigger a real blob download against /api/reportes/{id}/descargar when clicking Exportar Excel', async () => {
    vi.mocked(reportesApi.generar).mockResolvedValue({ id: 'REP-4' })
    vi.mocked(reportesApi.obtenerReporte).mockResolvedValue(REPORTE_LISTO)
    const blob = new Blob(['contenido-excel'])
    vi.mocked(reportesApi.descargar).mockResolvedValue(blob)
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:mock-url')
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {})
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})

    const user = userEvent.setup()
    renderReporteVista()
    await screen.findByLabelText('Aplicacion')
    await seleccionarYEnviarFiltro(user)
    await screen.findByText('Generando…')

    act(() => {
      MockEventSource.instances[0].emitMessage({ id: 'REP-4', estado: 'LISTO' })
    })

    const botonExcel = await screen.findByRole('button', {
      name: 'Exportar Excel',
    })
    await user.click(botonExcel)

    await waitFor(() => {
      expect(reportesApi.descargar).toHaveBeenCalledWith('REP-4', 'excel')
    })
  })

  it('should show a visible alert when POST /api/reportes/generar fails', async () => {
    vi.mocked(reportesApi.generar).mockRejectedValue(
      new ApiError(500, 'No se pudo iniciar la generación.'),
    )
    const user = userEvent.setup()

    renderReporteVista()
    await screen.findByLabelText('Aplicacion')
    await seleccionarYEnviarFiltro(user)

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'No se pudo iniciar la generación.',
    )
  })

  it('should show a visible alert when the download call fails', async () => {
    vi.mocked(reportesApi.generar).mockResolvedValue({ id: 'REP-5' })
    vi.mocked(reportesApi.obtenerReporte).mockResolvedValue(REPORTE_LISTO)
    vi.mocked(reportesApi.descargar).mockRejectedValue(
      new ApiError(404, 'El reporte no está listo todavía.'),
    )
    const user = userEvent.setup()

    renderReporteVista()
    await screen.findByLabelText('Aplicacion')
    await seleccionarYEnviarFiltro(user)
    await screen.findByText('Generando…')

    act(() => {
      MockEventSource.instances[0].emitMessage({ id: 'REP-5', estado: 'LISTO' })
    })

    const botonPdf = await screen.findByRole('button', { name: 'Exportar PDF' })
    await user.click(botonPdf)

    // Puede haber 2 alerts (el inline dentro de la vista + el toast) --
    // basta con que al menos uno tenga el mensaje real del error.
    await waitFor(() => {
      const alerts = screen.getAllByRole('alert')
      expect(
        alerts.some((alert) =>
          alert.textContent?.includes('El reporte no está listo todavía.'),
        ),
      ).toBe(true)
    })
  })
})
