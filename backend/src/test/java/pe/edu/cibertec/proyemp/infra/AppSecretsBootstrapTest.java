package pe.edu.cibertec.proyemp.infra;

import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import pe.edu.cibertec.proyemp.repository.RoleRepositoryTable;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerAsyncClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Cubre el acceptance criterion 2 de la feature 11 ({@code
 * aws_secrets_manager_integration} en {@code feature_list.json}): "Al menos
 * un secreto real de la app ... se lee desde Secrets Manager en tiempo de
 * arranque, verificado con un test de integración."
 *
 * <p>{@link AppSecretsBootstrap#onStart} (observador de {@code
 * StartupEvent}) ya corrió -- con una llamada real contra el LocalStack que
 * Quarkus Dev Services levanta para {@code @QuarkusTest} -- antes de que se
 * ejecute cualquier método {@code @Test} de esta clase, porque
 * {@code @QuarkusTest} arranca el contenedor CDI completo (incluidos sus
 * observadores de {@code StartupEvent}) antes de correr ningún test. Esta
 * clase no reactiva el arranque; verifica su resultado.
 */
@QuarkusTest
class AppSecretsBootstrapTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Inject
    AppSecretsBootstrap appSecretsBootstrap;

    @Inject
    SecretsManagerAsyncClient secretsManagerAsyncClient;

    @Test
    void should_resolveDynamoTableNameFromSecretsManager_atStartup() {
        String resuelto = appSecretsBootstrap.dynamoTableNameFromSecretsManager();

        assertNotNull(resuelto,
                "AppSecretsBootstrap deberia haber resuelto el secreto durante @Observes StartupEvent, antes de que corriera este test");
        assertEquals(RoleRepositoryTable.TABLE_NAME, resuelto);
    }

    @Test
    void should_matchValueActuallyStoredInSecretsManager_whenComparedIndependently() {
        // Verificación independiente: consulta Secrets Manager directamente (no reutiliza el
        // resultado cacheado del bean) para confirmar que el valor resuelto en el arranque
        // proviene realmente de un GetSecretValue exitoso contra LocalStack, no de un valor
        // hardcodeado en memoria -- si AppSecretsBootstrap nunca hubiera llamado a Secrets
        // Manager, este secreto no existiría y esta lectura fallaría.
        Uni<GetSecretValueResponse> lectura = Uni.createFrom()
                .completionStage(() -> secretsManagerAsyncClient.getSecretValue(GetSecretValueRequest.builder()
                        .secretId(AppSecretsBootstrap.DYNAMO_TABLE_NAME_SECRET_ID)
                        .build()));

        GetSecretValueResponse respuesta = lectura.await().atMost(TIMEOUT);

        assertEquals(respuesta.secretString(), appSecretsBootstrap.dynamoTableNameFromSecretsManager());
    }

    @Test
    void should_beIdempotent_whenResolvingAgainAfterSecretAlreadyExists() {
        // El secreto ya existe (creado durante el @Observes StartupEvent real de esta misma
        // corrida de @QuarkusTest) -- una segunda resolución manual debe recuperarlo con
        // GetSecretValue directo, sin intentar recrearlo ni fallar por ResourceExistsException.
        String segundaResolucion = appSecretsBootstrap.resolverDynamoTableNameDesdeSecretsManager()
                .await().atMost(TIMEOUT);

        assertEquals(RoleRepositoryTable.TABLE_NAME, segundaResolucion);
    }
}
