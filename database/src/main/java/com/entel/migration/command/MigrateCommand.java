package com.entel.migration.command;

import com.entel.migration.dump.MysqlDumpParseException;
import com.entel.migration.dump.MysqlDumpParser;
import com.entel.migration.dump.ParsedDump;
import com.entel.migration.dynamo.DynamoBatchWriter;
import com.entel.migration.schema.TableNames;
import com.entel.migration.transform.RoleRepositoryTransformer;
import com.entel.migration.transform.TransformResult;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;

import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletionException;

/**
 * Subcomando {@code migrate} (feature 4: migrate_data_mysql_to_dynamodb).
 *
 * <p>Uso: {@code migrate <ruta-al-dump.sql> [--target=localstack]}. Lee el
 * dump con {@link MysqlDumpParser} (feature 3), lo transforma con
 * {@link RoleRepositoryTransformer} (feature 4, misma clase que documenta
 * todas las decisiones de mapeo) siguiendo el diseno canonico de
 * docs/architecture.md (feature 2), asegura que las tablas existan (reusa
 * {@link DesignValidateCommand}, sin duplicar su logica de creacion
 * idempotente) y escribe los items con {@link DynamoBatchWriter}
 * ({@code BatchWriteItem} en lotes de 25, idempotente: correr {@code migrate}
 * dos veces sobreescribe los mismos items, nunca los duplica, porque la
 * PK/SK es siempre la misma para la misma fila de origen).
 *
 * <p><b>Regla dura (docs/architecture.md, docs/conventions.md): el target
 * por defecto es siempre LocalStack, nunca una cuenta AWS real.</b> El
 * unico target soportado explicitamente es {@code localstack} (default si
 * no se pasa {@code --target}). Si se pasa {@code --target=aws}, el
 * subcomando NO intenta conectarse a AWS real bajo ninguna circunstancia:
 * falla explicitamente con un mensaje claro y codigo de salida distinto de
 * cero (no hay ninguna ruta de codigo, ni siquiera detras de este flag, que
 * abra un cliente contra una cuenta AWS real) - ver acceptance criteria de
 * la feature 4 y docs/verification.md ("El flag --target=aws (si existe)
 * nunca se ejecuta en CI/tests automatizados"). Ningun test de este
 * proyecto pasa {@code --target=aws}.
 */
public final class MigrateCommand implements MigrationCommand {

    static final String DEFAULT_ENDPOINT = "http://localhost:4566";
    static final String ENV_ENDPOINT_OVERRIDE = "MIGRATION_DYNAMODB_ENDPOINT";
    private static final Region DEFAULT_REGION = Region.US_EAST_1;
    private static final String LOCALSTACK_ACCESS_KEY = "test";
    private static final String LOCALSTACK_SECRET_KEY = "test";
    private static final String TARGET_FLAG_PREFIX = "--target=";
    private static final String TARGET_LOCALSTACK = "localstack";
    private static final String TARGET_AWS = "aws";

    private final URI endpointOverride;

    /** Constructor usado por la CLI real: apunta a LocalStack por defecto. */
    public MigrateCommand() {
        this(null);
    }

    /** Constructor usado por los tests de integracion, igual que {@link DesignValidateCommand#DesignValidateCommand(URI)}. */
    public MigrateCommand(URI endpointOverride) {
        this.endpointOverride = endpointOverride;
    }

