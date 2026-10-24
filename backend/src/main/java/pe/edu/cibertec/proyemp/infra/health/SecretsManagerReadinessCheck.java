package pe.edu.cibertec.proyemp.infra.health;

import io.smallrye.health.api.AsyncHealthCheck;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerAsyncClient;
import software.amazon.awssdk.services.secretsmanager.model.ListSecretsRequest;

import java.time.Duration;

/**
 * Readiness check de AWS Secrets Manager (LocalStack en dev/test) -- feature
 * 12 ({@code reactive_resilience_observability}), acceptance 1. Mismo
 * diseño y mismas razones que {@link DynamoDbReadinessCheck} (ver su
 * Javadoc): llamada mínima y barata ({@code ListSecrets} con
 * {@code maxResults(1)}), {@link AsyncHealthCheck} reactivo, readiness (no
 * liveness) a propósito.
 *
 * <p>Complementa a {@code infra/AppSecretsBootstrap} (feature 11): ese bean
 * solo corre una vez al arranque; este check confirma en cada poll de
 * readiness que Secrets Manager sigue respondiendo durante toda la vida del
 * proceso, no solo al inicio.
 */
@Readiness
@ApplicationScoped
public class SecretsManagerReadinessCheck implements AsyncHealthCheck {

    private static final String NOMBRE = "AWS Secrets Manager (LocalStack)";
    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final SecretsManagerAsyncClient client;

    @Inject
    public SecretsManagerReadinessCheck(SecretsManagerAsyncClient client) {
        this.client = client;
    }

    @Override
    public Uni<HealthCheckResponse> call() {
        return Uni.createFrom()
                .completionStage(() -> client.listSecrets(ListSecretsRequest.builder().maxResults(1).build()))
                .onItem().transform(response -> HealthCheckResponse.up(NOMBRE))
                .ifNoItem().after(TIMEOUT)
                .recoverWithItem(() -> down("Tiempo de espera agotado tras " + TIMEOUT.getSeconds() + "s"))
                .onFailure().recoverWithItem(failure -> down(mensajeDe(failure)));
    }

    private static String mensajeDe(Throwable failure) {
        return failure.getMessage() != null ? failure.getMessage() : failure.getClass().getSimpleName();
    }

    private static HealthCheckResponse down(String detalle) {
        return HealthCheckResponse.named(NOMBRE).down().withData("detalle", detalle).build();
    }
}
