import { CatalogoMantenimiento } from '../../components/catalogo/CatalogoMantenimiento'
import * as estadosApi from '../../api/estados'
import type { Estado } from '../../types/estado'

/**
 * Mantenimiento de Estado — listar/filtrar/crear/modificar. Sin vista
 * legacy propia que replicar (`Estado` no tenía managed bean en el JSF
 * original, ver `types/estado.ts`), pero el backend real expone CRUD
 * completo (`EstadoResource.java`) y es uno de los 5 catálogos simples de
 * esta feature — se le da el mismo mantenimiento que a los otros 4 por
 * paridad y consistencia, no por replicar una vista que no existía.
 */
export function EstadoMantenimiento() {
  return (
    <CatalogoMantenimiento<Estado>
      titulo="Estado"
      queryKey="estados"
      api={estadosApi}
    />
  )
}
