import { CatalogoMantenimiento } from '../../components/catalogo/CatalogoMantenimiento'
import * as cargosApi from '../../api/cargos'
import type { Cargo } from '../../types/cargo'

/**
 * Mantenimiento de Cargo — listar/filtrar/crear/modificar. Replica el
 * alcance de `paginas/cargo/mantenimiento.xhtml` + `editar.xhtml` +
 * `modificar.xhtml`.
 */
export function CargoMantenimiento() {
  return (
    <CatalogoMantenimiento<Cargo>
      titulo="Cargo"
      queryKey="cargos"
      api={cargosApi}
    />
  )
}
