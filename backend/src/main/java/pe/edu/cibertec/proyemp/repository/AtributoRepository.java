package pe.edu.cibertec.proyemp.repository;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import pe.edu.cibertec.proyemp.domain.Atributo;
import pe.edu.cibertec.proyemp.domain.CategoriaSuministro;
import pe.edu.cibertec.proyemp.domain.EstadoAtributo;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Repositorio DynamoDB de {@link Atributo} (fila combinada Atributo+Detalle)
 * -- Patrones 2, 3 y 4 del esquema canónico, todos vía {@code GSI1}, nunca
 * {@code Scan} (docs/conventions.md: "aquí SÍ hay una clave de índice
 * adecuada"). No implementa CRUD completo -- solo {@code crear} (para
 * poblar datos de prueba) y las 3 consultas de lectura reales del legacy.
 */
@ApplicationScoped
public class AtributoRepository {

    private static final String COUNTER_ATRIBUTO = "ATRIBUTO";
    private static final String COUNTER_DETALLE = "DETALLE";

    /** {@code order by r.c_tipo, d.d_parametro desc} del JPQL legado. */
    private static final Comparator<Atributo> ORDEN_LEGADO = Comparator
            .comparing(Atributo::rolTipo, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(a -> a.categoria().name(), Comparator.reverseOrder());

    private final DynamoDbAsyncClient client;

    @Inject
    public AtributoRepository(DynamoDbAsyncClient client) {
        this.client = client;
    }

    public Uni<Atributo> crear(Atributo datosSinId) {
        return siguienteId(COUNTER_ATRIBUTO)
                .chain(idAtributo -> siguienteId(COUNTER_DETALLE)
                        .chain(idDetalle -> guardar(conIds(datosSinId, idAtributo, idDetalle))));
    }

    /** Patrones 2/3 ({@code AtributoRepository.findAppArea}): solo GSI1PK. */
    public Multi<Atributo> listarPorAplicacionArea(String idAplicacion, String idArea) {
        return consultarOrdenado(exclusiveStartKey -> QueryRequest.builder()
                .tableName(RoleRepositoryTable.TABLE_NAME)
                .indexName(RoleRepositoryTable.GSI1_NAME)
                .keyConditionExpression(RoleRepositoryTable.ATTR_GSI1PK + " = :gsi1pk")
                .filterExpression(RoleRepositoryTable.ATTR_ESTADO + " = :activo")
                .expressionAttributeValues(Map.of(
                        ":gsi1pk", AttributeValue.fromS(RoleRepositoryTable.gsi1PkAplicacionArea(idAplicacion, idArea)),
                        ":activo", AttributeValue.fromS(EstadoAtributo.ACTIVO.name())))
                .exclusiveStartKey(exclusiveStartKey)
                .build());
    }

    /** Patrón 4 ({@code AtributoRepository.findAppArea2}): GSI1PK + begins_with(GSI1SK, "CARGO#<id>"). */
    public Multi<Atributo> listarPorAplicacionAreaCargo(String idAplicacion, String idArea, String idCargo) {
        return consultarOrdenado(exclusiveStartKey -> QueryRequest.builder()
                .tableName(RoleRepositoryTable.TABLE_NAME)
                .indexName(RoleRepositoryTable.GSI1_NAME)
                .keyConditionExpression(RoleRepositoryTable.ATTR_GSI1PK + " = :gsi1pk and begins_with("
                        + RoleRepositoryTable.ATTR_GSI1SK + ", :cargoPrefijo)")
                .filterExpression(RoleRepositoryTable.ATTR_ESTADO + " = :activo")
                .expressionAttributeValues(Map.of(
                        ":gsi1pk", AttributeValue.fromS(RoleRepositoryTable.gsi1PkAplicacionArea(idAplicacion, idArea)),
                        ":cargoPrefijo", AttributeValue.fromS(RoleRepositoryTable.gsi1SkCargoPrefix(idCargo)),
                        ":activo", AttributeValue.fromS(EstadoAtributo.ACTIVO.name())))
                .exclusiveStartKey(exclusiveStartKey)
                .build());
    }

    /**
     * Las 4 categorías de suministro (Roles/Grupos/Inbox/DefaultInbox),
     * paridad con {@code DetalleRepository.ListarSuministroAtributoxParametro}.
     * Reutiliza GSI1 (requiere {@code idArea}, resuelto por el servicio a
     * partir del {@code Rol}) con un filtro adicional por rol y categoría --
     * sigue siendo una Query sobre índice, no un Scan.
     */
    public Multi<Atributo> listarPorRolYCategoria(String idAplicacion, String idArea, String idRol,
            CategoriaSuministro categoria) {
        return consultarOrdenado(exclusiveStartKey -> QueryRequest.builder()
                .tableName(RoleRepositoryTable.TABLE_NAME)
                .indexName(RoleRepositoryTable.GSI1_NAME)
                .keyConditionExpression(RoleRepositoryTable.ATTR_GSI1PK + " = :gsi1pk")
                .filterExpression(RoleRepositoryTable.ATTR_ESTADO + " = :activo and "
                        + RoleRepositoryTable.ATTR_ROL_ID + " = :rolId and "
                        + RoleRepositoryTable.ATTR_CATEGORIA + " = :categoria")
                .expressionAttributeValues(Map.of(
                        ":gsi1pk", AttributeValue.fromS(RoleRepositoryTable.gsi1PkAplicacionArea(idAplicacion, idArea)),
                        ":activo", AttributeValue.fromS(EstadoAtributo.ACTIVO.name()),
                        ":rolId", AttributeValue.fromS(idRol),
                        ":categoria", AttributeValue.fromS(categoria.name())))
                .exclusiveStartKey(exclusiveStartKey)
                .build());
    }

    /**
     * Pagina el {@code Query} completo (feature 12 no lo cubrio, bug real
     * encontrado el 2026-08-19 migrando datos legado reales -- ver Javadoc
     * de {@link DynamoQuerySupport}) antes de ordenar.
     */
    private Multi<Atributo> consultarOrdenado(
            java.util.function.Function<Map<String, AttributeValue>, QueryRequest> requestForKey) {
        return DynamoQuerySupport.queryAll(client, requestForKey)
                .onItem().transform(items -> items.stream()
                        .map(this::aDominio)
                        .sorted(ORDEN_LEGADO)
                        .toList())
                .onItem().transformToMulti(Multi.createFrom()::iterable);
    }

    private Atributo conIds(Atributo datos, String idAtributo, String idDetalle) {
        return new Atributo(idAtributo, idDetalle, datos.categoria(), datos.aplicacionCodigo(), datos.areaCodigo(),
                datos.cargoCodigo(), datos.cargoNombre(), datos.rolId(), datos.rolTipo(), datos.rolNombre(),
                datos.suministroId(), datos.atributoCodigo(), datos.atributoNombre(), datos.estado());
    }

    private Uni<Atributo> guardar(Atributo atributo) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put(RoleRepositoryTable.ATTR_PK, AttributeValue.fromS(RoleRepositoryTable.atributoPk(atributo.id())));
        item.put(RoleRepositoryTable.ATTR_SK, AttributeValue.fromS(
                RoleRepositoryTable.detalleSk(atributo.idDetalleAtributo(), atributo.categoria().name())));
        item.put(RoleRepositoryTable.ATTR_GSI1PK, AttributeValue.fromS(
                RoleRepositoryTable.gsi1PkAplicacionArea(atributo.aplicacionCodigo(), atributo.areaCodigo())));
        item.put(RoleRepositoryTable.ATTR_GSI1SK, AttributeValue.fromS(
                RoleRepositoryTable.gsi1SkCargoRol(atributo.cargoCodigo(), atributo.rolId())));
        item.put(RoleRepositoryTable.ATTR_ID, AttributeValue.fromS(atributo.id()));
        item.put(RoleRepositoryTable.ATTR_DETALLE_ID, AttributeValue.fromS(atributo.idDetalleAtributo()));
        item.put(RoleRepositoryTable.ATTR_CATEGORIA, AttributeValue.fromS(atributo.categoria().name()));
        item.put(RoleRepositoryTable.ATTR_APLICACION_CODIGO, AttributeValue.fromS(atributo.aplicacionCodigo()));
        item.put(RoleRepositoryTable.ATTR_AREA_CODIGO, AttributeValue.fromS(atributo.areaCodigo()));
        item.put(RoleRepositoryTable.ATTR_CARGO_CODIGO, AttributeValue.fromS(atributo.cargoCodigo()));
        item.put(RoleRepositoryTable.ATTR_CARGO_NOMBRE, AttributeValue.fromS(atributo.cargoNombre()));
        item.put(RoleRepositoryTable.ATTR_ROL_ID, AttributeValue.fromS(atributo.rolId()));
        item.put(RoleRepositoryTable.ATTR_ROL_TIPO, AttributeValue.fromS(atributo.rolTipo()));
        item.put(RoleRepositoryTable.ATTR_ROL_NOMBRE, AttributeValue.fromS(atributo.rolNombre()));
        item.put(RoleRepositoryTable.ATTR_SUMINISTRO_ID, AttributeValue.fromS(atributo.suministroId()));
        item.put(RoleRepositoryTable.ATTR_ATRIBUTO_CODIGO, AttributeValue.fromS(atributo.atributoCodigo()));
        item.put(RoleRepositoryTable.ATTR_ATRIBUTO_NOMBRE, AttributeValue.fromS(atributo.atributoNombre()));
        item.put(RoleRepositoryTable.ATTR_ESTADO, AttributeValue.fromS(atributo.estado().name()));

        return Uni.createFrom()
                .completionStage(() -> client.putItem(PutItemRequest.builder()
                        .tableName(RoleRepositoryTable.TABLE_NAME)
                        .item(item)
                        .build()))
                .onItem().transform(response -> atributo);
    }

