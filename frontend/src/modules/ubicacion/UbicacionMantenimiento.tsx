import { CatalogoMantenimiento } from '../../components/catalogo/CatalogoMantenimiento'
import * as ubicacionesApi from '../../api/ubicaciones'
import type { Ubicacion } from '../../types/ubicacion'

/**
 * Mantenimiento de Ubicación — listar/filtrar/crear/modificar. Replica el
 * alcance de `paginas/ubicacion/mantenimiento.xhtml` + `editar.xhtml` +
 * `modificar.xhtml`.
 */
export function UbicacionMantenimiento() {
  return (
    <CatalogoMantenimiento<Ubicacion>
      titulo="Ubicación"
      queryKey="ubicaciones"
      api={ubicacionesApi}
    />
  )
}
