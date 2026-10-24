/**
 * Cliente HTTP tipado centralizado. Única capa que conoce la URL base del
 * backend y que hace `fetch` directo — `docs/architecture.md` prohíbe
 * `fetch` fuera de `api/*.ts`.
 *
 * Genérico/tipado por diseño, sin funciones por entidad aquí — esas viven
 * en `api/<entidad>.ts` (p. ej. `api/areas.ts`), construidas sobre este
 * cliente una vez confirmada la URL exacta de cada endpoint real del
 * backend (ver `progress/current.md`).
 */

/**
 * Error HTTP tipado lanzado por `apiClient` para cualquier respuesta 4xx/5xx.
 * Permite a quien llama (p. ej. `onError` de una `useMutation` de TanStack
 * Query) distinguir el código de estado y mostrar feedback específico al
 * usuario — nunca un error silencioso solo en consola (ver
 * `docs/conventions.md`).
 */
export class ApiError extends Error {
  /** Código de estado HTTP de la respuesta (4xx/5xx). */
  readonly status: number
  /**
   * Cuerpo de la respuesta de error ya parseado (JSON si el `Content-Type`
   * lo permitía, texto plano en su defecto, `undefined` si no se pudo leer).
   */
  readonly body: unknown

  constructor(status: number, message: string, body?: unknown) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.body = body
  }
}

/**
 * URL base de desarrollo local usada SOLO cuando `VITE_API_BASE_URL` no está
 * definida. No es un valor de producción — ver `.env.example`.
 */
const DEV_FALLBACK_BASE_URL = 'http://localhost:8080'

function resolveBaseUrl(): string {
  const configured = import.meta.env.VITE_API_BASE_URL
  return configured && configured.trim().length > 0
    ? configured
    : DEV_FALLBACK_BASE_URL
}

/**
 * Compone la URL absoluta de un endpoint del backend a partir de
 * `resolveBaseUrl()`. Exportada (no solo de uso interno) porque
 * `modules/reporte/useReportEvents.ts` (feature 8, `reportes_view_export_sse`)
 * necesita construir la URL de `new EventSource(...)` con la misma lógica de
 * base URL que `apiClient`/`apiClientBlob` — `EventSource` no pasa por
 * `fetch`, así que no puede reusar `apiClient` directamente, pero sí debe
 * resolver la URL exactamente igual (mismo `VITE_API_BASE_URL`/fallback de
 * desarrollo) para no duplicar esa lógica ni desincronizarla.
 */
export function buildUrl(path: string): string {
  const baseUrl = resolveBaseUrl().replace(/\/+$/, '')
  const normalizedPath = path.startsWith('/') ? path : `/${path}`
  return `${baseUrl}${normalizedPath}`
}

/**
 * Intenta extraer un mensaje humano-legible del cuerpo de una respuesta de
 * error. Si el cuerpo es texto plano no vacío, lo usa tal cual; si es un
 * objeto, prueba primero el campo `mensaje` (español) y luego `message`
 * (inglés); si no hay nada aprovechable, cae a un mensaje genérico con el
 * status HTTP.
 *
 * **Corregido 09-ago-2026** (feature `crud_catalogos_simples`): el backend
 * real (`../backend/src/main/java/pe/edu/cibertec/proyemp/web/exception/CatalogoExceptionMappers.java`,
 * `record ErrorResponse(String mensaje)`) devuelve el mensaje de error en un
 * campo `mensaje`, no `message` — sin este fix, el mensaje real de error
 * del backend nunca llegaba al usuario y siempre caía al genérico
 * `HTTP {status} al llamar {path}`. Se conserva el soporte de `message`
 * (inglés) por si algún endpoint futuro lo usa; `mensaje` tiene prioridad
 * porque es el contrato confirmado del backend real.
 */
function resolveErrorMessage(
  status: number,
  path: string,
  body: unknown,
): string {
  if (typeof body === 'string' && body.trim().length > 0) {
    return body
  }
  if (body && typeof body === 'object') {
    const mensaje = (body as { mensaje?: unknown }).mensaje
    if (typeof mensaje === 'string' && mensaje.trim().length > 0) {
      return mensaje
    }
    const message = (body as { message?: unknown }).message
    if (typeof message === 'string' && message.trim().length > 0) {
      return message
    }
  }
  return `HTTP ${status} al llamar ${path}`
}

async function readErrorBody(response: Response): Promise<unknown> {
  try {
    return await response.clone().json()
  } catch {
    try {
      return await response.text()
    } catch {
      return undefined
    }
  }
}

/**
 * Fetch wrapper tipado genérico. Lee la URL base de
 * `import.meta.env.VITE_API_BASE_URL` (con fallback de desarrollo, nunca un
 * valor de producción hardcodeado), agrega `Content-Type`/`Accept:
 * application/json` por defecto (permite override vía `options.headers`), y
 * lanza `ApiError` de forma consistente para cualquier respuesta no-ok
 * (4xx/5xx).
 *
 * @param path Ruta relativa a la URL base (p. ej. `/api/areas`).
 * @param options `RequestInit` estándar, igual que el `fetch` nativo.
 */
export async function apiClient<T>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
  const headers = new Headers(options.headers)
  if (!headers.has('Accept')) {
    headers.set('Accept', 'application/json')
  }
  if (!headers.has('Content-Type') && options.body !== undefined) {
    headers.set('Content-Type', 'application/json')
  }

  const response = await fetch(buildUrl(path), { ...options, headers })

  if (!response.ok) {
    const body = await readErrorBody(response)
    throw new ApiError(
      response.status,
      resolveErrorMessage(response.status, path, body),
      body,
    )
  }

  if (response.status === 204) {
    return undefined as T
  }

  return (await response.json()) as T
}

/**
 * Variante de `apiClient` para respuestas binarias (`Blob`) — usada por
 * `api/reportes.ts#descargar` (`GET /api/reportes/{id}/descargar`, feature 8),
 * que devuelve el archivo PDF/Excel con `Content-Disposition: attachment`,
 * no JSON. `apiClient` no sirve aquí porque siempre intenta
 * `response.json()`; esta variante hace `response.blob()` en su lugar pero
 * comparte el mismo manejo uniforme de error 4xx/5xx (mismo `ApiError`,
 * mismo `resolveErrorMessage`) que `apiClient`, incluyendo el caso 404
 * documentado del endpoint (id inexistente o reporte aún no `LISTO`).
 */
export async function apiClientBlob(
  path: string,
  options: RequestInit = {},
): Promise<Blob> {
  const headers = new Headers(options.headers)

  const response = await fetch(buildUrl(path), { ...options, headers })

  if (!response.ok) {
    const body = await readErrorBody(response)
    throw new ApiError(
      response.status,
      resolveErrorMessage(response.status, path, body),
      body,
    )
  }

  return await response.blob()
}
