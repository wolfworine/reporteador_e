# Reglas para dispatch de agentes opencode en este proyecto

Este proyecto usa dos poblaciones de agentes en paralelo: subagentes Claude
(vía el harness Leader→Implementer→Reviewer de cada track) y, desde
2026-08-15, agentes `opencode` (CLI de terceros, modelos gratuitos
`opencode/nemotron-3-ultra-free` y `opencode/deepseek-v4-flash-free`, sin
credenciales propias) como capacidad adicional cuando Claude choca con
límites de sesión. Este archivo son las reglas aprendidas y establecidas
para que ambas poblaciones cooperen sin pisarse.

## Reglas para MÍ (orquestador) antes de lanzar un opencode

1. **Verificar el estado real en disco ANTES de dispatchar.** Leer
   `<track>/feature_list.json` (status de la feature objetivo) y los
   timestamps de `<track>/progress/{current,history,review}.md`. Si el
   estado cambió desde la última vez que lo revisé (p.ej. un Leader Claude
   avanzó por su cuenta mientras yo hacía otra cosa), el prompt que iba a
   usar puede estar apuntando a un objetivo obsoleto — re-derivar el
   prompt del estado actual, no reusar uno viejo. (Lección real: un
   review opencode de backend feature 4 llegó tarde, la feature ya estaba
   `done` y el Leader ya iba por la feature 5 — el review revisó un
   objetivo obsoleto y sobreescribió un `review.md` real con uno genérico.)
2. **No lanzar un opencode Reviewer/Implementer sobre un track con un
   Leader Claude activo en la misma feature al mismo tiempo**, salvo que
   sea deliberado (ej. pedirle una segunda opinión). Preferir: dejar que
   el Leader Claude complete su ciclo si ya está en marcha; usar opencode
   para trabajo genuinamente bloqueado/huérfano.
3. **Siempre indicarle al agente opencode que lea primero el protocolo
   real del rol** (`.claude/agents/{leader,implementer,reviewer}.md` del
   track correspondiente) — no improvisar el protocolo en el prompt; el
   protocolo ya documentado es la fuente de verdad y ya fue afinado con
   casos reales de este proyecto.
4. **Verificar el resultado en disco después**, exactamente igual que con
   los subagentes Claude — nunca marcar nada como confiable solo porque
   el proceso terminó con exit 0 o porque el resumen en chat dice
   "APPROVED"/"listo". Leer el archivo real (`progress/review.md`,
   `progress/current.md`) y, si el rol era Reviewer, confirmar que
   realmente ejecutó `bash init.sh`/tests reales (buscar la salida real
   de build/tests en el log del proceso, no solo la afirmación).
5. **Un solo proceso opencode `--auto` en background a la vez por
   track**, para evitar colisiones de escritura concurrente sobre los
   mismos archivos (`progress/review.md`, `progress/current.md`).

## Reglas que le doy a CADA agente opencode en el prompt (siempre incluir)

1. Actuar exactamente según el protocolo del rol definido en
   `.claude/agents/<rol>.md` de este track — leerlo primero, palabra por
   palabra, no resumir de memoria.
2. Antes de emitir cualquier veredicto o marcar cualquier trabajo como
   completo: **ejecutar `bash init.sh` (o el build/test real del track) y
   citar la salida real** — nunca afirmar "los tests pasan" sin haberlos
   corrido en esta misma invocación.
3. Si el rol es Reviewer: **nunca editar código de aplicación**, solo
   escribir el veredicto en `progress/review.md` con el formato exacto que
   pide `reviewer.md`. Ser específico — citar archivos y líneas, no dar
   feedback genérico. Para cualquier bugfix, verificar explícitamente que
   el test de regresión asociado **fallaría contra el código viejo** (no
   solo que existe un test) — este fue precisamente el hallazgo real y
   correcto que un reviewer opencode encontró en frontend feature 4:
   un test que no ejercitaba la ruta del bug, aunque "pasaba".
4. Si el rol es Implementer: **no tocar `feature_list.json`** — el cambio
   de status es responsabilidad exclusiva del Leader (Claude o humano),
   nunca del implementer ni del reviewer.
5. **No tocar ningún archivo fuera del scope declarado de la feature**, ni
   ningún archivo de otro track (`../backend`, `../frontend`,
   `../database` según corresponda) salvo lectura.
6. **Nunca usar credenciales AWS reales ni `--target=aws`** en ninguna
   herramienta de este proyecto — el único destino válido es LocalStack.
7. Para trabajo en el backend que toque Kafka (features 7/8): leer
   también `docs/kafka-reactive-guidance.md` antes de implementar o
   revisar — no está cubierto en `docs/conventions.md`.
8. Escribir hallazgos reales en `progress/current.md` o `progress/review.md`
   según el rol — la respuesta en chat es solo una línea de puntero,
   igual que ya exige el protocolo Claude existente.

## Métricas/hechos verificados (referencia rápida, actualizar en cada checkpoint)

Ver `handoff.md` de cada track para el detalle completo y actualizado.
Resumen al 2026-08-15 ~22:35 (ya puede estar desactualizado — **verificar
en disco, no confiar en esta tabla ciegamente**):

| Track | Features done | Feature activa | Tests verdes (última corrida real) |
|---|---|---|---|
| backend | 1-5 | 6 (`menu_dinamico_por_tipo_usuario`) | 54/54 (feature 5) |
| frontend | 1-3 | 4 (`crud_catalogos_simples`, CHANGES_REQUESTED, fix pendiente) | 21/21 |
| database | 1-3 | 4 (`migrate_data_mysql_to_dynamodb`, sin tests aún) | 38/38 (sin cobertura de `migrate`) |

## Fiabilidad observada de los modelos opencode (muestra pequeña, seguir registrando)

- `opencode/nemotron-3-ultra-free`: 1 review real hecha — llegó tarde
  sobre un objetivo ya obsoleto, no se puede evaluar calidad de juicio
  todavía con este único dato.
- `opencode/deepseek-v4-flash-free`: 1 review real hecha — rigurosa,
  específica, encontró un gap real y no evidente (test que no ejercitaba
  la ruta del bugfix). Buena señal inicial, sigue siendo una muestra de 1.
- Ambos modelos demostraron uso real de herramientas de archivo
  (Read/Write) en pruebas de humo antes de usarse en trabajo real.
- Ninguno de los dos requiere credenciales propias — usan el gateway
  gratuito de opencode/zen.
