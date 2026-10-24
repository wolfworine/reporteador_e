# Review — feature 10 (`reporte_final_mejoras`)

**Veredicto:** APPROVED

Última feature del track frontend. `MEJORAS.md` es un documento fiel a
`progress/history.md` y al estado real del código/repositorio — no se
encontraron invenciones, exageraciones ni omisiones significativas tras
cruzar cada sección contra su fuente primaria.

---

## Acceptance criteria (feature 10)

### 1. Módulos migrados con paridad funcional confirmada — MET

La tabla de la sección 1 de `MEJORAS.md` (features 1-9 → módulo React →
vista(s) legacy → paridad) corresponde punto por punto a lo narrado en cada
entrada de `history.md`. Spot-checks de código:

- `src/modules/atributo/AtributoTabs.tsx` existe (verificado con `ls`),
  junto a `useAtributoTabs.ts` y `AtributoTabs.test.tsx`, tal como cita la
  fila de la feature 6.
- `src/modules/atributo/AtributoPorSuministro.tsx` (feature 5) ya no existe
  — consistente con "reemplazado en feature 6" anotado entre paréntesis en
  la fila de la feature 5.
- La feature 9 se describe correctamente como "sin UI nueva, cierra el
  único gap real de cobertura" — coincide con `history.md` (auditoría del
  Leader: 2 de 3 criterios ya cubiertos, solo el CRUD editar era gap real).

### 2. Decisiones de UX que difieren del legacy, y por qué — MET

Las 8 decisiones listadas (edición inline, filtro client-side, password
enmascarada, etiquetas de texto en exportación, `role="alert"` consistente,
`codSubmenu` explícito, SSE sin polling, máquina de estados del flujo
async) están todas respaldadas en `history.md`, con la justificación
correcta en cada caso (p. ej. el hallazgo de que el cross-join del legacy
`MenuController.establecerPermisos()` es un bug, no un diseño intencional).

Spot-check de código: `type="password"` aparece efectivamente en
`src/modules/usuario/UsuarioMantenimiento.tsx` (línea 196, más un comentario
JSDoc en línea 61 que lo documenta) — confirma la mejora de seguridad
citada sobre el legacy (`p:inputText` sin máscara).

### 3. Cobertura de tests agregada por feature — MET

Verifiqué la aritmética completa de la tabla de la sección 3 fila por fila
contra los números citados textualmente en cada una de las 9 entradas de
`history.md`:

| Feature | Prev. total (history.md) | Agregados (tabla) | Total esperado | Total en tabla |
|---|---|---|---|---|
| 1 | 0 | 1 | 1 | 1 ✓ |
| 2 | 1 | 5 | 6 | 6 ✓ |
| 3 | 6 | 9 | 15 | 15 ✓ |
| 4 | 15 | 8 (6+2 corrección) | 23 | 23 ✓ |
| 5 | 23 | 9 | 32 | 32 ✓ |
| 6 | 32 | neto +5 (6 nuevos −1 eliminado) | 37 | 37 ✓ |
| 7 | 37 | 6 | 43 | 43 ✓ |
| 8 | 43 | 17 | 60 | 60 ✓ |
| 9 | 60 | 1 | 61 | 61 ✓ |

El caso F6 es el más delicado (test neto, no bruto) y `MEJORAS.md` lo marca
explícitamente como "neto +5 sobre 32", que es exactamente la aritmética
que el Reviewer de la feature 6 verificó en su momento (`32 − 1 + 6 = 37`,
citado en `history.md`). Sin errores de arrastre en ninguna fila.

Verificación independiente ejecutando `bash init.sh` desde
`D:\Proyecto textil\repositorio_entel_modernizado\frontend`: **15 archivos
de test, 61 tests, todos en verde** (`npm run build` + `npm test`
exitosos) — coincide exactamente con el total final que cita `MEJORAS.md`
("61 tests en 15 archivos").