    private Uni<String> siguienteId(String tipoContador) {
        Map<String, AttributeValue> key = Map.of(
                RoleRepositoryTable.ATTR_PK, AttributeValue.fromS(RoleRepositoryTable.counterPk(tipoContador)),
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

    private Atributo aDominio(Map<String, AttributeValue> item) {
        return new Atributo(
                item.get(RoleRepositoryTable.ATTR_ID).s(),
                item.get(RoleRepositoryTable.ATTR_DETALLE_ID).s(),
                CategoriaSuministro.valueOf(item.get(RoleRepositoryTable.ATTR_CATEGORIA).s()),
                item.get(RoleRepositoryTable.ATTR_APLICACION_CODIGO).s(),
                item.get(RoleRepositoryTable.ATTR_AREA_CODIGO).s(),
                item.get(RoleRepositoryTable.ATTR_CARGO_CODIGO).s(),
                item.get(RoleRepositoryTable.ATTR_CARGO_NOMBRE).s(),
                item.get(RoleRepositoryTable.ATTR_ROL_ID).s(),
                item.get(RoleRepositoryTable.ATTR_ROL_TIPO).s(),
                item.get(RoleRepositoryTable.ATTR_ROL_NOMBRE).s(),
                item.get(RoleRepositoryTable.ATTR_SUMINISTRO_ID).s(),
                item.get(RoleRepositoryTable.ATTR_ATRIBUTO_CODIGO).s(),
                item.get(RoleRepositoryTable.ATTR_ATRIBUTO_NOMBRE).s(),
                EstadoAtributo.valueOf(item.get(RoleRepositoryTable.ATTR_ESTADO).s()));
    }
}
