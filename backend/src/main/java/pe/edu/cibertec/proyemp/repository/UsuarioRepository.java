package pe.edu.cibertec.proyemp.repository;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import pe.edu.cibertec.proyemp.domain.EmailDuplicadoException;
import pe.edu.cibertec.proyemp.domain.TipoUsuario;
import pe.edu.cibertec.proyemp.domain.Usuario;
import pe.edu.cibertec.proyemp.domain.UsuarioNoEncontradoException;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.Delete;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.util.HashMap;
import java.util.Map;

/**
 * Repositorio DynamoDB de {@link Usuario} -- decisión de diseño completa
 * (por qué {@code Login} del legacy se fusiona en esta entidad, y por qué
 * existe una fila puntero de email) documentada en {@link Usuario} y en
 * {@code progress/current.md}.
 *
 * <p>Persiste dos filas por Usuario en la tabla única {@code RoleRepository}:
 * <ul>
 *   <li>{@code PK=USUARIO#<id>}, {@code SK=METADATA}: datos completos.</li>
 *   <li>{@code PK=EMAIL#<email-minusculas>}, {@code SK=METADATA}: puntero
 *       ({@code id}) usado por el login para resolver el email con un
 *       {@code GetItem} directo, y por el alta para hacer cumplir unicidad
 *       de email de forma atómica vía {@code TransactWriteItems}.</li>
 * </ul>
 */
@ApplicationScoped
public class UsuarioRepository {

    private static final String COUNTER_TIPO = "USUARIO";

    private final DynamoDbAsyncClient client;

    @Inject
    public UsuarioRepository(DynamoDbAsyncClient client) {
        this.client = client;
    }

    /**
     * Alta atómica: genera el siguiente id del contador
     * {@code COUNTER#USUARIO} y escribe ambas filas (datos + puntero de
     * email) en una única {@code TransactWriteItems} con
     * {@code attribute_not_exists(pk)} en las dos -- si el email ya está
     * registrado, la transacción completa se cancela y no queda ninguna fila
     * huérfana (a diferencia de dos {@code PutItem} independientes).
     */
    public Uni<Usuario> crear(Usuario datos) {
        return siguienteId().chain(id -> guardarNuevo(conId(datos, id)));
    }

    public Uni<Usuario> buscarPorId(String id) {
        return Uni.createFrom()
                .completionStage(() -> client.getItem(GetItemRequest.builder()
                        .tableName(RoleRepositoryTable.TABLE_NAME)
                        .key(claveUsuario(id))
                        .build()))
                .onItem().transform(response -> {
                    if (response.item() == null || response.item().isEmpty()) {
                        throw new UsuarioNoEncontradoException(id);
                    }
                    return aDominio(response.item());
                });
    }

    /**
     * Camino caliente del login ({@code service/AuthService}): un
     * {@code GetItem} directo sobre la fila puntero {@code EMAIL#<email>}
     * seguido de un {@code GetItem} directo sobre {@code USUARIO#<id>}.
     * Nunca un {@code Scan} -- ver la nota de diseño en
     * {@code progress/current.md} sobre por qué esta feature evita el
     * mismo patrón de {@code Scan} filtrado que usan los catálogos/Rol
     * (aceptable ahí, no aceptable en el camino caliente de autenticación).
     */
    public Uni<Usuario> buscarPorEmail(String email) {
        return Uni.createFrom()
                .completionStage(() -> client.getItem(GetItemRequest.builder()
                        .tableName(RoleRepositoryTable.TABLE_NAME)
                        .key(clavePuntero(email))
                        .build()))
                .onItem().transform(response -> {
                    if (response.item() == null || response.item().isEmpty()) {
                        throw new UsuarioNoEncontradoException(email);
                    }
                    return response.item().get(RoleRepositoryTable.ATTR_ID).s();
                })
                .chain(this::buscarPorId);
    }

    /** Elimina ambas filas (datos + puntero de email) de forma atómica. */
    public Uni<Void> eliminar(String id) {
        return buscarPorId(id)
                .chain(usuario -> {
                    TransactWriteItemsRequest request = TransactWriteItemsRequest.builder()
                            .transactItems(
                                    TransactWriteItem.builder()
                                            .delete(Delete.builder()
                                                    .tableName(RoleRepositoryTable.TABLE_NAME)
                                                    .key(claveUsuario(id))
                                                    .conditionExpression(
                                                            "attribute_exists(" + RoleRepositoryTable.ATTR_PK + ")")
                                                    .build())
                                            .build(),
                                    TransactWriteItem.builder()
                                            .delete(Delete.builder()
                                                    .tableName(RoleRepositoryTable.TABLE_NAME)
                                                    .key(clavePuntero(usuario.email()))
                                                    .build())
                                            .build())
                            .build();
                    return Uni.createFrom().completionStage(() -> client.transactWriteItems(request));
                })
                .replaceWithVoid();
    }

