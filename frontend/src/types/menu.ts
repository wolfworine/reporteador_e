/**
 * Espejo TypeScript de `domain/MenuNodo.java` -- lo que devuelve de verdad
 * `GET /api/menu/{tipoUsuario}`.
 *
 * **Corregido 2026-08-19** (bug real, mismo patrón que el de login/reportes
 * de esta sesión: contrato nunca verificado contra el backend real, solo
 * mockeado en tests): la forma anterior de este archivo modelaba una fila
 * PLANA de la tabla legacy `menu` (`codigo: number`, `tipo`, `tipoUsuario`,
 * `codSubmenu`, `estado`, `action`) que el cliente arma en árbol con
 * `modules/menu/buildMenuTree.ts` usando `codSubmenu`. El backend real
 * (`web/MenuResource.java`) **ya arma el árbol en el servidor** y solo
 * expone los campos que la UI necesita para pintar: `codigo` es `String`
 * (no `number`), no hay `tipo`/`tipoUsuario`/`codSubmenu`/`estado` en la
 * respuesta (se usan para filtrar/enlazar del lado del servidor, nunca
 * viajan al cliente), y los hijos ya vienen anidados en `hijos`. Confirmado
 * migrando datos reales de menú y llamando al endpoint real -- antes de
 * esto, `Layout.tsx#MenuTree` nunca había recibido datos reales, solo
 * fixtures mockeados con la forma equivocada.
 */
export interface MenuNodo {
  codigo: string
  nombre: string
  action: string | null
  hijos: MenuNodo[]
}
