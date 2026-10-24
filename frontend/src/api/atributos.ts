import { apiClient } from './client'
import type { Atributo, CategoriaSuministro } from '../types/atributo'

/**
 * Body de request de `POST /api/atributos` — espejo exacto de
 * `AtributoRequest` del backend
 * (`../backend/src/main/java/pe/edu/cibertec/proyemp/web/dto/AtributoRequest.java`):
 * `record AtributoRequest(String aplicacionCodigo, String rolId, String
 * suministroId, String categoria, String atributoCodigo, String
 * atributoNombre, String estado)`. `categoria` y `estado` viajan como
 * `String` (el backend los parsea con `Enum.valueOf(...toUpperCase())`,
 * ver `AtributoService.parseCategoriaYEstado`), no como los tipos union de
 * `types/atributo.ts` directamente, aunque en la práctica solo se envían
 * los 4/2 valores válidos de esos enums.
 */
export interface AtributoRequest {
  aplicacionCodigo: string
  rolId: string
  suministroId: string
  categoria: CategoriaSuministro
  atributoCodigo: string
  atributoNombre: string
  estado: string
}

/**
 * Cliente de lectura de Atributo contra el backend real
 * (`../backend/src/main/java/pe/edu/cibertec/proyemp/web/AtributoResource.java`,
 * `@Path("/api/atributos")`). Solo se implementa
 * `GET /api/atributos/rol/{idRol}/categoria/{categoria}` — el endpoint
 * necesario para la acción "Ver Atributo" de esta feature (acceptance
 * criterio 2). El resto de consultas (`GET /api/atributos?aplicacion=&area=&cargo=`
 * y el CRUD completo de Atributo/Detalle) son alcance de la feature 6
 * (`crud_atributo_detalle_tabs`), no de esta.
 *
 * **Limitación real del backend:** el repositorio consulta por
 * `GSI1PK = APLICACION#<aplicacion>#AREA#<area>` (`AtributoRepository.
 * listarPorRolYCategoria`), con `area` resuelta del propio Rol pero
 * `aplicacion` viniendo **solo** del query param opcional `aplicacion` de
 * este endpoint — si no se pasa, la key queda `APLICACION#null#AREA#...` y
 * la consulta no matchea ningún dato real. Ni `Rol` ni `Suministro`
 * (`types/rol.ts`/`types/suministro.ts`) exponen un `aplicacionCodigo`.
 *
 * **Bug real 2026-08-25 ("la vista de Atributos del Suministro no trae
 * data"):** `AtributoTabs.tsx` llamaba a esta función sin `aplicacion` —
 * la consulta SIEMPRE volvía vacía, sin importar el Rol/Suministro/
 * categoría elegidos (confirmado con `curl` contra el backend real: mismas
 * filas, `[]` sin el param, datos reales con `?aplicacion=1`). Corregido
 * resolviendo `aplicacion` desde el catálogo de Aplicaciones (`AtributoTabs.tsx`
 * usa la primera — hoy el sistema real solo tiene una, "PORTAL", así que no
 * hay ambigüedad que resolver con un selector propio). Si en el futuro
 * existe más de una Aplicación real, esto necesita un selector explícito en
 * la UI en vez de tomar "la primera".
 */
export function listarPorRolYCategoria(
  idRol: string,
  categoria: CategoriaSuministro,
  aplicacion?: string,
): Promise<Atributo[]> {
  const query =
    aplicacion && aplicacion.trim().length > 0
      ? `?aplicacion=${encodeURIComponent(aplicacion)}`
      : ''
  return apiClient<Atributo[]>(
    `/api/atributos/rol/${encodeURIComponent(idRol)}/categoria/${categoria}${query}`,
  )
}

/**
 * `POST /api/atributos` — único endpoint de escritura que expone
 * `AtributoResource.java` para Atributo (confirmado leyendo el archivo
 * real: el propio Javadoc del recurso dice "No expone CRUD completo...
 * solo POST para poblar datos de prueba"). **No existe `PUT`** — la
 * feature 6 (`crud_atributo_detalle_tabs`, `AtributoTabs.tsx`) implementa
 * "crear" por pestaña con esta función y documenta la ausencia de
 * "modificar" como gap real del backend, no como omisión del frontend.
 */
export function crear(request: AtributoRequest): Promise<Atributo> {
  return apiClient<Atributo>('/api/atributos', {
    method: 'POST',
    body: JSON.stringify(request),
  })
}
