# Historial de sesiones

> Log append-only. Cada entrada representa una sesión de trabajo cerrada.

---

## Sesión 09-ago-2026 — Feature 1: `bootstrap_react_vite_project` → DONE

**Resultado:** APPROVED tras 1 ronda de correcciones (2 revisiones del Reviewer).

### Qué se implementó
- Scaffold Vite + React 19 + TypeScript generado con `npm create vite@latest -- --template react-ts` (copiado desde un directorio temporal al ya existente `frontend/` para no chocar con los archivos del arnés).
- `react`/`react-dom` fijados en `^19.2.8` (React 19.x), `@types/react` `^19.2.18`, `@types/react-dom` `^19.2.4`.
- ESLint flat-config manual (`eslint.config.js`) instalado y configurado a mano porque la plantilla actual de `create-vite` trae `oxlint` en vez de ESLint, y el acceptance pedía ESLint explícitamente — se desinstaló `oxlint`.
- Prettier configurado (`.prettierrc`, `.prettierignore` — excluye explícitamente todos los archivos del arnés).
- Vitest + React Testing Library configurados (`vite.config.ts` bloque `test`, `src/test/setup.ts`), con test smoke `src/App.test.tsx` en verde.
- `App.tsx` reemplazado por placeholder simple ("Repositorio de Roles").
- `init.sh` reescrito: se quitó el guard de placeholder ("no esta parametrizado todavia") y el bug de sintaxis Maven heredado (`$BUILD_CMD clean compile`); ahora corre `npm run build` + `npm test` reales con `set -e`.
- Corrección post-review: se agregó `"strict": true` a `tsconfig.app.json` y `tsconfig.node.json` (faltaba en la primera entrega, violaba `docs/conventions.md`). No requirió cambios de código de aplicación.

### Verificación final
`npm run build`, `npm test`, `npm run lint` y `./init.sh` (`bash init.sh`) en verde. Arnés (`AGENTS.md`, `CLAUDE.md`, `CHECKPOINTS.md`, `docs/*`, `feature_list.template.json`, `.claude/agents/*`) verificado intacto por el Reviewer en ambas rondas.

### Archivos creados
`package.json`, `package-lock.json`, `vite.config.ts`, `index.html`, `tsconfig.json`, `tsconfig.app.json`, `tsconfig.node.json`, `.gitignore`, `README.md`, `eslint.config.js`, `.prettierrc`, `.prettierignore`, `src/main.tsx`, `src/App.tsx`, `src/App.css`, `src/App.test.tsx`, `src/index.css`, `src/test/setup.ts`, `public/favicon.svg`, `public/icons.svg`.

### Archivos del arnés modificados (esperado por el scope de la feature)
`init.sh`.

---

## Sesión 09-ago-2026 — Feature 2: `api_client_and_types` → DONE

**Resultado:** APPROVED en primera revisión (sin correcciones).

### Qué se implementó
- `src/types/{area,cargo,ubicacion,aplicacion,estado,rol,suministro,atributo,detalle,usuario,menu,reporte}.ts`
  — un archivo TS por entidad, cada uno con JSDoc citando la clase Java
  legacy exacta de origen (`D:\Capacitacion\IA\Migracion_Reporte\JSF_RepositorioV3.9.2_extraido\war_contenido\WEB-INF\classes\pe\edu\cibertec\proyemp\model\*.java`).
  Verificado campo por campo por el Reviewer contra las clases Java reales
  — sin campos inventados. Decisiones documentadas: `Estado` agregado
  (FK real de `Atributo`, no scope creep); `Login.java` y `Politica.java`
  deliberadamente no mapeados (login es de la feature 3; Politica es
  código muerto del legacy); `Usuario` separado en `Usuario` (lectura, sin
  password) y `CrearUsuarioRequest` (con password solo para el body de
  creación, feature 7); `Detalle`/`DetalleCategoria` modelan las 4
  categorías Roles/Grupos/Inbox/DefaultInbox a partir de `DetalleRol.java`.
- `src/api/client.ts`: `apiClient<T>()` genérico + `ApiError` (status +
  message + body), URL base vía `VITE_API_BASE_URL` con fallback de
  desarrollo (sin valor de producción hardcodeado). `.env.example` agregado,
  `.gitignore` ampliado para cubrir `.env` plano.
- `src/api/client.test.ts`: 5 tests con `fetch` mockeado (éxito 200,
  fallback de URL, error 4xx, error 5xx, 204 sin cuerpo).
- `src/app/queryClient.ts` + `src/main.tsx` ajustado: `QueryClientProvider`
  envuelve `<App />`.
- `@tanstack/react-query` instalado.

### Pendiente de reconciliar (anotado explícitamente)
El backend real (`../backend/`) aún no expone endpoints de dominio (solo
`/api/status`). Deliberadamente no se crearon funciones API por entidad
(`api/areas.ts`, etc.) — llegan en features 3+ cuando se confirme la URL
exacta de cada endpoint. Cuando `../backend/feature_list.json` marque
`done` las features de dominio, reconciliar `src/types/` contra los DTOs
reales del backend.

### Verificación final
`npm run build`, `npm test` (6 tests, 2 archivos), `npm run lint`,
`bash init.sh` — todos en verde, confirmados de forma independiente por el
Reviewer (no solo por el reporte del implementer).

### Archivos creados
`src/types/*.ts` (12 archivos), `src/api/client.ts`, `src/api/client.test.ts`,
`src/app/queryClient.ts`, `src/vite-env.d.ts`, `.env.example`.

### Archivos modificados
`src/main.tsx`, `.gitignore`, `package.json` (dependencia `@tanstack/react-query`).

---

## Sesión 09-ago-2026 — Feature 3: `layout_login_menu_dinamico` → DONE

**Resultado:** APPROVED en primera revisión (sin correcciones; 1 hallazgo menor no
bloqueante sobre C5, resuelto por el Leader al cerrar esta sesión).

### Qué se implementó
- `react-router-dom@^7.18.2` instalado; `src/app/router.tsx`
  (`createBrowserRouter`: `/login` público, `/` protegida por
  `ProtectedRoute` que renderiza `Layout`).
