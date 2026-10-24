# MEJORAS — Migración backend WiseVision Role Repository Basic → Quarkus reactivo

Reporte final de la migración del backend (13/13 features `done`), cliente
Entel. Sistema legacy: `D:\Proyecto textil\repositorio_entel` (JSF 2.2 +
PrimeFaces 5.2 + Spring 4 + EclipseLink JPA sobre MySQL 5.6). Sistema
nuevo: `entel-role-repository-backend` (Quarkus 3.38.1 + Java 21,
reactivo de punta a punta con SmallRye Mutiny). Fuente de verdad del plan:
`docs/migration-plan.md`. Detalle sesión por sesión de cada feature:
`progress/history.md`.

## 1. Bugs corregidos (antes → después)

Todos verificados contra el código fuente legacy real (no supuestos), con
test de regresión dedicado en cada caso.

1. **🔴 Contraseñas reversibles (`util/Cripto.java`).** Antes: AES/ECB con
   clave hardcodeada en el fuente — cualquiera con acceso al código podía
   descifrar cualquier contraseña de cualquier usuario
   (`docs/migration-plan.md` §3.1). Después: BCrypt de una vía (cost
   factor 12, `at.favre.lib:bcrypt`), `Cripto.java`/`Encripcion.java` **no
   portados**, ni siquiera como código muerto (feature 5). Test dedicado
   confirma hashes distintos para la misma contraseña (salt) y falla de
   login con contraseña incorrecta.
2. **Descarga de reportes rota fuera del servidor propio.** Antes:
   `ReporteManagedBean.imprimir()` escribía el PDF en una ruta fija del
   disco del servidor (`C:/repositorio/wisevision/pdf/`) y lo abría con
   `Runtime.getRuntime().exec("rundll32 url.dll,FileProtocolHandler ...")`
   — solo funcionaba si el proceso Java corría en la máquina del usuario,
   no en un despliegue real (`docs/migration-plan.md` §3.2). Después:
   generación 100% en memoria (`ByteArrayOutputStream`, `report/
   {PdfReportAdapter,ExcelReportAdapter}.java`, feature 8), servida por
   HTTP real (`GET /api/reportes/{id}/descargar`, `Content-Disposition:
   attachment`, feature 10). Cero `FileOutputStream` a ruta fija, cero
   `Runtime.exec`, confirmado por grep en cada feature de reportes.
3. **Credenciales hardcodeadas (`persistence.xml`).** Antes: usuario/
   password de MySQL en texto plano dentro del artefacto desplegable
   (`docs/migration-plan.md` §3.3). Después: sin credenciales de
   aplicación en el repo (solo `test`/`test` dummy de LocalStack, dev/test
   únicamente); `AppSecretsBootstrap` (feature 11) resuelve secretos
   reales desde AWS Secrets Manager en el arranque, releyendo siempre
   desde `GetSecretValue`, nunca de memoria.
4. **Deduplicación manual con `HashSet` en memoria.**
   `ReporteManagedBean.reporte()`/`imprimir()` (`hs.addAll(cargos);
   cargos.clear(); cargos.addAll(hs)`) indicaba que la consulta de origen
   devolvía duplicados y se parchaba después (`docs/migration-plan.md`
   §3.5). Después: `service/ReporteService.java` (feature 7) arma el
   árbol Cargo→Rol→4 categorías sin duplicados en origen (agrupado
   `LinkedHashMap`/`putIfAbsent` en `AtributoService.agruparPorCargo`,
   cada `Atributo` es un ítem DynamoDB distinto) — nunca un `Set`
   posterior a la query. Test de regresión reproduce el mismo escenario
   que forzaba el `HashSet` legacy (2 cargos/3 roles/6 atributos) y
   confirma cero duplicados.
5. **Filtro de menú asimétrico por `tipoUsuario`.** `MenuController.java`
   legacy solo filtraba de verdad los ítems cuando el usuario era `"O"`
   (Operador); para cualquier otro valor (incluido `"A"`, Administrador)
   el filtro se saltaba por completo — hallazgo no documentado
   previamente en `migration-plan.md`, encontrado por grep sobre el árbol
   fuente real durante la feature 6. Después: filtro simétrico para
   cualquier `tipoUsuario` recibido.
