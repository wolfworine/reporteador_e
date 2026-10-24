import { createContext, useCallback, useContext, useMemo, useState } from 'react'
import type { ReactNode } from 'react'

/**
 * Datos no sensibles del usuario autenticado que la UI necesita mantener
 * en memoria (para el header y para `useMenu(tipoUsuario)`).
 *
 * Deliberadamente NO incluye contraseña ni token en texto plano — nunca se
 * guarda en `localStorage`/`sessionStorage` (regla dura de
 * `docs/architecture.md`). El estado vive solo en memoria (`useState` de
 * React) mientras dura la sesión del tab; se pierde al recargar la página.
 * Esto es una decisión provisional: el mecanismo real de persistencia de
 * sesión (cookie httpOnly, token en memoria + refresh, etc.) depende de lo
 * que el backend exponga en `usuario_login_password_hash_fix`
 * (`../backend/feature_list.json` id=5, `pending`) — ver
 * `progress/current.md`.
 */
export interface AuthUser {
  email: string
  nombre: string
  apellido: string
  /** Determina el menú dinámico vía `GET /api/menu/{tipoUsuario}`. */
  tipo: string
}

interface AuthContextValue {
  user: AuthUser | null
  login: (user: AuthUser) => void
  logout: () => void
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined)

export function AuthProvider({
  children,
  initialUser = null,
}: {
  children: ReactNode
  /** Solo para tests de componente — evita tener que pasar por LoginPage. */
  initialUser?: AuthUser | null
}) {
  const [user, setUser] = useState<AuthUser | null>(initialUser)

  const login = useCallback((nextUser: AuthUser) => {
    setUser(nextUser)
  }, [])

  const logout = useCallback(() => {
    setUser(null)
  }, [])

  const value = useMemo<AuthContextValue>(
    () => ({ user, login, logout }),
    [user, login, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth debe usarse dentro de un <AuthProvider>')
  }
  return context
}
