package pe.edu.cibertec.proyemp.infra;

import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DeleteTableRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Smoke test de conectividad genérica contra LocalStack (feature 2 de
 * {@code feature_list.json}, {@code setup_localstack_dynamodb_secretsmanager}).
 *
 * <p>Usa una tabla de prueba dedicada, {@value #TABLE_NAME}, creada y
 * borrada por el propio test -- <b>no</b> es el esquema de tablas de
 * dominio real (Area, Cargo, Rol, etc.), que depende de que
 * {@code ../database/feature_list.json} -&gt; {@code design_dynamodb_schema}
 * esté {@code done} (ver docs/architecture.md, "Modelo de datos DynamoDB").
 * El único propósito de este test es verificar que
 * {@link DynamoDbAsyncClient}, envuelto en {@link Uni} tal como exige
 * docs/conventions.md, escribe y lee correctamente contra el LocalStack que
 * Quarkus Dev Services levanta automáticamente para {@code @QuarkusTest}
 * (sin credenciales AWS reales).
 */
@QuarkusTest
class DynamoDbConnectivityTest {

    private static final String TABLE_NAME = "TestConnectivityTable";
    private static final String PK_ATTRIBUTE = "pk";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Inject
    DynamoDbAsyncClient dynamoDbAsyncClient;

    @BeforeEach
    void createConnectivityTestTable() {
        Uni.createFrom()
                .completionStage(() -> dynamoDbAsyncClient.createTable(CreateTableRequest.builder()
                        .tableName(TABLE_NAME)
                        .billingMode(BillingMode.PAY_PER_REQUEST)
                        .attributeDefinitions(AttributeDefinition.builder()
                                .attributeName(PK_ATTRIBUTE)
                                .attributeType(ScalarAttributeType.S)
                                .build())
                        .keySchema(KeySchemaElement.builder()
                                .attributeName(PK_ATTRIBUTE)
                                .keyType(KeyType.HASH)
                                .build())
                        .build()))
                .await().atMost(TIMEOUT);
    }

    @AfterEach
    void deleteConnectivityTestTable() {
        Uni.createFrom()
                .completionStage(() -> dynamoDbAsyncClient.deleteTable(DeleteTableRequest.builder()
                        .tableName(TABLE_NAME)
                        .build()))
                .await().atMost(TIMEOUT);
    }

    @Test
    void should_readSameItem_whenItemIsWrittenThroughDynamoDbAsyncClientWrappedInUni() {
        String itemId = "smoke-test-1";
        Map<String, AttributeValue> item = Map.of(
                PK_ATTRIBUTE, AttributeValue.fromS(itemId),
                "payload", AttributeValue.fromS("conectividad-localstack-ok"));

        Uni<GetItemResponse> writeThenRead = Uni.createFrom()
                .completionStage(() -> dynamoDbAsyncClient.putItem(PutItemRequest.builder()
                        .tableName(TABLE_NAME)
                        .item(item)
                        .build()))
                .chain(putResponse -> Uni.createFrom()
                        .completionStage(() -> dynamoDbAsyncClient.getItem(GetItemRequest.builder()
                                .tableName(TABLE_NAME)
                                .key(Map.of(PK_ATTRIBUTE, AttributeValue.fromS(itemId)))
                                .build())));

        GetItemResponse response = writeThenRead.await().atMost(TIMEOUT);

        assertEquals("conectividad-localstack-ok", response.item().get("payload").s());
    }

    @Test
    void should_returnEmptyItem_whenKeyWasNeverWritten() {
        Uni<GetItemResponse> read = Uni.createFrom()
                .completionStage(() -> dynamoDbAsyncClient.getItem(GetItemRequest.builder()
                        .tableName(TABLE_NAME)
                        .key(Map.of(PK_ATTRIBUTE, AttributeValue.fromS("nunca-escrito")))
                        .build()));

        GetItemResponse response = read.await().atMost(TIMEOUT);

        assertNull(response.item().get("payload"));
    }
}
