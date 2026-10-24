# Convenciones — entel-role-repository-database-migration

## Estilo de código

- Java 21, Maven. Proyecto CLI simple (no framework web) — `main()` con
  subcomandos (`design-validate`, `parse-dump`, `migrate`, `verify`).
- Cliente DynamoDB: `DynamoDbAsyncClient` (AWS SDK v2) — coherente con
  `../backend/docs/conventions.md`, aunque aquí una herramienta CLI puede
  usar `.join()`/bloqueo controlado sobre los `CompletableFuture` sin
  necesidad de Mutiny (no es un servidor reactivo, es un batch job).
- Parseo de los `.sql` legacy: no hace falta un parser SQL completo — son
  dumps `mysqldump` estándar (`INSERT INTO \`tabla\` VALUES (...)`);
  extraer con un parser simple línea por línea o una librería ligera, sin
  levantar un MySQL real salvo que sea más simple para un caso puntual
  (permitido vía Testcontainers MySQL solo en tests, nunca en producción
  de la herramienta).

## Tests

- Unit: parseo de dump → estructuras Java, sin DynamoDB.
- Integración: carga contra LocalStack, verificando conteos e integridad
  referencial (ej. todo `Atributo.idRol` tiene un `Rol` correspondiente ya
  cargado).
- Regla dura: la migración debe ser **re-ejecutable sin duplicar** — todo
  test de carga corre el import dos veces y verifica que el conteo final
  es igual al de una sola corrida.
- Prohibido apuntar cualquier test a una cuenta AWS real.

## Qué NO hacer

- No migrar la tabla `sequence` literalmente a DynamoDB — ver decisión de
  IDs en `docs/architecture.md`.
- No inventar datos de prueba nuevos si ya existen dumps reales
  (`repositorio_entel/*.sql`) — usarlos como fuente para validar la
  migración salvo que el usuario pida explícitamente datos sintéticos.
