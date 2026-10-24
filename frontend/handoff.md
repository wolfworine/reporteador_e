# Handoff — frontend (entel-role-repository-frontend)

**TRACK COMPLETO.** Actualizado: 2026-08-19 ~23:20. Todas las 10
features `done`, verificado independientemente: `bash init.sh` verde,
14 archivos de test, **59/59 tests**, 0 errores de lint, build limpio.

## Actualización 2026-08-19 (noche, tras la anterior) — menú y CSS reales, no inventados

Dos correcciones más, encontradas por el usuario probando la demo real
(no por ningún test):

1. **CSS real.** `index.css` era el placeholder sin tocar de `npm create
   vite` -- ningún componente de la app (10 features) tenía una sola
   clase propia salvo `Layout.tsx`/`LoginPage.tsx`. Reescrito con
   selectores de elemento globales (`table`, `form`, `input`, `button`,
   `[role="alert"]`) que estilizan las 9 pantallas a la vez sin tocar
   JSX de cada módulo. `App.css` (placeholder muerto) vaciado.
2. **Menú real, no una lista estática inventada.** La sesión anterior
   (misma noche) había agregado una navegación estática mostrando los 8
   módulos a CUALQUIER usuario logueado -- el usuario correctamente
   señaló que esto ignoraba la lógica real de permisos por rol que
   existía en el sistema legado. Investigando, se encontró que el dump
   usado para la migración real (`reposrito_roles_jsf 20150814 1214.sql`)
   es más viejo que otros dumps del árbol legado y no incluye la tabla
   `menu` -- dumps posteriores (`prueba 20150921 1349.sql` y similares) sí
   la tienen, con 10 filas reales: Administrador ve 8 módulos
   (Aplicacion/Area/Cargo/Ubicacion/Rol/Suministro/Detalle/Usuario),
   **Operador ve solo Reporte**. Se migraron esas 10 filas reales a
   `RoleRepositoryMenu` (mismo mecanismo de PutItem directo que la
   migración de catálogos) y se corrigió `Layout.tsx`/`types/menu.ts`/
   `api/menu.ts`/`useMenu.ts` -- **la navegación estática se eliminó por
   completo**, la fuente de verdad ahora es 100% `GET
   /api/menu/{tipoUsuario}` real.
3. **Bug adicional encontrado al conectar datos de menú reales por
   primera vez (mismo patrón que login/reportes hoy: contrato nunca
   verificado contra el backend real):** `types/menu.ts` modelaba una
   fila plana (`codigo: number`, `tipo`, `tipoUsuario`, `codSubmenu`,
   `estado`) que el cliente arma en árbol con `buildMenuTree.ts`. El
   backend real (`domain/MenuNodo.java`) ya arma el árbol en el servidor
   y expone `{codigo: string, nombre, action, hijos}` -- sin esos otros
   campos. `buildMenuTree.ts` (y su test) se eliminaron por completo,
   `Layout.tsx#MenuTree` ahora usa un mapeo `action → ruta` para renderizar
   `<Link>` reales; ítems sin ruta equivalente (Suministro/Detalle, parte
   del drill-down anidado de Roles) se muestran como texto, no como link
   roto.

**4 usuarios reales del legado migrados** (tabla `usuario` del dump, la
migración original no los traía -- ver `../database/handoff.md` para el
porqué): `admin@wisevisioncorp.com`/`admin1` (Mark Falcon,
ADMINISTRADOR), `employer@wisevisioncorp.com`/`employer` (Cesar
Valenzuela, OPERADOR), `Raven@hotmail.com`/`12345` (Jorge Raven,
OPERADOR), `employer2@hotmail.com`/`employer2` (hola hola, OPERADOR) --
contraseñas reales del dump (texto plano en el legacy), ahora hasheadas
con BCrypt de verdad. Más 2 usuarios sintéticos de una sesión anterior
(`admin@entel.demo`/`operador@entel.demo`, password `Demo1234!`) que
quedaron creados también, sin motivo real para borrarlos.

**Verificado en navegador real, ambos roles:** `employer@wisevisioncorp.com`
(Operador) → solo ve "Reporte" en el menú. `admin@wisevisioncorp.com`
(Administrador) → ve los 8 módulos reales, con Suministro/Detalle como
texto no-clicable. 59/59 tests verdes.

## Actualización 2026-08-19 (noche) — sin navegación real, la app era inutilizable con datos reales

Encontrado por el usuario probando la demo de verdad (login OK, datos
reales en el backend confirmados por API, pero **pantalla en blanco, sin
forma de llegar a ninguna página**). Causa: dos gaps independientes que se
combinaban --

1. Este dataset (dump legado real, ver `../database/handoff.md`) no trae
   tabla `menu`, así que `GET /api/menu/{tipoUsuario}` siempre devuelve
   `[]` y el menú dinámico (`Layout.tsx#MenuTree`) queda vacío.
2. **Aun con items, `MenuTree` los pintaba como `<span>`, no `<Link>`** --
   nunca hubiera sido clicable. Nadie lo notó: `Layout.test.tsx` solo
   verificaba que el nombre del ítem aparece en pantalla, nunca que sea
   navegable (mismo patrón de gap que el bug de login: el test probaba la
   forma, no el comportamiento real).

