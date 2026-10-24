import { CatalogoMantenimiento } from '../../components/catalogo/CatalogoMantenimiento'
import * as areasApi from '../../api/areas'
import type { Area } from '../../types/area'

/**
 * Mantenimiento de Área — listar/filtrar/crear/modificar. Replica el
 * alcance de `paginas/area/mantenimiento.xhtml` + `editar.xhtml` +
 * `modificar.xhtml` (ver `components/catalogo/CatalogoMantenimiento.tsx`
 * para el detalle de las diferencias deliberadas frente al legacy).
 */
export function AreaMantenimiento() {
  return (
    <CatalogoMantenimiento<Area>
      titulo="Área"
      queryKey="areas"
      api={areasApi}
    />
  )
}