- `src/app/AuthContext.tsx`: sesión de usuario en memoria (`useState`),
  **sin** `localStorage`/`sessionStorage` — verificado línea por línea por
  el Reviewer.
- `src/types/auth.ts` + `src/api/auth.ts`: `login()` vía `apiClient` contra
  `POST /api/auth/login` (ruta razonada, no literal del legacy JSF —
  documentada como pendiente de reconciliar con `../backend/feature_list.json`
  id=5 `usuario_login_password_hash_fix`, aún `pending`).
- `src/api/menu.ts`: `fetchMenu(tipoUsuario)` contra `GET /api/menu/{tipoUsuario}`
  (ruta ya fijada en `docs/architecture.md`; payload real pendiente de
  `../backend/feature_list.json` id=6 `menu_dinamico_por_tipo_usuario`,
  aún `pending`).
- `src/modules/menu/buildMenuTree.ts` (+ 4 tests): arma árbol vía `codSubmenu`
  explícito. El Reviewer leyó `MenuController.java` legacy línea por línea y
  confirmó que su lógica real (`establecerPermisos()`) es un cross-join sin
  usar `cod_submenu` — un bug, no una jerarquía real — por lo que la
  desviación del nuevo código hacia `codSubmenu` explícito está justificada,
  no es un apartamiento arbitrario del legacy.
- `src/modules/login/LoginPage.tsx` (+ 2 tests): formulario con `useMutation`,
  error visible inline (`role="alert"`, nunca solo consola).
- `src/components/layout/Layout.tsx` (+ 3 tests): header/sidebar/contenido,
  sidebar renderiza el árbol de menú vía `useMenu`; test verificado por el
  Reviewer como no-trivial (2 `tipoUsuario` distintos con datasets y
  resultados distintos).
- `src/App.tsx`/`src/App.test.tsx` actualizados para montar `AuthProvider` +
  `RouterProvider`.

### Verificación final
`npm run build`, `npm test` (15/15 en verde), `npm run lint` (0 errores, 2
warnings no bloqueantes `react-refresh/only-export-components` en
`AuthContext.tsx`/`router.tsx`, patrón común de Context/Router, no afecta
build/test), `bash init.sh` — todos confirmados de forma independiente por
el Reviewer. Cero HTTP real en tests (`api/auth.ts` y `api/menu.ts`
mockeados con `vi.mock`).

### Dependencias backend pendientes de reconciliar (anotado explícitamente)
- `../backend/feature_list.json` id=5 (`usuario_login_password_hash_fix`,
  `pending`): fijará el contrato real de login (URL exacta, hashing, si
  hay token de sesión). Afecta `src/api/auth.ts`/`src/types/auth.ts`.
- `../backend/feature_list.json` id=6 (`menu_dinamico_por_tipo_usuario`,
  `pending`): confirmará el payload real de `GET /api/menu/{tipoUsuario}`.
  Afecta `src/api/menu.ts`.

### Archivos creados
`src/types/auth.ts`, `src/api/auth.ts`, `src/api/menu.ts`,
`src/modules/menu/buildMenuTree.ts` (+ test), `src/modules/menu/useMenu.ts`,
`src/modules/login/LoginPage.tsx` (+ test),
`src/components/layout/Layout.tsx` (+ test), `src/app/AuthContext.tsx`,
`src/app/router.tsx`.

### Archivos modificados
`src/App.tsx`, `src/App.test.tsx`, `package.json`/`package-lock.json`
(dependencia `react-router-dom`).

---

## Sesión 09/15-ago-2026 — Feature 4: `crud_catalogos_simples` → DONE

**Resultado:** APPROVED tras 1 ronda de correcciones (CHANGES_REQUESTED → fix → APPROVED).

### Novedad clave de la sesión: el backend ya expone endpoints reales

`../backend/feature_list.json` id=3 (`crud_catalogos_simples`) está `done`.
Contrato confirmado leyendo el código fuente real antes de implementar:

- `GET /api/{areas,cargos,ubicaciones,aplicaciones,estados}`, `GET .../{codigo}`,
  `POST .../` (body `{nombre}` → 201), `PUT .../{codigo}` (body `{nombre}`),
  `DELETE .../{codigo}` → 204. Mismo patrón exacto para los 5 catálogos
  (`AreaResource.java`/`CargoResource.java`/`UbicacionResource.java`/
  `AplicacionResource.java`/`EstadoResource.java`).
- **Discrepancia real con los tipos de la feature 2**: el DTO real es
  `record X(String codigo, String nombre)` — `codigo` es `string`, sin
  campo `id` separado (los 5 `domain/*.java` son records idénticos). Los
  tipos de la feature 2 (`codigo: number` + `id: string`, derivados de la
  entidad JPA legacy) estaban desactualizados — corregidos en esta feature.
- Body de request: `CatalogoRequest(String nombre)`, único campo, igual
  para los 5 catálogos.
- Errores de dominio (`CatalogoExceptionMappers.java`): 404/400 con body
  `record ErrorResponse(String mensaje)` — campo en **español**, no
  `message`. `resolveErrorMessage` en `client.ts` solo reconocía `message`
  — corregido para reconocer `mensaje` primero, con `message` como
  fallback.

### Qué se implementó

- `src/types/{area,cargo,ubicacion,aplicacion,estado}.ts` corregidos al
  shape real `{codigo: string, nombre: string}` (JSDoc documenta el cambio
  y cita el archivo backend fuente).
- `src/api/client.ts` → `resolveErrorMessage` reconoce `mensaje` (prioridad)
  y `message` (fallback).
- `src/api/catalogoApi.ts`: factory genérica `createCatalogoApi<T>(basePath)`
  reutilizada por los 5 catálogos (evita duplicar la misma lógica CRUD 5
  veces).
- `src/api/{areas,cargos,ubicaciones,aplicaciones,estados}.ts`: instancian
  la factory contra su `basePath` real, re-exportan funciones nombradas
  (`listar`, `buscarPorCodigo`, `crear`, `actualizar`, `eliminar`) para
  mockeo con `vi.mock`.
