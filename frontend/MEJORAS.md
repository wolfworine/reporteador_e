# MEJORAS — Migración frontend Repositorio de Roles (Entel)

> Reporte final de la migración JSF 2.2 + PrimeFaces → React 19 + TypeScript +
> Vite. Escrito al cierre de la feature 10 (`reporte_final_mejoras`), la
> última del track frontend. Fuente primaria: `progress/history.md` (9
> entradas de sesión, features 1-9). Este documento sintetiza y organiza esa
> bitácora; no reconstruye la migración desde cero.

**Precondición verificada:** las features 1-9 de `feature_list.json` están
todas en `status: "done"` (confirmado leyendo el archivo directamente antes
de escribir este reporte, tal como exige el acceptance criterio 6 de la
feature 10).

---

## 1. Módulos migrados con paridad funcional confirmada

Cada módulo fue verificado por el Reviewer contra la(s) vista(s) `.xhtml`
legacy correspondiente(s), no solo contra una descripción de alto nivel.

| Feature | Módulo(s) React | Vista(s) legacy replicada(s) | Paridad confirmada |
|---|---|---|---|
| 1 | Scaffold (`src/App.tsx`) | — (bootstrap, sin vista legacy) | Build/test/lint en verde |
| 2 | `src/api/client.ts`, `src/types/*.ts` | — (infraestructura, tipos espejo de las clases Java legacy) | Campos verificados uno a uno contra `pe.edu.cibertec.proyemp.model.*.java` |
| 3 | `src/modules/login/LoginPage.tsx`, `src/modules/menu/*`, `src/components/layout/Layout.tsx` | `MenuController.java` (armado de árbol) | Árbol de menú armado con `codSubmenu` explícito, verificado con datos de 2 niveles y 2 `tipoUsuario` distintos |
| 4 | `src/modules/{area,cargo,ubicacion,aplicacion,estado}/*Mantenimiento.tsx` sobre `src/components/catalogo/CatalogoMantenimiento.tsx` | `paginas/{area,cargo,ubicacion,aplicacion}/mantenimiento.xhtml` | Filtro por código/nombre, alta, edición y baja verificados contra el patrón `p:column filterBy` legacy; `Estado` sin vista legacy propia, mantenimiento agregado por paridad estructural con los otros 4 catálogos (no scope creep, confirmado contra `domain/Estado.java`) |
| 5 | `src/modules/rol/RolMantenimiento.tsx`, `src/modules/suministro/SuministroPorRol.tsx`, `src/modules/atributo/AtributoPorSuministro.tsx` (reemplazado en feature 6) | `paginas/rol/mantenimiento.xhtml`, flujo Aplicación → VerRol → Rol → VerSuministros → Suministro → VerAtributo | Drilldown Rol→Suministro probado con dataset mixto de 2 roles (test que confirma que el rol incorrecto nunca aparece); Suministro→Atributo probado igual con 2 suministros |
| 6 | `src/modules/atributo/AtributoTabs.tsx` | `paginas/*/{grupos,inbox,defaultinbox}.xhtml` + `mantenimiento_atributo.xhtml` (vista base) | 4 pestañas Roles/Grupos/Inbox/DefaultInbox, Roles activa por defecto (paridad confirmada leyendo las 4 vistas legacy y verificando que comparten forma idéntica); test verifica no solo presencia sino **ausencia** cruzada de datos entre pestañas |
| 7 | `src/modules/usuario/UsuarioMantenimiento.tsx` | `paginas/usuario/*.xhtml` | Listado nunca expone password/hash (verificado con mock "hostil" que intenta filtrar el campo igual); alta con password enmascarada |
| 8 | `src/modules/reporte/{ReporteFiltro,ReporteTabla,ReporteVista}.tsx` | `paginas/reportes/reporte.xhtml` (filtro) y `paginas/reportes/vista.xhtml` (tabla anidada) | Jerarquía Cargo→Rol→(Roles/Grupos/Inbox/DefaultInbox) replicada con fixture de 2 Cargos/3 Roles/4 categorías, columnas Rol/Tipo/Aplicacion/Titularidad igual que `p:dataTable`/`p:subTable` legacy |
| 9 | (test agregado a `AreaMantenimiento.test.tsx`) | — (cobertura, no UI nueva) | Cierra el único gap real de cobertura: crear→listar→editar en un solo flujo continuo |

---

## 2. Decisiones de UX deliberadas que difieren del legacy, y por qué

Todas están documentadas en el momento en que se tomaron (`history.md`), no
son racionalizaciones post-hoc:

- **Edición inline vs. navegación de página completa.** El legacy navegaba a
  `modificar.xhtml` para editar un catálogo; `CatalogoMantenimiento.tsx`
  (feature 4) edita la fila inline sin abandonar el listado — menos
  fricción, mismo resultado funcional.
