# Convenciones — entel-role-repository-frontend

## Estilo de código

- TypeScript estricto (`strict: true` en `tsconfig.json`), sin `any`
  salvo en los límites de librerías de terceros sin tipos, documentado con
  comentario.
- Componentes funcionales + hooks, nombre de archivo = nombre de
  componente (`AreaList.tsx` exporta `AreaList`).
- Manejo de errores: toda mutación (`useMutation`) maneja `onError`
  mostrando feedback al usuario (toast/inline) — nunca un error silencioso
  en consola solamente (ese fue justamente un problema del backend legacy
  que no debe repetirse en el frontend nuevo).
- Sin lógica de negocio en componentes de presentación — vive en hooks
  propios (`useReporteGenerator`, etc.) o en `api/`.

## Tests

- **Unit/component:** Vitest + React Testing Library.
- **Nomenclatura:** `describe('<Componente>')` + `it('should <resultado>
  when <condición>')`.
- Mockear `api/*.ts` en tests de componentes — cero llamadas HTTP reales
  contra el backend en la suite de tests.
- Todo flujo crítico (login, un CRUD representativo, disparo de reporte)
  tiene al menos un test de integración de componente.

## Dependencias

- React 19 (última estable al momento de bootstrap — la feature 1 fija la
  versión exacta usada y la documenta aquí).
- TanStack Query para estado de servidor, TanStack Table para las tablas
  anidadas de Rol/Suministro/Atributo y de Reportes.
- React Router para navegación.
- Sin Redux, sin Angular ni ninguna dependencia sobrante de la propuesta
  Angular descartada (`06_PLAN_MIGRACION_REACT_JAVA21.md` es la referencia
  vigente, no los documentos `00`-`04` originales).

## Qué NO hacer

- No introducir server-side rendering (Next.js) salvo que el usuario lo
  pida explícitamente — el alcance decidido es SPA.
- No hardcodear la URL del backend — usar variable de entorno
  (`VITE_API_BASE_URL`), nunca commiteada con un valor de producción real.
- No reescribir el diseño visual desde cero sin necesidad — priorizar
  paridad funcional con las vistas legacy sobre un rediseño visual no
  solicitado.
