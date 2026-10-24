package com.entel.migration.command;

import com.entel.migration.schema.RoleRepositoryMenuTableDefinition;
import com.entel.migration.schema.RoleRepositoryTableDefinition;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;

import java.io.PrintStream;
import java.net.URI;
import java.util.concurrent.CompletionException;

/**
 * Subcomando {@code design-validate} (feature 2: design_dynamodb_schema).
 *
 * <p>Crea (o valida que ya existan) las dos tablas del diseno canonico de
 * docs/architecture.md: {@code RoleRepository} (con su GSI1, que cubre los
 * patrones 1, 2, 2b, 3 y 4) y {@code RoleRepositoryMenu} (patron 5).
 *
 * <p>Regla dura (docs/architecture.md): el target por defecto es siempre
 * LocalStack, nunca una cuenta AWS real. El endpoint solo se puede
 * sobreescribir explicitamente (variable de entorno {@link
 * #ENV_ENDPOINT_OVERRIDE}, usada por los tests de integracion para apuntar
 * a un contenedor LocalStack efimero, o el constructor que recibe un
 * {@link URI}) - no existe ninguna bandera {@code --target=aws} en esta
 * feature.
 *
 * <p>Idempotente: si una tabla ya existe, {@code createTable} devuelve
 * {@link ResourceInUseException}, que se captura y se trata como exito (no
 * se reintenta crear ni se falla). En ambos casos se espera a que la tabla
 * quede {@code ACTIVE} antes de continuar.
 */
public final class DesignValidateCommand implements MigrationCommand {

    static final String DEFAULT_ENDPOINT = "http://localhost:4566";
    static final String ENV_ENDPOINT_OVERRIDE = "MIGRATION_DYNAMODB_ENDPOINT";
    private static final Region DEFAULT_REGION = Region.US_EAST_1;
    private static final String LOCALSTACK_ACCESS_KEY = "test";
    private static final String LOCALSTACK_SECRET_KEY = "test";

    private final URI endpointOverride;

    /** Constructor usado por la CLI real: apunta a LocalStack por defecto. */
    public DesignValidateCommand() {
        this(null);
    }

    /**
     * Constructor usado por los tests de integracion, que apuntan a un
     * contenedor LocalStack efimero (Testcontainers) en vez del LocalStack
     * fijo de {@code docker-compose.yml}.
     */
    public DesignValidateCommand(URI endpointOverride) {
        this.endpointOverride = endpointOverride;
    }

    @Override
    public int execute(String[] args, PrintStream out, PrintStream err) {
        URI endpoint = resolveEndpoint();
        try (DynamoDbAsyncClient client = buildClient(endpoint)) {
            ensureTable(client, RoleRepositoryTableDefinition.createTableRequest(), out);
            ensureTable(client, RoleRepositoryMenuTableDefinition.createTableRequest(), out);
            out.println("design-validate: OK (endpoint=" + endpoint + ")");
            return 0;
        } catch (RuntimeException e) {
            err.println("design-validate: fallo creando/validando el diseno de tablas: " + describe(e));
            return 1;
        }
    }

    private URI resolveEndpoint() {
        if (endpointOverride != null) {
            return endpointOverride;
        }
        String env = System.getenv(ENV_ENDPOINT_OVERRIDE);
        if (env != null && !env.isBlank()) {
            return URI.create(env);
        }
        return URI.create(DEFAULT_ENDPOINT);
    }

    private DynamoDbAsyncClient buildClient(URI endpoint) {
        return DynamoDbAsyncClient.builder()
                .endpointOverride(endpoint)
                .region(DEFAULT_REGION)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(LOCALSTACK_ACCESS_KEY, LOCALSTACK_SECRET_KEY)))
                .build();
    }

    /**
     * Crea la tabla si no existe; si ya existe (ResourceInUseException),
     * no falla ni la recrea. En ambos casos espera a que quede ACTIVE.
     */
    private void ensureTable(DynamoDbAsyncClient client, CreateTableRequest request, PrintStream out) {
        String tableName = request.tableName();
        try {
            client.createTable(request).join();
            out.println("Tabla '" + tableName + "' creada.");
        } catch (CompletionException ce) {
            if (unwrap(ce) instanceof ResourceInUseException) {
                out.println("Tabla '" + tableName + "' ya existia, no se recrea (idempotente).");
            } else {
                throw ce;
            }
        }
        client.waiter()
                .waitUntilTableExists(DescribeTableRequest.builder().tableName(tableName).build())
                .join();
    }

    private static Throwable unwrap(CompletionException ce) {
        return ce.getCause() != null ? ce.getCause() : ce;
    }

    private static String describe(RuntimeException e) {
        Throwable cause = e instanceof CompletionException ? unwrap((CompletionException) e) : e;
        return cause.getClass().getSimpleName() + ": " + cause.getMessage();
    }
}
