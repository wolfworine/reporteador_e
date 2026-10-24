import { useEffect, useState } from 'react'
import { buildUrl } from '../../api/client'
import { EVENTOS_PATH } from '../../api/reportes'
import type { ReporteEvento } from '../../types/reporte'

export interface UseReportEventsResult {
  /** Último evento SSE recibido para el `idReporte` observado, o `null` si aún no llegó ninguno. */
  evento: ReporteEvento | null
  /**
   * `true` si `EventSource` no está disponible en el entorno (derivado en
   * tiempo de render) o si la conexión falló/se cortó
   * (`EventSource.onerror`, seteado async por `handleError`). No dispara ningún
   * fallback de polling -- `docs/architecture.md` prohíbe explícitamente un
   * loop de `setInterval` para este flujo. El único fallback de resiliencia
   * contemplado por `docs/architecture.md` ("un único chequeo puntual al
   * volver a enfocar la pestaña") queda **deliberadamente sin implementar
   * en esta feature**: no hay ningún endpoint de "estado puntual" liviano
   * en el contrato mockeado (`GET /api/reportes/{id}/descargar` sirve el
   * binario, no es apto para un chequeo de estado) -- se documenta como gap
   * conocido en `progress/current.md` en vez de inventar un endpoint extra
   * no confirmado para resolverlo.
   */
  conexionError: boolean
}

/**
 * Se suscribe a `GET /api/reportes/eventos` vía la API nativa del navegador
 * `EventSource` (SSE) -- **nunca** `setInterval`/polling, ver
 * `docs/architecture.md` y acceptance criterio 2 de la feature. El stream
 * es compartido por todos los reportes en curso de la sesión, así que este
 * hook filtra en cliente los eventos que no correspondan al `idReporte`
 * pedido por quien lo usa.
 *
 * **Diseño mockeable a propósito**: usa `globalThis.EventSource` (no
 * `window.EventSource` importado/hardcodeado de otra forma), así los tests
 * -- y el propio desarrollo, mientras
 * `../backend/feature_list.json` id=7/8 sigan `pending` -- pueden
 * reemplazar `globalThis.EventSource` por un doble de test antes de montar
 * el componente, mismo patrón que `globalThis.fetch = vi.fn()` usado en
 * toda la suite (ver `docs/conventions.md`).
 *
 * Abre una conexión nueva por `idReporte` (se cierra y reabre si cambia) en
 * vez de una única conexión de por vida a nivel de layout -- simplificación
 * documentada: `docs/architecture.md` sugiere una única conexión
 * compartida a nivel de provider como optimización futura, pero para el
 * alcance de esta feature (una vista de Reportes a la vez) una conexión por
 * `idReporte` observado es funcionalmente equivalente y más simple de
 * probar de forma aislada.
 */
export function useReportEvents(
  idReporte: string | null,
): UseReportEventsResult {
  const [evento, setEvento] = useState<ReporteEvento | null>(null)
  const [conexionError, setConexionError] = useState(false)

  // Ajuste de estado en tiempo de render (no en un efecto) cuando cambia
  // `idReporte`: sigue el patrón recomendado por React para "resetear
  // estado cuando cambia una prop"
  // (https://react.dev/learn/you-might-not-need-an-effect#adjusting-some-state-when-a-prop-changes).
  // `idReporteObservado` guarda el último `idReporte` para el que ya se hizo
  // el reset; si difiere del actual, se hace el reset como parte del mismo
  // render (React permite `setState` condicional durante el render y lo
  // trata como parte del mismo pase, no como un efecto en cascada).
  const [idReporteObservado, setIdReporteObservado] = useState(idReporte)
  if (idReporte !== idReporteObservado) {
    setIdReporteObservado(idReporte)
    setEvento(null)
    setConexionError(false)
  }

  // También derivado en tiempo de render: si `EventSource` no existe en el
  // entorno, no hace falta un efecto para saberlo -- es un chequeo puro de
  // `typeof` sobre el `idReporte` actual, no una respuesta a un evento
  // asíncrono. Se combina con el `conexionError` async (seteado por
  // `handleError` más abajo) en el `return` de este hook.
  const eventSourceNoDisponible =
    idReporte !== null && typeof globalThis.EventSource === 'undefined'

  useEffect(() => {
    if (!idReporte) {
      return
    }

    if (typeof globalThis.EventSource === 'undefined') {
      return
    }

    const source = new globalThis.EventSource(buildUrl(EVENTOS_PATH))

    function handleMessage(message: MessageEvent<string>) {
      let data: ReporteEvento
      try {
        data = JSON.parse(message.data) as ReporteEvento
      } catch {
        // Evento no parseable: se ignora en silencio -- no es una acción
        // del usuario que requiera feedback visible (`role="alert"`), es un
        // dato malformado del stream del servidor.
        return
      }
      if (data.id === idReporte) {
        setEvento(data)
      }
    }

    function handleError() {
      setConexionError(true)
    }

    source.addEventListener('message', handleMessage)
    source.addEventListener('error', handleError)

    return () => {
      source.removeEventListener('message', handleMessage)
      source.removeEventListener('error', handleError)
      source.close()
    }
  }, [idReporte])

  return { evento, conexionError: conexionError || eventSourceNoDisponible }
}