- `src/modules/catalogo/useCatalogoMantenimiento.ts`: hook compartido
  TanStack Query (listado + 3 mutaciones, invalida listado al éxito).
- `src/components/catalogo/CatalogoMantenimiento.tsx`: componente
  contenedor genérico — filtro cliente por código/nombre (el backend no
  expone parámetros de búsqueda, mismo intento que el `filterBy` legacy
  pero en cliente), formulario de creación (solo `nombre`, el backend
  asigna `codigo`), edición inline por fila, eliminar por fila, error
  inline `role="alert"` en toda mutación.
- `src/modules/{area,cargo,ubicacion,aplicacion,estado}/*Mantenimiento.tsx`:
  wrappers finos sobre `CatalogoMantenimiento`.
- Rutas `/areas`, `/cargos`, `/ubicaciones`, `/aplicaciones`, `/estados`
  registradas en `src/app/router.tsx` (protegidas, dentro de `Layout`).
- Tests de componente: `AreaMantenimiento.test.tsx` (4: listar, filtrar,
  crear+refetch, error inline) y `CargoMantenimiento.test.tsx` (2: listar,
  crear+refetch), ambos con `vi.mock` de sus `api/*.ts`, cero HTTP real.

### Vistas legacy leídas para el alcance funcional

`paginas/{area,cargo,ubicacion,aplicacion}/{mantenimiento,editar,modificar}.xhtml`.
Confirmado: filtro por 2 columnas (código/nombre) vía `p:column filterBy`,
selección de fila única, botones Nuevo/Actualizar/Eliminar. `Estado` no
tiene vista legacy propia (confirmado en `domain/Estado.java`) — se le da
mantenimiento por paridad con los otros 4, no es scope creep. El botón
"VerRol" de `aplicacion/mantenimiento.xhtml` quedó explícitamente fuera de
alcance (feature 5).

### Ronda de corrección (Reviewer → CHANGES_REQUESTED → APPROVED)

Primer veredicto del Reviewer: **CHANGES_REQUESTED**, checkpoint C4 —
el bugfix de `resolveErrorMessage` no tenía test de regresión que
realmente fallara contra el código viejo (`client.test.ts` solo probaba
`message` puro y texto plano, ambos ya verdes antes del fix). Corregido
agregando 2 tests a `client.test.ts`:

- `'should throw an ApiError using the backend "mensaje" field...'`: body
  `{ mensaje: 'Algo no válido' }` → `error.message === 'Algo no válido'`.
  Falla contra el código pre-fix (caería al genérico `HTTP {status}...`).
- `'should prefer the "mensaje" field over "message"...'`: body con ambos
  campos → gana `mensaje`, fija el orden de precedencia. También falla
  contra el código pre-fix.

Segundo veredicto: **APPROVED**. Ambos tests verificados por el Reviewer
como genuinamente pineando el fix (razonamiento línea por línea contra el
código pre/post-fix). Scope check por `LastWriteTime` (repo sin `.git`)
confirmó que solo `src/api/client.test.ts` y `progress/current.md` se
tocaron en la ronda de corrección — nada más del scope de la feature.

### Hallazgo no bloqueante: backend no compilaba en un momento de la sesión

Durante un smoke test manual opcional, `../backend` falló en compilar por
un error de sintaxis reportado en `domain/CategoriaSuministro.java` (fuera
del alcance de esta feature). El Leader confirmó independientemente en la
sesión de continuación que el archivo real es un enum limpio de 4 valores
y que `../backend` compila y pasa 41/41 tests — el hallazgo era transitorio
o ya fue corregido por la sesión paralela de backend; no afectó el
veredicto de esta feature (los tests de frontend siguen 100% mockeados).

### Verificación final

`npm run build`, `npm test` (23/23 tests, 7 archivos — 21 originales + 2
de la ronda de corrección), `npm run lint` (0 errores, 2 warnings
preexistentes no relacionados en `AuthContext.tsx`/`router.tsx`),
`bash init.sh` — todo en verde, confirmado de forma independiente por el
Reviewer en ambas rondas y por el Leader.

### Archivos creados

`src/api/catalogoApi.ts`, `src/api/{areas,cargos,ubicaciones,aplicaciones,estados}.ts`,
`src/modules/catalogo/useCatalogoMantenimiento.ts`,
`src/components/catalogo/CatalogoMantenimiento.tsx`,
`src/modules/{area,cargo,ubicacion,aplicacion,estado}/*Mantenimiento.tsx`,
`src/modules/area/AreaMantenimiento.test.tsx`,
`src/modules/cargo/CargoMantenimiento.test.tsx`.

### Archivos modificados

`src/types/{area,cargo,ubicacion,aplicacion,estado}.ts`, `src/api/client.ts`
(fix + 2 tests de regresión en `client.test.ts`), `src/app/router.tsx`.

---

## Sesión 15-ago-2026 — Feature 5: `crud_rol_suministro_drilldown` → DONE

**Resultado:** APPROVED en primera revisión (sin correcciones).

### Contrato real del backend confirmado

- `web/RolResource.java` (`@Path("/api/roles")`): `GET /` (listar todos,
  sin query params — filtro por Área/Cargo/Tipo/Ámbito es client-side),
  `GET /cargos?aplicacion=&area=` (agrupado), `GET /{id}`, `POST/PUT/DELETE`.
  `domain/Rol.java`: `{id, ubicacionCodigo, areaCodigo, cargoCodigo, nombre,
  descripcion, tipo, reglaNegocio, ambito, propietarioRol,
  propietarioPersona, situacion, rolBase}`.
- `web/SuministroResource.java` (`@Path("/api/roles/{idRol}/suministros")`):
  filtro por rol **server-side** en el propio path (no query param) —
  exactamente el endpoint para "Ver Suministros". `domain/Suministro.java`:
  `{id, rolId, nombre, prioridad}`.
