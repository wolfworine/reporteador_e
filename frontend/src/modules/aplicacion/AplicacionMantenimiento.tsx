import { CatalogoMantenimiento } from '../../components/catalogo/CatalogoMantenimiento'
import * as aplicacionesApi from '../../api/aplicaciones'
import type { Aplicacion } from '../../types/aplicacion'

/**
 * Mantenimiento de Aplicación — listar/filtrar/crear/modificar. Replica
 * solo la parte de catálogo de `paginas/aplicacion/mantenimiento.xhtml` +
 * `editar.xhtml` + `modificar.xhtml`; el botón "VerRol" (drill-down a Rol)
 * de esa misma vista legacy es alcance de la feature 5
 * (`crud_rol_suministro_drilldown`), fuera de esta feature.
 */
export function AplicacionMantenimiento() {
  return (
    <CatalogoMantenimiento<Aplicacion>
      titulo="Aplicación"
      queryKey="aplicaciones"
      api={aplicacionesApi}
    />
  )
}
