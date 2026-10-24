package pe.edu.cibertec.proyemp.infra.health;

import io.smallrye.health.api.AsyncHealthCheck;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.ListTablesRequest;

import java.time.Duration;

/**
 * Readiness check de DynamoDB (LocalStack en dev/test) -- feature 12
 * ({@code reactive_resilience_observability}), acceptance 1: "GET
 * /q/health/ready reporta DOWN si LocalStack/Kafka no están disponibles, UP
 * si lo están".
 *
 * <p>El check en sí es una llamada mínima y barata ({@code ListTables} con
 * {@code limit(1)}, no depende de que ninguna tabla concreta exista todavía)
 * -- basta con que el {@link DynamoDbAsyncClient} pueda completar una
 * llamada real contra el endpoint configurado para confirmar que DynamoDB
 * responde. Implementa {@link AsyncHealthCheck} (SmallRye Health, no el
 * {@code HealthCheck} síncrono de MicroProfile) para devolver
 * {@code Uni<HealthCheckResponse>} y mantenerse 100% reactivo -- coherente
 * con docs/conventions.md ("ningún tipo de retorno público bloqueante"; un
 * {@code HealthCheck} síncrono forzaría bloquear un hilo esperando la
 * respuesta de DynamoDB, exactamente lo que se evita en todo el resto del
 * proyecto).
 *
 * <p><b>Por qué readiness y no liveness</b> (aunque la descripción de la
 * feature mencione "readiness/liveness" para las tres dependencias): un
 * chequeo de liveness solo debería reflejar si el proceso interno sigue
 * vivo (ya cubierto por {@code pe.edu.cibertec.proyemp.MyLivenessCheck}) --
 * atar la liveness a una dependencia externa como DynamoDB haría que
 * Kubernetes reinicie el pod cada vez que LocalStack/DynamoDB tenga un
 * problema transitorio, cuando lo correcto es sacarlo temporalmente del
 * balanceo (readiness) sin matar el proceso. Práctica estándar documentada
 * en docs/architecture.md, sección de esta feature.
 */
@Readiness
@ApplicationScoped
public class DynamoDbReadinessCheck implements AsyncHealthCheck {

    private static final String NOMBRE = "DynamoDB (LocalStack)";
    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final DynamoDbAsyncClient client;

    @Inject
    public DynamoDbReadinessCheck(DynamoDbAsyncClient client) {
        this.client = client;
    }

    @Override
    public Uni<HealthCheckResponse> call() {
        return Uni.createFrom()
                .completionStage(() -> client.listTables(ListTablesRequest.builder().limit(1).build()))
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