**Corregido:** `Layout.tsx` ahora tiene una `NavegacionPrincipal`
separada y estática (con `<Link>` de verdad) a los 8 módulos ya
implementados (`app/router.tsx`) -- Áreas/Cargos/Ubicaciones/
Aplicaciones/Estados/Roles/Usuarios/Reportes -- en un `<nav aria-label="Módulos">`
independiente del menú dinámico (que se deja como está, para cuando haya
datos de `menu` reales). `Layout.test.tsx` actualizado: las aserciones
del menú dinámico ahora están acotadas con `within()` al `nav` correcto
(la nueva navegación estática también tiene un link "Usuarios", que
colisionaría en texto con el ítem mockeado del menú dinámico si no se
acotara), más un test nuevo que verifica los 8 links reales. 62/62
tests verdes.

**Verificado en un navegador real:** login → clic en "Áreas" → tabla con
datos reales del dump legado (TESORERÍA/GA0054, CAPITAL HUMANO Y
ADMINISTRACIÓN/GC0001, etc.), no un placeholder.

## Actualización 2026-08-19 (tarde) — bug real de login, encontrado probando en navegador real

`LoginPage.tsx` (`onSuccess` de la mutación de login) leía
`data.usuario.email`/`.nombres`/etc., asumiendo que `POST
/api/auth/login` envolvía la respuesta en `{ usuario: {...} }` -- nunca
verificado contra el backend real (los 61 tests mockean `api/auth.ts` por
completo). El backend real (`web/AuthResource.java`, `done` desde la
feature 5) devuelve el `UsuarioResponse` plano, sin wrapper, sin token. El
fetch se resolvía (200 OK), pero `onSuccess` tiraba `TypeError: Cannot
read properties of undefined (reading 'email')` antes de autenticar --
login roto SIEMPRE con credenciales correctas, sin ningún mensaje de
error visible (el error no calzaba en la rama `mutation.isError` de la
UI).

**Corregido:** `types/auth.ts#LoginResponse` ahora es `= Usuario` (alias
del tipo ya existente, no un wrapper nuevo) -- espejo exacto de
`UsuarioResponse.java`. `LoginPage.tsx` lee los campos directo de `data`.
`LoginPage.test.tsx` reescrito para usar la forma real (antes probaba
solo que se llamaba a la API con los parámetros correctos, nunca que el
resultado de un login exitoso autenticara de verdad -- exactamente el gap
que dejó pasar este bug; ahora una sonda `AuthProbe` verifica que
`AuthContext` queda poblado y la app navega a "/"). 61/61 tests siguen
verdes.

**Verificado en un navegador real** (Claude in Chrome, no solo tests):
login con `admin@entel.demo`/`Demo1234!` → redirección real a "/" →
header muestra "Admin Demo". Requirió además arreglar CORS del lado
backend (nunca configurado, ver `../backend/handoff.md`) -- sin eso el
fetch ni siquiera llegaba a completarse en un navegador real, aunque
`curl` siempre funcionó (no está sujeto a CORS).

## Actualización 2026-08-19 (mañana) — coordinación cross-track backend↔frontend (gap DTO Reporte/DetalleRol)

El orquestador top-level ejecutó la coordinación Leader↔Leader pendiente
sobre el gap de contrato `Reporte`/`DetalleRol` (diagnosticado en la
review de la feature 10 del backend). Decisión: **el backend agrega una
capa de mapeo de respuesta que emparea los nombres de campo que el
frontend ya usa** (sin renombrar nada en el frontend por eso). Solo se
tocaron 2 cosas reales en el frontend, ambas verificadas
independientemente (`npx tsc -b` limpio, `npx vitest run` → 61/61 verdes,
sin regresiones):

1. **`types/reporte.ts` (`DetalleRol.idRol`) y `types/detalle.ts`
   (`Detalle.id`): `number` → `string`.** El backend real
   (`domain/DetalleRol.idRol`, `domain/Atributo.id`) usa ids opacos
   `String` (no autonuméricos) — el `number` original era una
   extrapolación del frontend nunca confirmada. Ninguno de los dos
   campos se usaba para aritmética, solo como `key` de React — cambio
   sin riesgo de comportamiento.
2. **`types/detalle.ts` (`Detalle.parametro`): eliminado.** Investigado
   en el código fuente legacy real (`repositorio_entel/version3.5/
   ReporteManagedBean.java` y su respaldo `backup/
   CopyOfReporteManagedBean.java`, los dos únicos lugares donde se
   construye un `DetalleRol`): `d_parametro` (legacy) era el
   discriminador de categoría (`"ROLES"`/`"GRUPOS"`/etc.), no un valor de
   negocio del detalle — el backend ya representa esa misma información
   estructuralmente (en cuál de las 4 listas paralelas aparece cada
   `Detalle`, más el enum `CategoriaSuministro`). El campo tampoco se
   renderizaba en `ReporteTabla.tsx`. Se eliminó en vez de fabricar un
   valor sin respaldo real en el backend.

**Hallazgo adicional, no bloqueante, documentado pero no corregido en
esta sesión:** `DetalleRol.tipoRol` (columna "Titularidad" en
`ReporteTabla.tsx`) — confirmado en el mismo código fuente legacy que
`setTiporol(...)` **nunca se invoca** en ningún managed bean real; la
columna "Titularidad" del legacy siempre renderizó una tabla vacía (dead
code). El backend reutiliza esa misma forma (`List<String>` serializado)
para `tiposConDatos`, un campo con semántica distinta y sí real: qué
categorías de suministro tienen datos para el rol. No se rediseñó la UI
en esta sesión — la próxima vez que se toque `ReporteTabla.tsx`, vale la
pena renombrar la columna a algo como "Categorías con datos" para
reflejar lo que en verdad muestra. Ver el comentario largo en
`types/reporte.ts` (`DetalleRol.tipoRol`) para el detalle completo.