6. **Jerarquía de menú por producto cruzado.** El campo `cod_submenu`
   (`model/Menu.java`) nunca se usaba para enlazar nada en el árbol fuente
   legado — cada submenú recibía TODOS los ítems que pasaban el filtro de
   tipo, no solo sus hijos reales. Después: `MenuService.armarArbol()`
   enlaza estrictamente `codSubmenu == codigo` del padre, con test de
   regresión verificado revirtiendo temporalmente la lógica para confirmar
   que sí detecta el bug.
7. **Config de Secrets Manager/DynamoDB con segmento de propiedad
   inexistente.** Bug introducido y corregido dentro de la propia
   migración (no legacy): `%dev.quarkus.{dynamodb,secretsmanager}.
   sdk.endpoint-override` (desde la feature 2) usaba un segmento `sdk.`
   que no existe en la versión 3.21.1 realmente resuelta de las
   extensiones Quarkiverse — Quarkus ignora keys desconocidas en vez de
   fallar el build, nunca se detectó porque `mvn test` usa Dev Services y
   no pasa por `application.properties`. Encontrado y corregido en la
   feature 11 (confirmado con `mvn dependency:tree` + inspección de
   `quarkus-config-model.json` del jar).

## 2. Versiones de stack (antes → después)

| Componente | Antes | Después |
|---|---|---|
| Lenguaje | Java 7/8 | **Java 21** |
| Framework web | JSF 2.2 + PrimeFaces 5.2, server-rendered | **Quarkus 3.38.1**, API REST reactiva (JSON), sin vistas server-side |
| Persistencia | Spring Data JPA + EclipseLink sobre MySQL 5.6 | **DynamoDB** (esquema diseñado en `../database/docs/architecture.md`), cliente 100% asíncrono (`DynamoDbAsyncClient`), emulado en dev/test con LocalStack |
| Mensajería | Ninguna (todo síncrono) | **Kafka** (SmallRye Reactive Messaging) para generación asíncrona de reportes — `report.requested`/`report.ready`/`report.failed` |
| Notificación de resultado | Ninguna (respuesta síncrona bloqueante) | **SSE** (`GET /api/reportes/eventos`, `Multi<ReporteEvento>`) — el cliente se suscribe una vez, sin polling |
| Secretos/config | Hardcodeados en `persistence.xml` empaquetado en el `.war` | **AWS Secrets Manager + KMS**, emulados en local con LocalStack, resueltos en el arranque (`AppSecretsBootstrap`) |
| Reportes | iText/POI a mano, ruta fija de disco del servidor, `Runtime.exec` | **OpenPDF** (LGPL, en vez de iText 5/7 por licencia) + **Apache POI** (`XSSFWorkbook`, `.xlsx`), en memoria, detrás de `report/ReportGeneratorPort` |
| Auth/contraseñas | `Cripto.java`: AES/ECB reversible | **BCrypt** (`at.favre.lib:bcrypt`, cost factor 12), hash de una vía |
| Modelo de programación | Bloqueante clásico (hilos de plataforma, Tomcat 7) | **Reactivo de punta a punta con SmallRye Mutiny** (`Uni`/`Multi`) — REST, DynamoDB y Kafka no bloqueantes; el único trabajo bloqueante real (iText/POI, sin API reactiva) se offloadea explícitamente con `.emitOn(Infrastructure.getDefaultWorkerPool())` |
| Observabilidad | Ninguna | Health checks reactivos de readiness (DynamoDB, Secrets Manager, Kafka vía `quarkus-messaging-kafka`), logging estructurado (JBoss Logging), manejo explícito de fallos de infraestructura (503 con mensaje claro, nunca el 500 genérico) |

## 3. Estado de seguridad

**Arreglado:**
- `Cripto.java` (AES/ECB reversible con clave hardcodeada) eliminado por
  completo, no portado ni como código muerto.
- Contraseñas nuevas: hash BCrypt de una vía, nunca texto plano
  persistido ni comparado (`UsuarioRequest` no tiene camino a texto plano;
  el login compara hash contra hash).
