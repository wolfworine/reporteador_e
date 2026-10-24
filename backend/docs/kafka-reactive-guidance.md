# Guía Kafka + Mutiny reactivo — features 7 y 8 (reportes async)

Complementa `docs/conventions.md` (que solo cubre el patrón general
`@Incoming`/`@Outgoing`). Este documento existe porque las features 7
(`reportes_generacion_async_kafka`) y 8
(`reportes_notificacion_sse_y_descarga`) aún no están implementadas al
momento de escribir esto y requieren decisiones de garantías de entrega
que el resto del proyecto (CRUDs simples) no necesitó. Destinado tanto a
implementers Claude como a agentes opencode — es un archivo plano, no una
Skill de Claude Code, así que cualquier agente que lea archivos puede
seguirlo.

## Modelo mental (por qué importa para este proyecto)

- El flujo es: `POST /api/reportes/generar` → `202 Accepted + id` →
  publica `report.requested` → un worker consume, genera PDF/Excel
  (offloaded a worker pool, ver `conventions.md`), guarda el resultado →
  publica `report.ready` → el frontend recibe la notificación por SSE.
- El "id" del reporte (UUID o contador atómico, decidir en la feature 7)
  es la clave de partición natural: mismo reporte, mismo consumer,
  garantiza que sus eventos (`requested`→`ready`/`failed`) se procesen en
  orden. No usar un valor aleatorio ni el id del usuario como partition
  key — el id del usuario agruparía todos sus reportes en una sola
  partición sin necesidad, el id del reporte es el grano correcto.

## Garantía de entrega: at-least-once + consumidor idempotente

Este proyecto **no necesita exactly-once vía transacciones Kafka** — es
justificable con at-least-once + un consumidor idempotente, más simple y
sin el costo de throughput de las transacciones:

- **Productor** (`POST /api/reportes/generar` → publica
  `report.requested`): usar `acks=all` (vía `application.properties`,
  `mp.messaging.outgoing.report-requested.acks=all`) y habilitar
  idempotencia del productor
  (`mp.messaging.outgoing.report-requested.enable.idempotence=true`) —
  sin esto, un timeout+retry del broker puede duplicar el mensaje de
  "generar reporte X", lo que dispararía el worker dos veces para el
  mismo id.
- **Worker consumidor** (`@Incoming("report-requested")`): **debe asumir
  que cualquier mensaje puede ser una redelivery** — antes de generar el
  PDF/Excel, verificar el estado actual del reporte en DynamoDB
  (`PK=REPORTE#<id>`). Si ya está en estado `READY` o `FAILED`, no
  regenerar — responder con un log y hacer `ack` sin reprocesar. Esto es
  el "dedupe key" natural: el propio id del reporte + su estado
  persistido, no hace falta una tabla de deduplicación separada.
- **NO usar auto-commit del offset.** SmallRye Reactive Messaging Kafka
  hace commit según la estrategia configurada
  (`mp.messaging.incoming.report-requested.commit-strategy`) —
  usar `throttled` (equivalente reactivo a "commit después de procesar
  exitosamente", no antes) en vez de `latest`/auto-commit, para no perder
  el evento si el worker crashea a mitad de la generación del PDF.
- **Mensaje envenenado (poison pill):** si un reporte falla
  repetidamente (ej. datos corruptos que rompen el generador PDF), no
  reintentar indefinidamente bloqueando la partición — después de un
  número acotado de reintentos (definir explícitamente, ej. 3), marcar el
  reporte como `FAILED` en DynamoDB con el motivo, publicar
  `report.failed` para que el SSE notifique al usuario, y hacer commit
  del offset para que la partición siga avanzando. No dejar un reporte
  "atascado" reintentando para siempre.

## SSE (feature 8) — relación con Kafka

- El endpoint `GET /api/reportes/eventos` (`Multi<ReporteEvento>`) NO debe
  consumir directamente del tópico Kafka por request — eso crearía un
  consumer group nuevo por cada conexión SSE abierta, ineficiente y sin
  necesidad. En vez de eso: el worker que consume `report.requested`
  escribe el estado a DynamoDB Y publica a un `Multi` interno
  (`BroadcastProcessor` o similar de Mutiny) que el endpoint SSE expone a
  múltiples clientes conectados. Kafka es la fuente de verdad
  asíncrona entre servicios; el `Multi` interno es solo el puente hacia
  las conexiones SSE de este mismo proceso.
- Si en el futuro el backend escala a múltiples instancias, ese `Multi`
  interno per-proceso ya no alcanza (un cliente SSE conectado a la
  instancia A no vería un evento generado por la instancia B) — está
  documentado como limitación conocida a resolver si/cuando se decida
  escalar horizontalmente, no bloqueante para esta feature.

## Checklist rápido para el reviewer de features 7/8

1. ¿El productor tiene `acks=all` + idempotencia explícitos (no dejados
   al default del entorno)?
2. ¿El consumidor verifica el estado persistido antes de regenerar (no
   asume que cada mensaje es la primera vez que lo ve)?
3. ¿El commit-strategy es `throttled` (o equivalente post-procesamiento),
   no auto-commit/`latest`?
4. ¿Hay un límite de reintentos y un camino a `FAILED` + `report.failed`
   en vez de un loop infinito?
5. ¿El endpoint SSE evita crear un consumer group nuevo por conexión?
6. ¿La clave de partición es el id del reporte, no algo aleatorio o el id
   de usuario?

Si alguno de estos falla, es motivo de `CHANGES_REQUESTED` — son
garantías de correctitud, no preferencias de estilo.
