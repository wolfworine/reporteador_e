package pe.edu.cibertec.proyemp.repository;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import pe.edu.cibertec.proyemp.domain.EstadoReporte;
import pe.edu.cibertec.proyemp.domain.ReporteEjecucion;
import pe.edu.cibertec.proyemp.domain.ReporteEjecucionNoEncontradaException;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Repositorio DynamoDB de {@link ReporteEjecucion} (feature 9,
 * {@code reportes_generacion_async_kafka}), persistida bajo
 * {@code PK=REPORTE#<id>}/{@code SK=METADATA} en la tabla única
 * {@code RoleRepository} (mismo criterio que {@code UsuarioRepository}
 * cuando no había patrón canónico para una entidad nueva de la aplicación,
 * ver {@code repository/RoleRepositoryTable.java}).
 *
 * <p>Diseñado para que {@code messaging/ReporteRequestWorker} (esta
 * feature) y la feature 10 ({@code GET /api/reportes/{id}}, descarga, SSE)
 * lo consuman sin rediseñar nada: el binario generado ({@code B} nativo de
 * DynamoDB) y la estructura {@code List<Reporte>} que lo originó
 * (serializada a JSON en {@code reporteJson}) quedan recuperables por
 * {@code id} junto al {@code estado} (PENDIENTE/LISTO/ERROR).
 *
 * <p><b>Limitación conocida:</b> un ítem DynamoDB no puede superar 400KB
 * totales -- si el PDF/Excel generado (más el JSON del árbol) excede ese
 * límite, {@code putItem}/{@code updateItem} fallará con
 * {@code ValidationException}. No se resuelve en esta feature (mover el
 * binario a S3 está fuera de scope de {@code docs/migration-plan.md} para
 * este proyecto) -- documentado aquí y en {@code docs/architecture.md}
 * como limitación conocida, no un bug silencioso.
 */
@ApplicationScoped
public class ReporteEjecucionRepository {

    private final DynamoDbAsyncClient client;

    @Inject
    public ReporteEjecucionRepository(DynamoDbAsyncClient client) {
        this.client = client;
    }

    /** Crea la fila inicial en estado {@code PENDIENTE}, intentos=0. */
    public Uni<Void> crearPendiente(String id, String idAplicacion, String idArea, String formato) {
        return crearPendienteSiNoExiste(id, idAplicacion, idArea, formato).replaceWithVoid();
    }

    /**
     * Idempotencia: escritura condicional ({@code attribute_not_exists(pk)}).
     * Devuelve {@code true} si creó la fila, {@code false} si ya existía una
     * ejecución con ese {@code id} (otra petición idéntica llegó antes) --
     * atómico en DynamoDB, así dos clics simultáneos no crean dos filas.
     */
    public Uni<Boolean> crearPendienteSiNoExiste(String id, String idAplicacion, String idArea, String formato) {
        String ahora = Instant.now().toString();
        Map<String, AttributeValue> item = new HashMap<>();
        item.put(RoleRepositoryTable.ATTR_PK, AttributeValue.fromS(RoleRepositoryTable.reportePk(id)));
        item.put(RoleRepositoryTable.ATTR_SK, AttributeValue.fromS(RoleRepositoryTable.SK_METADATA));
        item.put(RoleRepositoryTable.ATTR_ID, AttributeValue.fromS(id));
        item.put(RoleRepositoryTable.ATTR_REPORTE_ESTADO, AttributeValue.fromS(EstadoReporte.PENDIENTE.name()));
        item.put(RoleRepositoryTable.ATTR_REPORTE_FORMATO, AttributeValue.fromS(formato));
        item.put(RoleRepositoryTable.ATTR_REPORTE_ID_APLICACION, AttributeValue.fromS(idAplicacion));
        item.put(RoleRepositoryTable.ATTR_REPORTE_ID_AREA, AttributeValue.fromS(idArea));
        item.put(RoleRepositoryTable.ATTR_REPORTE_INTENTOS, AttributeValue.fromN("0"));
        item.put(RoleRepositoryTable.ATTR_REPORTE_CREADO_EN, AttributeValue.fromS(ahora));
        item.put(RoleRepositoryTable.ATTR_REPORTE_ACTUALIZADO_EN, AttributeValue.fromS(ahora));

        return Uni.createFrom()
                .completionStage(() -> client.putItem(PutItemRequest.builder()
                        .tableName(RoleRepositoryTable.TABLE_NAME)
                        .item(item)
                        .conditionExpression("attribute_not_exists(" + RoleRepositoryTable.ATTR_PK + ")")
                        .build()))
                .replaceWith(Boolean.TRUE)
                .onFailure(ConditionalCheckFailedException.class).recoverWithItem(Boolean.FALSE);
    }

    public Uni<ReporteEjecucion> buscarPorId(String id) {
        return Uni.createFrom()
                .completionStage(() -> client.getItem(GetItemRequest.builder()
                        .tableName(RoleRepositoryTable.TABLE_NAME)
                        .key(clave(id))
                        .build()))
                .onItem().transform(response -> {
                    if (response.item() == null || response.item().isEmpty()) {
                        throw new ReporteEjecucionNoEncontradaException(id);
                    }
                    return aDominio(response.item());
                });
    }

    /**
     * Incrementa (atómicamente, {@code ADD}) el contador de intentos y
     * devuelve el nuevo valor -- fuente de verdad del límite de reintentos
     * del worker (poison pill), sobrevive a un crash-restart a media
     * generación porque queda persistido, no en memoria del proceso.
     */
    public Uni<Integer> incrementarIntentos(String id) {
        return Uni.createFrom()
                .completionStage(() -> client.updateItem(UpdateItemRequest.builder()
                        .tableName(RoleRepositoryTable.TABLE_NAME)
                        .key(clave(id))
                        .updateExpression("ADD " + RoleRepositoryTable.ATTR_REPORTE_INTENTOS + " :incremento SET "
                                + RoleRepositoryTable.ATTR_REPORTE_ACTUALIZADO_EN + " = :ahora")
                        .expressionAttributeValues(Map.of(
                                ":incremento", AttributeValue.fromN("1"),
                                ":ahora", AttributeValue.fromS(Instant.now().toString())))
                        .returnValues(ReturnValue.UPDATED_NEW)
                        .build()))
                .onItem()
                .transform(response -> Integer.valueOf(response.attributes()
                        .get(RoleRepositoryTable.ATTR_REPORTE_INTENTOS).n()));
    }

    /** Marca el resultado como {@code LISTO} con el binario y el árbol JSON generados. */
    public Uni<Void> marcarListo(String id, byte[] binario, String reporteJson) {
        return Uni.createFrom()
                .completionStage(() -> client.updateItem(UpdateItemRequest.builder()
                        .tableName(RoleRepositoryTable.TABLE_NAME)
                        .key(clave(id))
                        .updateExpression("SET " + RoleRepositoryTable.ATTR_REPORTE_ESTADO + " = :estado, "
                                + RoleRepositoryTable.ATTR_REPORTE_BINARIO + " = :binario, "
                                + RoleRepositoryTable.ATTR_REPORTE_JSON + " = :reporteJson, "
                                + RoleRepositoryTable.ATTR_REPORTE_ACTUALIZADO_EN + " = :ahora")
                        .expressionAttributeValues(Map.of(
                                ":estado", AttributeValue.fromS(EstadoReporte.LISTO.name()),
                                ":binario", AttributeValue.fromB(SdkBytes.fromByteArray(binario)),
                                ":reporteJson", AttributeValue.fromS(reporteJson),
                                ":ahora", AttributeValue.fromS(Instant.now().toString())))
                        .build()))
                .replaceWithVoid();
    }

    /** Marca el resultado como {@code ERROR} con el motivo (poison pill, reintentos agotados). */
    public Uni<Void> marcarError(String id, String motivo) {
        return Uni.createFrom()
                .completionStage(() -> client.updateItem(UpdateItemRequest.builder()
                        .tableName(RoleRepositoryTable.TABLE_NAME)
                        .key(clave(id))
                        .updateExpression("SET " + RoleRepositoryTable.ATTR_REPORTE_ESTADO + " = :estado, "
                                + RoleRepositoryTable.ATTR_REPORTE_MOTIVO_ERROR + " = :motivo, "
                                + RoleRepositoryTable.ATTR_REPORTE_ACTUALIZADO_EN + " = :ahora")
                        .expressionAttributeValues(Map.of(
                                ":estado", AttributeValue.fromS(EstadoReporte.ERROR.name()),
                                ":motivo", AttributeValue.fromS(motivo),
                                ":ahora", AttributeValue.fromS(Instant.now().toString())))
                        .build()))
                .replaceWithVoid();
    }

    private Map<String, AttributeValue> clave(String id) {
        return Map.of(
                RoleRepositoryTable.ATTR_PK, AttributeValue.fromS(RoleRepositoryTable.reportePk(id)),
                RoleRepositoryTable.ATTR_SK, AttributeValue.fromS(RoleRepositoryTable.SK_METADATA));
    }

    private ReporteEjecucion aDominio(Map<String, AttributeValue> item) {
        AttributeValue binario = item.get(RoleRepositoryTable.ATTR_REPORTE_BINARIO);
        AttributeValue reporteJson = item.get(RoleRepositoryTable.ATTR_REPORTE_JSON);
        AttributeValue motivoError = item.get(RoleRepositoryTable.ATTR_REPORTE_MOTIVO_ERROR);
        AttributeValue intentos = item.get(RoleRepositoryTable.ATTR_REPORTE_INTENTOS);
        return new ReporteEjecucion(
                item.get(RoleRepositoryTable.ATTR_ID).s(),
                EstadoReporte.valueOf(item.get(RoleRepositoryTable.ATTR_REPORTE_ESTADO).s()),
                item.get(RoleRepositoryTable.ATTR_REPORTE_FORMATO).s(),
                item.get(RoleRepositoryTable.ATTR_REPORTE_ID_APLICACION).s(),
                item.get(RoleRepositoryTable.ATTR_REPORTE_ID_AREA).s(),
                binario == null ? null : binario.b().asByteArray(),
                reporteJson == null ? null : reporteJson.s(),
                motivoError == null ? null : motivoError.s(),
                intentos == null ? 0 : Integer.parseInt(intentos.n()),
                item.get(RoleRepositoryTable.ATTR_REPORTE_CREADO_EN).s(),
                item.get(RoleRepositoryTable.ATTR_REPORTE_ACTUALIZADO_EN).s());
    }
}
