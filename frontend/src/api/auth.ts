import { apiClient } from './client'
import type { LoginRequest, LoginResponse } from '../types/auth'

/**
 * Llama al endpoint de autenticación.
 *
 * Ruta y forma **confirmadas contra el código fuente real del backend**
 * (`web/AuthResource.java`, 2026-08-19): `POST /api/auth/login` (coincide
 * con la ruta ya elegida acá cuando el contrato todavía estaba
 * `pending`), responde con el `Usuario` plano (`UsuarioResponse`, ver
 * `types/auth.ts#LoginResponse`) — sin wrapper, sin token, por decisión
 * de diseño explícita del backend (no implementa sesión/JWT propio).
 */
export async function login(request: LoginRequest): Promise<LoginResponse> {
  return apiClient<LoginResponse>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify(request),
  })
}
