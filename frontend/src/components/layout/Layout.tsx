import { useEffect } from 'react'
import type { ReactElement, SVGProps } from 'react'
import { Link, Outlet, useLocation } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { useAuth } from '../../app/AuthContext'
import { useMenu } from '../../modules/menu/useMenu'
import type { MenuNodo } from '../../types/menu'
import * as areasApi from '../../api/areas'
import * as aplicacionesApi from '../../api/aplicaciones'
import {
  IconBadge,
  IconBox,
  IconBuilding,
  IconFileChart,
  IconGrid,
  IconList,
  IconMapPin,
  IconShield,
  IconUser,
} from '../ui/icons'

/**
 * Metadata por `action` de menú legado (`GET /api/menu/{tipoUsuario}`, ver
 * `types/menu.ts`) -- el backend nunca expone ruta, ícono ni agrupación,
 * solo el nombre del managed bean legacy (`action`, ej.
 * `"areaManagedBean"`), así que las tres viven acá, no en el backend.
 *
 * **Rediseño 2026-08-25 (Dirección A del canvas "Rediseño del Menú
 * Administrativo", elegida por el usuario tras revisar 3 direcciones
 * inspiradas en guías de Alf Design Group/NN·Jakob Nielsen sobre
 * navegación y controles deshabilitados):** además de la ruta, cada
 * `action` ahora trae un ícono y un grupo temático (Catálogos/Roles y
 * permisos/Reportes/Administración) para agrupar visualmente el sidebar
 * -- antes era una lista plana sin jerarquía visual ni ícono. `ayuda` es
 * el texto explicando por qué `Suministro`/`Detalle` no son clicables:
 * antes se mostraban como texto gris sin ninguna explicación, lo que
 * Nielsen recomienda evitar explícitamente ("Inactive GUI Controls: Show,
 * Disable, or Hide?" -- un control deshabilitado debe explicar por qué,
 * no solo verse atenuado).
 */
const GRUPO_CATALOGOS = 'Catálogos'
const GRUPO_ROLES = 'Roles y permisos'
const GRUPO_REPORTES = 'Reportes'
const GRUPO_ADMINISTRACION = 'Administración'
const GRUPO_ORDEN = [GRUPO_CATALOGOS, GRUPO_ROLES, GRUPO_REPORTES, GRUPO_ADMINISTRACION]

interface AccionInfo {
  ruta?: string
  Icono: (props: SVGProps<SVGSVGElement>) => ReactElement
  grupo: string
  ayuda?: string
}

const ACCION_INFO: Record<string, AccionInfo> = {
  aplicacionManagedBean: { ruta: '/aplicaciones', Icono: IconGrid, grupo: GRUPO_CATALOGOS },
  areaManagedBean: { ruta: '/areas', Icono: IconBuilding, grupo: GRUPO_CATALOGOS },
  cargoManagedBean: { ruta: '/cargos', Icono: IconBadge, grupo: GRUPO_CATALOGOS },
  ubicacionManagedBean: { ruta: '/ubicaciones', Icono: IconMapPin, grupo: GRUPO_CATALOGOS },
  rolManagedBean: { ruta: '/roles', Icono: IconShield, grupo: GRUPO_ROLES },
  suministroManagedBean: {
    Icono: IconBox,
    grupo: GRUPO_ROLES,
    ayuda: 'Se abre desde Rol',
  },
  detalleManagedBean: {
    Icono: IconList,
    grupo: GRUPO_ROLES,
    ayuda: 'Se abre desde Suministro',
  },
  reporteManagedBean: { ruta: '/reportes', Icono: IconFileChart, grupo: GRUPO_REPORTES },
  usuarioManagedBean: { ruta: '/usuarios', Icono: IconUser, grupo: GRUPO_ADMINISTRACION },
}

const GRUPO_SIN_CLASIFICAR = 'Otros'

/**
 * Un ítem de menú (con su ícono/ruta/ayuda si `action` está mapeado en
 * `ACCION_INFO`) más, recursivamente, sus `hijos` -- mismo mecanismo de
 * árbol que antes (`getByRole('link', ...)` sigue encontrando nodos
 * anidados), solo que ahora cada nivel también puede traer ícono y estado
 * activo. La agrupación por sección (`.grupo`) es exclusiva del nivel
 * superior (`MenuAgrupado`); los hijos siguen listándose en un `<ul>`
 * plano como antes.
 */
