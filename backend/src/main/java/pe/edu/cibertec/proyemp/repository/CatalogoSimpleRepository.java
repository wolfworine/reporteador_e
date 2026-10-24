package pe.edu.cibertec.proyemp.repository;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import pe.edu.cibertec.proyemp.domain.CatalogoNoEncontradoException;
import pe.edu.cibertec.proyemp.domain.TipoCatalogo;
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
 * Implementación reactiva única, parametrizada por {@link TipoCatalogo} y un
 * {@link CatalogoMapper}, del acceso a DynamoDB para los 5 catálogos simples
 * (Patrón 1 del esquema canónico: PK {@code ENTITY#<tipo>#<codigo>}, SK
 * {@code METADATA}). No es un bean CDI -- cada repositorio concreto (ej.
 * {@code AreaRepository}) construye y encapsula su propia instancia, para
 * mantener tipos de dominio distintos por catálogo (trazabilidad) sin
 * duplicar la lógica de acceso a DynamoDB 5 veces (docs/conventions.md:
 * "no accedas a DynamoDB fuera de esta capa").
 *
 * <p>Todas las llamadas usan {@link DynamoDbAsyncClient} envuelto en
 * {@code Uni.createFrom().completionStage(...)} -- ninguna llamada
 * bloqueante, tal como exige docs/architecture.md.
 */
public final class CatalogoSimpleRepository<T> implements CatalogoRepositorio<T> {

    private final DynamoDbAsyncClient client;
    private final TipoCatalogo tipo;
    private final CatalogoMapper<T> mapper;

    public CatalogoSimpleRepository(DynamoDbAsyncClient client, TipoCatalogo tipo, CatalogoMapper<T> mapper) {
        this.client = client;
        this.tipo = tipo;
        this.mapper = mapper;
    }

    @Override
    public Uni<T> buscarPorCodigo(String codigo) {
        return Uni.createFrom()
                .completionStage(() -> client.getItem(GetItemRequest.builder()
                        .tableName(RoleRepositoryTable.TABLE_NAME)
                        .key(clave(codigo))
                        .build()))
                .onItem().transform(response -> {
                    if (response.item() == null || response.item().isEmpty()) {
                        throw new CatalogoNoEncontradoException(tipo, codigo);
                    }
                    return aDominio(response.item());
                });
    }

    @Override
    public Multi<T> listarTodos() {
        // Pagina el Scan completo (feature 12 no lo cubrio, bug real
        // encontrado el 2026-08-19 migrando datos legado reales -- ver
        // Javadoc de DynamoScanSupport): con volumen bajo (datos de prueba)
        // una sola pagina alcanzaba y el bug nunca se manifesto en tests.
        return DynamoScanSupport.scanAll(client, exclusiveStartKey -> ScanRequest.builder()
                        .tableName(RoleRepositoryTable.TABLE_NAME)
                        .filterExpression("begins_with(" + RoleRepositoryTable.ATTR_PK + ", :prefijo)")
                        .expressionAttributeValues(Map.of(
                                ":prefijo", AttributeValue.fromS(RoleRepositoryTable.catalogPkPrefix(tipo.name()))))
                        .exclusiveStartKey(exclusiveStartKey)
                        .build())
                .onItem().<T>transformToMulti(items -> Multi.createFrom()
                        .iterable(items.stream().map(this::aDominio).toList()));
    }

    @Override
    public Uni<T> crear(String nombre) {
        return siguienteCodigo()
                .chain(codigo -> guardar(codigo, nombre, "attribute_not_exists(" + RoleRepositoryTable.ATTR_PK + ")"));
    }

    @Override
    public Uni<T> actualizar(String codigo, String nombre) {
        return guardar(codigo, nombre, "attribute_exists(" + RoleRepositoryTable.ATTR_PK + ")")
                .onFailure(ConditionalCheckFailedException.class)
                .transform(failure -> new CatalogoNoEncontradoException(tipo, codigo));
    }

    @Override
    public Uni<Void> eliminar(String codigo) {
        return Uni.createFrom()
                .completionStage(() -> client.deleteItem(DeleteItemRequest.builder()
                        .tableName(RoleRepositoryTable.TABLE_NAME)
                        .key(clave(codigo))
                        .conditionExpression("attribute_exists(" + RoleRepositoryTable.ATTR_PK + ")")
                        .build()))
                .onFailure(ConditionalCheckFailedException.class)
                .transform(failure -> new CatalogoNoEncontradoException(tipo, codigo))
                .replaceWithVoid();
    }

    private Uni<T> guardar(String codigo, String nombre, String conditionExpression) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put(RoleRepositoryTable.ATTR_PK, AttributeValue.fromS(RoleRepositoryTable.catalogPk(tipo.name(), codigo)));
        item.put(RoleRepositoryTable.ATTR_SK, AttributeValue.fromS(RoleRepositoryTable.SK_METADATA));
        item.put(RoleRepositoryTable.ATTR_TIPO, AttributeValue.fromS(tipo.name()));
        item.put(RoleRepositoryTable.ATTR_CODIGO, AttributeValue.fromS(codigo));
        item.put(RoleRepositoryTable.ATTR_NOMBRE, AttributeValue.fromS(nombre));

        return Uni.createFrom()
                .completionStage(() -> client.putItem(PutItemRequest.builder()
                        .tableName(RoleRepositoryTable.TABLE_NAME)
                        .item(item)
                        .conditionExpression(conditionExpression)
                        .build()))
                .onItem().transform(response -> mapper.construir(codigo, nombre));
    }

    /**
     * Siguiente código numérico (como String) para una alta nueva, vía
     * contador atómico DynamoDB ({@code UpdateItem ADD valor :1}) -- decisión
     * documentada en {@code progress/current.md} y avalada por
     * {@code ../database/docs/architecture.md} §"Generación de IDs" (opción
     * de contador atómico para continuidad de formato numérico en altas
     * nuevas, a cargo de este backend).
     */
    private Uni<String> siguienteCodigo() {
        Map<String, AttributeValue> key = Map.of(
                RoleRepositoryTable.ATTR_PK, AttributeValue.fromS(RoleRepositoryTable.counterPk(tipo.name())),
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

    private Map<String, AttributeValue> clave(String codigo) {
        return Map.of(
                RoleRepositoryTable.ATTR_PK, AttributeValue.fromS(RoleRepositoryTable.catalogPk(tipo.name(), codigo)),
                RoleRepositoryTable.ATTR_SK, AttributeValue.fromS(RoleRepositoryTable.SK_METADATA));
    }

    private T aDominio(Map<String, AttributeValue> item) {
        return mapper.construir(
                item.get(RoleRepositoryTable.ATTR_CODIGO).s(),
                item.get(RoleRepositoryTable.ATTR_NOMBRE).s());
    }
}
