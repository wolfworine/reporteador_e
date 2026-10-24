package pe.edu.cibertec.proyemp.infra.health;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;

import java.net.URI;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Feature 12 ({@code reactive_resilience_observability}), acceptance 1:
 * "GET /q/health/ready reporta DOWN si LocalStack/Kafka no están
 * disponibles, UP si lo están" -- cubre la parte de DynamoDB.
 *
 * <p>Caso UP: se inyecta el bean real {@link DynamoDbReadinessCheck} tal
 * como lo usa Quarkus en producción, contra el LocalStack real que Quarkus
 * Dev Services levanta para {@code @QuarkusTest} -- mismo patrón que
 * {@code infra/DynamoDbConnectivityTest}.
 *
 * <p>Caso DOWN: en vez de romper el arranque completo de la app apuntando
 * {@code quarkus.dynamodb.endpoint-override} a un puerto inválido (lo cual
 * haría fallar {@code RoleRepositoryTableInitializer}/{@code MenuTableInitializer}/
 * {@code AppSecretsBootstrap} -- sus {@code @Observes StartupEvent} usan
 * {@code .await().atMost(...)} y NO tienen fallback si DynamoDB no responde
 * en absoluto, ver sus Javadoc -- este test construye un
 * {@link DynamoDbReadinessCheck} standalone con su PROPIO
 * {@link DynamoDbAsyncClient} apuntado a un puerto que nadie escucha
 * ({@code http://127.0.0.1:1}), sin pasar por CDI ni afectar al cliente de
 * la aplicación. Prueba el comportamiento real de la clase sin arriesgar el
 * arranque de toda la suite.
 */
@QuarkusTest
class DynamoDbReadinessCheckTest {

    // @Readiness es un qualifier CDI (no solo un marcador) -- el bean
    // DynamoDbReadinessCheck lo declara, así que el punto de inyección debe
    // repetirlo (sin esto, Arc no encuentra el bean por qualifiers).
    @Inject
    @Readiness
    DynamoDbReadinessCheck readinessCheck;

    @Test
    void should_reportUp_when_dynamoDbRespondsThroughLocalStack() {
        HealthCheckResponse respuesta = readinessCheck.call().await().atMost(Duration.ofSeconds(10));

        assertEquals(HealthCheckResponse.Status.UP, respuesta.getStatus());
    }

    @Test
    void should_reportDown_when_dynamoDbEndpointIsUnreachable() {
        try (DynamoDbAsyncClient clienteInalcanzable = DynamoDbAsyncClient.builder()
                .region(Region.US_EAST_1)
                .endpointOverride(URI.create("http://127.0.0.1:1"))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallTimeout(Duration.ofSeconds(2))
                        .apiCallAttemptTimeout(Duration.ofSeconds(2))
                        .build())
                .build()) {
            DynamoDbReadinessCheck checkInalcanzable = new DynamoDbReadinessCheck(clienteInalcanzable);

            HealthCheckResponse respuesta = checkInalcanzable.call().await().atMost(Duration.ofSeconds(10));

            assertEquals(HealthCheckResponse.Status.DOWN, respuesta.getStatus());
            assertNotNull(respuesta.getData().orElse(null),
                    "la respuesta DOWN deberia traer datos explicando el fallo, no un DOWN silencioso");
        }
    }
}
