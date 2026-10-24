# Metodología: de un plan de análisis a `feature_list.json`

Este documento es para quien (humano o agente Leader) tiene que convertir
`docs/migration-plan.md` (el análisis + plan de migración de un proyecto
concreto) en un `feature_list.json` ejecutable por el loop
Leader/Implementer/Reviewer.

## Principios

1. **Cada feature es un cambio verificable de forma independiente.** Debe
   poder compilar y (cuando aplique) pasar sus tests sin depender de que
   otra feature `pending` ya esté hecha, salvo dependencias explícitas de
   orden (ver más abajo).
2. **Orden = dependencias, no orden del documento fuente.** Un plan de
   migración normalmente se escribe por fases temáticas (bugs, luego stack,
   luego concurrencia, luego tests, luego reporte); ese orden temático
   *suele* coincidir con el orden correcto de ejecución, pero verifícalo:
   - Bugs y config rota que impiden que el proyecto arranque van primero.
   - Cambios de versión de plataforma/framework (el "salto" de stack) van
     antes de reescribir código que usa las APIs nuevas.
   - Features de comportamiento nuevo (concurrencia, integraciones nuevas)
     van después de que el stack ya compile en la versión objetivo.
   - Tests de regresión de un bug van en la MISMA feature que el fix, no
     separados — si no, hay una ventana donde `done` no significa "probado".
   - El reporte de mejoras (`MEJORAS.md` o similar) es siempre la ÚLTIMA
     feature: necesita que todo lo demás ya esté `done` para poder resumirlo
     con precisión.
3. **Acceptance criteria concretos y verificables por un Reviewer sin
   contexto previo.** Nunca "mejorar el manejo de errores" — sí "`Util.decrypt`
   lanza `PgpDecryptionException` en vez de retornar `null`; existe un test
   que verifica que una clave inválida produce esa excepción".
4. **Una feature no debe ser tan grande que un Implementer no pueda
   terminarla y autoverificarla en una sesión.** Si un ítem del plan toca
   más de ~3-4 archivos no triviales o mezcla dos preocupaciones distintas
   (ej. "arreglar el bug X" + "migrar la librería Y"), divídelo en dos
   features.
5. **Nombra las features con snake_case descriptivo** (`fix_x_bug`,
   `migrate_y_to_z`, `add_w_tests`), no `feature_1`, `feature_2` — el nombre
   solo ya debe transmitir el qué.

## Plantilla de una feature

```json
{
  "id": 3,
  "name": "fix_customer_processor_codpais_bug",
  "title": "Corregir mapeo de codPais en CustomerProcessor",
  "description": "CustomerProcessor.process() mapea item.getNumDocumento() al campo codPais en vez de item.getCodPais() (bug de copy-paste). Corregirlo y agregar test de regresión.",
  "acceptance": [
    "CustomerProcessor.java línea ~20 usa item.getCodPais()",
    "Existe un test que falla contra el código viejo (numDocumento != codPais en el fixture) y pasa contra el nuevo",
    "mvn clean compile sin errores"
  ],
  "status": "pending"
}
```

## Categorías típicas de features en una migración

Úsalas como checklist al descomponer el plan — no todos los proyectos
tendrán las seis, pero es raro que falten más de una o dos:

1. **Bugs que impiden confiar en el comportamiento actual** — se arreglan
   primero, sobre el stack viejo si es más rápido verificarlos ahí, o
   directo sobre el nuevo si el bug está en código que de todas formas se
   reescribe en el salto de stack (evita arreglarlo dos veces).
2. **Bugs/config que impiden que el proyecto arranque o se autoejecute
   accidentalmente** (ej. una bandera de arranque mal leída) — van muy
   temprano, porque bloquean poder verificar cualquier otra cosa.
3. **Upgrade de plataforma/framework** — un salto de versión mayor
   (lenguaje, framework) suele ser una sola feature grande de "que compile
   en lo nuevo", seguida de features más chicas por cada API deprecada que
   haya que reescribir.
4. **Concurrencia/paralelismo** — casi siempre depende de que el upgrade de
   plataforma ya esté hecho (para poder usar las primitivas nuevas).
5. **Migraciones de dependencias externas** (SDKs, drivers, clientes) — son
   grandes y arriesgadas; si el plan las marca como "alcance completo vs.
   mínimo", respeta la decisión ya tomada con el usuario en vez de
   reabrirla.
6. **Seguridad** (secretos, gitignore, credenciales) — no elimines ni
   reescribas historial de git sin una decisión explícita del usuario
   documentada en `docs/migration-plan.md`; si no está explícita, la feature
   correspondiente debe quedar en `blocked` con una nota pidiendo esa
   decisión en vez de improvisar.
7. **Tests** — agrégalos junto con cada fix cuando sea barato; resérvalos
   como feature aparte solo para suites más grandes (tests de integración
   end-to-end) que no tiene sentido levantar hasta que el resto ya esté
   estable.
8. **Reporte de mejoras** — última feature, siempre. Su `acceptance` debe
   pedir explícitamente que enumere: bugs corregidos (antes/después),
   versiones de stack (antes/después), cambios de concurrencia y su
   justificación, estado de seguridad (qué se arregló vs. qué queda
   pendiente y por qué), cobertura de tests agregada, y recomendaciones no
   implementadas en este pase.

## Qué NO va en `feature_list.json`

- Decisiones de alcance que ya se conversaron con el usuario (documéntalas
  en `docs/migration-plan.md`, no las repitas como pregunta dentro de una
  feature).
- Trabajo exploratorio/de análisis — eso ya pasó, vive en
  `docs/migration-plan.md`, no es una feature con estado `pending`.
