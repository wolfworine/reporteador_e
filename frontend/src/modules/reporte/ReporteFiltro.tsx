import { useState } from 'react'
import type { FormEvent } from 'react'
import { useQuery } from '@tanstack/react-query'
import * as areasApi from '../../api/areas'
import * as aplicacionesApi from '../../api/aplicaciones'
import type { ReporteGenerarRequest } from '../../types/reporte'

export interface ReporteFiltroProps {
  /** Dispara `POST /api/reportes/generar` (vía `useReporteFlujo#generar`) con la selección Área + Aplicación. */
  onGenerar: (request: ReporteGenerarRequest) => void
  disabled?: boolean
}

/**
 * Formulario de filtro Área + Aplicación -- replica
 * `paginas/reportes/reporte.xhtml` (confirmado leyendo el archivo
 * directamente): 2 `h:selectOneMenu` requeridos (`Aplicacion`/`Area`,
 * poblados desde `aplicacionManagedBean.aplicaciones`/
 * `areaManagedBean.areas`) y un botón "Reporte" que dispara la generación
 * (`reporteManagedBean.reporte`). Las opciones de los selects se reusan de
 * `api/areas.ts`/`api/aplicaciones.ts` (feature 4) vía `useQuery` -- server
 * data siempre por TanStack Query, nunca `fetch` directo (regla dura de
 * `docs/architecture.md`).
 *
 * **Historial:** el 2026-08-20 se agregó acá un tercer `<select>` de
 * Formato para arreglar un bug real ("formato debe ser pdf o excel,
 * recibido: null") -- `POST /generar` exige `formato` y el filtro nunca
 * lo mandaba. Esa solución funcionaba pero cambió la UX: obligaba a
 * elegir PDF/Excel ANTES de generar, y la vista de resultado solo
 * mostraba el botón de exportar del formato ya generado (pedir el otro
 * exigía generar todo de nuevo). El usuario señaló 2026-08-25 que
 * originalmente el filtro solo pedía Aplicación+Área y la vista de
 * resultado siempre mostraba los 2 botones "Exportar PDF"/"Exportar
 * Excel" -- eso resultó ser la UX correcta, solo que mal implementada
 * (el `formato` de `/generar` nunca se usó para servir el binario
 * correcto en `/descargar`, ver `../../../backend/handoff.md`). Con
 * `GET /descargar?formato=` ahora regenerando al vuelo el formato que no
 * coincide con el ya generado, el `formato` de ESTE filtro deja de
 * importarle al usuario -- se manda siempre `"pdf"` (cualquier valor
 * válido serviría, es solo cuál de los dos binarios queda cacheado
 * primero) y se elimina el tercer `<select>`.
 *
 * El submit NO genera el reporte de forma síncrona: delega a `onGenerar`
 * (implementado por `useReporteFlujo` en `ReporteVista`), que dispara
 * `POST /api/reportes/generar` (202 + id) y pasa a esperar el evento SSE --
 * el flujo es 100% asíncrono desde el día uno, no hay versión síncrona
 * previa (ver `docs/architecture.md`).
 */
export function ReporteFiltro({ onGenerar, disabled }: ReporteFiltroProps) {
  const areasQuery = useQuery({ queryKey: ['areas'], queryFn: areasApi.listar })
  const aplicacionesQuery = useQuery({
    queryKey: ['aplicaciones'],
    queryFn: aplicacionesApi.listar,
  })

  const [areaCodigo, setAreaCodigo] = useState('')
  const [aplicacionCodigo, setAplicacionCodigo] = useState('')

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    onGenerar({ idAplicacion: aplicacionCodigo, idArea: areaCodigo, formato: 'pdf' })
  }

  return (
    <form aria-label="Filtrar Reporte" onSubmit={handleSubmit}>
      <label htmlFor="reporte-filtro-aplicacion">Aplicacion</label>
      <select
        id="reporte-filtro-aplicacion"
        value={aplicacionCodigo}
        onChange={(event) => setAplicacionCodigo(event.target.value)}
        required
      >
        <option value="">Selecciona uno</option>
        {(aplicacionesQuery.data ?? []).map((aplicacion) => (
          <option key={aplicacion.codigo} value={aplicacion.codigo}>
            {aplicacion.nombre}
          </option>
        ))}
      </select>

      <label htmlFor="reporte-filtro-area">Area</label>
      <select
        id="reporte-filtro-area"
        value={areaCodigo}
        onChange={(event) => setAreaCodigo(event.target.value)}
        required
      >
        <option value="">Selecciona uno</option>
        {(areasQuery.data ?? []).map((area) => (
          <option key={area.codigo} value={area.codigo}>
            {area.nombre}
          </option>
        ))}
      </select>

      {(areasQuery.isError || aplicacionesQuery.isError) && (
        <p role="alert">
          No se pudieron cargar las opciones de Área/Aplicación.
        </p>
      )}

      <button type="submit" disabled={disabled}>
        Reporte
      </button>
    </form>
  )
}
