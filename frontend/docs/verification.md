# Verificación — entel-role-repository-frontend

## Checklist antes de marcar una feature como `done`

### 1. Compilación limpia
```bash
npm run build
```
- Sin errores de TypeScript ni de build de Vite.

### 2. Tests
```bash
npm test
```
- Todos los tests en verde (nuevos y preexistentes).
- Cero llamadas HTTP reales — `api/*.ts` mockeado en tests de componentes.

### 3. No regresión de comportamiento
- Compara contra `docs/migration-plan.md` §3: ¿la feature preserva el
  alcance funcional de la vista legacy correspondiente (mismos
  campos/filtros/acciones)?
- Smoke test manual (una vez exista backend real o mock server):
```bash
npm run dev
# navegar el flujo afectado por la feature
```

### 4. Seguridad
- Confirmar que no se guardan contraseñas ni tokens sensibles en
  `localStorage` sin justificación documentada en `docs/architecture.md`.
- Confirmar que `VITE_API_BASE_URL` (o equivalente) no tiene un valor de
  producción hardcodeado y commiteado.

### 5. Estado del arnés
- `feature_list.json`: la feature en curso y solo ella está `in_progress`.
- `progress/current.md` refleja la sesión activa.

## Red flags 🚩

- 🚩 `npm run build` con errores de tipo suprimidos vía `any`/`@ts-ignore`
  sin justificación.
- 🚩 Tests que mockean de más (verifican implementación, no comportamiento).
- 🚩 Fetch directo fuera de `api/*.ts`.
- 🚩 Cambios fuera del alcance de la feature en curso (scope creep).
- 🚩 `feature_list.json` con más de una feature `in_progress`.

## Criterios de aceptación de una feature

Una feature está **DONE** cuando:

1. Compila sin errores (`npm run build`).
2. Sus tests pasan (`npm test`).
3. No rompe ninguna feature previamente `done`.
4. Es coherente con `docs/architecture.md`/`docs/conventions.md`.
5. `docs/migration-plan.md`/`docs/architecture.md` se actualizan si la
   feature cambió algo que esos documentos describían.
6. Revisada y aprobada por el agente `reviewer`.