- `web/AtributoResource.java`: **no expone filtro por `suministroId`** —
  solo `GET /rol/{idRol}/categoria/{categoria}`. "Ver Atributo" llama este
  endpoint con categoría `ROLES` (paridad con la pestaña por defecto del
  legacy) y filtra en cliente por `suministroId` — limitación real del
  backend, documentada, no scope creep.
- **Gap adicional encontrado por el Implementer** (confirmado por el
  Reviewer leyendo `AtributoRepository.listarPorRolYCategoria` línea por
  línea): la GSI1PK real es `"APLICACION#" + idAplicacion + "#AREA#" +
  idArea` — `idArea` se resuelve del `Rol`, pero `idAplicacion` no tiene
  fuente en el flujo Rol→Suministro→Atributo (ni `Rol` ni `Suministro`
  exponen `aplicacionCodigo`). Contra un backend real sin ese parámetro, la
  key sería literalmente `APLICACION#null#AREA#...` y no matchearía datos.
  No bloquea esta feature (los tests son 100% mockeados, ningún acceptance
  criterio exige integración real), pero queda documentado como candidato
  para la feature 6 o una feature que agregue `aplicacionCodigo` a
  `Rol`/`Suministro`, o propague el código de Aplicación como contexto de
  navegación.

### Qué se implementó

- `src/types/{rol,suministro,atributo}.ts` reescritos como filas planas
  (antes modelaban objetos anidados sin contrato confirmado, deuda de la
  feature 2 — mismo patrón de corrección que los 5 catálogos de la
  feature 4).
- `src/api/roles.ts`, `src/api/suministros.ts` (parametrizado por `idRol`),
  `src/api/atributos.ts` (`listarPorRolYCategoria`, `aplicacion` expuesto
  como parámetro opcional para un caller futuro, sin inventar endpoint).
- `src/modules/rol/{useRolMantenimiento.ts,RolMantenimiento.tsx}`:
  listar/filtrar (Área/Cargo/Tipo/Ámbito, client-side) + CRUD + acción "Ver
  Suministros" por fila.
- `src/modules/suministro/{useSuministroMantenimiento.ts,SuministroPorRol.tsx}`:
  suministros de un rol (route param) + CRUD + acción "Ver Atributo".
- `src/modules/atributo/AtributoPorSuministro.tsx`: vista mínima de solo
  lectura, categoría ROLES fija, filtrada client-side por `suministroId`
  — deliberadamente sin tabs (feature 6) ni CRUD de Atributo.
- Rutas anidadas en `router.tsx`: `/roles` → `/roles/:idRol/suministros` →
  `/roles/:idRol/suministros/:idSuministro/atributos`.
- Tests: `RolMantenimiento.test.tsx` (4), `RolSuministroDrilldown.test.tsx`
  (1 — acceptance criterio 3 explícito, dataset mixto de 2 roles, prueba
  que el rol incorrecto nunca aparece), `SuministroPorRol.test.tsx` (3),
  `SuministroAtributoDrilldown.test.tsx` (1 — dataset mixto de 2
  suministros, prueba discriminación real del filtro client-side).

### Verificación final

`npm run build`, `npm test` (**11 archivos, 32 tests** — 23 previos + 9
nuevos), `npm run lint` (0 errores, mismos 2 warnings preexistentes de
`AuthContext.tsx`/`router.tsx`), `bash init.sh` — todo en verde, confirmado
de forma independiente por el Reviewer (incluyendo verificación cruzada
directa del contrato backend, no solo el resumen del Implementer).

### Archivos creados

`src/api/roles.ts`, `src/api/suministros.ts`, `src/api/atributos.ts`,
`src/modules/rol/{useRolMantenimiento.ts,RolMantenimiento.tsx,RolMantenimiento.test.tsx,RolSuministroDrilldown.test.tsx}`,
`src/modules/suministro/{useSuministroMantenimiento.ts,SuministroPorRol.tsx,SuministroPorRol.test.tsx}`,
`src/modules/atributo/{AtributoPorSuministro.tsx,SuministroAtributoDrilldown.test.tsx}`.

### Archivos modificados

`src/types/{rol,suministro,atributo}.ts`, `src/app/router.tsx`.

---

## Sesión 15-ago-2026 — Feature 6: `crud_atributo_detalle_tabs` → DONE

**Resultado:** APPROVED en primera revisión (sin correcciones).

### Confirmación del patrón de tabs en el legacy

4 páginas legacy separadas (`paginas/suministro/{mantenimiento_atributo,
grupos,inbox,defaultinbox}.xhtml`), forma idéntica: mismos botones Nuevo/
Eliminar/Actualizar, misma tabla (Codigo/Aplicacion/Parametro/Atributo
Codigo/Atributo Nombre/Estado, cada columna con `filterBy`), mismo
`f:facet name="{Exporters}"` con 4 botones de navegación cruzada
Rol/Grupos/Inbox/DefaultInbox — confirma que las 4 páginas son variantes
de una misma vista conceptual (tabs), con "Roles" (`mantenimiento_atributo.xhtml`,
sin sufijo de categoría) como la vista base/por defecto.

### Qué se implementó

- `src/modules/atributo/useAtributoTabs.ts`: `CATEGORIAS_ATRIBUTO`
  (Roles/Grupos/Inbox/DefaultInbox) + hook `useAtributoCategoria(idRol,
  categoria, aplicacion?)` — una `useQuery` por categoría activa (queryKey
  incluye `categoria`, nunca mezcla caché entre pestañas) + `useMutation`
  de creación.
- `src/modules/atributo/AtributoTabs.tsx`: componente de 4 pestañas
  (`role="tablist"`/`tab`/`aria-selected`), Roles activa por defecto,
  tabla con las 6 columnas legacy, filtro cliente por atributoCodigo/
  atributoNombre, form de creación por pestaña (`categoria` fija a la
  activa), sigue filtrando por `suministroId` de la ruta (continúa la
  semántica "Ver Atributo de este Suministro" de la feature 5).
- `src/api/atributos.ts`: agregado `AtributoRequest` (espejo exacto del
  DTO backend) y `crear()` (`POST /api/atributos`).
