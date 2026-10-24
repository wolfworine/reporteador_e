import { useQuery } from '@tanstack/react-query'
import * as reportesApi from '../../api/reportes'
import { ApiError } from '../../api/client'
import { ReporteFiltro } from './ReporteFiltro'
import { ReporteTabla } from './ReporteTabla'
import { useReporteFlujo } from './useReporteFlujo'
import { useToast } from '../../components/ui/ToastProvider'
import { TableSkeleton } from '../../components/ui/TableSkeleton'

function errorMessage(error: unknown, fallback: string): string {
  if (error instanceof ApiError) {
    return error.message
  }
  if (error instanceof Error) {
    return error.message
  }
  return fallback
}

/**
 * Vista de Reportes completa (feature 8, `reportes_view_export_sse`):
 * filtro (`ReporteFiltro`) → generación asíncrona (202 + id) → notificación
 * SSE (`useReportEvents` vía `useReporteFlujo`) → tabla anidada
 * (`ReporteTabla`) + exportación del formato generado, sin polling y sin
 * recargar la página en ningún punto del flujo.
 *
 * El detalle JSON del reporte (`obtenerReporte`) solo se pide una vez el
 * estado es `LISTO` (`enabled: estado === 'LISTO'`) -- es un endpoint
 * propio del frontend, no confirmado en el backend real, ver
 * `types/reporte.ts` y `progress/current.md` para la justificación
 * completa de esa decisión.
 *
 * **Historial de bugs reales en este flujo de exportación (ver
 * `../../../backend/handoff.md` para el detalle completo):**
 * - 2026-08-20: se descubrió que esta vista mostraba SIEMPRE los dos
 *   botones "Exportar PDF"/"Exportar Excel" aunque `GET
 *   /{id}/descargar?formato=...` ignoraba ese query param (siempre
 *   servía el único binario ya generado) -- pedir el que no coincidía
 *   descargaba el binario correcto con extensión equivocada, "Excel
 *   corrupto". El fix de ESE día fue mostrar un solo botón, el del
 *   formato realmente generado.
 * - 2026-08-25: el usuario señaló que esa solución cambió una UX que
 *   originalmente funcionaba distinto (un solo filtro Aplicación+Área,
 *   siempre 2 botones de exportar) y preguntó por qué se había separado
 *   en "dos consultas". La causa raíz real no era la UI sino que
 *   `/descargar?formato=` nunca se implementó -- ahora sí regenera al
 *   vuelo el formato que no coincide con el ya generado (reusando el
 *   árbol ya persistido, sin volver a pasar por Kafka), así que se
 *   revierte a mostrar SIEMPRE los 2 botones -- esa era la UX correcta
 *   desde el principio, solo le faltaba esta pieza del backend.
 */
export function ReporteVista() {
  const {
    idReporte,
    estado,
    mensajeFallo,
    generarMutation,
    descargarMutation,
    generar,
  } = useReporteFlujo()
  const { mostrarExito, mostrarError } = useToast()

  const reporteQuery = useQuery({
    queryKey: ['reporte', idReporte],
    queryFn: () => reportesApi.obtenerReporte(idReporte as string),
    enabled: estado === 'LISTO' && idReporte !== null,
  })

  function handleDescargar(formato: 'pdf' | 'excel') {
    descargarMutation.mutate(formato, {
      onSuccess: () => mostrarExito('Descarga iniciada.'),
      onError: (error) =>
        mostrarError(errorMessage(error, 'No se pudo descargar el reporte.')),
    })
  }

  return (
    <section className="reporte-vista">
      <h2>Reportes</h2>

      <ReporteFiltro
        onGenerar={generar}
        disabled={generarMutation.isPending || estado === 'GENERANDO'}
      />

      {generarMutation.isError && (
        <p role="alert">
          {errorMessage(
            generarMutation.error,
            'No se pudo iniciar la generación del reporte.',
          )}
        </p>
      )}

      {estado === 'GENERANDO' && (
        <p role="status" className="reporte-vista__estado">
          Generando…
        </p>
      )}

      {estado === 'FALLIDO' && (
        <p role="alert">
          {mensajeFallo ?? 'La generación del reporte falló.'}
        </p>
      )}

      {estado === 'LISTO' && (
        <div className="reporte-vista__resultado">
          <div className="reporte-vista__acciones">
            <p role="status" className="reporte-vista__estado">
              Listo, descargar
            </p>
            <button
              type="button"
              onClick={() => handleDescargar('pdf')}
              disabled={descargarMutation.isPending}
            >
              {descargarMutation.isPending && descargarMutation.variables === 'pdf'
                ? 'Descargando…'
                : 'Exportar PDF'}
            </button>
            <button
              type="button"
              onClick={() => handleDescargar('excel')}
              disabled={descargarMutation.isPending}
            >
              {descargarMutation.isPending && descargarMutation.variables === 'excel'
                ? 'Descargando…'
                : 'Exportar Excel'}
            </button>
          </div>

          {descargarMutation.isError && (
            <p role="alert">
              {errorMessage(
                descargarMutation.error,
                'No se pudo descargar el reporte.',
              )}
            </p>
          )}

          {reporteQuery.isLoading && <TableSkeleton columns={5} rows={3} />}
          {reporteQuery.isError && (
            <p role="alert">
              {errorMessage(
                reporteQuery.error,
                'No se pudo cargar el detalle del reporte.',
              )}
            </p>
          )}
          {reporteQuery.data && <ReporteTabla reportes={reporteQuery.data} />}
        </div>
      )}
    </section>
  )
}
