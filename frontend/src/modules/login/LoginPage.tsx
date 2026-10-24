import { useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { login } from '../../api/auth'
import { ApiError } from '../../api/client'
import { useAuth } from '../../app/AuthContext'

/**
 * Pantalla de login. Reemplaza al formulario JSF de
 * `LoginManagedBean.iniciarSesion()` (email + password). Ver `src/api/auth.ts`
 * y `src/types/auth.ts` para el mapeo detallado del legacy y la decisión
 * (pendiente de reconciliar con el backend) sobre la URL exacta.
 *
 * Manejo de error: `docs/conventions.md` prohíbe un error silencioso solo
 * en consola — cualquier fallo de la mutación se muestra inline con
 * `role="alert"`.
 *
 * Seguridad: `password` solo vive en el estado local del formulario y
 * viaja dentro del body de la request de login; nunca se guarda en
 * `localStorage`/`sessionStorage` ni se loguea en consola.
 *
 * **Corregido 2026-08-19** (bug real, encontrado probando el login en el
 * navegador contra el backend real, no solo con tests mockeados — los
 * tests mockean `api/auth.ts` por completo y nunca ejercitaron el
 * contrato real de red): el mapeo leía `data.usuario.nombres`, pero
 * `data.usuario` es `undefined` -- `POST /api/auth/login` devuelve el
 * `Usuario` plano, sin envolver en `{ usuario: ... }` (ver
 * `web/AuthResource.java`, `types/auth.ts#LoginResponse`). El login
 * fallaba SIEMPRE con credenciales correctas: la request HTTP funcionaba
 * (200 OK), pero `onSuccess` tiraba un `TypeError` leyendo `.email` de
 * `undefined` antes de llegar a `navigate`. Corregido a leer los campos
 * directo de `data`.
 */
export function LoginPage() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const { login: setAuthenticatedUser } = useAuth()
  const navigate = useNavigate()

  const mutation = useMutation({
    mutationFn: login,
    onSuccess: (data) => {
      setAuthenticatedUser({
        email: data.email,
        nombre: data.nombres,
        apellido: data.apellidos,
        tipo: data.tipo,
      })
      navigate('/', { replace: true })
    },
  })

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    mutation.mutate({ email, password })
  }

  return (
    <main className="login-page">
      <form onSubmit={handleSubmit} noValidate>
        <div className="login-page__brand" aria-hidden="true">
          RR
        </div>
        <h1>Repositorio de Roles</h1>
        <p className="login-page__subtitle">Ingresá con tu cuenta corporativa</p>

        <label htmlFor="login-email">Correo</label>
        <input
          id="login-email"
          name="email"
          type="email"
          autoComplete="username"
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          required
        />

        <label htmlFor="login-password">Contraseña</label>
        <input
          id="login-password"
          name="password"
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          required
        />

        <button type="submit" disabled={mutation.isPending}>
          {mutation.isPending ? 'Ingresando…' : 'Ingresar'}
        </button>

        {mutation.isError && (
          <p role="alert" className="login-page__error">
            {mutation.error instanceof ApiError
              ? mutation.error.message
              : 'No se pudo iniciar sesión. Intenta nuevamente.'}
          </p>
        )}
      </form>
    </main>
  )
}
