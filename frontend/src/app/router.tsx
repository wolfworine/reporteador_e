import { createBrowserRouter, Navigate } from 'react-router-dom'
import type { ReactNode } from 'react'
import { Layout } from '../components/layout/Layout'
import { LoginPage } from '../modules/login/LoginPage'
import { AreaMantenimiento } from '../modules/area/AreaMantenimiento'
import { CargoMantenimiento } from '../modules/cargo/CargoMantenimiento'
import { UbicacionMantenimiento } from '../modules/ubicacion/UbicacionMantenimiento'
import { AplicacionMantenimiento } from '../modules/aplicacion/AplicacionMantenimiento'
import { EstadoMantenimiento } from '../modules/estado/EstadoMantenimiento'
import { RolMantenimiento } from '../modules/rol/RolMantenimiento'
import { SuministroPorRol } from '../modules/suministro/SuministroPorRol'
import { AtributoTabs } from '../modules/atributo/AtributoTabs'
import { UsuarioMantenimiento } from '../modules/usuario/UsuarioMantenimiento'
import { ReporteVista } from '../modules/reporte/ReporteVista'
import { useAuth } from './AuthContext'

/**
 * Ruta raíz protegida: redirige a `/login` si no hay usuario autenticado
 * (`AuthContext`). No hay concepto de token/expiración todavía porque el
 * backend aún no define ese mecanismo (`usuario_login_password_hash_fix`,
 * `../backend/feature_list.json` id=5, `pending`) — la única condición hoy
 * es "hay un `user` en memoria".
 */
function ProtectedRoute({ children }: { children: ReactNode }) {
  const { user } = useAuth()
  if (!user) {
    return <Navigate to="/login" replace />
  }
  return children
}

/**
 * `/login` y una raíz protegida que renderiza `Layout` (header/sidebar/
 * contenido + menú dinámico). Las rutas hijas de los 5 catálogos simples
 * (feature 4, `crud_catalogos_simples`) se agregan aquí dentro de la raíz
 * protegida; los módulos de cadena compleja (Rol/Suministro/Atributo,
 * feature 5+) agregarán las suyas de la misma forma.
 *
 * `roles` / `roles/:idRol/suministros` / `roles/:idRol/suministros/:idSuministro/atributos`
 * (feature 5, `crud_rol_suministro_drilldown`) replican la navegación
 * anidada real del legacy (Rol → VerSuministros → Suministro → VerAtributo,
 * ver `paginas/rol/mantenimiento.xhtml` y `mantenimiento_suministro.xhtml`)
 * con un esquema de ruta anidado por `idRol`/`idSuministro` en vez del
 * `@SessionScoped` managed bean del legacy. La última ruta renderizaba
 * `AtributoPorSuministro` (placeholder de solo lectura, categoría ROLES
 * fija) hasta la feature 6 (`crud_atributo_detalle_tabs`), que la
 * reemplazó por `AtributoTabs` (las 4 pestañas Roles/Grupos/Inbox/
 * DefaultInbox) — ver `progress/current.md` para el criterio.
 *
 * `usuarios` (feature 7, `crud_usuario`) renderiza `UsuarioMantenimiento`
 * — listado + filtro + creación únicamente, sin ruta de edición: el
 * backend real no expone `PUT /api/usuarios/{id}` (ver
 * `modules/usuario/UsuarioMantenimiento.tsx` para el detalle del gap).
 *
 * `reportes` (feature 8, `reportes_view_export_sse`) renderiza
 * `ReporteVista` — filtro Área+Aplicación, generación asíncrona (202 + id),
 * notificación SSE (`useReportEvents`, `EventSource` real -- mockeado en
 * desarrollo/tests porque `../backend/feature_list.json` id=7/8 siguen
 * `pending`, ver `progress/current.md`) y exportación PDF/Excel.
 */
export const router = createBrowserRouter([
  {
    path: '/login',
    element: <LoginPage />,
  },
  {
    path: '/',
    element: (
      <ProtectedRoute>
        <Layout />
      </ProtectedRoute>
    ),
    children: [
      {
        index: true,
        element: <p>Selecciona una opción del menú.</p>,
      },
      {
        path: 'areas',
        element: <AreaMantenimiento />,
      },
      {
        path: 'cargos',
        element: <CargoMantenimiento />,
      },
      {
        path: 'ubicaciones',
        element: <UbicacionMantenimiento />,
      },
      {
        path: 'aplicaciones',
        element: <AplicacionMantenimiento />,
      },
      {
        path: 'estados',
        element: <EstadoMantenimiento />,
      },
      {
        path: 'roles',
        element: <RolMantenimiento />,
      },
      {
        path: 'roles/:idRol/suministros',
        element: <SuministroPorRol />,
      },
      {
        path: 'roles/:idRol/suministros/:idSuministro/atributos',
        element: <AtributoTabs />,
      },
      {
        path: 'usuarios',
        element: <UsuarioMantenimiento />,
      },
      {
        path: 'reportes',
        element: <ReporteVista />,
      },
    ],
  },
])
