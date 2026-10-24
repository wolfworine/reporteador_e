import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { LoginPage } from './LoginPage'
import { AuthProvider, useAuth } from '../../app/AuthContext'
import * as authApi from '../../api/auth'
import { ApiError } from '../../api/client'

// Cero HTTP real: se mockea `api/auth.ts` por completo (regla dura de
// `docs/conventions.md`).
vi.mock('../../api/auth')

/** Sonda mínima para leer el estado real de `AuthContext` tras el login,
 * en vez de solo comprobar que se llamó a la API -- eso es exactamente lo
 * que NO habría detectado el bug real del 2026-08-19 (mapeo `data.usuario`
 * cuando el backend devuelve el objeto plano): la llamada a la API
 * "funcionaba" (se resolvía), pero el mapeo posterior tiraba y el usuario
 * nunca quedaba autenticado.
 */
function AuthProbe() {
  const { user } = useAuth()
  return <p>Autenticado: {user ? `${user.nombre} ${user.apellido}` : 'no'}</p>
}

function renderLoginPage() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })

  return render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <MemoryRouter initialEntries={['/login']}>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/" element={<AuthProbe />} />
          </Routes>
        </MemoryRouter>
      </AuthProvider>
    </QueryClientProvider>,
  )
}

describe('LoginPage', () => {
  it('should authenticate and redirect when the backend returns the flat Usuario response', async () => {
    // Forma real de `POST /api/auth/login` (`UsuarioResponse` plano, sin
    // wrapper `{ usuario: ... }` ni token -- confirmado leyendo
    // `web/AuthResource.java`, ver `types/auth.ts#LoginResponse`).
    vi.mocked(authApi.login).mockResolvedValue({
      id: '1',
      nombres: 'Ana',
      apellidos: 'López',
      email: 'ana@entel.pe',
      tipo: 'ADMINISTRADOR',
    })
    const user = userEvent.setup()
    renderLoginPage()

    await user.type(screen.getByLabelText(/correo/i), 'ana@entel.pe')
    await user.type(screen.getByLabelText(/contraseña/i), 'secreta123')
    await user.click(screen.getByRole('button', { name: /ingresar/i }))

    expect(vi.mocked(authApi.login).mock.calls[0][0]).toEqual({
      email: 'ana@entel.pe',
      password: 'secreta123',
    })

    // La aserción que importa: el contexto de auth queda poblado con los
    // datos reales (nombre/apellido) y la app navega a "/" -- si el mapeo
    // de `onSuccess` vuelve a romperse leyendo un campo de `undefined`,
    // este `await` nunca encuentra el texto y el test falla.
    await waitFor(() => {
      expect(screen.getByText('Autenticado: Ana López')).toBeInTheDocument()
    })
  })

  it('should show an inline error message when the credentials are invalid', async () => {
    vi.mocked(authApi.login).mockRejectedValue(
      new ApiError(401, 'Credenciales incorrectas'),
    )
    const user = userEvent.setup()
    renderLoginPage()

    await user.type(screen.getByLabelText(/correo/i), 'ana@entel.pe')
    await user.type(screen.getByLabelText(/contraseña/i), 'mala-clave')
    await user.click(screen.getByRole('button', { name: /ingresar/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Credenciales incorrectas',
    )
  })
})
