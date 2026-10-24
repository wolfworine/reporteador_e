# Arquitectura — entel-role-repository-frontend

## Arquitectura actual (JSF 2.2 + PrimeFaces, vistas server-rendered)

- Vistas en `paginas/<modulo>/*.xhtml` (mantenimiento, editar, modificar, +
  sub-vistas grupos/inbox/defaultinbox donde aplica), plantilla común
  `WEB-INF/templates/BasicTemplate.xhtml`.
- Toda la interacción pasa por `h:form`/`p:commandButton` con
  `action="#{xManagedBean.metodo}"` — no hay concepto de API, cliente o
  estado independiente del servidor.
- Navegación centralizada en `NavigationBean`, plantilla en
  `PlantillaController`, menú en `MenuController`.

## Arquitectura objetivo (React 19 + TypeScript + Vite)

```
src/
  api/                     # cliente HTTP tipado, un archivo por módulo
    client.ts               # fetch wrapper con manejo de error uniforme
    areas.ts, cargos.ts, ubicaciones.ts, aplicaciones.ts,
    roles.ts, suministros.ts, atributos.ts, usuarios.ts,
    menu.ts, reportes.ts
  types/                    # interfaces TS espejo de los DTOs backend
  modules/
    area/ cargo/ ubicacion/ aplicacion/ estado/   # CRUD simple
    rol/ suministro/ atributo/                     # cadena compleja
    usuario/
    reportes/
    menu/                    # árbol de navegación dinámico
  components/                # tabla genérica, formularios, layout, toasts de error
  app/                        # routing (React Router), providers (QueryClient)
```

- **Qué cambia:** de renderizado server-side a SPA consumiendo JSON;
  estado de servidor gestionado por TanStack Query en vez de sesión JSF;
  tablas anidadas PrimeFaces → jerarquía de componentes React + TanStack
  Table.
- **Qué se mantiene:** los 8 módulos y su alcance funcional exacto (mismos
  campos, mismas búsquedas/filtros que las vistas legacy), y la forma del
  reporte (Cargo→Rol→4 categorías) — ver `../backend/docs/architecture.md`
  para el contrato de datos exacto que expone el backend.

### Flujo de datos de reportes (asíncrono, notificación SSE — corregido 09-ago-2026)

El backend (`../backend/docs/architecture.md`) es 100% asíncrono desde el
día uno: no hay endpoint síncrono ni se implementa polling en el frontend
— la notificación es **push** vía Server-Sent Events.

```
[ReporteFiltro] --POST /api/reportes/generar (202 Accepted + id)--> [backend]
       │
       ▼
[EventSource abierto a GET /api/reportes/eventos, una vez por sesión]
       │
       ▼ (llega evento SSE con ese id, tipo report.ready)
[Botón de descarga habilitado] --GET /api/reportes/{id}/descargar-->  archivo
```

- Se usa la API nativa del navegador `EventSource` (o un hook propio
  `useReportEvents` sobre ella) — no hace falta una librería de WebSocket,
  SSE es HTTP simple y unidireccional.
- La conexión SSE se abre una vez (a nivel de layout/provider) y se
  distribuye a los componentes de Reportes que estén escuchando ese `id`,
  no una conexión nueva por cada reporte solicitado.
- Si `EventSource` no está disponible o la conexión se corta, hacer
  fallback a un único chequeo puntual (no un loop de polling) al volver a
  enfocar la pestaña — documentar esa decisión de resiliencia en la
  feature correspondiente si se implementa.

## Reglas duras específicas de este proyecto

- `modules/*` no llama `fetch` directamente — siempre a través de
  `api/*.ts`, que es la única capa que conoce las URLs del backend.
- Todo dato de servidor pasa por TanStack Query (`useQuery`/`useMutation`);
  prohibido `useEffect` + `useState` manual para fetch de datos (patrón
  legacy a evitar, no el de JSF sino el anti-patrón típico de React
  temprano).
- Los componentes de `modules/` no conocen PrimeFaces ni ninguna librería
  de UI heredada — es una reescritura, no un envoltorio.
- El árbol de menú (`modules/menu`) se arma en el cliente a partir de la
  respuesta plana de `GET /api/menu/{tipoUsuario}` (campo `cod_submenu`),
  replicando la jerarquía que hoy arma `MenuController` en Java.
- Ninguna contraseña se guarda en `localStorage`/`sessionStorage`; el
  token de sesión (definido por el backend) se maneja según lo que
  `usuario_login_password_hash_fix` (feature backend #6) exponga —
  documentar aquí la decisión final una vez esa feature esté `done`.
