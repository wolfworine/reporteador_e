package pe.edu.cibertec.proyemp.repository;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import pe.edu.cibertec.proyemp.domain.EstadoMenu;
import pe.edu.cibertec.proyemp.domain.MenuItem;
import pe.edu.cibertec.proyemp.domain.MenuTipo;
import pe.edu.cibertec.proyemp.domain.TipoUsuario;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.util.HashMap;
import java.util.Map;

/**
 * Repositorio DynamoDB de {@link MenuItem} sobre la tabla separada
 * {@code RoleRepositoryMenu} (Patrón 5 del esquema canónico). No colisiona
 * con {@code repository.MenuRepository} del proyecto legacy JSF -- ese
 * código vive en un árbol fuente completamente distinto
 * ({@code D:\Capacitacion\IA\Migracion_Reporte\...}), no forma parte de
 * este proyecto Quarkus.
 */
@ApplicationScoped
public class MenuRepository {

    private final DynamoDbAsyncClient client;

    @Inject
    public MenuRepository(DynamoDbAsyncClient client) {
        this.client = client;
    }

    /** Alta usada para poblar datos de prueba (no hay acceptance que exija CRUD completo). */
    public Uni<MenuItem> crear(MenuItem datosSinCodigo) {
        return siguienteCodigo().chain(codigo -> guardar(conCodigo(datosSinCodigo, codigo)));
    }

    /**
     * {@code Query} por {@code PK=MENU#<tipo>} (nunca {@code Scan}) con
     * {@code FilterExpression} por {@code tipoUsuario} + estado activo --
     * corrige el acceptance criterion 1 de la feature: a diferencia del
     * legacy (filtro asimétrico -- el Operador sí filtraba de verdad, el
     * Administrador y cualquier otro tipo NO, ver
     * {@code MenuController.establecerPermisos()} y
     * {@code progress/current.md}), este método SIEMPRE filtra de forma
     * simétrica por el {@code tipoUsuario} recibido, sin excepción por
     * valor.
     */
    public Multi<MenuItem> listarPorTipoYTipoUsuario(MenuTipo tipo, TipoUsuario tipoUsuario) {
        QueryRequest request = QueryRequest.builder()
                .tableName(MenuTable.TABLE_NAME)
                .keyConditionExpression(MenuTable.ATTR_PK + " = :pk")
                .filterExpression(MenuTable.ATTR_TIPO_USUARIO + " = :tipoUsuario and "
                        + MenuTable.ATTR_ESTADO + " = :activo")
                .expressionAttributeValues(Map.of(
                        ":pk", AttributeValue.fromS(MenuTable.menuPk(tipo)),
                        ":tipoUsuario", AttributeValue.fromS(tipoUsuario.name()),
                        ":activo", AttributeValue.fromS(EstadoMenu.ACTIVO.name())))
                .build();

        return Uni.createFrom()
                .completionStage(() -> client.query(request))
                .onItem().<MenuItem>transformToMulti(response -> Multi.createFrom()
                        .iterable(response.items().stream().map(this::aDominio).toList()));
    }

    private Uni<String> siguienteCodigo() {
        Map<String, AttributeValue> key = Map.of(
                MenuTable.ATTR_PK, AttributeValue.fromS(MenuTable.counterPk()),
                MenuTable.ATTR_SK, AttributeValue.fromS(MenuTable.SK_METADATA));

        return Uni.createFrom()
                .completionStage(() -> client.updateItem(UpdateItemRequest.builder()
                        .tableName(MenuTable.TABLE_NAME)
                        .key(key)
                        .updateExpression("ADD " + MenuTable.ATTR_VALOR + " :incremento")
                        .expressionAttributeValues(Map.of(":incremento", AttributeValue.fromN("1")))
                        .returnValues(ReturnValue.UPDATED_NEW)
                        .build()))
                .onItem().transform(response -> response.attributes().get(MenuTable.ATTR_VALOR).n());
    }

    private MenuItem conCodigo(MenuItem datos, String codigo) {
        return new MenuItem(codigo, datos.nombre(), datos.tipo(), datos.tipoUsuario(), datos.codSubmenu(),
                datos.estado(), datos.action());
    }

    private Uni<MenuItem> guardar(MenuItem item) {
        Map<String, AttributeValue> attrs = new HashMap<>();
        attrs.put(MenuTable.ATTR_PK, AttributeValue.fromS(MenuTable.menuPk(item.tipo())));
        attrs.put(MenuTable.ATTR_SK, AttributeValue.fromS(MenuTable.ordenSk(item.codigo())));
        attrs.put(MenuTable.ATTR_CODIGO, AttributeValue.fromS(item.codigo()));
        attrs.put(MenuTable.ATTR_NOMBRE, AttributeValue.fromS(item.nombre()));
        attrs.put(MenuTable.ATTR_TIPO, AttributeValue.fromS(item.tipo().codigoLegado()));
        attrs.put(MenuTable.ATTR_TIPO_USUARIO, AttributeValue.fromS(item.tipoUsuario().name()));
        attrs.put(MenuTable.ATTR_ESTADO, AttributeValue.fromS(item.estado().name()));
        if (item.codSubmenu() != null) {
            attrs.put(MenuTable.ATTR_COD_SUBMENU, AttributeValue.fromS(item.codSubmenu()));
        }
        if (item.action() != null) {
            attrs.put(MenuTable.ATTR_ACTION, AttributeValue.fromS(item.action()));
        }

        return Uni.createFrom()
                .completionStage(() -> client.putItem(PutItemRequest.builder()
                        .tableName(MenuTable.TABLE_NAME)
                        .item(attrs)
                        .build()))
                .onItem().transform(response -> item);
    }

    private MenuItem aDominio(Map<String, AttributeValue> item) {
        AttributeValue codSubmenu = item.get(MenuTable.ATTR_COD_SUBMENU);
        AttributeValue action = item.get(MenuTable.ATTR_ACTION);
        return new MenuItem(
                item.get(MenuTable.ATTR_CODIGO).s(),
                item.get(MenuTable.ATTR_NOMBRE).s(),
                item.get(MenuTable.ATTR_TIPO).s().equals(MenuTipo.SUBMENU.codigoLegado())
                        ? MenuTipo.SUBMENU
                        : MenuTipo.ITEM,
                TipoUsuario.valueOf(item.get(MenuTable.ATTR_TIPO_USUARIO).s()),
                codSubmenu == null ? null : codSubmenu.s(),
                EstadoMenu.valueOf(item.get(MenuTable.ATTR_ESTADO).s()),
                action == null ? null : action.s());
    }
}