- `router.tsx`: `AtributoTabs` reemplaza a `AtributoPorSuministro` (feature
  5) en `/roles/:idRol/suministros/:idSuministro/atributos`.
- **Reemplazo limpio, no coexistencia:** `AtributoPorSuministro.tsx` (vista
  mínima ROLES-only de la feature 5, documentada ahí mismo como placeholder
  de esta feature) y su test se eliminaron; el nuevo test 6 de
  `AtributoTabs.test.tsx` es superset estricto del test viejo (mismo
  escenario S1/S2 + verifica también el tab Roles activo tras la
  navegación) — sin pérdida de cobertura, confirmado por el Reviewer con
  aritmética exacta de tests (32 − 1 + 6 = 37).
- Tests (`AtributoTabs.test.tsx`, 6): tab Roles por defecto; cambio de
  pestaña con 4 datasets distintos y aserciones explícitas de **ausencia**
  cruzada en cada pestaña (no solo presencia — acceptance criterio 3);
  filtro cliente por nombre; creación en pestaña activa con `categoria`
  correcta; error de creación visible vía `role="alert"`; navegación real
  desde `SuministroPorRol` hasta `AtributoTabs`.

### Gap de "modificar" (PUT) — confirmado, no resuelto

`AtributoResource.java`/`AtributoService.java` solo exponen `POST` + 2
`GET` — **no hay `PUT`** para Atributo (Javadoc del propio backend lo
declara explícitamente). El legacy sí tenía "Modificar"
(`modificar_atributo.xhtml`, confirmado por el Reviewer), así que no es un
requisito inventado, pero el backend actual no lo respalda — se documentó
como gap real, no se inventó un endpoint. Ninguno de los 4 acceptance
criteria de esta feature exige "modificar" explícitamente (solo aparece en
la `description`), así que no bloqueó la aprobación. Candidato para
resolver junto con el gap de `aplicacionCodigo` (heredado de la feature 5,
sigue sin resolver: el form de creación pide `aplicacionCodigo` como texto
manual).

### Verificación final

`npm run build`, `npm test` (**11 archivos, 37 tests** — 31 previos + 6
nuevos, neto de −1 test eliminado +6 nuevos sobre los 32 de la feature 5),
`npm run lint` (0 errores, mismos 2 warnings preexistentes), `bash init.sh`
— todo en verde, confirmado de forma independiente por el Reviewer
(incluyendo cross-check directo del backend: sin PUT, contrato
`AtributoRequest`/`CategoriaSuministro`/`EstadoAtributo` espejado campo a
campo).

### Concerns no bloqueantes (para próxima feature que toque estos archivos)

- Comentarios stale en `src/api/atributos.ts` y `src/types/atributo.ts`
  que aún mencionan el `AtributoPorSuministro.tsx` ya eliminado.
- `AtributoRequest.estado` es `string` genérico (espeja el `String` crudo
  del backend), podría endurecerse a un tipo si el backend lo tipa alguna
  vez.

### Archivos creados

`src/modules/atributo/{useAtributoTabs.ts,AtributoTabs.tsx,AtributoTabs.test.tsx}`.

### Archivos modificados

`src/api/atributos.ts`, `src/app/router.tsx`.

### Archivos eliminados

`src/modules/atributo/AtributoPorSuministro.tsx`,
`src/modules/atributo/SuministroAtributoDrilldown.test.tsx` (reemplazo
limpio documentado arriba).

---

## Sesión 16-ago-2026 — Feature 7: `crud_usuario` → DONE

**Resultado:** APPROVED en primera revisión (sin correcciones).

### Contrato real del backend confirmado

- `web/UsuarioResource.java` (`@Path("/api/usuarios")`): `GET /`, `GET /{id}`,
  `POST /` (crear), `DELETE /{id}`. **Sin `PUT`** (Javadoc lo declara
  explícitamente: "CRUD reactivo... sin actualización") — mismo gap que
  Atributo en la feature 6.
- `UsuarioRequest`: `{nombres, apellidos, email, password, tipo:
  TipoUsuario}` — deliberadamente sin método `aDominio()`, el hashing solo
  ocurre en `UsuarioService.crear(...)` vía `PasswordHasher`.
- `UsuarioResponse`: `{id, nombres, apellidos, email, tipo}` —
  deliberadamente sin `passwordHash`, ningún endpoint serializa jamás el
  dominio `Usuario` crudo (estructuralmente imposible filtrar el hash).
- `TipoUsuario`: enum `ADMINISTRADOR`/`OPERADOR`.

### Qué se implementó

- `src/types/usuario.ts` corregido: `Usuario` (listado/lectura, sin
  password, `id: string`) + `CrearUsuarioRequest` (con password), espejo
  exacto de `UsuarioResponse`/`UsuarioRequest`.
- `src/api/usuarios.ts` (nuevo): `listar`, `buscarPorId`, `crear`,
  `eliminar` — deliberadamente sin `actualizar` (no hay PUT).
- `src/modules/usuario/{useUsuarioMantenimiento.ts,UsuarioMantenimiento.tsx}`:
  listado + filtro client-side por Nombre/Apellido/Email/Tipo, formulario
  de creación (password `type="password"`), eliminar por fila, errores
  `role="alert"`.
- Ruta `/usuarios` en `router.tsx`.
- **Sin UI de edición** (mismo criterio que Atributo en la feature 6):
  `modificar.xhtml` existía en el legacy (confirmado real, no requisito
  inventado — flujo real con password), pero el backend no expone `PUT
  /api/usuarios/{id}`; se omitió la UI en vez de apuntar a un endpoint
  inexistente, documentado en 3 lugares.
- Mejora deliberada sobre el legacy: el campo password del legacy
  (`editar.xhtml`) era un `p:inputText` sin máscara (texto plano visible);
  el nuevo frontend lo sube a `type="password"` (enmascarado) — desviación
  positiva de paridad, no scope creep.

### Ripple no planeado: `LoginPage.tsx` (feature 3) — escrutinio dedicado del Reviewer

