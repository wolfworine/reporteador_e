package com.entel.migration.localstack;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;

import java.net.URI;

/**
 * Utilidades compartidas por los tests de integracion de la feature 2
 * (design_dynamodb_schema) contra LocalStack - nunca contra una cuenta AWS
 * real (docs/conventions.md: "Prohibido apuntar cualquier test a una
 * cuenta AWS real").
 *
 * <p><b>Como se levanta LocalStack para estos tests (decision de esta
 * sesion):</b> se intento primero Testcontainers ({@code
 * org.testcontainers:localstack}), que arranca y gestiona el contenedor
 * automaticamente desde el propio test. En este entorno concreto (Windows,
 * Docker Desktop con el contexto {@code desktop-linux}, invocado desde
 * dentro del sandbox de este agente) Testcontainers/docker-java no logra
 * localizar el daemon Docker via named pipe (falla con {@code
 * IllegalStateException: Could not find a valid Docker environment}),
 * aunque el CLI {@code docker} funciona correctamente en el mismo entorno.
 * Es una limitacion de infraestructura local, no del diseno de los tests.
 *
 * <p>Por eso estos tests de integracion apuntan al servicio {@code
 * localstack} ya definido en {@code docker-compose.yml} (el mismo que usa
 * la feature 1 para su smoke test manual), en vez de un contenedor efimero
 * gestionado desde Java. <b>Requisito para correr estos tests:</b> antes de
 * {@code mvn test} hay que levantar LocalStack manualmente:
 * <pre>
 *   docker compose up -d localstack
 *   # esperar a que quede "healthy", ej.:
 *   docker inspect --format='{{.State.Health.Status}}' entel-migration-localstack
 * </pre>
 * {@code init.sh} automatiza este paso (levanta LocalStack antes de {@code
 * mvn test} y lo apaga al final) - ver ese script para el detalle.
 */
public final class LocalStackIntegrationSupport {

    /** Mismo default que {@link com.entel.migration.command.DesignValidateCommand}. */
    public static final String DEFAULT_ENDPOINT = "http://localhost:4566";

    /** Misma variable de entorno que reconoce {@code DesignValidateCommand}. */
    public static final String ENV_ENDPOINT_OVERRIDE = "MIGRATION_DYNAMODB_ENDPOINT";

    private static final String LOCALSTACK_ACCESS_KEY = "test";
    private static final String LOCALSTACK_SECRET_KEY = "test";

    private LocalStackIntegrationSupport() {
    }

    /**
     * Endpoint del LocalStack contra el que corren los tests: el de
     * {@code docker-compose.yml} por defecto, o el de {@link
     * #ENV_ENDPOINT_OVERRIDE} si esta seteada (nunca AWS real).
     */
    public static URI resolveEndpoint() {
        String env = System.getenv(ENV_ENDPOINT_OVERRIDE);
        if (env != null && !env.isBlank()) {
            return URI.create(env);
        }
        return URI.create(DEFAULT_ENDPOINT);
    }

    public static DynamoDbAsyncClient newDynamoDbClient() {
        return DynamoDbAsyncClient.builder()
                .endpointOverride(resolveEndpoint())
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(LOCALSTACK_ACCESS_KEY, LOCALSTACK_SECRET_KEY)))
                .build();
    }
}