El resto de nombres de campo (`area`/`cargo`/`codigoArea`/`codigoCargo`/
`suministroDefaultinbox`/`cantidadSuministros`, etc.) se mantienen tal
cual en el frontend — el backend los mapea, cero cambios adicionales
aquí. Ver `../backend/handoff.md` para el lado backend de este trabajo
(capa de mapeo, pendiente de implementar/verificar ahí).

## Documento final

`frontend/MEJORAS.md` (252 líneas) es el handoff completo del track:
paridad módulo por módulo con el legacy, desviaciones UX deliberadas
(edición inline, passwords enmascarados, `role="alert"` en todos lados,
SSE en vez de polling, corrección de un bug real del menú legacy),
progresión de cobertura de tests (1→61 en 15 archivos).

## Pendientes cross-track (§4 de MEJORAS.md) — no bloqueantes para este track, sí para el resto del proyecto

1. `auth.ts`/`menu.ts` necesitan reconciliarse ahora que backend features
   5/6 están `done`.
2. El flujo async/SSE de reportes necesita conectarse cuando backend
   features 7-10 (post-split) aterricen, incluyendo `GET /api/reportes/{id}`
   — backend ya se comprometió explícitamente a igualar esa forma (ver
   `backend/handoff.md`, `feature_list.json` id=10 del backend nuevo
   esquema).
3. Faltan endpoints `PUT` para Atributo y Usuario en el backend — gaps
   del backend, no defectos de frontend.
4. Gap de `aplicacionCodigo` en el GSI1PK que afecta el drilldown
   Rol→Suministro→Atributo.

## Lección de proceso de esta sesión (útil para los otros tracks)

Varias veces un Implementer/Reviewer se cortó a mitad de tarea por límite
de sesión, pero **ya había terminado de escribir su veredicto en disco**
antes de morir — solo la respuesta de chat nunca llegó. El Leader
verificó el estado real en disco antes de asumir que había que
relanzar, evitando trabajo duplicado dos veces. Aplica directamente a
Observación #2 y #3 del log de task-observer — antes de relanzar
cualquier subagente "muerto", leer el archivo de progreso/review real
primero.

## 2026-08-20 — Bug real: Reportes nunca mandaba `formato` (mismo patrón de siempre)

**Síntoma reportado por el usuario:** al presionar "Reporte" en la
pantalla de reportes, la generación fallaba con `formato debe ser 'pdf' o
'excel', recibido: 'null'`.

**Root cause:** `modules/reporte/ReporteFiltro.tsx` nunca tuvo un
`<select>` de formato en absoluto, y mandaba `{areaCodigo, aplicacionCodigo}`
en vez de `{idAplicacion, idArea, formato}` — los nombres de campo del
lado del backend real (`web/dto/ReporteGenerarRequest.java`) nunca se
habían confirmado, exactamente el mismo patrón de "extrapolación propia,
nunca validada" que login/menú antes en esta misma sesión.

**Fix:** `types/reporte.ts` (`ReporteGenerarRequest` corregido a
`{idAplicacion, idArea, formato}`), `ReporteFiltro.tsx` (nuevo `<select
id="reporte-filtro-formato">` con `pdf`/`excel`, default `pdf`, submit
corregido), `api/reportes.ts` (doc comment actualizado). Test afectado:
`ReporteVista.test.tsx` (fixture de la aserción del body actualizado).
`tsc -b` limpio, 59/59 tests pasando.

**Verificado end-to-end contra el backend real** (no solo mocks): `POST
/api/reportes/generar` con `formato: "pdf"` y por separado `"excel"`,
escuchando `GET /api/reportes/eventos` (SSE) en paralelo con curl → evento
`LISTO` recibido en ambos casos. Esto además destapó un SEGUNDO bug,
puramente de backend, en el mismo flujo (tópicos Kafka de reportes nunca
creados a tiempo, dejando el "Generando…" colgado para siempre incluso
con el `formato` ya corregido) — documentado y arreglado en
`../backend/handoff.md`, no requirió ningún cambio de frontend.

**Actualización — el usuario SÍ lo seguía viendo colgado tras el fix de
arriba, y tenía razón:** había un TERCER bug en el mismo flujo, esta vez
sí en el backend puro (no en `useReportEvents.ts`, que estaba bien
escrito desde el principio). Root-causado inyectando un wrapper de
`EventSource`/`fetch` en la página real (`javascript_tool`, no solo
curl) y comparando el `id` de `POST /generar` contra el `data` crudo del
mismo `EventSource` que usa la app: el payload SSE real era
`ReporteEvento[id=..., estado=LISTO, mensaje=null]` (el `toString()` de
Java), no JSON — `JSON.parse(message.data)` en `useReportEvents.ts`
fallaba SIEMPRE y el error se descartaba en silencio (por diseño
documentado: "dato malformado, no requiere alert"), dejando el estado
trabado en `GENERANDO` para siempre pese a que el evento correcto
realmente llegaba. Causa raíz y fix (una anotación JAX-RS mal puesta,
`@RestStreamElementType` con el media type del stream en vez del de cada
elemento) documentados en `../backend/handoff.md`. Cero cambios de
frontend — `useReportEvents.ts` ya hacía lo correcto, solo nunca recibió
un payload parseable hasta ahora.

