# Verificación — entel-role-repository-database-migration

## Checklist antes de marcar una feature como `done`

### 1. Compilación limpia
```bash
mvn clean compile
```

### 2. Tests
```bash
mvn test
```
- Todos los tests en verde.
- Tests de carga contra LocalStack, nunca contra AWS real.
- Existe un test que corre el import dos veces y verifica que no duplica
  ítems (regla dura de idempotencia).

### 3. No regresión de comportamiento
- El conteo de filas migradas coincide con el conteo real del dump fuente
  (`docs/migration-plan.md` §1 tiene las cifras de referencia del dump
  `reposrito_roles_jsf 20150814 1214.sql`).
- Cada access pattern de `docs/architecture.md` tiene al menos un test que
  lo ejercita contra datos migrados reales (no solo datos sintéticos).

### 4. Seguridad
- Sin credenciales AWS reales en el repo — solo las dummy de LocalStack.
- El flag `--target=aws` (si existe) nunca se ejecuta en CI/tests
  automatizados.

### 5. Estado del arnés
- `feature_list.json`: la feature en curso y solo ella está `in_progress`.
- `progress/current.md` refleja la sesión activa.

## Red flags 🚩

- 🚩 Migración no idempotente (duplica al correr dos veces).
- 🚩 Test que se conecta a una cuenta AWS real.
- 🚩 Migrar la tabla `sequence` literalmente sin pasar por la decisión de
  IDs documentada en `docs/architecture.md`.
- 🚩 Conteos de filas migradas que no coinciden con el dump fuente sin una
  explicación documentada (ej. filas descartadas deliberadamente).

## Criterios de aceptación de una feature

1. Compila y testea en verde contra LocalStack.
2. No rompe features previas (`mvn test` completo).
3. Coherente con el diseño canónico de `docs/architecture.md`.
4. Revisada y aprobada por el agente `reviewer`.
