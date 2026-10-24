import { useMutation } from '@tanstack/react-query'
import { useState } from 'react'
import type { UseMutationResult } from '@tanstack/react-query'
import * as reportesApi from '../../api/reportes'
import type {
  ReporteEvento,
  ReporteFormato,
  ReporteGenerarRequest,
  ReporteGenerarResponse,
} from '../../types/reporte'
import { useReportEvents } from './useReportEvents'

export type ReporteEstadoUI = 'INACTIVO' | 'GENERANDO' | 'LISTO' | 'FALLIDO'

export interface UseReporteFlujoResult {
  idReporte: string | null
  estado: ReporteEstadoUI
  mensajeFallo: string | undefined
  conexionSseError: boolean
  generarMutation: UseMutationResult<
    ReporteGenerarResponse,
    Error,
    ReporteGenerarRequest
  >
  descargarMutation: UseMutationResult<Blob, Error, ReporteFormato>
  generar: (request: ReporteGenerarRequest) => void
  descargar: (formato: ReporteFormato) => void
}

/**
 * Descarga un `Blob` ya obtenido del backend como archivo del navegador, vía
 * un `<a download>` temporal + `URL.createObjectURL` -- sin recargar la
 * página (acceptance criterio 3) y sin depender de que el backend fije
 * `Content-Disposition` para que el navegador dispare la descarga solo, ya
 * que aquí el blob llega vía `fetch`/`apiClientBlob`, no navegación directa.
 */
function descargarBlobComoArchivo(
  blob: Blob,
  idReporte: string,
  formato: ReporteFormato,
): void {
  const extension = formato === 'pdf' ? 'pdf' : 'xlsx'
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `reporte-${idReporte}.${extension}`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}

/**
 * Hook de orquestación del flujo completo de Reportes (generar → esperar
 * evento SSE → descargar), sin lógica de negocio en los componentes de
 * presentación (`docs/conventions.md`). Máquina de estados simple:
 *
 * `INACTIVO` --generar()--> `GENERANDO` --evento SSE `LISTO`--> `LISTO`
 *                                        --evento SSE `FALLIDO`--> `FALLIDO`
 *
 * `useReportEvents` hace el trabajo de escuchar `GET /api/reportes/eventos`
 * vía `EventSource`; este hook solo traduce el evento recibido a un estado
 * de UI y expone las 2 mutaciones (`generar`/`descargar`) que sí pasan por
 * `api/reportes.ts` (TanStack Query para toda mutación de servidor, regla
 * dura de `docs/architecture.md`). La traducción evento -> estado ocurre en
 * tiempo de render (no en un `useEffect`), siguiendo el patrón de React de
 * "ajustar estado cuando cambia una prop"
 * (https://react.dev/learn/you-might-not-need-an-effect#adjusting-some-state-when-a-prop-changes):
 * las transiciones de la máquina de estados de arriba son las mismas, solo
 * cambia el mecanismo por el cual se disparan los `setState`.
 */
export function useReporteFlujo(): UseReporteFlujoResult {
  const [idReporte, setIdReporte] = useState<string | null>(null)
  const [estado, setEstado] = useState<ReporteEstadoUI>('INACTIVO')
  const [mensajeFallo, setMensajeFallo] = useState<string | undefined>(
    undefined,
  )

  const { evento, conexionError } = useReportEvents(idReporte)

  // Ajuste de estado en tiempo de render: `eventoProcesado` guarda la
  // referencia del último `evento` ya traducido a `estado`/`mensajeFallo`.
  // Si `evento` cambió (nueva referencia recibida de `useReportEvents`), se
  // procesa como parte del mismo render en vez de en un efecto -- React
  // permite `setState` condicional durante el render y lo trata como parte
  // del mismo pase, no como un efecto en cascada.
  const [eventoProcesado, setEventoProcesado] = useState<ReporteEvento | null>(
    null,
  )
  if (evento && evento !== eventoProcesado) {
    setEventoProcesado(evento)
    if (evento.estado === 'LISTO') {
      setEstado('LISTO')
      setMensajeFallo(undefined)
    } else if (evento.estado === 'FALLIDO') {
      setEstado('FALLIDO')
      setMensajeFallo(evento.mensaje ?? 'La generación del reporte falló.')
    }
  }

  const generarMutation = useMutation({
    mutationFn: (request: ReporteGenerarRequest) =>
      reportesApi.generar(request),
    onSuccess: (response) => {
      setIdReporte(response.id)
      setEstado('GENERANDO')
      setMensajeFallo(undefined)
    },
  })

  const descargarMutation = useMutation({
    mutationFn: (formato: ReporteFormato) => {
      if (!idReporte) {
        return Promise.reject(
          new Error('No hay ningún reporte listo para descargar.'),
        )
      }
      return reportesApi.descargar(idReporte, formato)
    },
    onSuccess: (blob, formato) => {
      if (idReporte) {
        descargarBlobComoArchivo(blob, idReporte, formato)
      }
    },
  })

  function generar(request: ReporteGenerarRequest): void {
    generarMutation.mutate(request)
  }

  function descargar(formato: ReporteFormato): void {
    descargarMutation.mutate(formato)
  }

  return {
    idReporte,
    estado,
    mensajeFallo,
    conexionSseError: conexionError,
    generarMutation,
    descargarMutation,
    generar,
    descargar,
  }
}