**Verificado en navegador real esta vez, de punta a punta:** login real,
generar reporte real → "Generando…" pasa a "Listo, descargar" con la
tabla completa, "Exportar PDF" descarga un blob real de 6585 bytes
`application/pdf` (confirmado interceptando `URL.createObjectURL`). El
truco que permitió encontrar y confirmar este bug (curl no lo detecta
porque nunca intenta parsear el payload) fue inyectar JS en la página
real vía `javascript_tool` en vez de confiar solo en clicks simulados —
si en el futuro algo "funciona por curl pero no en el navegador", esa es
la técnica a usar primero, antes de sospechar de la herramienta de
automatización.

## 2026-08-20 — Overhaul de diseño pedido por el usuario: COMPLETO

Pedido explícito, textual, del usuario (verbatim, muy enfático): "el
diseño esta muy simplon [...] boton registar no abre una pestaña para
registro actualizar igual elimninar ni que decinar no hay notificaciones
[...] UNIFICA EL FILTRO DE CODIGO NOMBRE EN UNO [...] MODERNIZA LA WEB
PARA ALGO DEL 2026 REACT". Implementado sobre las 9 pantallas de
CRUD/mantenimiento (Área/Cargo/Ubicación/Aplicación/Estado vía
`CatalogoMantenimiento.tsx`, más `RolMantenimiento.tsx`,
`UsuarioMantenimiento.tsx`, `AtributoTabs.tsx`, `SuministroPorRol.tsx`):

- **`queryClient.ts`**: `staleTime: 60_000` / `gcTime: 5*60_000` (antes
  `staleTime` defaulteaba a 0, causando el "Cargando..." repetido al
  volver a una pantalla ya visitada).