- `POST /api/auth/login` responde 401 genérico tanto para email
  inexistente como para contraseña incorrecta (evita filtrar por mensaje
  cuál de los dos casos ocurrió).
- Sin credenciales de aplicación reales en el repo — auditado por grep en
  cada feature que tocó `application.properties`/`docker-compose.yml`;
  solo quedan las dummy `test`/`test` de LocalStack, scoped a `%dev`/dev
  local.
- Secretos de aplicación (nombre de tabla DynamoDB) resueltos en tiempo
  de arranque desde AWS Secrets Manager real (emulado en LocalStack en
  dev/test), nunca cacheados indefinidamente en memoria sin releer.

**Pendiente (fuera de scope de esta migración, requiere decisión de
negocio):**
- **Estrategia de reseteo de contraseñas de usuarios reales.** El corte a
  BCrypt aplica solo a altas nuevas — los usuarios existentes en la base
  de datos legacy (si migran datos reales, ver `../database/`) tienen
  contraseñas cifradas con el esquema reversible viejo, no hasheadas. No
  se definió el mecanismo de comunicación del reseteo (correo,
  primer-login forzado) — documentado como `⚠️ Pendiente` desde
  `docs/migration-plan.md` §4, condición explícita para no bloquear el
  desarrollo del backend en sí, pero **sí bloquea la migración de datos de
  usuarios reales a producción**.
- **Credenciales productivas de AWS.** Todo el desarrollo corrió contra
  LocalStack (dev/test). La tabla "Resolución de secretos por ambiente"
  de `docs/architecture.md` marca la fila `prod` explícitamente como
  "⚠️ Pendiente de credenciales productivas" — no hay credenciales AWS
  reales en ningún ambiente todavía.

## 4. Cobertura de tests agregada por feature

| Feature | Tests nuevos | Acumulado |
|---|---|---|
| 1 — bootstrap_quarkus_reactive_project | 3 | 3 |
| 2 — setup_localstack_dynamodb_secretsmanager | 2 | 6 |
| 3 — crud_catalogos_simples | 20 | 26 |
| 4 — crud_rol_suministro_atributo | 15 | 41 |
| 5 — usuario_login_password_hash_fix | 13 | 54 |
| 6 — menu_dinamico_por_tipo_usuario | 5 | 59 |
| 7 — reportes_dominio_y_query_sin_duplicados | 3 | 62 |
| 8 — reportes_adaptadores_pdf_excel | 3 | 65 |
| 9 — reportes_generacion_async_kafka | 4 | 69 |
| 10 — reportes_notificacion_sse_y_descarga | 8 | 77 |
| 11 — aws_secrets_manager_integration | 3 | 80 |
| 12 — reactive_resilience_observability | 15 | 95 |
| Coordinación cross-track DTO (no es feature, ver §5) | 0 nuevos (8 existentes reescritos con aserciones nuevas) | 95 |

**Estado final: 95/95 tests verdes, `BUILD SUCCESS`, `./init.sh` limpio**
(verificado de forma independiente para cada feature, no solo confiado al
resumen de cada implementer — ver `progress/history.md` para el detalle
de verificación sesión por sesión). Todos los tests corren contra
LocalStack + Kafka reales vía Quarkus Dev Services, ningún mock de
infraestructura.

## 5. Coordinación cross-track: contrato de reportes con el frontend

No es una feature de `feature_list.json`, pero es trabajo real de cierre
del backend documentado aquí porque toca directamente el contrato HTTP
público de reportes. La feature 10 dejó abierto (no bloqueante para esa
feature) un gap de nombres/tipos de campo entre `domain/Reporte`/
`DetalleRol` y lo que `../frontend/src/types/reporte.ts` esperaba. Se
resolvió con una capa de mapeo de respuesta (`web/dto/
{ReporteRespuestaDto,DetalleRolRespuestaDto,DetalleRespuestaDto}.java`)
en vez de reabrir el dominio ya aprobado — `GET /api/reportes/{id}` ahora
serializa a través de estos DTOs. Investigación real contra el código
fuente legacy confirmó que un campo del frontend (`tipoRol`/"Titularidad")
estaba basado en un campo legacy que nunca se poblaba (dead code) y que
otro (`Detalle.parametro`) no tenía equivalente real en el nuevo dominio
por ser un discriminador de categoría ya representado estructuralmente.
Detalle completo: `handoff.md` (raíz del proyecto) y
`progress/history.md`, sesión "Coordinación cross-track".

