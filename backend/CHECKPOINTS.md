# CHECKPOINTS — Evaluación del estado final

> En sistemas multi-agente no se evalúa el camino, se evalúa el destino.
> Estos son los checkpoints objetivos que un juez (humano o IA) puede usar
> para decidir si la migración está sana.

## C1 — El arnés está completo

- [ ] Existen los archivos base: `AGENTS.md`, `CLAUDE.md`, `init.sh`,
      `feature_list.json` (no el `.template.json`), `progress/current.md`.
- [ ] Existen los docs: `docs/migration-plan.md` (con contenido real, no
      placeholder), `docs/architecture.md`, `docs/conventions.md`,
      `docs/verification.md`.
- [ ] `./init.sh` termina con exit code 0.

## C2 — El estado es coherente

- [ ] Como mucho una feature en `in_progress` en `feature_list.json`.
- [ ] Toda feature `done` tiene tests asociados que pasan.
- [ ] Ninguna feature `blocked` sin una nota explicando qué decisión falta.
- [ ] `progress/current.md` está vacío o describe la sesión activa
      (no contiene basura de sesiones anteriores).

## C3 — La migración respeta el plan

- [ ] El código respeta `docs/architecture.md` (capas/dependencias
      declaradas para el proyecto objetivo).
- [ ] El código respeta `docs/conventions.md`.
- [ ] No quedan referencias a APIs del stack origen (`JSF 2.2 + PrimeFaces 5.2 + Spring 4 + EclipseLink JPA sobre MySQL 5.6, Java 7/8, Tomcat 7`) que
      el plan marcó para reemplazo, salvo que una feature `pending`
      explícitamente las cubra todavía.
- [ ] No hay `System.out.println()`/`print()` sueltos de debug, ni TODOs
      sin contexto.
- [ ] No se introdujeron secretos nuevos en el código o configuración.

## C4 — La verificación es real

- [ ] Cada bugfix `done` tiene un test de regresión (falla contra el
      comportamiento viejo, pasa contra el nuevo) — verificable leyendo el
      test, no solo confiando en que "dice que sí".
- [ ] Los tests son independientes y no dependen del orden de ejecución.
- [ ] `mvn test` muestra > 0 tests y todos verdes.
- [ ] Ningún test depende de infraestructura real en la nube o credenciales
      de producción.

## C5 — La sesión se cerró bien

- [ ] No hay archivos sin trackear sospechosos fuera de `.gitignore`.
- [ ] `progress/history.md` tiene una entrada por la última sesión.
- [ ] La última feature trabajada está reflejada en su estado correcto.
- [ ] Si todas las features están `done`, existe el reporte de mejoras
      final (última feature del `feature_list.json`).

---

**Cómo usar este archivo:** un agente revisor (`.claude/agents/reviewer.md`)
recorre cada checkbox, marca `[x]` o `[ ]`, y rechaza el cierre de sesión
si quedan boxes vacíos en C1-C5.
