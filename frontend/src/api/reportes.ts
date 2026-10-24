import { apiClient, apiClientBlob } from './client'
import type {
  Reporte,
  ReporteFormato,
  ReporteGenerarRequest,
  ReporteGenerarResponse,
} from '../types/reporte'

/**
 * Cliente del flujo asíncrono de Reportes. Las features de backend
 * correspondientes (7-10) están `done` desde hace días -- el comentario
 * anterior de este archivo decía lo contrario (`pending`) y quedó
 * desactualizado; el contrato real de `generar()` se corrigió 2026-08-20
 * tras un bug real (ver `types/reporte.ts#ReporteGenerarRequest`).
 */

const BASE_PATH = '/api/reportes'

/** `POST /api/reportes/generar` → `202 Accepted` + `{id}` inmediato (nunca el binario en la misma respuesta). Confirmado en `../backend/feature_list.json` id=7. */
export function generar(
  request: ReporteGenerarRequest,
): Promise<ReporteGenerarResponse> {
  return apiClient<ReporteGenerarResponse>(`${BASE_PATH}/generar`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

/**
 * `GET /api/reportes/{id}` → `Reporte[]` (árbol Cargo→Rol→4 categorías) para
 * poblar `ReporteTabla` en pantalla una vez el reporte está `LISTO`.
 * **No confirmado en el backend real** — extrapolación propia documentada
 * en `types/reporte.ts` (los 3 endpoints confirmados no devuelven JSON apto
 * para pantalla, solo el binario de descarga). Ajustar/confirmar cuando
 * las features 7/8 del backend aterricen.
 */
export function obtenerReporte(id: string): Promise<Reporte[]> {
  return apiClient<Reporte[]>(`${BASE_PATH}/${encodeURIComponent(id)}`)
}

/**
 * `GET /api/reportes/{id}/descargar?formato=pdf|excel` → el binario (blob)
 * con `Content-Disposition: attachment` si el reporte ya está `LISTO`; el
 * backend documentado devuelve `404` si el id no existe o el reporte aún no
 * está listo (`apiClientBlob` propaga eso como `ApiError` con `status:
 * 404`, mismo manejo uniforme que el resto de la app). Confirmado en
 * `../backend/feature_list.json` id=8; el query param `formato` es decisión
 * propia documentada en `types/reporte.ts`.
 */
export function descargar(id: string, formato: ReporteFormato): Promise<Blob> {
  const query = new URLSearchParams({ formato }).toString()
  return apiClientBlob(`${BASE_PATH}/${encodeURIComponent(id)}/descargar?${query}`)
}

/**
 * Ruta relativa de `GET /api/reportes/eventos` (stream SSE). Exportada como
 * constante (no una función que arme la URL absoluta) porque
 * `useReportEvents` necesita pasarla por `buildUrl` de `api/client.ts` para
 * construir `new EventSource(...)` con la misma resolución de base URL que
 * el resto de la app — ver `modules/reporte/useReportEvents.ts`.
 */
export const EVENTOS_PATH = `${BASE_PATH}/eventos`
