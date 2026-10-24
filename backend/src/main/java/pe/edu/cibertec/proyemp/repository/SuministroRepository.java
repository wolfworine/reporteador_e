package pe.edu.cibertec.proyemp.repository;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import pe.edu.cibertec.proyemp.domain.Suministro;
import pe.edu.cibertec.proyemp.domain.SuministroNoEncontradoException;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.util.HashMap;
import java.util.Map;

/**
 * Repositorio DynamoDB de {@link Suministro} -- Patrón 2b del esquema
 * canónico ({@code SuministroRepository.findSuministro(idRol)}): PK=
 * {@code ROL#<idRol>}, SK={@code SUMINISTRO#<id>}. Listar los Suministros de
 * un Rol es una Query por PK con {@code begins_with(SK,"SUMINISTRO#")}, no
 * un Scan.
 */
@ApplicationScoped
public class SuministroRepository {

    private static final String COUNTER_TIPO = "SUMINISTRO";

    private final DynamoDbAsyncClient client;

    @Inject
    public SuministroRepository(DynamoDbAsyncClient client) {
        this.client = client;
    }

    public Uni<Suministro> crear(String idRol, String nombre, String prioridad) {
        return siguienteId()
                .chain(id -> guardar(new Suministro(id, idRol, nombre, prioridad),
                        "attribute_not_exists(" + RoleRepositoryTable.ATTR_PK + ")"));
    }

    public Uni<Suministro> buscarPorId(String idRol, String idSuministro) {
        return Uni.createFrom()
                .completionStage(() -> client.getItem(GetItemRequest.builder()
                        .tableName(RoleRepositoryTable.TABLE_NAME)
                        .key(clave(idRol, idSuministro))
                        .build()))
                .onItem().transform(response -> {
                    if (response.item() == null || response.item().isEmpty()) {
                        throw new SuministroNoEncontradoException(idRol, idSuministro);
                    }
                    return aDominio(response.item());
                });
    }

    public Uni<Suministro> actualizar(String idRol, String idSuministro, String nombre, String prioridad) {
        return guardar(new Suministro(idSuministro, idRol, nombre, prioridad),
                "attribute_exists(" + RoleRepositoryTable.ATTR_PK + ")")
                .onFailure(ConditionalCheckFailedException.class)
                .transform(failure -> new SuministroNoEncontradoException(idRol, idSuministro));
    }

    public Uni<Void> eliminar(String idRol, String idSuministro) {
        return Uni.createFrom()
                .completionStage(() -> client.deleteItem(DeleteItemRequest.builder()
                        .tableName(RoleRepositoryTable.TABLE_NAME)
                        .key(clave(idRol, idSuministro))
                        .conditionExpression("attribute_exists(" + RoleRepositoryTable.ATTR_PK + ")")
                        .build()))
                .onFailure(ConditionalCheckFailedException.class)
                .transform(failure -> new SuministroNoEncontradoException(idRol, idSuministro))
                .replaceWithVoid();
    }

    public Multi<Suministro> listarPorRol(String idRol) {
        QueryRequest request = QueryRequest.builder()
                .tableName(RoleRepositoryTable.TABLE_NAME)
                .keyConditionExpression(RoleRepositoryTable.ATTR_PK + " = :pk and begins_with("
                        + RoleRepositoryTable.ATTR_SK + ", :prefijo)")
                .expressionAttributeValues(Map.of(
                        ":pk", AttributeValue.fromS(RoleRepositoryTable.rolPk(idRol)),
                        ":prefijo", AttributeValue.fromS(RoleRepositoryTable.SK_SUMINISTRO_PREFIX)))
                .build();

        return Uni.createFrom()
                .completionStage(() -> client.query(request))
                .onItem().<Suministro>transformToMulti(response -> Multi.createFrom()
                        .iterable(response.items().stream().map(this::aDominio).toList()));
    }

    private Uni<Suministro> guardar(Suministro suministro, String conditionExpression) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put(RoleRepositoryTable.ATTR_PK, AttributeValue.fromS(RoleRepositoryTable.rolPk(suministro.rolId())));
        item.put(RoleRepositoryTable.ATTR_SK, AttributeValue.fromS(RoleRepositoryTable.suministroSk(suministro.id())));
        item.put(RoleRepositoryTable.ATTR_ID, AttributeValue.fromS(suministro.id()));
        item.put(RoleRepositoryTable.ATTR_ROL_ID, AttributeValue.fromS(suministro.rolId()));
        item.put(RoleRepositoryTable.ATTR_NOMBRE, AttributeValue.fromS(suministro.nombre()));
        item.put(RoleRepositoryTable.ATTR_PRIORIDAD, AttributeValue.fromS(suministro.prioridad()));

        return Uni.createFrom()
                .completionStage(() -> client.putItem(PutItemRequest.builder()
                        .tableName(RoleRepositoryTable.TABLE_NAME)
                        .item(item)
                        .conditionExpression(conditionExpression)
                        .build()))
                .onItem().transform(response -> suministro);
    }

    private Uni<String> siguienteId() {
        Map<String, AttributeValue> key = Map.of(
                RoleRepositoryTable.ATTR_PK, AttributeValue.fromS(RoleRepositoryTable.counterPk(COUNTER_TIPO)),
                RoleRepositoryTable.ATTR_SK, AttributeValue.fromS(RoleRepositoryTable.SK_METADATA));

        return Uni.createFrom()
                .completionStage(() -> client.updateItem(UpdateItemRequest.builder()
                        .tableName(RoleRepositoryTable.TABLE_NAME)
                        .key(key)
                        .updateExpression("ADD " + RoleRepositoryTable.ATTR_VALOR + " :incremento")
                        .expressionAttributeValues(Map.of(":incremento", AttributeValue.fromN("1")))
                        .returnValues(ReturnValue.UPDATED_NEW)
                        .build()))
                .onItem().transform(response -> response.attributes().get(RoleRepositoryTable.ATTR_VALOR).n());
    }

    private Map<String, AttributeValue> clave(String idRol, String idSuministro) {
        return Map.of(
                RoleRepositoryTable.ATTR_PK, AttributeValue.fromS(RoleRepositoryTable.rolPk(idRol)),
                RoleRepositoryTable.ATTR_SK, AttributeValue.fromS(RoleRepositoryTable.suministroSk(idSuministro)));
    }

    private Suministro aDominio(Map<String, AttributeValue> item) {
        return new Suministro(
                item.get(RoleRepositoryTable.ATTR_ID).s(),
                item.get(RoleRepositoryTable.ATTR_ROL_ID).s(),
                item.get(RoleRepositoryTable.ATTR_NOMBRE).s(),
                item.get(RoleRepositoryTable.ATTR_PRIORIDAD).s());
    }
}