- **Filtro client-side en vez de `p:column filterBy` server-side.** Ningún
  endpoint real de catálogos, rol, suministro o atributo expone parámetros
  de búsqueda — el backend solo ofrece listar completo. Todos los módulos
  (features 4-7) filtran en cliente para preservar la experiencia de
  filtrado del legacy sin poder replicar su mecanismo interno.
- **Contraseña enmascarada.** El legacy (`editar.xhtml` de Usuario) usaba un
  `p:inputText` sin máscara — la contraseña era visible en texto plano en
  pantalla. El nuevo formulario (feature 7) usa `type="password"` — mejora
  de seguridad deliberada, no paridad estricta.
- **Etiquetas de texto en exportación en vez de íconos sin texto.** Los
  botones "Exportar PDF"/"Exportar Excel" (feature 8) reemplazan los
  enlaces con solo ícono del legacy — mejora de accesibilidad deliberada.
- **Manejo de errores explícito y consistente (`role="alert"`).** El legacy
  no tenía un patrón uniforme de feedback de error. Todo formulario y
  mutación del nuevo frontend (login en feature 3, catálogos en feature 4,
  atributos en feature 6, usuarios en feature 7, reportes en feature 8)
  muestra el error inline con `role="alert"`, nunca solo en consola —
  verificado explícitamente con tests que espían `console.log/error/warn`
  durante flujos de fallo (feature 7).
- **`codSubmenu` explícito en vez de replicar el cross-join real del
  legacy.** El Reviewer leyó `MenuController.establecerPermisos()` línea por
  línea (feature 3) y confirmó que la jerarquía "real" del legacy es en
  realidad un cross-join sin usar `cod_submenu` — un bug, no un diseño
  intencional. El nuevo `buildMenuTree.ts` arma el árbol correctamente vía
  `cod_submenu`, una desviación justificada del comportamiento legacy
  observado, no un apartamiento arbitrario.
- **Notificación por SSE en vez de polling.** Documentado como decisión de
  producto desde el inicio del track (no una alternativa evaluada en
  frontend): `useReportEvents.ts` (feature 8) usa `EventSource` real,
  verificado con un test que confirma explícitamente que `setInterval`
  nunca se invoca.
