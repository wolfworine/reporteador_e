package pe.edu.cibertec.proyemp.repository;

import io.quarkus.runtime.StartupEvent;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndex;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.Projection;
import software.amazon.awssdk.services.dynamodb.model.ProjectionType;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

import java.time.Duration;

/**
 * Crea la tabla única {@code RoleRepository} (esquema canónico de
 * {@code ../database/docs/architecture.md}) al arranque de la aplicación si
 * todavía no existe -- ni Dev Services ni LocalStack la crean por defecto,
 * solo levantan el emulador vacío. Idempotente: si la tabla ya existe
 * (creada por una corrida anterior, por el propio track {@code database/},
 * o por otra instancia concurrente), ignora {@link ResourceInUseException}.
 *
 * <p>Se crea con el esquema físico COMPLETO (pk/sk + GSI1) aunque esta
 * feature (catálogos simples, Patrón 1) solo usa pk/sk -- para que la
 * feature 4 (Rol/Suministro/Atributo/Detalle, que sí necesita GSI1) no
 * tenga que alterar una tabla ya en uso por catálogos simples. Decisión
 * documentada en {@code progress/current.md}.
 */
@ApplicationScoped
public class RoleRepositoryTableInitializer {

    private static final Logger LOG = Logger.getLogger(RoleRepositoryTableInitializer.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final DynamoDbAsyncClient client;

    @Inject
    public RoleRepositoryTableInitializer(DynamoDbAsyncClient client) {
        this.client = client;
    }

    /**
     * {@code .await()} está permitido aquí por excepción explícita de
     * docs/conventions.md ("solo se permite en tests o en el
     * main/bootstrap, nunca en código de request") -- este método corre una
     * única vez durante el arranque de Quarkus, antes de que se sirva
     * ninguna request.
     */
    void onStart(@Observes StartupEvent event) {
        crearTablaSiNoExiste().await().atMost(TIMEOUT);
    }

    Uni<Void> crearTablaSiNoExiste() {
        CreateTableRequest request = CreateTableRequest.builder()
                .tableName(RoleRepositoryTable.TABLE_NAME)
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .attributeDefinitions(
                        AttributeDefinition.builder()
                                .attributeName(RoleRepositoryTable.ATTR_PK)
                                .attributeType(ScalarAttributeType.S)
                                .build(),
                        AttributeDefinition.builder()
                                .attributeName(RoleRepositoryTable.ATTR_SK)
                                .attributeType(ScalarAttributeType.S)
                                .build(),
                        AttributeDefinition.builder()
                                .attributeName(RoleRepositoryTable.ATTR_GSI1PK)
                                .attributeType(ScalarAttributeType.S)
                                .build(),
                        AttributeDefinition.builder()
                                .attributeName(RoleRepositoryTable.ATTR_GSI1SK)
                                .attributeType(ScalarAttributeType.S)
                                .build())
                .keySchema(
                        KeySchemaElement.builder()
                                .attributeName(RoleRepositoryTable.ATTR_PK)
                                .keyType(KeyType.HASH)
                                .build(),
                        KeySchemaElement.builder()
                                .attributeName(RoleRepositoryTable.ATTR_SK)
                                .keyType(KeyType.RANGE)
                                .build())
                .globalSecondaryIndexes(GlobalSecondaryIndex.builder()
                        .indexName(RoleRepositoryTable.GSI1_NAME)
                        .keySchema(
                                KeySchemaElement.builder()
                                        .attributeName(RoleRepositoryTable.ATTR_GSI1PK)
                                        .keyType(KeyType.HASH)
                                        .build(),
                                KeySchemaElement.builder()
                                        .attributeName(RoleRepositoryTable.ATTR_GSI1SK)
                                        .keyType(KeyType.RANGE)
                                        .build())
                        .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                        .build())
                .build();

        return Uni.createFrom().completionStage(() -> client.createTable(request))
                .onItem().transform(response -> (Void) null)
                .onFailure(ResourceInUseException.class).recoverWithItem(() -> null)
                .onFailure().invoke(failure -> LOG.warnf(failure,
                        "No se pudo crear/verificar la tabla %s en DynamoDB", RoleRepositoryTable.TABLE_NAME));
    }
}
