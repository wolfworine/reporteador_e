import type { Usuario } from './usuario'

/**
 * Tipos del flujo de autenticación, basados en el `LoginManagedBean` legacy
 * (`pe.edu.cibertec.proyemp.managedbean.LoginManagedBean.iniciarSesion()`)
 * y en el modelo `Usuario` ya existente en `types/usuario.ts`.
 *
 * Mapeo Login.java / LoginManagedBean.java -> tipos TS:
 * - `model/Login.java` (tabla `login`) solo tiene `(id, email, password)` —
 *   es la tabla de credenciales, no lo que viaja en el request real. El
 *   formulario JSF (`LoginManagedBean.email` / `.password`) es la forma
 *   real del "request": dos campos de texto, `email` y `password` en texto
 *   plano tal como los tipea el usuario.
 * - `LoginManagedBean.iniciarSesion()` hashea `password` con SHA-256
 *   (`StringMD.getStringMessageDigest`, sin sal) ANTES de consultar
 *   `LoginRepository.findbyEmailandPassword(email, encriptado)` — ese
 *   hashing ocurre en el servidor legacy, nunca en el cliente. El frontend
 *   nuevo replica esa responsabilidad: envía `password` en texto plano
 *   dentro del body HTTPS y deja el hashing/verificación al backend.
 * - Si hay match, el legacy guarda el `Usuario` completo en sesión JSF
 *   (`e_tipo`, `a_firstname`, `b_lastname`, etc.) — de ahí `LoginResponse`
 *   devuelve un `Usuario` (tipo ya existente, sin campo password).
 *
 * **Corregido 2026-08-19** (coordinación cross-track, encontrado al probar
 * el login real en el navegador contra el backend real -- no en tests,
 * que mockean `api/auth.ts` y nunca ejercitaron el contrato real): el
 * contrato quedó documentado como "pendiente de reconciliar" en la
 * feature `crud_usuario` del frontend, pero **nunca se reconcilió de
 * verdad** aunque `usuario_login_password_hash_fix` (backend, id 5) ya
 * estaba `done` desde antes. `web/AuthResource.java#login` (leído
 * directamente) devuelve `UsuarioResponse` tal cual -- el mismo record
 * plano que `POST /api/usuarios`, **sin envolver en `{ usuario: ... }`**
 * y **sin token** (decisión de diseño explícita y documentada del
 * backend: "este endpoint deliberadamente NO inventa un mecanismo de
 * sesión/JWT propio"). `LoginResponse` se corrige para ser exactamente
 * `Usuario`, no un wrapper.
 */
export interface LoginRequest {
  email: string
  /**
   * Contraseña en texto plano. Viaja solo dentro del body HTTPS de la
   * request de login — jamás en `localStorage`, `sessionStorage`, query
   * string, ni logs de consola (regla dura de `docs/architecture.md`).
   */
  password: string
}

/**
 * Espejo exacto de lo que devuelve `POST /api/auth/login` -- el mismo
 * `UsuarioResponse` plano que `POST /api/usuarios` (ver `types/usuario.ts`),
 * sin wrapper ni token. Alias explícito (no solo `= Usuario`) para que el
 * significado ("esto es la respuesta de login") quede claro en el sitio
 * de uso (`api/auth.ts`, `LoginPage.tsx`).
 */
export type LoginResponse = Usuario
