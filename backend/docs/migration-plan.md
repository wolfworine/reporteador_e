# Plan de migración — entel-role-repository-backend

> Fuentes: `D:\Capacitacion\IA\Migracion_Reporte\05_VALIDACION_Y_HALLAZGOS_REALES.md`,
> `06_PLAN_MIGRACION_REACT_JAVA21.md`, y el código fuente real extraído en
> `D:\Capacitacion\IA\Migracion_Reporte\JSF_RepositorioV3.9.2_extraido\war_contenido\`.
> Este documento es la fuente de verdad para `feature_list.json`.

## 1. Qué hace el proyecto hoy

**WiseVision Role Repository Basic**, sistema de gestión de roles y accesos
para el cliente **Entel** (2015, autores Cesar Valenzuela y Marco Oscco
Enciso). Administra qué **Rol** tiene cada **Cargo** dentro de un **Área**,
qué **Aplicación**/**Suministro** (con sus **Atributos**) están asociados a
cada rol, y genera **Reportes** PDF/Excel consolidando esa información para
auditoría de accesos. Incluye un menú dinámico (`Menu`, tabla en BD) que
varía según `tipoUsuario`, y login propio (`Login`/`Usuario`).

Paquete real: `pe.edu.cibertec.proyemp` (managedbean/model/service/repository/util).

## 2. Stack actual vs. objetivo

| | Actual | Objetivo |
|---|---|---|
| Lenguaje | Java 7/8 | **Java 21** |
| Framework web | JSF 2.2 + PrimeFaces 5.2 (server-rendered) | **Quarkus 3.x**, API REST (JSON), sin vistas server-side |
| Persistencia | Spring Data JPA + EclipseLink sobre MySQL 5.6, esquema relacional (`consulta.sql`) | **DynamoDB** (NoSQL), emulado en local con **LocalStack**, acceso vía cliente **asíncrono** (`DynamoDbAsyncClient`) |
| Mensajería | Ninguna (todo síncrono) | **Kafka** (SmallRye Reactive Messaging) para generación asíncrona de reportes |
| Secretos/config | Hardcodeados en `persistence.xml` empaquetado en el `.war` (`root`/`root`) | **AWS Secrets Manager + KMS** (emulados en local con LocalStack) |
| Reportes | iText/POI, tablas armadas a mano en Java, guardadas en ruta fija del servidor (`C:/repositorio/wisevision/pdf/`), abiertas con `Runtime.exec("rundll32 ...")` | iText/POI detrás de un puerto/adaptador, generación en memoria, entrega vía descarga HTTP o notificación async (Kafka) |
| Auth/contraseñas | `Cripto.java`: AES/ECB **reversible** con clave hardcodeada en el fuente | Hash de una vía (BCrypt/Argon2) + reseteo forzado de contraseñas existentes |
| Modelo de programación | Bloqueante clásico (hilos de plataforma, Tomcat 7) | **Reactivo de punta a punta con SmallRye Mutiny** (`Uni`/`Multi`) — REST, DynamoDB y Kafka no bloqueantes. El trabajo bloqueante inevitable (iText/POI, que no tienen API reactiva) se offloadea explícitamente a un worker pool (`emitOn`), respaldado por Virtual Threads de Java 21 para no gastar hilos de plataforma en esa espera |

## 3. Bugs y deuda técnica encontrados (con evidencia)

1. **🔴 Seguridad crítica — contraseñas reversibles.** `util/Cripto.java`
   cifra (no hashea) contraseñas con AES/ECB y una clave en texto plano en
   el código fuente. Cualquiera con el código puede descifrar cualquier
   contraseña. **No migrar este mecanismo.** Ver decisión en §4.
2. **Descarga de reportes rota fuera del propio servidor.**
   `ReporteManagedBean.imprimir()` escribe el PDF en una ruta fija del disco
   del servidor y lo abre con `Runtime.getRuntime().exec("rundll32
   url.dll,FileProtocolHandler " + ficha)` — solo "funciona" si el proceso
   Java corre en la máquina del usuario. En un despliegue real esto no
   descarga nada al navegador del cliente.
3. **Credenciales hardcodeadas.** `persistence.xml` trae usuario/password
   de MySQL en texto plano dentro del artefacto desplegable.
4. **Claves compuestas complejas en el esquema relacional** (ver
   `consulta.sql`): `atributo` tiene PK de 5 columnas
   (`id, id_aplicacion, id_rol, id_suministro, id_detalleatributo,
   id_estado`). Al pasar a DynamoDB esto se resuelve con un diseño de
   clave compuesta partition/sort key + GSIs (ver `docs/architecture.md`),
   no se puede copiar 1:1.
5. **Deduplicación manual con `HashSet` en memoria** en
   `ReporteManagedBean.reporte()`/`imprimir()` (`hs.addAll(cargos);
   cargos.clear(); cargos.addAll(hs)`) — indica que las consultas de origen
   devuelven duplicados; la query nueva en DynamoDB debe diseñarse para no
   duplicar en primer lugar, no parchear con `Set` después.

## 4. Decisiones de alcance ya tomadas con el usuario (09-ago-2026)

- **Backend:** Quarkus, Java 21 con Virtual Threads. ✅ Decidido.
- **Mensajería:** Kafka. Se mantiene la arquitectura asíncrona (no se
  simplifica a síncrono puro). ✅ Decidido.
- **Base de datos:** DynamoDB (no PostgreSQL, no se mantiene MySQL). Local
  vía **LocalStack**. ✅ Decidido — implica rediseño de esquema (NoSQL),
  ver `docs/architecture.md` §"Modelo de datos DynamoDB".
- **Secretos:** AWS Secrets Manager + KMS (no HashiCorp Vault), emulados en
  local vía LocalStack; ninguna credencial en `application.properties` ni
  en el código. ✅ Decidido (corregido 09-ago-2026).
- **Modelo de programación:** reactivo puro con SmallRye Mutiny de punta a
  punta (no imperativo con Virtual Threads como único mecanismo) — Java 21
  sigue siendo el runtime, y sus Virtual Threads se usan como worker pool
  para offload de trabajo bloqueante (iText/POI), pero la API pública
  (REST, acceso a datos) es reactiva (`Uni`/`Multi`). ✅ Decidido (corregido
  09-ago-2026).
- **Frontend:** React (última versión estable). Ver
  `../frontend/docs/migration-plan.md` para el plan del otro track.
- **Contraseñas:** no se reutiliza `Cripto.java`. Se migra a hash de una
  vía (BCrypt/Argon2) con reseteo forzado de contraseñas de usuarios
  existentes en el corte. ⚠️ Pendiente de definir el mecanismo exacto de
  comunicación del reseteo (correo, primer-login forzado) — no bloquea el
  desarrollo del backend, sí bloquea la migración de datos de usuarios
  reales a producción.
- **Volumen real de uso:** no confirmado por el usuario todavía. Se asume
  volumen bajo-medio (sistema interno de auditoría de roles) pero se
  construye igual sobre Kafka por decisión explícita ya tomada arriba.
- **Serverless (Lambda/API Gateway) evaluado y DIFERIDO, no descartado**
  (09-ago-2026): hoy el backend es un servicio Quarkus persistente
  (contenedor de larga duración), no funciones Lambda. Solo DynamoDB y
  AWS Secrets Manager/KMS son serverless nativos en este diseño; Kafka
  autogestionado y el propio Quarkus no lo son. Se identificó un
  conflicto real si se pasara a Lambda: **SSE no es compatible con
  Lambda** (no mantiene conexiones abiertas) — requeriría cambiar la
  notificación a WebSocket API Gateway y posiblemente Kafka por
  EventBridge/SQS + DynamoDB Streams. Decisión del usuario: continuar con
  el diseño actual (servicio persistente) y, en una fase futura, construir
  también una variante serverless para comparar cuál funciona mejor antes
  de decidir en cuál quedarse. **No crear ese track todavía** — solo
  queda anotado aquí como trabajo futuro, no es una feature de
  `feature_list.json` de esta ronda.

## 5. Plan por fases

1. **Fase 0 — Bootstrap.** Proyecto Quarkus compilable, LocalStack con
   DynamoDB, Vault en modo dev, todo levantable con `docker-compose` +
   `mvn quarkus:dev`.
2. **Fase 1 — Catálogos simples.** Área, Cargo, Ubicación, Aplicación,
   Estado: CRUD completo sobre DynamoDB.
3. **Fase 2 — Cadena de dominio compleja.** Rol → Suministro → Atributo →
   Detalle (con las 4 categorías Roles/Grupos/Inbox/DefaultInbox).
4. **Fase 3 — Usuario/Login/seguridad.** Hash de contraseñas, endpoints de
   autenticación, y **Menu** (menú dinámico por tipo de usuario).
5. **Fase 4 — Reportes.** Generación PDF/Excel corregida (en memoria, sin
   ruta fija de servidor), primero síncrona.
6. **Fase 5 — Asíncrono.** Kafka + Virtual Threads para generación de
   reportes desacoplada, con endpoint de estado.
7. **Fase 6 — Secretos.** Vault reemplaza cualquier config hardcodeada
   introducida en fases previas (DB endpoint, credenciales Kafka, etc.)
8. **Fase 7 — Observabilidad y cierre.** Health checks, logging
   estructurado, reporte final de mejoras.

Ver `feature_list.json` para el desglose ejecutable de estas fases.
