# AGENTS.md — Mapa de navegación para agentes de IA

> Punto de entrada para cualquier agente que trabaje en este repositorio en
> migración. NO es una biblia de reglas: es un **mapa**. Lee solo lo que
> necesites cuando lo necesites (divulgación progresiva).

---

## 1. Antes de empezar (obligatorio)

1. Ejecuta `./init.sh` y verifica que termina sin errores. Si falla, **para**
   y resuelve el entorno antes de tocar código.
2. Lee `progress/current.md` para entender en qué estado quedó la última sesión.
3. Lee `docs/migration-plan.md` para entender qué proyecto es y hacia dónde va.
4. Lee `feature_list.json` y elige **una** tarea con estado `pending`. No
   trabajes en más de una a la vez.

## 2. Mapa del repositorio

| Archivo / carpeta            | Qué contiene                                              | Cuándo leerlo |
|------------------------------|-------------------------------------------------------------|---------------|
| `docs/migration-plan.md`     | Análisis completo: stack actual/objetivo, bugs, decisiones  | Siempre, al empezar |
| `feature_list.json`          | Lista de tareas con estado (pending / in_progress / done / blocked) | Siempre, al empezar |
| `progress/current.md`        | Estado de la sesión actual                                   | Siempre, al empezar |
| `progress/history.md`        | Bitácora append-only de sesiones anteriores                  | Si necesitas contexto histórico |
| `docs/architecture.md`       | Arquitectura actual vs. objetivo                              | Antes de implementar |
| `docs/conventions.md`        | Reglas de estilo, tests, dependencias del stack objetivo      | Antes de escribir código |
| `docs/verification.md`       | Cómo verificar que tu trabajo funciona                        | Antes de declarar una tarea como `done` |
| `docs/plan-to-features.md`   | Metodología para convertir el plan en features (solo si te toca generar/ajustar `feature_list.json`) | Al planificar, no al implementar |
| `CHECKPOINTS.md`             | Criterios objetivos de "estado final correcto"                | Para auto-evaluarte |
| `.claude/agents/`            | Definiciones de subagentes (líder, implementador, revisor)    | Si orquestas trabajo |

## 3. Reglas duras (no negociables)

- **Una sola feature a la vez.** No mezcles cambios de varias tareas en la misma sesión.
- **No declares una tarea `done` sin pruebas verdes.** Ejecuta `./init.sh` y
  asegúrate de que el bloque de tests pasa al 100%.
- **No rompas lo que ya funcionaba.** A diferencia de un scaffold vacío, aquí
  siempre hay una versión previa que compilaba/pasaba tests — si tu cambio
  hace fallar algo que antes pasaba, es un bloqueo, no un detalle menor.
- **Todo fix de bug lleva su test de regresión** en la misma feature (debe
  fallar contra el código viejo, pasar contra el nuevo).
- **Nada de infraestructura real en tests** (AWS/DB en la nube reales,
  credenciales de producción) — usa emuladores locales o mocks.
- **No borres ni reescribas secretos/historial de git ya versionados** salvo
  que sea explícitamente el objeto de la feature en curso Y esté respaldado
  por una decisión explícita en `docs/migration-plan.md`.
- **Documenta lo que haces** en `progress/current.md` mientras trabajas, no al final.
- **Deja el repositorio limpio** antes de cerrar la sesión.
- **Si no sabes algo, busca en `docs/`** antes de inventarlo.

## 4. Cómo elegir una tarea

```
1. Abre feature_list.json
2. Filtra por status == "pending"
3. Coge la de menor "id"
4. Cambia su status a "in_progress" y guarda
5. Anota en progress/current.md: feature, hora de inicio, plan breve
```

## 5. Cierre de sesión (lifecycle)

Antes de terminar:

1. Ejecuta `./init.sh` — todo verde.
2. Si la tarea está acabada: marca `status: "done"` en `feature_list.json`.
3. Mueve el resumen de `progress/current.md` al final de `progress/history.md`.
4. Vacía `progress/current.md` dejando solo la plantilla.
5. No dejes archivos temporales ni TODOs sin contexto.

## 6. Si te bloqueas

- Relee la sección relevante de `docs/`.
- Si la herramienta no hace lo que esperas, **no inventes un workaround**:
  documenta el bloqueo en `progress/current.md`, marca la feature `blocked`
  con la razón concreta, y para la sesión.
- Si el bloqueo es una decisión que solo el humano puede tomar (alcance,
  qué hacer con secretos existentes, etc.), dilo explícitamente — no lo
  decidas por tu cuenta.

## 7. Red flags específicas de migración

- 🚩 Reescribir código que la feature actual no pedía tocar.
- 🚩 Un fix de bug sin test de regresión.
- 🚩 Tests que requieren credenciales o servicios en la nube reales.
- 🚩 Eliminar o reescribir historial de git sin decisión explícita del humano.
- 🚩 Marcar `done` con `mvn clean compile` o `mvn test` en rojo.
- 🚩 Más de una feature `in_progress` a la vez.
