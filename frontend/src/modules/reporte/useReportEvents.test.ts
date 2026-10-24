import { act, renderHook, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useReportEvents } from './useReportEvents'
import type { ReporteEvento } from '../../types/reporte'

/**
 * Doble de test de la API nativa `EventSource` -- mismo espíritu que
 * `globalThis.fetch = vi.fn()` usado en el resto de la suite
 * (`docs/conventions.md`: cero conexiones HTTP/SSE reales en tests).
 * Registra cada instancia creada en `instances` para que los tests puedan
 * disparar `message`/`error` manualmente y verificar la URL exacta con la
 * que se construyó -- así se prueba que el hook usa genuinamente
 * `EventSource` contra `/api/reportes/eventos` y no un `setInterval`.
 */
class MockEventSource {
  static instances: MockEventSource[] = []
  url: string
  listeners: Record<string, ((event: MessageEvent<string>) => void)[]> = {}
  closed = false

  constructor(url: string) {
    this.url = url
    MockEventSource.instances.push(this)
  }

  addEventListener(type: string, listener: (event: MessageEvent<string>) => void) {
    this.listeners[type] = [...(this.listeners[type] ?? []), listener]
  }

  removeEventListener(
    type: string,
    listener: (event: MessageEvent<string>) => void,
  ) {
    this.listeners[type] = (this.listeners[type] ?? []).filter(
      (existing) => existing !== listener,
    )
  }

  close() {
    this.closed = true
  }

  emitMessage(data: ReporteEvento) {
    const messageEvent = { data: JSON.stringify(data) } as MessageEvent<string>
    for (const listener of this.listeners['message'] ?? []) {
      listener(messageEvent)
    }
  }

  emitError() {
    for (const listener of this.listeners['error'] ?? []) {
      listener({} as MessageEvent<string>)
    }
  }
}

describe('useReportEvents', () => {
  const originalEventSource = globalThis.EventSource

  beforeEach(() => {
    MockEventSource.instances = []
    // @ts-expect-error -- doble de test, no implementa el resto de la interfaz de EventSource.
    globalThis.EventSource = MockEventSource
  })

  afterEach(() => {
    globalThis.EventSource = originalEventSource
    vi.restoreAllMocks()
  })

  it('should open a real EventSource against /api/reportes/eventos, never a setInterval/polling loop', () => {
    const setIntervalSpy = vi.spyOn(globalThis, 'setInterval')

    renderHook(() => useReportEvents('R1'))

    expect(MockEventSource.instances).toHaveLength(1)
    expect(MockEventSource.instances[0].url).toContain('/api/reportes/eventos')
    expect(setIntervalSpy).not.toHaveBeenCalled()
  })

  it('should flip evento to LISTO when the matching SSE event arrives, ignoring events for other ids', async () => {
    const { result } = renderHook(() => useReportEvents('R1'))
    const source = MockEventSource.instances[0]

    expect(result.current.evento).toBeNull()

    act(() => {
      source.emitMessage({ id: 'OTRO-ID', estado: 'LISTO' })
    })
    expect(result.current.evento).toBeNull()

    act(() => {
      source.emitMessage({ id: 'R1', estado: 'LISTO' })
    })

    await waitFor(() => {
      expect(result.current.evento).toEqual({ id: 'R1', estado: 'LISTO' })
    })
  })

  it('should expose the FALLIDO event path with its mensaje', async () => {
    const { result } = renderHook(() => useReportEvents('R1'))
    const source = MockEventSource.instances[0]

    act(() => {
      source.emitMessage({
        id: 'R1',
        estado: 'FALLIDO',
        mensaje: 'Timeout tras reintentos',
      })
    })

    await waitFor(() => {
      expect(result.current.evento).toEqual({
        id: 'R1',
        estado: 'FALLIDO',
        mensaje: 'Timeout tras reintentos',
      })
    })
  })

  it('should set conexionError when the SSE connection errors', async () => {
    const { result } = renderHook(() => useReportEvents('R1'))
    const source = MockEventSource.instances[0]

    expect(result.current.conexionError).toBe(false)

    act(() => {
      source.emitError()
    })

    await waitFor(() => {
      expect(result.current.conexionError).toBe(true)
    })
  })

  it('should close the previous connection and open a new one when idReporte changes', () => {
    const { rerender } = renderHook(
      ({ id }: { id: string | null }) => useReportEvents(id),
      { initialProps: { id: 'R1' } },
    )
    expect(MockEventSource.instances).toHaveLength(1)
    const first = MockEventSource.instances[0]

    rerender({ id: 'R2' })

    expect(first.closed).toBe(true)
    expect(MockEventSource.instances).toHaveLength(2)
    expect(MockEventSource.instances[1].url).toContain('/api/reportes/eventos')
  })

  it('should not open any connection when idReporte is null', () => {
    renderHook(() => useReportEvents(null))
    expect(MockEventSource.instances).toHaveLength(0)
  })
})
