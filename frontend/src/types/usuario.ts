/**
 * Espejo TypeScript de los DTOs reales del backend Quarkus y del enum de
 * dominio, confirmados leyendo el código fuente antes de esta feature
 * (`crud_usuario`):
 * - `../backend/src/main/java/pe/edu/cibertec/proyemp/web/dto/UsuarioResponse.java`
 * - `../backend/src/main/java/pe/edu/cibertec/proyemp/web/dto/UsuarioRequest.java`
 * - `../backend/src/main/java/pe/edu/cibertec/proyemp/domain/TipoUsuario.java`
 *
 * **Corregido 16-ago-2026** (feature `crud_usuario`): el modelado original
 * de la feature 2 asumía la entidad JPA legacy directa (`id: number`,
 * `nombre`/`apellido` en singular, `tipo: string` de 1 carácter sin enum).
 * El contrato real es distinto — mismo patrón de corrección ya aplicado a
 * `types/{area,cargo,ubicacion,aplicacion,estado,rol,suministro,atributo}.ts`
 * en las features 4/5:
 * - `id` es `String` (no `number`).
 * - los campos son `nombres`/`apellidos` (plural, tal como los nombra el
 *   record Java), no `nombre`/`apellido`.
 * - `tipo` es el enum `TipoUsuario` (`ADMINISTRADOR`|`OPERADOR`), no un
 *   `string` de 1 carácter (`e_tipo` legacy: `"A"` = Administrador,
 *   cualquier otro = Operador — ver `domain/TipoUsuario.java`).
 *
 * **Seguridad (acceptance criterio 1 de esta feature):** `Usuario` (el tipo
 * de lectura/listado de este archivo) deliberadamente NO tiene un campo
 * `password`/`passwordHash`. El backend real (`UsuarioResponse.java`,
 * confirmado leyendo el archivo fuente) nunca serializa el dominio
 * `Usuario` crudo en ninguna respuesta — ningún endpoint de
 * `UsuarioResource.java` puede devolver ese campo aunque quisiera — así que
 * es estructuralmente imposible que este tipo modele un campo que el
 * backend no puede enviar. La contraseña en texto plano solo existe en
 * `CrearUsuarioRequest`, usada exclusivamente como body de
 * `POST /api/usuarios` (ver `api/usuarios.ts`).
 */

/**
 * Paridad con `domain/TipoUsuario.java` (legacy `e_tipo`: `"A"` =
 * Administrador, cualquier otro valor = Operador).
 */
export type TipoUsuario = 'ADMINISTRADOR' | 'OPERADOR'

/**
 * Tipo de lectura/listado — espejo exacto de `UsuarioResponse.java`
 * (`record UsuarioResponse(String id, String nombres, String apellidos,
 * String email, TipoUsuario tipo)`). Nunca incluye password/hash — ver
 * nota de seguridad arriba.
 */
export interface Usuario {
  id: string
  nombres: string
  apellidos: string
  email: string
  tipo: TipoUsuario
}

/**
 * Body de creación (o cambio de contraseña) de un `Usuario` — espejo
 * exacto de `UsuarioRequest.java` (`record UsuarioRequest(String nombres,
 * String apellidos, String email, String password, TipoUsuario tipo)`).
 * Único tipo de esta feature con la contraseña en texto plano; viaja solo
 * dentro del body HTTPS de `POST /api/usuarios` (`api/usuarios.ts`) —
 * nunca en query string, nunca logueada en consola, nunca persistida en
 * `localStorage`/`sessionStorage`.
 */
export interface CrearUsuarioRequest {
  nombres: string
  apellidos: string
  email: string
  /**
   * Contraseña en texto plano. Viaja solo dentro del body HTTPS de la
   * request de creación — el backend la hashea del lado del servidor
   * (`UsuarioService.crear` vía `PasswordHasher`), nunca se persiste ni se
   * devuelve en ninguna respuesta.
   */
  password: string
  tipo: TipoUsuario
}