### 4. Dependencias frontend↔backend pendientes de sincronizar — MET, con una imprecisión menor no bloqueante

Ver sección dedicada de cross-track accuracy abajo — el contenido sustantivo
es correcto y está genuinamente re-verificado contra el backend actual, no
copiado sin más de `history.md`. Se encontró una imprecisión de redacción
(no de hechos operativos) en el punto (b): ver detalle abajo.

### 5. Recomendaciones no implementadas en este pase — MET

Las 8 recomendaciones listadas están todas trazables a "concerns no
bloqueantes" documentados en `history.md` (features 6, 8, 9) o a gaps de la
sección 4. Spot-checks:

- `clearMocks` efectivamente **no** está configurado en `vite.config.ts`
  (`grep` sin resultados) — confirma la recomendación tal como está descrita.
- Los comentarios stale mencionando `AtributoPorSuministro.tsx` sí existen
  en `src/api/atributos.ts` (línea 49) y `src/types/atributo.ts` (línea 19)
  — el componente fue eliminado en la feature 6, los comentarios quedaron.

Ninguna recomendación de esta sección se siente como relleno genérico; cada
una cita un archivo concreto y una razón concreta.

### 6. Features 1-9 en `status: "done"` antes de escribir el reporte — MET

Leí `feature_list.json` directamente: los 9 features (ids 1-9) tienen
`"status": "done"`. La feature 10 está en `"status": "in_progress"`
(correcto — se cierra recién cuando este review se apruebe). `MEJORAS.md`
sección 6 y la nota de precondición al inicio del documento reflejan esto
con precisión.

---

## Cross-track accuracy (sección 4 de MEJORAS.md) — verificación dedicada

Leí `../backend/feature_list.json` directamente (no me apoyé en lo que
`MEJORAS.md` afirma) para las 3 verificaciones pedidas:

**(a) Backend features 5 y 6 — ¿realmente `done` ahora?**
Confirmado: `id=5` (`usuario_login_password_hash_fix`) → `"status": "done"`,
`id=6` (`menu_dinamico_por_tipo_usuario`) → `"status": "done"`. `MEJORAS.md`
lo afirma correctamente como cambio respecto al momento en que se escribió
la feature 3 del frontend (cuando ambas estaban `pending`). También es
correcto que el frontend **no ha sido revisitado** desde entonces —
`src/api/auth.ts`/`src/api/menu.ts` no aparecen en el listado de archivos
tocados desde la feature 3 en ningún `history.md` posterior.

**(b) ¿La feature única de reportes-Kafka se dividió en 4 features (7-10), todas no-`done`?**
Los **estados** citados son exactos: `id=7`
(`reportes_dominio_y_query_sin_duplicados`) → `"in_progress"`; `id=8`
(`reportes_adaptadores_pdf_excel`) → `"pending"`; `id=9`
(`reportes_generacion_async_kafka`) → `"pending"`; `id=10`
(`reportes_notificacion_sse_y_descarga`) → `"pending"`. Ninguna es `done` —
la conclusión de `MEJORAS.md` ("Ninguna de las cuatro está `done`") es
correcta.

Sin embargo, la caracterización de *cómo* se llegó a 4 features es
imprecisa. La propia descripción de nivel de proyecto en
`../backend/feature_list.json` dice textualmente: *"la feature original
'reportes_generacion_async_kafka' se dividió **en tres** (7, 8, 9 —
ensamblado de datos sin duplicados / adaptadores PDF-Excel / orquestación
Kafka) [...]; features 8-11 originales se renumeraron a 10-13."* Es decir:
la feature única se dividió en **tres** (7, 8, 9); la feature 10
(`reportes_notificacion_sse_y_descarga`) **ya existía como feature separada
antes de la división** (era la vieja id=8) y solo fue renumerada, no creada
por el split. `MEJORAS.md` dice "el backend original dividió su antigua
feature única `reportes_generacion_async_kafka` en cuatro" — eso no es lo
que dice la fuente backend. Es una imprecisión de redacción, no un error
sobre los hechos operativos que importan (ids, estados, y que las 4
features cubren colectivamente el trabajo de reportes pendiente de
conectar) — no cambia ninguna conclusión práctica de la sección 4, pero es
la clase de detalle que "cuatro" vs. "tres+una preexistente" un lector
podría querer que fuera exacto. **No bloqueante.**