Corregir `types/usuario.ts` rompía la compilación de `LoginPage.tsx`
(consumía `Usuario` vía `types/auth.ts` → `LoginResponse.usuario` con
nombres de campo viejos). El Reviewer verificó explícitamente: (a) el
ripple era genuinamente inevitable — sin el fix, `tsc -b` (parte de `npm
run build`) habría fallado; (b) el fix fue mínimo — solo 2 accesores de
campo + comentario, sin reescritura; (c) la afirmación de "sin más
ripple" se confirmó leyendo `AuthContext.tsx`/`Layout.tsx` completos —
ambos usan su propio tipo `AuthUser`, no importan `Usuario`. Conclusión:
ripple legítimo y bien acotado, consistente con la regla dura de
`AGENTS.md` ("no rompas lo que ya funcionaba"), no el red flag de
reescribir código fuera de alcance.

### Verificación de que la contraseña nunca se filtra

1. Tipo `Usuario` sin campo password — imposible referenciarlo sin error
   de TypeScript.
2. Test con mock "hostil" (respuesta runtime con `password`/`passwordHash`
   pese al tipo estático) que verifica que la tabla no renderiza ningún
   rastro — regresión-catcher real, no solo confía en el tipo.
3. `crear()` envía password solo en `body: JSON.stringify(...)` de un
   POST, nunca interpolado en la URL.
4. Test que espía `console.log/error/warn` durante todo el flujo de
   creación, incluyendo un fallo de mutación — confirma que el password
   nunca aparece en ninguna llamada de consola.

### Verificación final

`npm run build`, `npm test` (**12 archivos, 43 tests** — 37 previos + 6
nuevos), `npm run lint` (0 errores, mismos 2 warnings preexistentes),
`bash init.sh` — todo en verde, confirmado de forma independiente por el
Reviewer (incluyendo cross-check directo del backend y escrutinio
dedicado del ripple de `LoginPage.tsx`).

### Concerns no bloqueantes

- Filtro por "Tipo" es substring sobre la etiqueta humanizada, no
  match exacto — consistente con el patrón de otros módulos.
- `useUsuarioMantenimiento.ts` no expone `buscarPorId` aunque
  `api/usuarios.ts` lo define — sin uso actual, no es un defecto.

### Archivos creados

`src/api/usuarios.ts`,
`src/modules/usuario/{useUsuarioMantenimiento.ts,UsuarioMantenimiento.tsx,UsuarioMantenimiento.test.tsx}`.

### Archivos modificados

`src/types/usuario.ts`, `src/app/router.tsx`,
`src/modules/login/{LoginPage.tsx,LoginPage.test.tsx}` (ripple justificado,
ver arriba).

---

## Sesión 16-ago-2026 — Feature 8: `reportes_view_export_sse` → DONE

**Resultado:** APPROVED. Feature implementada en 2 sesiones de Implementer
(la primera se cortó por límite de sesión mientras arreglaba lint, la
segunda cerró exactamente los 2 errores pendientes sin tocar
funcionalidad); aprobada en la revisión que siguió a esa continuación.

### Dependencia backend verificada antes de empezar

`../backend/feature_list.json`: feature 6 `in_progress`, features 7
(`reportes_generacion_async_kafka`) y 8
(`reportes_notificacion_sse_y_descarga`) **`pending`, sin empezar**. Cero
endpoints reales de reportes/SSE en el backend al momento de esta feature
— `EventSource` mockeado por completo, tanto en tests como conceptualmente
en desarrollo. También se leyó `../backend/docs/kafka-reactive-guidance.md`
(escrito por el backend Leader esa sesión): confirma que el contrato
público SSE a mockear (un evento JSON por reporte listo) es la superficie
correcta y no cambiará de forma cuando la integración real llegue (el
puente Kafka→`Multi` interno es un detalle de implementación del backend).

### Qué se implementó

- `src/modules/reporte/ReporteFiltro.tsx`: filtro Área+Aplicación, réplica
  de `paginas/reportes/reporte.xhtml`.
- `src/modules/reporte/ReporteTabla.tsx`: tabla anidada Cargo→Rol→(Roles/
  Grupos/Inbox/DefaultInbox) vía `useReactTable` (TanStack Table),
  réplica exacta de `paginas/reportes/vista.xhtml` (`p:dataTable` →
  `p:subTable` por Cargo → columnas Rol/Tipo/Aplicacion/Titularidad/
  Atributos con las 4 categorías, cada una oculta si vacía). Genera un
  warning informativo esperado de React Compiler (`useReactTable` no
  memoizable) — documentado, no es un bug.
- `src/modules/reporte/ReporteVista.tsx`: orquesta filtro → generación →
  espera de evento SSE → tabla + exportar, con los 4 estados de UI
  (INACTIVO/GENERANDO/LISTO/FALLIDO) vía `role="status"`/`role="alert"`.
- `src/modules/reporte/useReportEvents.ts`: hook SSE real
  (`globalThis.EventSource`, nunca polling), filtra por `idReporte` en
  cliente.
- `src/modules/reporte/useReporteFlujo.ts`: máquina de estados
  INACTIVO→GENERANDO→LISTO|FALLIDO, envuelve mutaciones `generar`/
  `descargar`, traduce evento SSE a estado UI, dispara la descarga del
  archivo vía `<a download>` temporal + `URL.createObjectURL` (sin
  recargar la página).
- `src/api/reportes.ts` (nuevo): `generar`, `descargar`, `EVENTOS_PATH`
  (contrato confirmado del backend) + `obtenerReporte` (**extrapolación
  propia, documentada explícitamente en 3 lugares** — ver abajo).
- `src/types/reporte.ts` (nuevo): `Reporte`/`DetalleRol` + tipos del flujo
  async, con distinción explícita entre lo confirmado y lo extrapolado.
- `src/api/client.ts`: se agregó `buildUrl` (exportado, para que
  `EventSource` resuelva la misma URL base que `fetch`) y `apiClientBlob`
  (para descargas binarias) — cambios aditivos, `apiClient` sin alterar.
