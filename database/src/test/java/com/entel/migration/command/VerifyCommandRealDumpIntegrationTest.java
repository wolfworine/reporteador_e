package com.entel.migration.command;

import com.entel.migration.localstack.LocalStackIntegrationSupport;
import com.entel.migration.schema.TableNames;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.DeleteTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Cubre, contra el LocalStack real de {@code docker-compose.yml} y el dump
 * real completo, el acceptance criteria central de la feature 5
 * (data_migration_validation):
 *
 * <blockquote>"verify reporta 0 referencias rotas sobre los datos migrados
 * del dump real."</blockquote>
 *
 * <p>Este test es autocontenido (no depende de que
 * {@code MigrateCommandIntegrationTest} haya corrido antes en la misma
 * sesion de {@code mvn test}): borra y recrea las tablas, corre {@code
 * migrate} el sobre el dump real, y recien despues corre {@code verify}
 * sobre el resultado - mismo patron de aislamiento que las demas clases de
 * test de integracion de este proyecto, que comparten el mismo LocalStack
 * persistente).
 *
 * <p><b>Requiere</b> {@code docker compose up -d localstack} corrido antes
 * de {@code mvn test} (lo hace {@code init.sh}) y el dump real presente en
 * {@code D:\Proyecto textil\repositorio_entel\reposrito_roles_jsf 20150814
 * 1214.sql} - si no esta presente, este test se omite limpiamente (igual
 * patron que {@code MigrateCommandIntegrationTest}).
 *
 * <p>Escanear ~17132 items via {@code Scan} paginado sobre HTTP (aunque sea
 * LocalStack local), ademas de la carga inicial de {@code migrate}, toma un
 * tiempo real no trivial - no se usa ningun timeout corto para no forzar un
 * fallo espurio.
 */
class VerifyCommandRealDumpIntegrationTest {

    private static final Path REAL_DUMP_PATH =
            Path.of("D:\\Proyecto textil\\repositorio_entel\\reposrito_roles_jsf 20150814 1214.sql");

    private static DynamoDbAsyncClient rawClient;

    @BeforeAll
    static void setUpAndMigrateRealDump() {
        assumeTrue(
                Files.isRegularFile(REAL_DUMP_PATH),
                () -> "El dump real de referencia no esta presente en " + REAL_DUMP_PATH + " - se omite este test.");
        rawClient = LocalStackIntegrationSupport.newDynamoDbClient();
        deleteTableIfExists(TableNames.ROLE_REPOSITORY);
        deleteTableIfExists(TableNames.ROLE_REPOSITORY_MENU);

        ByteArrayOutputStream migrateOut = new ByteArrayOutputStream();
        ByteArrayOutputStream migrateErr = new ByteArrayOutputStream();
        int migrateExit = new MigrateCommand(LocalStackIntegrationSupport.resolveEndpoint())
                .execute(new String[] {REAL_DUMP_PATH.toString()}, printStream(migrateOut), printStream(migrateErr));
        if (migrateExit != 0) {
            throw new AssertionError("migrate (preparacion del fixture real) fallo: stderr="
                    + migrateErr.toString(StandardCharsets.UTF_8) + " stdout=" + migrateOut.toString(StandardCharsets.UTF_8));
        }
    }

    @AfterAll
    static void tearDownClient() {
        if (rawClient != null) {
            rawClient.close();
        }
    }

    @Test
    void verifySobreElDumpRealReportaCeroReferenciasRotasYConteosCorrectos() {
        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();

        int exitCode = new VerifyCommand(LocalStackIntegrationSupport.resolveEndpoint())
                .execute(new String[] {REAL_DUMP_PATH.toString()}, printStream(outBuffer), printStream(errBuffer));

        String stdout = outBuffer.toString(StandardCharsets.UTF_8);
        String stderr = errBuffer.toString(StandardCharsets.UTF_8);
        assertEquals(0, exitCode, "verify sobre los datos migrados del dump real debe reportar exito: stderr=" + stderr
                + " stdout=" + stdout);
        assertTrue(stderr.isBlank(), "no debe haber mensajes de error: " + stderr);
        assertTrue(stdout.contains("Referencias rotas encontradas: 0"), stdout);
        assertTrue(
                stdout.contains("catalogos=826") && stdout.contains("suministro=741") && stdout.contains("atributo=15565"),
                "los conteos por tipo deben coincidir con los ya establecidos en la feature 4: " + stdout);
        // Trap #2, ya documentado (14 filas de rol.area sin catalogo ->
        // 76 items ATRIBUTO# con idArea sin resolver): deben seguir
        // reportandose aparte, NO como referencias rotas.
        assertTrue(stdout.contains("AREA=76"), "los 76 items con area sin resolver deben reportarse aparte: " + stdout);
    }

    private static void deleteTableIfExists(String tableName) {
        try {
            rawClient.describeTable(DescribeTableRequest.builder().tableName(tableName).build()).join();
        } catch (CompletionException ce) {
            if (ce.getCause() instanceof ResourceNotFoundException) {
                return;
            }
            throw ce;
        }
        rawClient.deleteTable(DeleteTableRequest.builder().tableName(tableName).build()).join();
        rawClient.waiter()
                .waitUntilTableNotExists(DescribeTableRequest.builder().tableName(tableName).build())
                .join();
    }

    private static PrintStream printStream(ByteArrayOutputStream buffer) {
        return new PrintStream(buffer, true, StandardCharsets.UTF_8);
    }
}