function MenuItem({ node, pathname }: { node: MenuNodo; pathname: string }) {
  const info = node.action ? ACCION_INFO[node.action] : undefined
  const ruta = info?.ruta
  const activo = !!ruta && (pathname === ruta || pathname.startsWith(`${ruta}/`))
  const Icono = info?.Icono

  return (
    <li>
      {ruta ? (
        <Link
          className={activo ? 'item item--activo' : 'item'}
          to={ruta}
          aria-current={activo ? 'page' : undefined}
        >
          {Icono && <Icono />}
          {node.nombre}
        </Link>
      ) : (
        <span className="item item--deshabilitado" title={info?.ayuda}>
          {Icono && <Icono />}
          {node.nombre}
        </span>
      )}
      {info?.ayuda && <div className="item__ayuda">{info.ayuda}</div>}
      {node.hijos.length > 0 && (
        <ul>
          {node.hijos.map((hijo) => (
            <MenuItem key={hijo.codigo} node={hijo} pathname={pathname} />
          ))}
        </ul>
      )}
    </li>
  )
}

/**
 * Agrupa los nodos de nivel superior del árbol de menú real por
 * `ACCION_INFO[...].grupo` (Catálogos/Roles y permisos/Reportes/
 * Administración), preservando `GRUPO_ORDEN`. Un nodo sin `action`
 * mapeado (ninguno en los datos reales de producción, pero posible en
 * datos hipotéticos/tests) cae en un grupo "Otros" sin encabezado
 * especial, para no perderlo.
 */
function MenuAgrupado({ nodes, pathname }: { nodes: MenuNodo[]; pathname: string }) {
  if (nodes.length === 0) {
    return null
  }

  const porGrupo = new Map<string, MenuNodo[]>()
  for (const node of nodes) {
    const grupo = (node.action && ACCION_INFO[node.action]?.grupo) || GRUPO_SIN_CLASIFICAR
    const items = porGrupo.get(grupo) ?? []
    items.push(node)
    porGrupo.set(grupo, items)
  }

  const gruposEnOrden = [
    ...GRUPO_ORDEN.filter((grupo) => porGrupo.has(grupo)),
    ...(porGrupo.has(GRUPO_SIN_CLASIFICAR) ? [GRUPO_SIN_CLASIFICAR] : []),
  ]

  return (
    <>
      {gruposEnOrden.map((grupo) => (
        <div className="grupo" key={grupo}>
          {grupo !== GRUPO_SIN_CLASIFICAR && <div className="grupo__titulo">{grupo}</div>}
          <ul>
            {porGrupo.get(grupo)!.map((node) => (
              <MenuItem key={node.codigo} node={node} pathname={pathname} />
            ))}
          </ul>
        </div>
      ))}
    </>
  )
}

/**
 * Layout base (header/sidebar/contenido) — reemplaza a
 * `WEB-INF/templates/BasicTemplate.xhtml` + `PlantillaController`/
 * `MenuController` del legacy. El sidebar consume el árbol de menú
 * específico del `tipoUsuario` del usuario autenticado (`useAuth`) vía
 * `useMenu`, que llama a `GET /api/menu/{tipoUsuario}` (`api/menu.ts`).
 *
 * Solo se monta dentro de una ruta protegida (ver `app/router.tsx`), por lo
 * que `user` debería existir siempre que este componente se renderiza en la
 * app real; se maneja el caso `null` de todas formas por prudencia (p. ej.
 * durante el instante de un `logout`).
 */
export function Layout() {
  const { user, logout } = useAuth()
  const menuQuery = useMenu(user?.tipo)
  const queryClient = useQueryClient()
  const { pathname } = useLocation()

  // Precarga 2026-08-20 (pedido explícito del usuario: "deberias precargar
  // al inicio toda la informacion para que no demore en cargar"): Área y
  // Aplicación son las dos listas de referencia más usadas en toda la app
  // (filtros de Reportes, catálogos propios) -- se piden una vez acá, al
  // montar el layout autenticado (antes de que el usuario navegue a
  // ninguna pantalla que las necesite), para que ya estén tibias en cache
  // cuando las pida `ReporteFiltro`/`AreaMantenimiento`/
  // `AplicacionMantenimiento`. `prefetchQuery` respeta el `staleTime`
  // global (`app/queryClient.ts`) -- si ya están frescas, no repite el
  // fetch.
  useEffect(() => {
    queryClient.prefetchQuery({ queryKey: ['areas'], queryFn: areasApi.listar })
    queryClient.prefetchQuery({
      queryKey: ['aplicaciones'],
      queryFn: aplicacionesApi.listar,
    })
  }, [queryClient])

  return (
    <div className="layout">
      <header className="layout__header">
        <h1>Repositorio de Roles</h1>
        {user && (
          <div className="layout__user">
            <span>
              {user.nombre} {user.apellido}
            </span>
            <button type="button" onClick={logout}>
              Cerrar sesión
            </button>
          </div>
        )}
      </header>

      <div className="layout__body">
        <nav aria-label="Menú principal" className="layout__sidebar">
          {menuQuery.isLoading && <p>Cargando menú…</p>}
          {menuQuery.isError && (
            <p role="alert">No se pudo cargar el menú. Intenta nuevamente.</p>
          )}
          {menuQuery.data && <MenuAgrupado nodes={menuQuery.data} pathname={pathname} />}
        </nav>

        <main className="layout__content">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
