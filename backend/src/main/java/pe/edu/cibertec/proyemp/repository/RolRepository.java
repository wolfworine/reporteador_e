package pe.edu.cibertec.proyemp.repository;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import pe.edu.cibertec.proyemp.domain.Rol;
import pe.edu.cibertec.proyemp.domain.RolNoEncontradoException;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.util.HashMap;
import java.util.Map;

/**
 * Repositorio DynamoDB de {@link Rol}, persistido bajo
 * {@code PK=ROL#<id>}, {@code SK=METADATA} -- ver
 * {@code repository/RoleRepositoryTable.java} y {@code progress/current.md}
 * para la decisión de diseño de esta fila.
 */
@ApplicationScoped
public class RolRepository {

    private static final String COUNTER_TIPO = "ROL";

    private final DynamoDbAsyncClient client;

    @Inject
    public RolRepository(DynamoDbAsyncClient client) {
        this.client = client;
    }

    public Uni<Rol> crear(Rol datos) {
        return siguienteId()
                .chain(id -> guardar(conId(datos, id), "attribute_not_exists(" + RoleRepositoryTable.ATTR_PK + ")"));
    }

    public Uni<Rol> buscarPorId(String id) {
        return Uni.createFrom()
                .completionStage(() -> client.getItem(GetItemRequest.builder()
                        .tableName(RoleRepositoryTable.TABLE_NAME)
                        .key(clave(id))
                        .build()))
                .onItem().transform(response -> {
                    if (response.item() == null || response.item().isEmpty()) {
                        throw new RolNoEncontradoException(id);
                    }
                    return aDominio(response.item());
                });
    }

    public Uni<Rol> actualizar(String id, Rol datos) {
        return guardar(conId(datos, id), "attribute_exists(" + RoleRepositoryTable.ATTR_PK + ")")
                .onFailure(ConditionalCheckFailedException.class)
                .transform(failure -> new RolNoEncontradoException(id));
    }

    public Uni<Void> eliminar(String id) {
        return Uni.createFrom()
                .completionStage(() -> client.deleteItem(DeleteItemRequest.builder()
                        .tableName(RoleRepositoryTable.TABLE_NAME)
                        .key(clave(id))
                        .conditionExpression("attribute_exists(" + RoleRepositoryTable.ATTR_PK + ")")
                        .build()))
                .onFailure(ConditionalCheckFailedException.class)
                .transform(failure -> new RolNoEncontradoException(id))
                .replaceWithVoid();
    }

    public Multi<Rol> listarTodos() {
        // Pagina el Scan completo -- ver Javadoc de DynamoScanSupport.
        return DynamoScanSupport.scanAll(client, exclusiveStartKey -> ScanRequest.builder()
                        .tableName(RoleRepositoryTable.TABLE_NAME)
                        .filterExpression("begins_with(" + RoleRepositoryTable.ATTR_PK + ", :prefijo) and "
                                + RoleRepositoryTable.ATTR_SK + " = :metadata")
                        .expressionAttributeValues(Map.of(
                                ":prefijo", AttributeValue.fromS("ROL#"),
                                ":metadata", AttributeValue.fromS(RoleRepositoryTable.SK_METADATA)))
                        .exclusiveStartKey(exclusiveStartKey)
                        .build())
                .onItem().<Rol>transformToMulti(items -> Multi.createFrom()
                        .iterable(items.stream().map(this::aDominio).toList()));
    }

    private Rol conId(Rol datos, String id) {
        return new Rol(id, datos.ubicacionCodigo(), datos.areaCodigo(), datos.cargoCodigo(), datos.nombre(),
                datos.descripcion(), datos.tipo(), datos.reglaNegocio(), datos.ambito(), datos.propietarioRol(),
                datos.propietarioPersona(), datos.situacion(), datos.rolBase());
    }

    private Uni<Rol> guardar(Rol rol, String conditionExpression) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put(RoleRepositoryTable.ATTR_PK, AttributeValue.fromS(RoleRepositoryTable.rolPk(rol.id())));
        item.put(RoleRepositoryTable.ATTR_SK, AttributeValue.fromS(RoleRepositoryTable.SK_METADATA));
        item.put(RoleRepositoryTable.ATTR_ID, AttributeValue.fromS(rol.id()));
        item.put(RoleRepositoryTable.ATTR_UBICACION_CODIGO, AttributeValue.fromS(rol.ubicacionCodigo()));
        item.put(RoleRepositoryTable.ATTR_AREA_CODIGO, AttributeValue.fromS(rol.areaCodigo()));
        item.put(RoleRepositoryTable.ATTR_CARGO_CODIGO, AttributeValue.fromS(rol.cargoCodigo()));
        item.put(RoleRepositoryTable.ATTR_NOMBRE, AttributeValue.fromS(rol.nombre()));
        item.put(RoleRepositoryTable.ATTR_DESCRIPCION, AttributeValue.fromS(rol.descripcion()));
        item.put(RoleRepositoryTable.ATTR_ROL_TIPO, AttributeValue.fromS(rol.tipo()));
        item.put(RoleRepositoryTable.ATTR_REGLA_NEGOCIO, AttributeValue.fromS(rol.reglaNegocio()));
        item.put(RoleRepositoryTable.ATTR_AMBITO, AttributeValue.fromS(rol.ambito()));
        item.put(RoleRepositoryTable.ATTR_PROPIETARIO_ROL, AttributeValue.fromS(rol.propietarioRol()));
        item.put(RoleRepositoryTable.ATTR_PROPIETARIO_PERSONA, AttributeValue.fromS(rol.propietarioPersona()));
        item.put(RoleRepositoryTable.ATTR_SITUACION, AttributeValue.fromS(rol.situacion()));
        item.put(RoleRepositoryTable.ATTR_ROL_BASE, AttributeValue.fromS(rol.rolBase()));

        return Uni.createFrom()
                .completionStage(() -> client.putItem(PutItemRequest.builder()
                        .tableName(RoleRepositoryTable.TABLE_NAME)
                        .item(item)
                        .conditionExpression(conditionExpression)
                        .build()))
                .onItem().transform(response -> rol);
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

    private Map<String, AttributeValue> clave(String id) {
        return Map.of(
                RoleRepositoryTable.ATTR_PK, AttributeValue.fromS(RoleRepositoryTable.rolPk(id)),
                RoleRepositoryTable.ATTR_SK, AttributeValue.fromS(RoleRepositoryTable.SK_METADATA));
    }

    private Rol aDominio(Map<String, AttributeValue> item) {
        return new Rol(
                item.get(RoleRepositoryTable.ATTR_ID).s(),
                item.get(RoleRepositoryTable.ATTR_UBICACION_CODIGO).s(),
                item.get(RoleRepositoryTable.ATTR_AREA_CODIGO).s(),
                item.get(RoleRepositoryTable.ATTR_CARGO_CODIGO).s(),
                item.get(RoleRepositoryTable.ATTR_NOMBRE).s(),
                item.get(RoleRepositoryTable.ATTR_DESCRIPCION).s(),
                item.get(RoleRepositoryTable.ATTR_ROL_TIPO).s(),
                item.get(RoleRepositoryTable.ATTR_REGLA_NEGOCIO).s(),
                item.get(RoleRepositoryTable.ATTR_AMBITO).s(),
                item.get(RoleRepositoryTable.ATTR_PROPIETARIO_ROL).s(),
                item.get(RoleRepositoryTable.ATTR_PROPIETARIO_PERSONA).s(),
                item.get(RoleRepositoryTable.ATTR_SITUACION).s(),
                item.get(RoleRepositoryTable.ATTR_ROL_BASE).s());
    }
}
