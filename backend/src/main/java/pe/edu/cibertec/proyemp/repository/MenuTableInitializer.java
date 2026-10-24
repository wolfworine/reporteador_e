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
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

import java.time.Duration;

/**
 * Crea la tabla **separada** {@code RoleRepositoryMenu} (esquema canónico,
 * Patrón 5 de {@code ../database/docs/architecture.md}) al arranque de la
 * aplicación si todavía no existe -- mismo patrón que
 * {@link RoleRepositoryTableInitializer} para {@code RoleRepository}, pero
 * sin GSI (el Patrón 5 solo necesita {@code pk}/{@code sk}). Idempotente:
 * ignora {@link ResourceInUseException} si la tabla ya existe.
 */
@ApplicationScoped
public class MenuTableInitializer {

    private static final Logger LOG = Logger.getLogger(MenuTableInitializer.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final DynamoDbAsyncClient client;

    @Inject
    public MenuTableInitializer(DynamoDbAsyncClient client) {
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
                .tableName(MenuTable.TABLE_NAME)
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .attributeDefinitions(
                        AttributeDefinition.builder()
                                .attributeName(MenuTable.ATTR_PK)
                                .attributeType(ScalarAttributeType.S)
                                .build(),
                        AttributeDefinition.builder()
                                .attributeName(MenuTable.ATTR_SK)
                                .attributeType(ScalarAttributeType.S)
                                .build())
                .keySchema(
                        KeySchemaElement.builder()
                                .attributeName(MenuTable.ATTR_PK)
                                .keyType(KeyType.HASH)
                                .build(),
                        KeySchemaElement.builder()
                                .attributeName(MenuTable.ATTR_SK)
                                .keyType(KeyType.RANGE)
                                .build())
                .build();

        return Uni.createFrom().completionStage(() -> client.createTable(request))
                .onItem().transform(response -> (Void) null)
                .onFailure(ResourceInUseException.class).recoverWithItem(() -> null)
                .onFailure().invoke(failure -> LOG.warnf(failure,
                        "No se pudo crear/verificar la tabla %s en DynamoDB", MenuTable.TABLE_NAME));
    }
}
