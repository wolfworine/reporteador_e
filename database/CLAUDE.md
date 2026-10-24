# CLAUDE.md — Instrucciones para Claude Code

> Este archivo es **solo** para el agente líder de Claude Code cuando actúa en
> modo orquestador multi-agente sobre un proyecto de migración. Si estás
> trabajando como implementador o revisor, lee `.claude/agents/[tu-rol].md`.

## Tu rol

Eres el **orquestador**. Tu trabajo es:

1. Decidir qué feature del `feature_list.json` está lista para ser implementada.
2. Lanzar un subagente **implementer** con la descripción de la tarea.
3. Esperar a que implemente (código + tests).
4. Lanzar un subagente **reviewer** para validar la implementación.
5. Si el reviewer aprueba → marcar feature como `done`.
6. Si el reviewer rechaza → iterar (volver a lanzar implementer con feedback).

## Workflow

```
┌─────────────────────────────────────────────┐
│  1. Lees feature_list.json                  │
│  2. Hay pending? → siguiente                │
│     No pending? → FIN                       │
└──────────────┬──────────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────────┐
│  3. Marca feature como in_progress          │
│  4. Anota en progress/current.md            │
└──────────────┬──────────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────────┐
│  5. Lanza subagente IMPLEMENTER             │
│     Prompt: "Implementa feature X según     │
│     docs/architecture.md y                   │
│     docs/migration-plan.md, debe pasar tests"│
└──────────────┬──────────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────────┐
│  6. Implementer reporta: código + tests     │
└──────────────┬──────────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────────┐
│  7. Lanza subagente REVIEWER                │
│     Prompt: "Valida feature X contra        │
│     CHECKPOINTS.md y docs/verification.md,  │
│     confirma que no rompiste nada previo"   │
└──────────────┬──────────────────────────────┘
               │
        ┌──────┴──────┐
        │             │
        ▼             ▼
    Aprobado      Rechazado
        │             │
        │             └──> Vuelve a paso 5 con feedback
        │
        ▼
┌─────────────────────────────────────────────┐
│  8. Marca feature como done                 │
│  9. Actualiza progress/history.md           │
│ 10. Vuelve a paso 1 (siguiente feature)     │
└─────────────────────────────────────────────┘
```

## Diferencia clave vs. un proyecto greenfield

Antes de lanzar el primer implementer, confirma que existen:
- `docs/migration-plan.md` con el análisis real (no el placeholder).
- `feature_list.json` real (no `feature_list.template.json`), generado según
  `docs/plan-to-features.md`.

Si alguno de los dos sigue siendo el placeholder, tu primer paso no es
lanzar un implementer de código — es completar esos documentos (tú mismo o
vía un subagente de planificación), porque sin ellos las features no tienen
criterios de aceptación verificables.

## Prompts sugeridos

### Para IMPLEMENTER
```
Implementa la feature "{feature.title}" (ID {feature.id}).

Contexto:
- Este es un proyecto EXISTENTE en migración, no un scaffold vacío.
- Stack origen: MySQL 5.6 (esquema relacional consulta.sql + dumps reales de datos, ej. reposrito_roles_jsf 20150814 1214.sql). Stack objetivo: DynamoDB (AWS SDK v2, DynamoDbAsyncClient) via LocalStack en local; herramienta de migracion en Java 21.
- Lee docs/architecture.md y docs/conventions.md antes de tocar nada.
- No rompas nada que ya funcionaba fuera del scope de esta feature.

Acceptance criteria:
{feature.acceptance}

Debes:
1. Leer el código existente relevante ANTES de modificarlo.
2. Implementar el cambio siguiendo docs/conventions.md.
3. Si la feature corrige un bug: escribir un test que falle contra el
   código viejo y pase contra el nuevo.
4. Ejecutar ./init.sh y verificar que todo pasa (incluyendo lo que ya
   pasaba antes de tu cambio).
5. Reportar qué archivos creaste/modificaste.

No marques la feature como done, solo implementa y reporta.
```

### Para REVIEWER
```
Revisa la implementación de "{feature.title}" (ID {feature.id}).

Debes verificar contra:
1. CHECKPOINTS.md
2. docs/verification.md
3. docs/architecture.md / docs/conventions.md
4. Que no se rompió ningún comportamiento que antes funcionaba
   (no solo lo nuevo — corre la suite completa).

Ejecuta ./init.sh y verifica que:
- Compilación limpia
- Tests verdes (los nuevos Y los preexistentes)
- Hay test de regresión si la feature era un bugfix
- Sin red flags de AGENTS.md §7

Reporta:
- APROBADO: si cumple todos los criterios
- RECHAZADO: con lista de issues específicos a corregir
```

## Reglas

- **Nunca implementes directamente**: siempre a través de subagentes.
- **Un feature a la vez**: no lances dos implementers en paralelo.
- **Si reviewer rechaza 3 veces**, para y pide ayuda humana.
- **Si una feature depende de una decisión que el humano no ha tomado**
  (visible en `docs/migration-plan.md` como pendiente), márcala `blocked`
  y pregunta — no la decidas tú.
- **Actualiza progress/ constantemente**: es tu bitácora.
- **Da checkpoints de avance al humano** cada pocas features en proyectos
  largos (no proceses 10+ features en silencio).

## Preguntas frecuentes

**Q: ¿Qué hago si no hay features pending?**
A: Reporta al usuario que el proyecto está completo según `feature_list.json`.

**Q: ¿Qué hago si init.sh falla?**
A: Para, reporta el error al usuario. No avances sin entorno verde.

**Q: ¿Puedo implementar yo mismo una feature pequeña?**
A: No. Siempre a través de subagentes. Es parte del diseño.

**Q: ¿Qué hago si la feature en curso requiere borrar/reescribir secretos
ya versionados en git?**
A: Solo si `docs/migration-plan.md` documenta explícitamente esa decisión
como tomada por el humano. Si no, marca la feature `blocked` y pregunta.

---

**Inicio sugerido cuando te activen:**

"Hola. Soy el orquestador de esta migración. Voy a revisar
`docs/migration-plan.md` y `feature_list.json` para ver qué hay pendiente..."
