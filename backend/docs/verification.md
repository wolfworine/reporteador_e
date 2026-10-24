# Verificación — entel-role-repository-backend

## Checklist antes de marcar una feature como `done`

### 1. Compilación limpia
```bash
mvn clean compile
```
- Sin errores de compilación.
- Sin quedar referencias a APIs de JSF/Spring/EclipseLink (stack origen)
  que la feature debía eliminar.

### 2. Tests
```bash
mvn test
```
- Todos los tests en verde (incluye los preexistentes, no solo los nuevos).
- El test de regresión de la feature (si corrige un bug) está presente y
  fallaría si se revierte el fix.
- Cero llamadas a AWS/Kafka reales en tests — solo LocalStack (DynamoDB,
  Secrets Manager, KMS) y Kafka embebido (Quarkus Dev Services).
- Ningún test usa `.await().indefinitely()` de forma que enmascare un
  bloqueo real del pipeline reactivo — si un test necesita esperar un
  `Uni`, usar `UniAssertSubscriber` con timeout explícito.

### 3. No regresión de comportamiento
- Compara contra `docs/migration-plan.md` §3 (bugs) y §5 (fases): ¿esta
  feature tenía que preservar algún comportamiento existente (ej. la forma
  del reporte Cargo→Rol→4 categorías)? Confírmalo.
- Smoke test funcional end-to-end (una vez exista el entorno completo):
```bash
docker-compose up -d localstack kafka
mvn quarkus:dev
curl http://localhost:8080/q/health
```

### 4. Seguridad
- `grep -ri "AKIA\|secret\|password.*=.*['\"]" src/` rápido para confirmar
  que la feature no introdujo una credencial hardcodeada nueva.
- Confirmar que ningún endpoint nuevo expone contraseñas o hashes en las
  respuestas JSON.
- Si la feature toca autenticación: confirmar que no se usó `Cripto.java`
  ni ningún cifrado reversible.

### 5. Estado del arnés
- `feature_list.json`: la feature en curso y solo ella está `in_progress`.
- `progress/current.md` refleja la sesión activa.

## Red flags 🚩

- 🚩 `mvn clean compile` con warnings de APIs `javax.faces`/`javax.persistence`
  que la migración debía eliminar.
- 🚩 Tests que dependen de credenciales AWS reales o de que Kafka/DynamoDB
  reales estén disponibles.
- 🚩 Un fix de bug sin test de regresión.
- 🚩 `.await()` bloqueante dentro de un endpoint reactivo.
- 🚩 Trabajo de iText/POI ejecutado sin `.emitOn()` (bloquea el event loop).
- 🚩 Cambios fuera del alcance de la feature en curso (scope creep).
- 🚩 `feature_list.json` con más de una feature `in_progress`.

## Criterios de aceptación de una feature

Una feature está **DONE** cuando:

1. Compila sin errores en el stack objetivo (Quarkus 3.x / Java 21).
2. Sus tests (y los de regresión si corrige un bug) pasan contra LocalStack.
3. No rompe ninguna feature previamente `done` (`mvn test` completo, no
   solo los tests nuevos).
4. Es coherente con el modelo reactivo (Mutiny) y las reglas de
   `docs/architecture.md`/`docs/conventions.md`.
5. `docs/migration-plan.md`/`docs/architecture.md` se actualizan si la
   feature cambió algo que esos documentos describían (ej. el diseño de
   tablas DynamoDB se valida y refina en la feature correspondiente).
6. Revisada y aprobada por el agente `reviewer`.