- Ruta `/reportes` en `router.tsx`.
- Tests: `ReporteTabla.test.tsx` (fixture con 2 Cargos, 3 Roles, las 4
  categorías representadas), `useReportEvents.test.ts` (prueba EventSource
  real vs. `setInterval` nunca llamado), `ReporteVista.test.tsx`
  (flujo completo Generando→Listo sin reload, ambos botones de exportación
  con blob mockeado, camino FALLIDO).

### El endpoint inventado `obtenerReporte` — decisión evaluada por el Reviewer

Ninguno de los 3 endpoints confirmados del backend (generar/eventos/
descargar) devuelve JSON estructurado para poblar `ReporteTabla` en
pantalla — sin un 4º endpoint no hay forma de cumplir el acceptance
criterio 1 de esta feature. El Reviewer confirmó que esto es una decisión
de ingeniería legítima bajo ambigüedad real (no fabricación): está
documentada explícitamente como no confirmada en 3 lugares
(`progress/current.md`, `types/reporte.ts`, JSDoc de `api/reportes.ts`).
**Nota para la próxima sesión que toque backend features 7/8**: este
4º endpoint (`GET /api/reportes/{id}`, o alternativamente que el propio
evento SSE cargue el árbol completo) es una necesidad real del frontend
que el backend deberá confirmar o reemplazar — no está reflejado todavía
en `../backend/feature_list.json`.

### Fix de lint (sesión de continuación)

2 errores `react-hooks/set-state-in-effect` en `useReportEvents.ts` y
`useReporteFlujo.ts` — resueltos con el patrón de ajuste de estado en
tiempo de render de React (mover `setState` de dentro del `useEffect` a un
bloque condicional durante el render, comparando contra un valor
"anterior" guardado en estado). El Reviewer trazó ambos hooks a mano
(null→id→evento SSE) y confirmó que no hay loop infinito, no hay estado
obsoleto, y el comportamiento observable es idéntico al de la versión
basada en efecto.

### Verificación final

`npm run build`, `npm test` (**15 archivos, 60 tests** — 43 previos + 17
nuevos), `npm run lint` (0 errores, 3 warnings: 2 preexistentes + 1
informativo esperado de `ReporteTabla.tsx`), `bash init.sh` — todo en
verde, confirmado de forma independiente por el Reviewer, incluyendo
cross-check directo del contrato backend y escrutinio dedicado tanto del
endpoint inventado como del fix de lint.

### Concerns no bloqueantes

- Mutaciones usan `isError`/lectura de estado en vez de callback `onError`
  explícito — cumple la intención de la regla (feedback visible, nunca
  silencioso) pero es un mecanismo distinto a la letra de la convención.
- Sin tests unitarios aislados de `ReporteFiltro`/`useReporteFlujo` (solo
  cubiertos indirectamente vía `ReporteVista.test.tsx`) — adecuado para
  esta feature, pero tests aislados darían señal más rápida a futuro.
- Etiquetas de texto "Exportar PDF"/"Exportar Excel" reemplazan los íconos
  sin texto del legacy — mejora de accesibilidad deliberada, no scope
  creep.

### Archivos creados

`src/modules/reporte/{ReporteFiltro.tsx,ReporteTabla.tsx,ReporteVista.tsx,useReportEvents.ts,useReporteFlujo.ts}`
+ sus 3 archivos de test, `src/api/reportes.ts`, `src/types/reporte.ts`.

### Archivos modificados

`src/app/router.tsx`, `src/api/client.ts` (aditivo: `buildUrl` exportado +
`apiClientBlob`).

---

## Sesión 16/17-ago-2026 — Feature 9: `tests_e2e_smoke_flujos_criticos` → DONE

**Resultado:** APPROVED en primera revisión. Feature deliberadamente
acotada tras auditoría del Leader: 2 de los 3 criterios funcionales ya
estaban cubiertos por features anteriores, solo 1 gap real.

### Auditoría de cobertura (Leader, antes de lanzar al implementer)

1. **Login completo (éxito y error)** — ya cubierto,
   `src/modules/login/LoginPage.test.tsx` (feature 3): tests de éxito y de
   error inline. Sin trabajo nuevo.
2. **CRUD completo (crear, listar, editar) end-to-end** — **gap real**.
   `AreaMantenimiento.test.tsx` (feature 4) tenía listar/filtrar/crear pero
   ningún test ejercitaba "editar", pese a que la UI de edición inline sí
   funciona (`CatalogoMantenimiento.tsx`). Trabajo real de la feature.
3. **Generación de reporte + estado final de UI** — ya cubierto,
   `ReporteVista.test.tsx` (feature 8): dispara generación, simula evento
   SSE, verifica "Listo, descargar" con exportación habilitada. Sin
   trabajo nuevo.

### Qué se implementó

Un único test nuevo en `src/modules/area/AreaMantenimiento.test.tsx`:
`'should complete the full crear -> listar -> editar lifecycle for an area
in a single flow'` — un solo `it` continuo: crea un área → confirma que
aparece en el listado tras refetch → click en "Actualizar" de esa fila →
cambia el nombre en el formulario inline → guarda → confirma que el
listado muestra el nombre nuevo y ya no el viejo. Las aserciones finales
(`findByText` del nombre nuevo presente, `queryByText` del nombre viejo
ausente) prueban que la edición se reflejó en la UI, no solo que la
mutación fue llamada.

**Nota técnica**: el mock de `areasApi.listar` es compartido por todo el
archivo (`vi.mock` a nivel de módulo, sin `clearMocks` en
`vite.config.ts`) — `toHaveBeenCalledTimes` acumula entre `it`s. El test
nuevo evita aserciones de conteo absoluto por esta razón. El Reviewer
verificó esto empíricamente corriendo el test en aislamiento
(`npx vitest run -t "..."` → 1 passed, 4 skipped) y confirmó que es
order-independent: cada `it` del archivo consume sus propios
`mockResolvedValueOnce` en cantidad exacta, sin dejar residuo entre tests.

