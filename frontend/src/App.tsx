import { RouterProvider } from 'react-router-dom'
import './App.css'
import { AuthProvider } from './app/AuthContext'
import { router } from './app/router'
import { ToastProvider } from './components/ui/ToastProvider'

/**
 * Reemplaza al placeholder de la Fase 0 (bootstrap). `AuthProvider` vive
 * aquí (fuera del router) porque `router.tsx` usa `useAuth` dentro de la
 * ruta protegida. `QueryClientProvider` sigue envolviendo `<App />` en
 * `main.tsx` (feature 2, sin cambios). `ToastProvider` (overhaul de diseño
 * 2026-08-20) también fuera del router para que las notificaciones
 * sobrevivan a la navegación entre pantallas.
 */
function App() {
  return (
    <ToastProvider>
      <AuthProvider>
        <RouterProvider router={router} />
      </AuthProvider>
    </ToastProvider>
  )
}

export default App
