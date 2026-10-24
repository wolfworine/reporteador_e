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
import software.amazon.awssdk.services.secretsmanager.SecretsManagerAsyncClient;

import java.net.URI;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Feature 12 ({@code reactive_resilience_observability}), acceptance 1,
 * parte de Secrets Manager. Mismo diseño y mismas razones que
 * {@code DynamoDbReadinessCheckTest} (ver su Javadoc): caso UP contra el
 * LocalStack real de Dev Services vía el bean inyectado, caso DOWN con un
 * {@link SecretsManagerAsyncClient} standalone apuntado a un puerto
 * inalcanzable, sin tocar el cliente de la aplicación ni arriesgar el
 * arranque de {@code AppSecretsBootstrap} (feature 11).
 */
@QuarkusTest
class SecretsManagerReadinessCheckTest {

    // @Readiness es un qualifier CDI (no solo un marcador) -- el bean
    // SecretsManagerReadinessCheck lo declara, así que el punto de inyección
    // debe repetirlo (sin esto, Arc no encuentra el bean por qualifiers).
    @Inject
    @Readiness
    SecretsManagerReadinessCheck readinessCheck;

    @Test
    void should_reportUp_when_secretsManagerRespondsThroughLocalStack() {
        HealthCheckResponse respuesta = readinessCheck.call().await().atMost(Duration.ofSeconds(10));

        assertEquals(HealthCheckResponse.Status.UP, respuesta.getStatus());
    }

    @Test
    void should_reportDown_when_secretsManagerEndpointIsUnreachable() {
        try (SecretsManagerAsyncClient clienteInalcanzable = SecretsManagerAsyncClient.builder()
                .region(Region.US_EAST_1)
                .endpointOverride(URI.create("http://127.0.0.1:1"))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallTimeout(Duration.ofSeconds(2))
                        .apiCallAttemptTimeout(Duration.ofSeconds(2))
                        .build())
                .build()) {
            SecretsManagerReadinessCheck checkInalcanzable = new SecretsManagerReadinessCheck(clienteInalcanzable);

            HealthCheckResponse respuesta = checkInalcanzable.call().await().atMost(Duration.ofSeconds(10));

            assertEquals(HealthCheckResponse.Status.DOWN, respuesta.getStatus());
            assertNotNull(respuesta.getData().orElse(null),
                    "la respuesta DOWN deberia traer datos explicando el fallo, no un DOWN silencioso");
        }
    }
}