    public Multi<Usuario> listarTodos() {
        // Pagina el Scan completo -- ver Javadoc de DynamoScanSupport.
        return DynamoScanSupport.scanAll(client, exclusiveStartKey -> ScanRequest.builder()
                        .tableName(RoleRepositoryTable.TABLE_NAME)
                        .filterExpression("begins_with(" + RoleRepositoryTable.ATTR_PK + ", :prefijo) and "
                                + RoleRepositoryTable.ATTR_SK + " = :metadata")
                        .expressionAttributeValues(Map.of(
                                ":prefijo", AttributeValue.fromS("USUARIO#"),
                                ":metadata", AttributeValue.fromS(RoleRepositoryTable.SK_METADATA)))
                        .exclusiveStartKey(exclusiveStartKey)
                        .build())
                .onItem().<Usuario>transformToMulti(items -> Multi.createFrom()
                        .iterable(items.stream().map(this::aDominio).toList()));
    }

    private Usuario conId(Usuario datos, String id) {
        return new Usuario(id, datos.nombres(), datos.apellidos(), datos.email(), datos.passwordHash(), datos.tipo());
    }

    private Uni<Usuario> guardarNuevo(Usuario usuario) {
        Map<String, AttributeValue> itemUsuario = itemUsuario(usuario);
        Map<String, AttributeValue> itemPuntero = new HashMap<>();
        itemPuntero.put(RoleRepositoryTable.ATTR_PK, AttributeValue.fromS(RoleRepositoryTable.emailPk(usuario.email())));
        itemPuntero.put(RoleRepositoryTable.ATTR_SK, AttributeValue.fromS(RoleRepositoryTable.SK_METADATA));
        itemPuntero.put(RoleRepositoryTable.ATTR_ID, AttributeValue.fromS(usuario.id()));

        TransactWriteItemsRequest request = TransactWriteItemsRequest.builder()
                .transactItems(
                        TransactWriteItem.builder()
                                .put(Put.builder()
                                        .tableName(RoleRepositoryTable.TABLE_NAME)
                                        .item(itemUsuario)
                                        .conditionExpression("attribute_not_exists(" + RoleRepositoryTable.ATTR_PK + ")")
                                        .build())
                                .build(),
                        TransactWriteItem.builder()
                                .put(Put.builder()
                                        .tableName(RoleRepositoryTable.TABLE_NAME)
                                        .item(itemPuntero)
                                        .conditionExpression("attribute_not_exists(" + RoleRepositoryTable.ATTR_PK + ")")
                                        .build())
                                .build())
                .build();

        return Uni.createFrom()
                .completionStage(() -> client.transactWriteItems(request))
                .onItem().transform(response -> usuario)
                .onFailure(TransactionCanceledException.class)
                .transform(failure -> new EmailDuplicadoException(usuario.email()));
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

    private Map<String, AttributeValue> claveUsuario(String id) {
        return Map.of(
                RoleRepositoryTable.ATTR_PK, AttributeValue.fromS(RoleRepositoryTable.usuarioPk(id)),
                RoleRepositoryTable.ATTR_SK, AttributeValue.fromS(RoleRepositoryTable.SK_METADATA));
    }

    private Map<String, AttributeValue> clavePuntero(String email) {
        return Map.of(
                RoleRepositoryTable.ATTR_PK, AttributeValue.fromS(RoleRepositoryTable.emailPk(email)),
                RoleRepositoryTable.ATTR_SK, AttributeValue.fromS(RoleRepositoryTable.SK_METADATA));
    }

    private Map<String, AttributeValue> itemUsuario(Usuario usuario) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put(RoleRepositoryTable.ATTR_PK, AttributeValue.fromS(RoleRepositoryTable.usuarioPk(usuario.id())));
        item.put(RoleRepositoryTable.ATTR_SK, AttributeValue.fromS(RoleRepositoryTable.SK_METADATA));
        item.put(RoleRepositoryTable.ATTR_ID, AttributeValue.fromS(usuario.id()));
        item.put(RoleRepositoryTable.ATTR_NOMBRES, AttributeValue.fromS(usuario.nombres()));
        item.put(RoleRepositoryTable.ATTR_APELLIDOS, AttributeValue.fromS(usuario.apellidos()));
        item.put(RoleRepositoryTable.ATTR_EMAIL, AttributeValue.fromS(usuario.email()));
        item.put(RoleRepositoryTable.ATTR_PASSWORD_HASH, AttributeValue.fromS(usuario.passwordHash()));
        item.put(RoleRepositoryTable.ATTR_TIPO_USUARIO, AttributeValue.fromS(usuario.tipo().name()));
        return item;
    }

    private Usuario aDominio(Map<String, AttributeValue> item) {
        return new Usuario(
                item.get(RoleRepositoryTable.ATTR_ID).s(),
                item.get(RoleRepositoryTable.ATTR_NOMBRES).s(),
                item.get(RoleRepositoryTable.ATTR_APELLIDOS).s(),
                item.get(RoleRepositoryTable.ATTR_EMAIL).s(),
                item.get(RoleRepositoryTable.ATTR_PASSWORD_HASH).s(),
                TipoUsuario.valueOf(item.get(RoleRepositoryTable.ATTR_TIPO_USUARIO).s()));
    }
}