    @Override
    public int execute(String[] args, PrintStream out, PrintStream err) {
        String dumpPathArg = null;
        String target = TARGET_LOCALSTACK;
        for (String arg : args) {
            if (arg.startsWith(TARGET_FLAG_PREFIX)) {
                target = arg.substring(TARGET_FLAG_PREFIX.length());
            } else if (dumpPathArg == null) {
                dumpPathArg = arg;
            }
        }

        if (dumpPathArg == null || dumpPathArg.isBlank()) {
            err.println("Error: uso: migrate <ruta-al-dump.sql> [--target=localstack]");
            return 1;
        }

        if (!TARGET_LOCALSTACK.equals(target)) {
            if (TARGET_AWS.equals(target)) {
                err.println("Error: --target=aws no esta soportado por esta herramienta. "
                        + "Regla dura de docs/architecture.md: la migracion nunca se conecta a una cuenta AWS real. "
                        + "Usa el target por defecto (LocalStack).");
            } else {
                err.println("Error: target desconocido '" + target + "'. Unico valor soportado: '" + TARGET_LOCALSTACK + "'.");
            }
            return 1;
        }

        Path path = Path.of(dumpPathArg);
        if (!Files.isRegularFile(path)) {
            err.println("Error: no existe el archivo '" + dumpPathArg + "'.");
            return 1;
        }

        ParsedDump dump;
        try {
            dump = new MysqlDumpParser().parse(path);
        } catch (IOException e) {
            err.println("Error leyendo '" + dumpPathArg + "': " + e.getMessage());
            return 1;
        } catch (MysqlDumpParseException e) {
            err.println("Error parseando '" + dumpPathArg + "': " + e.getMessage());
            return 1;
        }

        TransformResult result = new RoleRepositoryTransformer().transform(dump);

        URI endpoint = resolveEndpoint();

        // Asegura que las tablas existan (idempotente) reusando la logica ya
        // probada de DesignValidateCommand, en vez de duplicarla aqui - esto
        // hace que `migrate` no dependa de que alguien haya corrido
        // `design-validate` antes (mas robusto, en particular para tests de
        // integracion que arrancan de un LocalStack limpio).
        int designExit = new DesignValidateCommand(endpoint).execute(new String[0], out, err);
        if (designExit != 0) {
            err.println("migrate: fallo asegurando el diseno de tablas antes de migrar.");
            return designExit;
        }

        try (DynamoDbAsyncClient client = buildClient(endpoint)) {
            int writtenRoleRepository = DynamoBatchWriter.writeAll(client, TableNames.ROLE_REPOSITORY, result.roleRepositoryItems());
            int writtenMenu = DynamoBatchWriter.writeAll(client, TableNames.ROLE_REPOSITORY_MENU, result.roleRepositoryMenuItems());

            printSummary(out, dumpPathArg, result, writtenRoleRepository, writtenMenu, endpoint);
            return 0;
        } catch (RuntimeException e) {
            err.println("migrate: fallo escribiendo en DynamoDB: " + describe(e));
            return 1;
        }
    }

    private void printSummary(
            PrintStream out, String dumpPath, TransformResult result, int writtenRoleRepository, int writtenMenu, URI endpoint) {
        TransformResult.Stats stats = result.stats();
        out.println("migrate: OK (" + dumpPath + ", endpoint=" + endpoint + ")");
        out.println("  " + TableNames.ROLE_REPOSITORY + ": " + writtenRoleRepository + " items escritos"
                + " (catalogos=" + stats.catalogItems()
                + ", rol=" + stats.rolItems()
                + ", suministro=" + stats.suministroItems()
                + ", atributo=" + stats.atributoItems() + ")");
        out.println("  " + TableNames.ROLE_REPOSITORY_MENU + ": " + writtenMenu + " items escritos");
        out.println("  Fuera de alcance (no migrados, ver docs/migration-plan.md): "
                + "detalle, usuario, login, sequence.");
        if (stats.unresolvedAplicacionNames() > 0 || stats.unresolvedAreaNames() > 0
                || stats.unresolvedCargoNames() > 0 || stats.unresolvedRolReferences() > 0) {
            out.println("  Advertencia: nombres/FKs sin resolver a un catalogo real (ver "
                    + "RoleRepositoryTransformer, se usa un id de fallback determinista): "
                    + "aplicacion=" + stats.unresolvedAplicacionNames()
                    + ", area=" + stats.unresolvedAreaNames()
                    + ", cargo=" + stats.unresolvedCargoNames()
                    + ", rol=" + stats.unresolvedRolReferences());
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

    private static String describe(RuntimeException e) {
        Throwable cause = e instanceof CompletionException && e.getCause() != null ? e.getCause() : e;
        return cause.getClass().getSimpleName() + ": " + cause.getMessage();
    }
}