- **Estados de UI explícitos para el flujo asíncrono de reportes.**
  `useReporteFlujo.ts` (feature 8) modela INACTIVO→GENERANDO→LISTO|FALLIDO
  como máquina de estados visible (`role="status"`/`role="alert"`) — el
  legacy no tenía equivalente porque el flujo async no existía antes de
  esta migración (corrección 09-ago-2026 al plan: "el flujo es 100%
  asíncrono desde el día uno, no hay versión síncrona previa").

---

## 3. Cobertura de tests agregada por feature

Verificado contra los números citados textualmente en `progress/history.md`
y contra el conteo real de archivos `*.test.ts(x)` en `src/` (15 archivos,
confirmado con `find` sobre el repo al momento de escribir este reporte).

| Feature | Qué se testeó | Tests agregados | Total acumulado | Archivos de test |
|---|---|---|---|---|
| 1 | Smoke test del scaffold | 1 | 1 | 1 |
| 2 | `api/client.ts` (éxito y error de fetch mockeado) | 5 | 6 | 2 |
| 3 | Login, armado de árbol de menú, Layout por `tipoUsuario` | 9 | 15 | 5 |
| 4 | Área/Cargo: listar, filtrar, crear+refetch, error inline; +2 regresión del fix `mensaje`/`message` | 8 (6 + 2 de la ronda de corrección) | 23 | 7 |
| 5 | Rol/Suministro/Atributo: mantenimiento + drilldown discriminado por id | 9 | 32 | 11 |
| 6 | `AtributoTabs`: 4 pestañas, filtro, creación, error, navegación (neto: −1 test eliminado de la feature 5 + 6 nuevos) | 6 (neto +5 sobre 32) | 37 | 11 |
| 7 | Usuario: filtro, creación con password enmascarada, no-filtración de password (mock hostil + espía de consola) | 6 | 43 | 12 |
| 8 | `ReporteTabla` (fixture jerárquico), `useReportEvents` (SSE vs. polling), `ReporteVista` (flujo Generando→Listo, exportación, camino FALLIDO) | 17 | 60 | 15 |
| 9 | Flujo crear→listar→editar completo en un único `it` (Área) | 1 | 61 | 15 |

**Total final: 61 tests en 15 archivos**, todo con `api/*.ts` mockeado vía
`vi.mock` — cero llamadas HTTP reales en la suite.

---

## 4. Dependencias frontend↔backend pendientes de sincronizar

Estado verificado leyendo `../backend/feature_list.json` directamente al
momento de escribir este reporte (no el estado citado en `history.md` en el
momento de cada feature, que puede haber quedado desactualizado):

- **(a) Login y menú dinámico (feature 3 del frontend).**
  `src/api/auth.ts` y `src/api/menu.ts` se construyeron contra rutas
  razonadas (`POST /api/auth/login`, `GET /api/menu/{tipoUsuario}`),
  documentadas explícitamente como pendientes de reconciliar con el
  contrato real. **Actualización respecto a `history.md`:** las features
  backend correspondientes — id=5 `usuario_login_password_hash_fix` e id=6
  `menu_dinamico_por_tipo_usuario` — están ahora ambas en `status: "done"`
  en `../backend/feature_list.json` (no `pending` como cuando se escribió la
  feature 3). El frontend **no ha sido revisitado** desde entonces para
  verificar `src/api/auth.ts`/`src/api/menu.ts`/`src/types/auth.ts` campo a
  campo contra el contrato real ya implementado — sigue siendo trabajo
  pendiente, aunque el bloqueo original (backend sin implementar) ya no
  existe.

- **(b) Flujo async/SSE de reportes (feature 8 del frontend).** El backend
  dividió su antigua feature única `reportes_generacion_async_kafka` en
  tres (ids 7, 8, 9); la feature `reportes_notificacion_sse_y_descarga` ya
  existía como feature separada antes de la división y fue renumerada a
  id=10 en el mismo cambio (16-ago-2026), no creada por el split. Las 4
  features cubren colectivamente el trabajo de reportes pendiente de
  conectar: id=7
  `reportes_dominio_y_query_sin_duplicados` (**`in_progress`**), id=8
  `reportes_adaptadores_pdf_excel` (**`pending`**), id=9
  `reportes_generacion_async_kafka` (**`pending`**), id=10
  `reportes_notificacion_sse_y_descarga` (**`pending`**). Ninguna de las
  cuatro está `done` al momento de escribir este reporte. El frontend
  (`ReporteVista.tsx`, `useReportEvents.ts`, `useReporteFlujo.ts`) opera
  100% contra un `EventSource` y endpoints mockeados en tests, sin ninguna
  integración real posible todavía.

- **(c) Endpoint `GET /api/reportes/{id}` — coordinación cross-track ya
  hecha, implementación pendiente.** El frontend necesitó un 4º endpoint no
  confirmado (`obtenerReporte` en `src/api/reportes.ts`) para poblar
  `ReporteTabla` en pantalla, porque ninguno de los 3 endpoints confirmados
  (generar/eventos/descargar) devuelve la estructura tabulable. Esto se
  documentó explícitamente en 3 lugares del frontend (`progress/current.md`
  de la feature 8, JSDoc de `api/reportes.ts`, `types/reporte.ts`). El
  backend **ya se comprometió** a esta forma exacta: la feature backend
  id=10 (`reportes_notificacion_sse_y_descarga`) tiene como acceptance
  criterion explícito "`GET /api/reportes/{id}` devuelve la estructura
  `Reporte[]`... misma forma que espera `../frontend/src/api/reportes.ts`
  (coordinado con el Leader de `../frontend/` el 16-ago-2026)" — pero la
  feature sigue `pending`, sin implementar. Cuando se implemente, reconciliar
  `obtenerReporte` contra la forma real entregada (puede diferir en detalle
  aunque la forma general esté acordada).

- **(d) Gap `PUT /api/atributos/{id}` (feature 6 del frontend).**
  `AtributoResource.java`/`AtributoService.java` solo exponen `POST` + 2
  `GET`, sin `PUT` (confirmado leyendo el Javadoc del propio backend, que lo
  declara explícitamente). El legacy sí tenía "Modificar"
  (`modificar_atributo.xhtml`), así que la UI de edición se omitió por falta
  de soporte backend, no por decisión de producto — gap real, documentado,
  no defecto de frontend. No hay ninguna feature backend que lo cierre
  actualmente en `../backend/feature_list.json`.

- **(e) Gap `PUT /api/usuarios/{id}` (feature 7 del frontend).** Mismo
  patrón: `UsuarioResource.java` solo expone `GET`/`POST`/`DELETE`
  ("CRUD reactivo... sin actualización", declarado en su propio Javadoc). El
  legacy sí tenía `editar.xhtml` con flujo de password. Sin feature backend
  que lo cierre actualmente.

- **(f) Gap `aplicacionCodigo` en el drilldown Rol→Suministro→Atributo
  (features 5 y 6 del frontend).** La GSI1PK real usada por
  `AtributoRepository.listarPorRolYCategoria` es
  `"APLICACION#" + idAplicacion + "#AREA#" + idArea`. `idArea` se resuelve
  del `Rol`, pero **ni `Rol` ni `Suministro` exponen `aplicacionCodigo`** en
  su DTO actual — no hay forma de obtener ese valor navegando
  Rol→Suministro→Atributo sin pedirlo manualmente. Contra un backend real
  (no mockeado), la key resultante sería literalmente
  `APLICACION#null#AREA#...` y no matchearía datos. Confirmado por el
  Reviewer leyendo el repositorio backend línea por línea (feature 5); el
  formulario de creación de Atributo (feature 6) termina pidiendo
  `aplicacionCodigo` como texto manual como mitigación. No bloquea ninguna
  feature ya cerrada (tests 100% mockeados) pero afectará cualquier
  integración real. No hay feature backend dedicada a resolverlo — sería
  scope de una nueva feature en cualquiera de los dos tracks (agregar el
  campo a `Rol`/`Suministro`, o propagar el código de Aplicación como
  contexto de navegación en frontend).

---

## 5. Recomendaciones no implementadas en este pase

- **`clearMocks: true` global en `vite.config.ts`.** Actualmente los mocks
  de `vi.mock` a nivel de módulo persisten entre `it`s dentro del mismo
  archivo (ej. `AreaMantenimiento.test.tsx`), lo que obliga a evitar
  aserciones de conteo absoluto (`toHaveBeenCalledTimes`) o a depender de
  `mockResolvedValueOnce` consumido en orden exacto. Funciona hoy porque
  cada test consume su cuota exacta sin residuo (verificado en aislamiento
  en la feature 9), pero es una trampa latente para tests futuros del mismo
  archivo. Señalado explícitamente como concern no bloqueante en el cierre
  de la feature 9.
- **Tests unitarios aislados de `ReporteFiltro.tsx` y `useReporteFlujo.ts`.**
  Ambos solo están cubiertos indirectamente vía `ReporteVista.test.tsx`
  (feature 8). Adecuado para cerrar la feature, pero tests aislados darían
  señal más rápida y granular a futuro.
- **Endurecer `AtributoRequest.estado` (`src/api/atributos.ts`) a un tipo
  real.** Actualmente es `string` genérico porque espeja el `String` crudo
  del backend (`AtributoRequest` no lo tipa como enum). Si el backend define
  un enum real para este campo, propagar el tipo al frontend.
- **Comentarios stale en `src/api/atributos.ts` y `src/types/atributo.ts`**
  que aún mencionan `AtributoPorSuministro.tsx`, componente eliminado en la
  feature 6 al ser reemplazado por `AtributoTabs.tsx`. Limpieza cosmética
  pendiente.
- **Mecanismo de mutación con `isError`/lectura de estado en vez de callback
  `onError` explícito** (`useReporteFlujo.ts` y otros hooks de mutación).
  Cumple la intención de la convención (feedback visible, nunca silencioso)
  pero es un mecanismo distinto a la letra exacta de `docs/conventions.md`
  — no bloqueó ninguna revisión, pero vale unificar si se audita el patrón
  de manejo de errores del proyecto.
- **Resolver los gaps `PUT` de Atributo y Usuario** (sección 4, puntos d y
  e) si "modificar" se vuelve un requisito real de negocio — requiere
  trabajo en el backend primero.
- **Conectar el flujo real de reportes** una vez las features backend 7-10
  (sección 4, punto b) estén `done`: reemplazar el `EventSource` mockeado
  por el real, y reconciliar `obtenerReporte` (`src/api/reportes.ts`) contra
  la forma exacta que entregue `GET /api/reportes/{id}` cuando se
  implemente.
- **Limitación de un solo proceso del puente SSE↔Kafka**, documentada en
  `../backend/docs/kafka-reactive-guidance.md`: el puente interno
  Kafka→`Multi` que alimentará `GET /api/reportes/eventos` es válido para un
  solo proceso backend; si el backend escala horizontalmente, ese puente
  necesitará repensarse (ej. server-side fan-out entre instancias) antes de
  que el frontend pueda depender de recibir el evento SSE sin importar qué
  instancia lo atienda. No es una limitación de este frontend, pero
  condiciona cuándo su integración real con SSE será confiable en
  producción.
- **Reconciliar `src/api/auth.ts`/`src/api/menu.ts`/`src/types/auth.ts`**
  contra el contrato real de las features backend 5 y 6, ya `done` (sección
  4, punto a) — no se hizo en ningún momento posterior a la feature 3.

---

## 6. Verificación de precondición

Features 1-9 verificadas `status: "done"` en `feature_list.json` antes de
escribir este documento (lectura directa del archivo, ver también
`progress/current.md`, que registra la misma verificación hecha por el
Leader al iniciar esta feature). Ninguna feature del track frontend quedó en
`pending`, `in_progress` o `blocked` al momento de este reporte.