**(c) ¿La feature 10 del backend contiene el compromiso de `GET /api/reportes/{id}` citado?**
Confirmado con cita textual. El acceptance criterion 3 de `id=10` dice
literalmente: *"GET /api/reportes/{id} devuelve la estructura Reporte[]
(árbol Cargo→Rol→4 categorías de suministro) en JSON cuando el reporte está
LISTO, y 404 si el id no existe o aún no está listo — misma forma que
espera ../frontend/src/api/reportes.ts (coordinado con el Leader de
../frontend/ el 16-ago-2026)"*. `MEJORAS.md` cita esto casi verbatim y con
precisión — incluida la fecha de coordinación y que la feature sigue
`pending`. **Correcto.**

**Conclusión cross-track:** la sección 4 de `MEJORAS.md` está genuinamente
re-verificada contra el estado actual del backend (no es una copia estática
de lo que decía `history.md` en el momento de cada feature) — el propio
documento señala explícitamente dónde el estado cambió desde entonces (caso
a). El único defecto encontrado es la imprecisión "tres" vs. "cuatro" en la
narrativa del punto (b), que no afecta ninguna conclusión operativa.

---

## Otras observaciones (no bloqueantes)

- Verifiqué independientemente que `UsuarioResource.java` no tiene `@PUT`
  (solo `@GET`×2, `@POST`, `@DELETE`) y que `AtributoResource.java` tampoco
  tiene `@PUT` (`@POST`, `@GET`×2) — confirma los gaps (d) y (e) de la
  sección 4 tal como los describe `MEJORAS.md`.
- Todo lo que `history.md` documenta como concern no bloqueante en las
  features 6, 8 y 9 aparece reflejado en la sección 5 de `MEJORAS.md`; no
  encontré ningún gap u observación relevante de `history.md` que
  `MEJORAS.md` haya omitido.
- Scope check (sin `.git`, por `LastWriteTime`/mtime): los únicos archivos
  con timestamp del 18-ago-2026 (sesión de esta feature) son
  `MEJORAS.md` (nuevo) y `progress/current.md` (actualizado), más el
  esperado status-flip bookkeeping en `feature_list.json`.
  `progress/history.md` y `progress/review.md` tienen timestamps
  agrupados justo antes (00:44-00:45, mismo clúster que el flip de
  `feature_list.json`), consistentes con el cierre/bookkeeping de la
  feature 9 al inicio de esta sesión, no con trabajo nuevo de la feature
  10. `src/modules/area/AreaMantenimiento.test.tsx` tiene timestamp del
  17-ago (feature 9), no tocado en esta sesión. No se tocó ningún otro
  archivo de código fuente.
- `bash init.sh` ejecutado de forma independiente: verde, 15/15 archivos de
  test, 61/61 tests — sin regresión, tal como se esperaba de un cambio
  puramente documental.

## No-regresión

¿Algo que antes funcionaba ahora falla? **NO.** `init.sh` verde con los
mismos 15 archivos / 61 tests que al cierre de la feature 9.

## Cambios requeridos

Ninguno bloqueante. Sugerencia opcional (no bloquea el `APPROVED`): en la
sección 4(b) de `MEJORAS.md`, corregir "se dividió [...] en cuatro" por
algo como "se dividió en tres (7, 8, 9); la feature 10 ya existía como
feature separada y fue renumerada en el mismo cambio" — para alinear
exactamente con la propia descripción de `../backend/feature_list.json`.