## 6. Recomendaciones no implementadas en este pase

1. **Licencia final de PDF: OpenPDF vs. alternativas.** Se eligió OpenPDF
   (LGPL 2.1) sobre iText 5 (dual LGPL/MPL, ambiguo) e iText 7 (AGPL) por
   ser la opción de licencia más simple para uso comercial sin comprar
   licencia — decisión tomada, no pendiente, pero vale la pena que legal/
   negocio la confirme formalmente antes de producción si el volumen de
   reportes generados crece mucho (LGPL permite uso comercial pero exige
   atribución de la librería si se distribuye modificada).
2. **Dimensionamiento real de Kafka.** El diseño actual (3 tópicos,
   `acks=all` + `enable.idempotence=true`, sin particionado explícito más
   allá de key=id del reporte) se construyó sobre un supuesto de volumen
   bajo-medio (sistema interno de auditoría de roles) nunca confirmado
   por el usuario (`docs/migration-plan.md` §4). Revisar particiones/
   réplicas/retención cuando se confirme el volumen real de generación de
   reportes en producción.
3. **Límite de 400KB por ítem DynamoDB para reportes grandes.** La
   persistencia de `ReporteEjecucion` (binario + JSON del árbol) vive en
   un solo ítem de la tabla única `RoleRepository` — documentado
   explícitamente como limitación conocida desde la feature 9, sin
   mitigación implementada (ej. mover el binario a S3 con referencia en
   DynamoDB). No bloqueante hoy porque no se ha confirmado que algún
   reporte real se acerque a ese tamaño, pero es el primer cuello de
   botella esperable si el volumen de datos por reporte crece.
4. **Reseteo de contraseñas de usuarios reales** — ver §3, pendiente de
   decisión de negocio, no técnica.
5. **Credenciales AWS productivas** — ver §3, pendiente de que el cliente
   provisione una cuenta AWS real.
6. **Variante serverless (Lambda + API Gateway).** Evaluada y diferida
   explícitamente por el usuario el 09-ago-2026 (`docs/migration-plan.md`
   §4) — identificado un conflicto real (SSE no es compatible con
   Lambda, requeriría WebSocket API Gateway + posiblemente EventBridge/
   SQS + DynamoDB Streams en vez de Kafka). No se creó ese track en esta
   ronda; queda como trabajo futuro si el cliente decide comparar ambos
   diseños.
7. **Renombrar la columna "Titularidad" del frontend.** Hallazgo de la
   coordinación cross-track (§5): la columna actualmente muestra
   categorías de suministro con datos, no titularidad real (ese dato
   nunca existió, era dead code legacy). Cosmético, no bloqueante, pero
   vale la pena corregir el rótulo en una futura sesión de UI para que no
   induzca a error a quien lea el reporte en pantalla.
8. **Patrón `web/` inyectando `repository/` directamente en los
   endpoints de reportes** (`ReporteGenerarResource`, `ReporteResource`),
   sin pasar por una capa `service/` dedicada — señalado como observación
   no bloqueante por el reviewer de las features 9 y 10. Se mantuvo por
   consistencia con el patrón ya presente, pero si el dominio de reportes
   crece en complejidad vale la pena decidir explícitamente si conviene
   introducir un `service/ReporteEjecucionService` o documentar esto como
   excepción deliberada a la regla general de capas.

## 7. Cómo verificar este reporte

```bash
cd backend
bash init.sh   # mvn clean compile && mvn test — esperado: 95/95 verdes, BUILD SUCCESS
```

Requiere Docker (LocalStack + Kafka vía Quarkus Dev Services, sin
credenciales AWS reales). Ningún test depende de infraestructura externa
real.
