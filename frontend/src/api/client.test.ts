import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, apiClient } from './client'

describe('apiClient', () => {
  const originalFetch = globalThis.fetch

  beforeEach(() => {
    globalThis.fetch = vi.fn()
  })

  afterEach(() => {
    globalThis.fetch = originalFetch
    vi.restoreAllMocks()
  })

  it('should return the parsed JSON body when the response is ok', async () => {
    const payload = { codigo: 1, id: 'SIS', nombre: 'Sistemas' }
    vi.mocked(globalThis.fetch).mockResolvedValue(
      new Response(JSON.stringify(payload), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    const result = await apiClient<typeof payload>('/api/areas/1')

    expect(result).toEqual(payload)
    expect(globalThis.fetch).toHaveBeenCalledTimes(1)
    const [calledUrl] = vi.mocked(globalThis.fetch).mock.calls[0]
    expect(String(calledUrl)).toContain('/api/areas/1')
  })

  it('should default to the dev fallback base URL when VITE_API_BASE_URL is not set', async () => {
    vi.mocked(globalThis.fetch).mockResolvedValue(
      new Response('{}', { status: 200 }),
    )

    await apiClient('/api/areas')

    const [calledUrl] = vi.mocked(globalThis.fetch).mock.calls[0]
    expect(String(calledUrl)).toMatch(/^http:\/\/localhost:8080\/api\/areas$/)
  })

  it('should throw an ApiError carrying the status and message when the response is a 4xx client error', async () => {
    vi.mocked(globalThis.fetch).mockResolvedValue(
      new Response(JSON.stringify({ message: 'Área no encontrada' }), {
        status: 404,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    const error = await apiClient('/api/areas/999').catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ status: 404, message: 'Área no encontrada' })
  })

  it('should throw an ApiError using the backend "mensaje" field when the response is a 4xx client error', async () => {
    vi.mocked(globalThis.fetch).mockResolvedValue(
      new Response(JSON.stringify({ mensaje: 'Algo no válido' }), {
        status: 400,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    const error = await apiClient('/api/areas').catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ status: 400, message: 'Algo no válido' })
  })

  it('should prefer the "mensaje" field over "message" when the response body has both', async () => {
    vi.mocked(globalThis.fetch).mockResolvedValue(
      new Response(
        JSON.stringify({
          mensaje: 'Prioridad español',
          message: 'Priority english',
        }),
        {
          status: 400,
          headers: { 'Content-Type': 'application/json' },
        },
      ),
    )

    const error = await apiClient('/api/areas').catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ status: 400, message: 'Prioridad español' })
  })

  it('should throw an ApiError carrying the status when the response is a 5xx server error', async () => {
    vi.mocked(globalThis.fetch).mockResolvedValue(
      new Response('Internal error', { status: 500 }),
    )

    const error = await apiClient('/api/areas').catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(500)
    expect((error as ApiError).message).toBe('Internal error')
  })

  it('should return undefined for a 204 No Content response without attempting to parse JSON', async () => {
    vi.mocked(globalThis.fetch).mockResolvedValue(
      new Response(null, { status: 204 }),
    )

    const result = await apiClient('/api/areas/1')

    expect(result).toBeUndefined()
  })
})