### Verificación final

`npm run build`, `npm test` (**15 archivos, 61 tests** — 60 previos + 1
nuevo), `npm run lint` (0 errores, mismos 3 warnings preexistentes),
`bash init.sh` — todo en verde, confirmado de forma independiente por el
Reviewer, incluyendo la corrida en aislamiento del test nuevo y la
re-lectura completa de los 2 tests citados como "ya cubiertos" (no
aceptados solo por la afirmación).

### Concerns no bloqueantes

- El patrón de mock compartido sin reset es una trampa latente para
  futuros tests de este archivo que sí necesiten `toHaveBeenCalledTimes`
  — candidato para `clearMocks: true` global en una futura limpieza.
- El criterio 2 se cubrió vía el módulo Área específicamente; los otros 4
  módulos de catálogo (Cargo/Ubicación/Aplicación/Estado) que comparten
  `CatalogoMantenimiento` no tienen su propio test crear→listar→editar —
  aceptable dado que el componente compartido es lo que realmente se
  prueba, y el criterio pide "al menos un módulo".

### Archivos creados

Ninguno (test agregado a un archivo existente).

### Archivos modificados

`src/modules/area/AreaMantenimiento.test.tsx`.

---

## Sesión 17/18-ago-2026 — Feature 10: `reporte_final_mejoras` → DONE

**Resultado:** APPROVED (1 sugerencia editorial no bloqueante, aplicada).
**Última feature del track frontend — features 1-10 completas.**

### Qué se implementó

`MEJORAS.md` escrito en la raíz de `frontend/`, sintetizado a partir de
`progress/history.md` (no reconstruido desde cero), con 6 secciones (una
por acceptance criterion):

1. **Módulos migrados con paridad confirmada** — tabla feature 1-9 →
   módulo React → vista(s) `.xhtml` legacy → paridad.
2. **Decisiones de UX deliberadas** — edición inline, filtro client-side,
   password enmascarada, etiquetas de texto en exportación, `role="alert"`
   consistente, `codSubmenu` explícito (corrección de un bug real del
   legacy, no desviación arbitraria), SSE sin polling, máquina de estados
   explícita del flujo de reportes.
3. **Cobertura de tests por feature** — tabla con progresión
   1→6→15→23→32→37→43→60→61 tests, 15 archivos, verificada aritméticamente
   fila por fila por el Reviewer contra los números citados en cada
   entrada de `history.md`.
4. **Dependencias frontend↔backend pendientes** — re-verificado contra
   `../backend/feature_list.json` **al momento de escribir el reporte**
   (no una copia estática de estados antiguos): login/menú (backend
   features 5/6 ya `done`, frontend sin reconciliar desde la feature 3),
   reportes async/SSE (backend dividido en 4 features, ids 7-10, ninguna
   `done`), el compromiso cross-track de `GET /api/reportes/{id}`
   (backend feature id=10, aún `pending`), gaps `PUT` de Atributo/Usuario,
   gap `aplicacionCodigo` en el drilldown Rol/Suministro/Atributo.
5. **Recomendaciones no implementadas** — `clearMocks` global, tests
   aislados de `ReporteFiltro`/`useReporteFlujo`, tipar
   `AtributoRequest.estado`, limpiar comentarios stale, unificar patrón
   `isError`/`onError`, resolver gaps `PUT`, conectar el flujo real de
   SSE/Kafka una vez el backend lo implemente, limitación de un solo
   proceso del puente Kafka→`Multi` (documentada en
   `../backend/docs/kafka-reactive-guidance.md`), reconciliar
   `auth.ts`/`menu.ts` contra el contrato backend ya disponible.
6. Confirmación de precondición (features 1-9 `done`).

### Revisión

El Reviewer verificó cada sección cruzándola contra `history.md`, el
código real (spot-checks: `AtributoTabs.tsx` existe,
`AtributoPorSuministro.tsx` ya no existe, `type="password"` presente en
`UsuarioMantenimiento.tsx`), `feature_list.json` (features 1-9 `done`,
confirmado por lectura directa), y **`../backend/feature_list.json` leído
de primera mano** (no aceptado del documento) para el chequeo de
cross-track accuracy: confirmó que backend features 5/6 están `done`
(cambio real desde que se escribió la feature 3), que las 4 features de
reportes (7-10) siguen sin `done`, y citó textualmente el acceptance
criterion de la feature backend id=10 que compromete la forma de `GET
/api/reportes/{id}`.

Único hallazgo: una imprecisión de redacción en la sección 4(b) — decía
que el backend dividió su feature de reportes "en cuatro", cuando en
realidad se dividió "en tres" (ids 7,8,9) y la cuarta
(`reportes_notificacion_sse_y_descarga`, id=10) ya existía como feature
separada y solo fue renumerada en el mismo cambio. No bloqueante (los
hechos operativos — ids, estados, alcance colectivo — eran correctos),
corregido por el Leader antes de cerrar la feature.

### Verificación final

`npm run build`, `npm test` (**15 archivos, 61 tests, sin cambio** —
cambio 100% documentación), `bash init.sh` — todo en verde. Scope check
por timestamps confirmó que solo `MEJORAS.md` y `progress/current.md`
fueron tocados (más el bookkeeping esperado de `feature_list.json`).

### Archivos creados

`MEJORAS.md`.

### Archivos modificados

`progress/current.md` (documentación de cierre), `MEJORAS.md` (corrección
editorial post-review).

---

## Track frontend completo — features 1-10, todas `done`

Cierre del track `entel-role-repository-frontend`. Resumen final en
`MEJORAS.md` (raíz de `frontend/`). Estado de verificación al cierre:
`bash init.sh` verde, **15 archivos de test, 61 tests**, `npm run lint` 0
errores. Dependencias cross-track pendientes de sincronizar documentadas
en `MEJORAS.md` sección 4 (login/menú a reconciliar contra backend ya
`done`; reportes async/SSE a conectar cuando backend features 7-10
aterricen; gaps `PUT` de Atributo/Usuario; gap `aplicacionCodigo`).

---