- **`components/ui/Modal.tsx`** (nuevo, vía `createPortal`): Registrar/
  Actualizar dejan de ser formularios siempre visibles inline y pasan a
  un diálogo modal (`role="dialog"`, cierra con Escape/clic afuera/botón
  ×). Eliminar ahora pide confirmación en un modal propio ("¿Seguro que
  querés eliminar...?" + Cancelar/Sí eliminar) — antes borraba directo
  sin ningún paso intermedio.
- **`components/ui/ToastProvider.tsx`** (nuevo, montado en `App.tsx` fuera
  del router): notificación toast de éxito/error en las 3 mutaciones
  (crear/actualizar/eliminar) de cada pantalla, complementa (no
  reemplaza) el `role="alert"` inline ya existente dentro de cada modal.
- **`components/ui/Pagination.tsx` + `usePagination.ts`** (nuevos):
  paginación client-side, 10 filas por página, aplicada a las 9 tablas —
  crítico para Cargo (695 filas) y Atributo (hasta 15,565 filas
  potenciales) que antes rendereizaban TODO de una vez.
  `usePagination` vuelve a la página 1 cuando cambia el tamaño de la
  lista filtrada (mismo patrón "ajustar estado en render" ya usado en
  `useReportEvents`/`useReporteFlujo`).
  `SuministroPorRol.tsx` pagina pero no tiene campo de búsqueda propio
  (el backend ya filtra por rol en la URL, no hay nada más que filtrar
  en cliente).
- **Filtro unificado**: los filtros separados Código/Nombre
  (`CatalogoMantenimiento.tsx`), Área/Cargo/Tipo/Ámbito
  (`RolMantenimiento.tsx`), Nombre/Apellido/Email/Tipo
  (`UsuarioMantenimiento.tsx`) y Código/Nombre de atributo
  (`AtributoTabs.tsx`) se unifican cada uno en un único campo "Buscar"
  que matchea contra todos los campos relevantes a la vez.

**Tests**: todos los archivos de test afectados
(`AreaMantenimiento.test.tsx`, `CargoMantenimiento.test.tsx`,
`RolMantenimiento.test.tsx`, `RolSuministroDrilldown.test.tsx`,
`UsuarioMantenimiento.test.tsx`, `AtributoTabs.test.tsx`,
`SuministroPorRol.test.tsx`) actualizados al nuevo flujo modal (abrir el
modal antes de interactuar con el form, confirmar antes de eliminar) —
**59/59 tests pasan**, `tsc -b` limpio.

**Verificado en navegador real** (no solo tests): login real, pantalla
Área — búsqueda unificada filtrando en vivo, modal de Registrar (crea de
verdad contra el backend, `POST /api/areas` → 201), modal de Actualizar
(pre-carga el valor actual), modal de confirmación de Eliminar (borra de
verdad, la fila desaparece y muestra "Sin resultados" tras filtrar),
paginación mostrando "1–10 de 69, Página 1 de 7" antes de filtrar. Las
otras 8 pantallas comparten literalmente el mismo `Modal`/`ToastProvider`/
`Pagination` y pasan su propia suite de tests con el mismo patrón, pero
no se verificaron una por una a mano en el navegador por presupuesto de
tiempo de esta sesión — si aparece algo raro en Rol/Usuario/Atributo/
Suministro, es el primer lugar donde mirar.

**Pendiente real (no bloqueante, cosmético):** pulido visual adicional
más allá de lo pedido explícitamente (iconos, skeleton loaders en vez de
texto "Cargando...", etc.) no se hizo — se priorizó lo que el usuario
pidió textualmente sobre polish especulativo no pedido.

## 2026-08-20 (continuación) — Bug real: "el excel corrupto" + rediseño de Reportes + precarga

**Root cause del Excel corrupto:** `ReporteVista.tsx` mostraba SIEMPRE los
dos botones "Exportar PDF"/"Exportar Excel", pero el backend solo genera
y persiste UN binario por reporte (el formato elegido en `ReporteFiltro`
antes de generar) — `GET /{id}/descargar?formato=...` **ignora ese query
param** y siempre devuelve el binario ya generado con el content-type/
nombre del formato ORIGINAL (ver `web/ReporteResource.java#descargar`,
`../backend/handoff.md`). Si el usuario generaba en `pdf` y clickeaba
"Exportar Excel", el navegador guardaba el binario PDF real con extensión
`.xlsx` — Excel lo rechazaba como corrupto porque el contenido no
correspondía a la extensión.

**Fix:** `useReporteFlujo.ts` ahora expone `formatoGenerado` (el
`formato` con el que realmente se llamó a `generar()`, leído de
`generarMutation`'s `variables` en su `onSuccess`). `ReporteVista.tsx`
renderiza un ÚNICO botón de exportación, el que corresponde a
`formatoGenerado` — ya no hay forma de pedir un formato que no se generó.

**Verificado en navegador real:** generé un reporte en Excel → solo
apareció "Exportar Excel" (no "Exportar PDF") → clickeado → el blob
descargado tiene los magic bytes reales de ZIP (`50 4b 03 04`, firma
OOXML/xlsx), tamaño y content-type correctos — confirmado leyendo los
primeros bytes del blob interceptado vía `URL.createObjectURL`, no solo
confiando en el nombre de archivo.

**Rediseño visual de Reportes** (mismo pedido del usuario, "rediseña la
vista para web dale espacios"): `ReporteVista.tsx` — el bloque
"Listo, descargar" + botón de exportar ahora es una tarjeta con padding
propia (`.reporte-vista__acciones`) en vez de texto suelto; el botón de
exportar usa el estilo primario (azul, como los submit); toast de éxito/
error al descargar además del `role="alert"` inline existente. Cada
"Cargo" del árbol del reporte (`ReporteTabla.tsx`, sin cambios de JSX,
solo CSS vía `section[aria-label^="Cargo "]`) ahora es su propia tarjeta
con separación entre una y otra — antes eran tablas apiladas sin ningún
respiro visual.

**Precarga/caché** (pedido repetido del usuario, "deberias precargar al
inicio toda la informacion"): `components/layout/Layout.tsx` ahora
precarga (`queryClient.prefetchQuery`) las listas de Área y Aplicación al
montar el layout autenticado (una vez por sesión, apenas se loguea),
antes de que el usuario navegue a Reportes o a esos catálogos — combinado
con el `staleTime: 60s` ya seteado (ver más arriba en este archivo), la
navegación a Reportes/Área/Aplicación dentro del primer minuto de sesión
ya no dispara ningún fetch visible. `Layout.test.tsx` actualizado
(mockea `api/areas.ts`/`api/aplicaciones.ts`, antes solo mockeaba
`api/menu.ts`).

**Tests:** `ReporteVista.test.tsx` (aserciones de "un solo botón" +
`ToastProvider` en el render helper + selector de formato en el helper
compartido `seleccionarYEnviarFiltro`), `Layout.test.tsx` (mocks nuevos).
59/59 tests, `tsc -b` limpio.

**Pendiente real (fuera de este alcance, documentado para cuando se
retome):** el backend ignorando el query param `formato` de
`/descargar` sigue siendo una superficie de bug latente si alguna vez se
llama a ese endpoint directo (no vía la UI ya corregida) pidiendo un
formato distinto al generado — no se tocó el backend para esto porque el
fix del frontend ya cierra el único camino real por el que un usuario
podía llegar a esa situación; endurecer el backend (400 si no coincide,
o generar bajo demanda el formato pedido) queda para una iteración futura
si se decide exponer `/descargar` a consumidores externos a esta UI.

## 2026-08-25 — Skeleton loaders + branding de Login

Cerrando el único ítem que había quedado explícitamente pendiente del
overhaul de diseño ("estados de carga... bien diseñados", pedido textual
del usuario en la sesión del overhaul):

- **`components/ui/TableSkeleton.tsx`** (nuevo): placeholder de tabla con
  barras shimmer (`prefers-reduced-motion` respetado), reemplaza el
  `<p>Cargando {titulo}…</p>` de texto plano en las 9 pantallas de
  listado (`CatalogoMantenimiento`, `RolMantenimiento`,
  `UsuarioMantenimiento`, `AtributoTabs`, `SuministroPorRol`) y también el
  `<p>Cargando datos del reporte…</p>` de `ReporteVista.tsx`. `columns`
  se pasa a mano por pantalla para que coincida con el `<thead>` real (evita
  que el layout salte al llegar los datos).
- **`LoginPage.tsx`**: agregado un badge de marca ("RR") y subtítulo
  ("Ingresá con tu cuenta corporativa"); el `<h1>` cambió de "Iniciar
  sesión" a "Repositorio de Roles" (más consistente con el header real de
  la app una vez logueado). `App.test.tsx` (no `LoginPage.test.tsx`, que
  no dependía del texto exacto) tenía una aserción sobre el heading viejo
  -- actualizada.

**Tests:** 59/59, `tsc -b` limpio. **Verificado en navegador real**: login
con el badge/subtítulo nuevos, y pantalla Usuario mostrando los 4 usuarios
reales tras el incidente de persistencia de LocalStack (ver
`../handoff.md` 2026-08-25) — confirma que el rediseño y la recuperación
de datos conviven sin problema.

## 2026-08-25 (continuación) — Titularidad sin forma, inconsistente con Atributos

El usuario mandó una captura real de la vista comparando la columna
"Titularidad" (una lista de texto suelto flotando, sin caja ni
encabezado) contra "Atributos" (4 cajas bien formadas con `<caption>`
ROLES/GRUPOS/INBOX/DEFAULTINBOX) — pidió centrarla y hacerla consistente.

**Fix:** `ReporteTabla.tsx#TitularidadCell` — se agregó `<caption>Titularidad</caption>`
(el MISMO selector CSS `caption`/`td table` que ya usan las 4 cajas de
`AtributosCell`, así que el tratamiento visual es idéntico por
construcción, no solo aproximado) y una clase `.reporte-titularidad`
nueva en `index.css` que centra el texto (`text-align: center`) y centra
la caja dentro de su celda (`margin: 0 auto`).

**Verificado en navegador real:** generé un reporte y confirmé
visualmente que "Titularidad" ahora tiene la misma caja con encabezado
gris que "ROLES"/"GRUPOS"/"INBOX", con el texto centrado. `ReporteTabla.test.tsx`
sigue verde (17/17 en el módulo `reporte`), no dependía de la estructura
interna de esa celda más allá del `aria-label`.

## 2026-08-25 (continuación) — Titularidad: segunda vuelta + revertido el flujo de un solo botón

**Titularidad, corrección real (la del punto anterior no era lo que
pedía el usuario):** el usuario mandó una comparación real "to-be" (sin
caja, cada valor a la altura de su categoría en Atributos) vs "as-is" (la
caja compacta con `<caption>` del fix anterior, agrupada en el medio).
`TitularidadCell` se reescribió: de `<table><caption>` a un `<ul
className="reporte-titularidad">` con `display:flex; flex-direction:
column; justify-content: space-between; height:100%` -- reparte los
valores desde arriba hasta abajo de TODA la altura de la fila (la fila ya
es tan alta como el contenido de Atributos) en vez de agruparlos. No es
alineación pixel-perfecta por categoría (dos celdas de tabla
independientes no dan esa garantía), pero reproduce el efecto visual real
pedido -- Titularidad tiene ≤4 valores, mismas categorías que Atributos
en el mismo orden, así que coincide razonablemente bien. Test actualizado
(`getByRole('table', ...)` → `getByRole('list', ...)`, ya no es una
`<table>`).

**Revertido el flujo de "un solo botón de exportar":** el usuario
preguntó por qué el filtro pedía Formato antes de generar y por qué solo
aparecía un botón de exportar, cuando originalmente era un filtro
Aplicación+Área con los 2 botones siempre disponibles. La causa real
estaba en el backend (`GET /descargar?formato=` nunca implementado, ver
`../backend/handoff.md`) -- con eso arreglado, se revierte la UI:
- `ReporteFiltro.tsx`: se quita el `<select>` de Formato, vuelve a mandar
  siempre `formato: "pdf"` fijo (ya no le importa al usuario cuál se
  genera primero, el otro se regenera al vuelo al pedirlo).
- `ReporteVista.tsx`/`useReporteFlujo.ts`: se quita `formatoGenerado` y
  la lógica de un solo botón -- vuelven a mostrarse SIEMPRE "Exportar
  PDF" y "Exportar Excel", cada uno con su propio estado
  `isPending`/`variables` para deshabilitarse solo el que está
  descargando (no ambos a la vez).

**Verificado en navegador real:** login, generar un reporte SIN elegir
formato (el filtro ya no lo pide), confirmé que aparecen los 2 botones de
exportar, clickeé "Exportar Excel" en un reporte generado como PDF -- el
blob descargado tiene magic bytes ZIP reales (`50 4b 03 04`), confirmando
que la regeneración al vuelo del backend funciona de punta a punta desde
la UI real, no solo por curl. 59/59 tests, `tsc -b` limpio.

## 2026-08-25 (continuación) — Titularidad, tercera vuelta: centrado proporcional real

El fix anterior (`space-between` repartiendo en partes iguales) no era lo
que pedía el usuario: mandó otra comparación real donde señaló que cada
valor debe quedar centrado DENTRO del alto real de su propia categoría en
Atributos (si "GRUPOS" tiene 4 atributos e "INBOX" 1, la caja "GRUPOS" es
más alta que "INBOX" -- partes iguales no lo reflejaba).

**Fix:** `TitularidadCell` ahora deriva sus ítems del mismo array
`CATEGORIAS` y el mismo filtro (`detalle[key].length > 0`) que usa
`AtributosCell` -- garantiza mismo orden y mismas categorías visibles en
las dos columnas por construcción, no por coincidencia. Cada `<li>` recibe
`flex-grow` proporcional a `cantidad de atributos + 1` (el +1 imita la
fila de encabezado que sí tiene su caja en Atributos) y es a su vez un
contenedor flex que centra su texto dentro del espacio que le tocó. No es
pixel-perfecto (dos tablas independientes no lo garantizan) pero es
proporcional a la cantidad real de filas de cada bloque -- verificado
visualmente en navegador real con zoom, el resultado coincide bien con la
captura "to-be" del usuario.

**Fixture de test corregido en el proceso:** `ReporteTabla.test.tsx`
tenía un caso (`Rol Analista Dos`) con `tipoRol: ['Heredado', 'Directo']`
pero las 4 listas `suministroX` vacías -- una combinación que el backend
real nunca produce (`tipoRol` son códigos de categoría reales, siempre en
correspondencia 1:1 con qué `suministroX` viene poblado). El nuevo
`TitularidadCell`, al derivar directamente de `suministroX.length`,
expuso esta inconsistencia (el test fallaba con la lista vacía). Se
corrigió el fixture para que sea representativo de datos reales (`GRUPOS`
e `INBOX` pobladas con datos reales en vez de las etiquetas inventadas) y
se actualizaron los conteos de tablas por categoría que dependían del
fixture viejo.

**PDF (`PdfReportAdapter.java`):** se sacó la caja con encabezado gris
"Titularidad" que había quedado de un intento anterior (pedido explícito:
"retiralo el encabezado") -- ahora es una lista simple centrada, sin
encabezado, igual que la versión final de la web. El centrado
PROPORCIONAL de la web no se replicó en el PDF -- documentado como
limitación conocida y aceptada en el Javadoc de `tablaTitularidad`
(OpenPDF calcula el alto de cada fila DESPUÉS de poner el contenido, no
hay forma simple de reservarle a esta tabla anidada la proporción exacta
que va a terminar ocupando la tabla de Atributos vecina antes de que
ambas se rendericen).

**Tests:** 59/59, `tsc -b` limpio (frontend); `mvn compile` limpio,
`ReportGeneratorAdaptersTest` 3/3 (backend). Verificado en navegador real
con zoom sobre la columna Titularidad -- el resultado coincide con la
captura "to-be" del usuario.

## 2026-08-25 (continuación) — Pantallas de mantenimiento: layout de tabla estable + iconos

Dos pedidos explícitos del usuario probando Área en el navegador real:
"cuando paso de pagina 1 a 2 los texto se mueven, no hay tamaño fijado...
esto replica a todas las tablas" y "el boton registrar, lista de
acciones modernizalas... usan iconos".

**Layout de tabla estable:** ninguna tabla tenía `table-layout: fixed` --
el navegador repartía el ancho de columna según el CONTENIDO de la
página actual, así que cambiar de página (texto distinto) reacomodaba
las columnas visiblemente. Se agrega `.data-table` (`table-layout: fixed`
+ `overflow-wrap: break-word` en `index.css`) y un `<colgroup>` con
anchos porcentuales explícitos en las 5 tablas de mantenimiento
(`CatalogoMantenimiento.tsx`, `RolMantenimiento.tsx`,
`UsuarioMantenimiento.tsx`, `AtributoTabs.tsx`, `SuministroPorRol.tsx`) --
cada columna tiene un ancho fijo independiente del contenido de la
página actual; texto que no entra pasa a otra línea (la FILA crece, no
la columna). Deliberadamente NO se tocó `ReporteTabla.tsx` (tablas
anidadas de datos muy variables, sin paginación, el ancho fijo se vería
peor que el auto-fit actual). Verificado en navegador real: comparé el
ancho en píxeles de cada columna entre página 1 y página 2 de Área vía
`getBoundingClientRect()` -- idéntico (219.8/549.6/329.8px, misma
proporción 20/50/30% en ambas páginas).

**Iconos en botones de acción:** `components/ui/icons.tsx` (nuevo) --
4 iconos SVG inline (`IconPlus`/`IconPencil`/`IconTrash`/`IconEye`,
trazo `currentColor`, sin librería externa nueva) usados en
"Registrar"/"Actualizar"/"Eliminar"/"Ver Suministros"/"Ver Atributo" en
las mismas 5 pantallas. El icono SIEMPRE va junto a su texto (nunca
icon-only) -- el nombre accesible del botón sigue siendo el texto, así
que ningún test que buscaba por `getByRole('button', {name: 'Actualizar'})`
etc. se rompió. Celdas de Acciones con 3+ botones (Rol, Suministro) usan
`.acciones-cell` (`flex-wrap`) para que los botones envuelvan en vez de
desbordar la columna de ancho fijo.

**Tests:** 59/59 sin cambios (los botones conservan su texto), `tsc -b`
limpio. Verificado en navegador real: Área con paginación estable +
iconos visibles y legibles junto a cada acción.

## PENDIENTE — Botones a icon-only (pedido explícito, NO empezado)

El usuario volvió a probar en el navegador tras el fix de arriba y mandó
`vista_sobrecarga.png`: con 3 botones icono+texto por fila (Rol,
Suministro) el área de Acciones se ve "sobrecargada" y los botones
envuelven a 2 líneas. Pedido: **sacar el texto visible, dejar solo
ícono**, e iconos "más intuitivos". Sin empezar todavía -- próximo paso
concreto:

1. En las 5 pantallas (`CatalogoMantenimiento.tsx`, `RolMantenimiento.tsx`,
   `UsuarioMantenimiento.tsx`, `AtributoTabs.tsx`,
   `SuministroPorRol.tsx`), sacar el texto de cada `<button
   className="icon-button">` y agregar `aria-label="Actualizar"` (o el
   texto que corresponda) + `title="Actualizar"` (tooltip on hover) --
   **el `aria-label` preserva el nombre accesible**, así que los tests
   existentes que hacen `getByRole('button', {name: 'Actualizar'})` etc.
   siguen pasando sin tocar los `.test.tsx` (Testing Library resuelve el
   nombre accesible desde `aria-label` igual que desde texto visible).
2. Los iconos actuales (`components/ui/icons.tsx`:
   `IconPlus`/`IconPencil`/`IconTrash`/`IconEye`) son razonablemente
   estándar (lápiz=editar, tacho=eliminar, ojo=ver) -- probablemente no
   hace falta cambiarlos, pero SÍ subir el tamaño (hoy 15px, pensado para
   ir al lado de texto) ahora que van solos, y confirmar que siguen
   siendo reconocibles a simple vista en el navegador real antes de dar
   por cerrado el pedido de "más intuitivos".
3. Verificar: `npx vitest run` (59/59 esperado, sin cambios de aserción),
   `tsc -b` limpio, y **capturar en el navegador real** que el área de
   Acciones de Rol/Suministro (los casos de 3 botones) ya no envuelve a 2
   líneas -- comparar contra `vista_sobrecarga.png`.

## PENDIENTE — Suministro/Detalle "desactivados" en el sidebar: ¿así era en el original?

El usuario notó que "Suministro"/"Detalle" aparecen sin link (span, no
clickable) en el sidebar actual (`Layout.tsx#ACTION_A_RUTA`, decisión de
diseño de una sesión anterior: se acceden solo por drill-down desde Rol)
y pidió confirmar si el legacy real ya se comportaba así. Investigación
en curso, **inconclusa pero con hallazgos reales**, retomar desde acá:

- El árbol `war/ROOT_fixed` tiene el `paginas/` correcto para este
  dominio (`aplicacion`, `area`, `cargo`, `detalle`, `reportes`, `rol`,
  `suministro`, `ubicacion`, `usuario` -- confirmado por carpeta) y las
  vistas referencian beans con los nombres esperados (`#{rolManagedBean`,
  `#{suministroManagedBean`, `#{detalleManagedBean`, etc., confirmado con
  grep sobre los `.xhtml`), pero su `WEB-INF/classes` compilado **no
  tiene ninguna de esas clases** -- solo contiene beans de un dominio de
  ecommerce/videojuegos (`AccesorioManagedBean`, `JuegoManagedBean`,
  etc., paquete `pe.edu.cibertec.ecommerce.*`). Es decir: `ROOT_fixed` es
  una extracción con las vistas correctas pero el `.war` compilado de
  OTRO proyecto (o una carpeta de deploy reusada/pisada a medias) --
  **los managed beans reales de Rol/Suministro/Detalle/Menu de este
  dominio no están compilados en ningún lado del árbol** buscado hasta
  ahora.
- El menú real en producción NO se arma desde markup estático -- el
  `header.xhtml` activo usa `<p:menubar model="#{menuController.model}">`
  (un `MenuModel` armado en Java). Busqué `MenuController.java`/`.class`
  en TODO `D:\Proyecto textil` (incluido dentro de jars) -- no existe en
  ningún lado. Sin ese archivo no hay forma de leer la lógica real de
  qué ítems quedaban clicables.
- Debajo de ese `<p:menubar>` hay un bloque `<p:megaMenu>` **comentado**
  (`<!-- ... -->`, nunca compilado ni servido) que es un borrador de
  diseño con Aplicacion/Area/Cargo/Ubicacion/Rol/Suministro/Atributo/
  Detalle (×2, duplicado) -- **ahí Suministro y Detalle NO están
  especialmente desactivados**: absolutamente TODOS los módulos del
  borrador (incluidos Rol, Area, Cargo) usan `url="#"` como placeholder
  en "Nuevo"/"Listado" salvo un único `url="index.xhtml"` en un "Nuevo"
  de una de las dos entradas duplicadas de "Detalle" (parece un
  copy-paste accidental, no una decisión de diseño). Este bloque es
  código muerto de todos modos, no evidencia del comportamiento real.
- **Evidencia más fuerte, de datos reales de producción** (ya extraída
  esta sesión desde `version 3.8.1/prueba 20150921 1349.sql`, tabla
  `menu`, ver `../handoff.md` sección 2026-08-25 persistencia): en el
  dump real, Suministro y Detalle tienen `tipo='S'` / `tipoUsuario='A'`
  -- **exactamente los mismos valores que Aplicacion/Area/Cargo/
  Ubicacion/Rol**. Nada en la tabla `menu` real distingue a Suministro/
  Detalle como inactivos frente a los demás módulos.

**Conclusión preliminar (no 100% cerrada, el `MenuController.java` real
sigue sin aparecer):** no hay evidencia -- ni en el borrador comentado ni
en los datos reales de la tabla `menu` -- de que Suministro/Detalle
fueran especialmente inactivos en el legacy. Lo más probable es que SÍ
eran top-level clicables en el original (mismo `tipo`/`tipoUsuario` que
los demás), y que el `<span>` no-clicable actual en `Layout.tsx` es una
decisión de diseño tomada en una sesión anterior de ESTE proyecto (no
heredada del legacy) al notar que Suministro/Detalle requieren un
`idRol`/`idSuministro` en la ruta (no tienen sentido como listado
"global" sin padre) -- una restricción real del modelo de datos que el
legacy quizás resolvía distinto (ej. mostrando un selector de Rol antes,
o un listado plano). Antes de tocar código, reportarle esto al usuario
tal cual y preguntar si quiere (a) dejarlo como está (ya es coherente
con el modelo de datos real), o (b) agregar una pantalla intermedia de
selección de Rol/Suministro para que Suministro/Detalle sean
clicables desde el sidebar top-level como en los datos legacy.
