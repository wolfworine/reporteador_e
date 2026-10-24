package pe.edu.cibertec.proyemp.infra;

import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerAsyncClient;
import software.amazon.awssdk.services.secretsmanager.model.CreateSecretRequest;
import software.amazon.awssdk.services.secretsmanager.model.DeleteSecretRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Smoke test de conectividad genérica contra Secrets Manager emulado por
 * LocalStack (feature 2 de {@code feature_list.json},
 * {@code setup_localstack_dynamodb_secretsmanager}).
 *
 * <p>Guarda y lee un secreto de prueba, {@value #SECRET_NAME}, para
 * verificar que {@link SecretsManagerAsyncClient}, envuelto en {@link Uni}
 * tal como exige docs/conventions.md, funciona contra el LocalStack que
 * Quarkus Dev Services levanta automáticamente para {@code @QuarkusTest}
 * (sin credenciales AWS reales) -- no representa ningún secreto real de la
 * aplicación, eso queda para la feature 11
 * ({@code aws_secrets_manager_integration}, ver
 * {@code infra/AppSecretsBootstrap.java}).
 */
@QuarkusTest
class SecretsManagerConnectivityTest {

    private static final String SECRET_NAME = "test-connectivity-secret";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Inject
    SecretsManagerAsyncClient secretsManagerAsyncClient;

    @AfterEach
    void deleteConnectivityTestSecret() {
        Uni.createFrom()
                .completionStage(() -> secretsManagerAsyncClient.deleteSecret(DeleteSecretRequest.builder()
                        .secretId(SECRET_NAME)
                        .forceDeleteWithoutRecovery(true)
                        .build()))
                .onFailure(ResourceNotFoundException.class).recoverWithNull()
                .await().atMost(TIMEOUT);
    }

    @Test
    void should_readSameSecretValue_whenSecretIsStoredThroughSecretsManagerAsyncClientWrappedInUni() {
        String secretValue = "smoke-test-secret-value";

        Uni<GetSecretValueResponse> createThenRead = Uni.createFrom()
                .completionStage(() -> secretsManagerAsyncClient.createSecret(CreateSecretRequest.builder()
                        .name(SECRET_NAME)
                        .secretString(secretValue)
                        .build()))
                .chain(createResponse -> Uni.createFrom()
                        .completionStage(() -> secretsManagerAsyncClient.getSecretValue(GetSecretValueRequest.builder()
                                .secretId(SECRET_NAME)
                                .build())));

        GetSecretValueResponse response = createThenRead.await().atMost(TIMEOUT);

        assertEquals(secretValue, response.secretString());
    }
}
