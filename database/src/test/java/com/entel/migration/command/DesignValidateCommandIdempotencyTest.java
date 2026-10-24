package com.entel.migration.command;

import com.entel.migration.localstack.LocalStackIntegrationSupport;
import com.entel.migration.schema.TableNames;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.DeleteTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.TableDescription;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cubre el acceptance criteria "Las tablas se crean correctamente con
 * design-validate de forma idempotente (correrlo dos veces no falla ni
 * duplica)" de la feature 2 (design_dynamodb_schema).
 *
 * <p>Corre contra el LocalStack de {@code docker-compose.yml} (ver
 * {@link LocalStackIntegrationSupport} para el porque: Testcontainers no
 * logra localizar el daemon Docker en este entorno concreto). Como ese
 * LocalStack es un servicio persistente (no un contenedor efimero por
 * test), este test primero borra las tablas si ya existian de una corrida
 * anterior, para que la primera invocacion de {@code design-validate}
 * arranque realmente desde un estado limpio.
 *
 * <p><b>Requiere</b> {@code docker compose up -d localstack} corrido antes
 * de {@code mvn test} (lo hace {@code init.sh}).
 */
class DesignValidateCommandIdempotencyTest {

    private static DynamoDbAsyncClient rawClient;

    @BeforeAll
    static void setUpClientAndCleanState() {
        rawClient = LocalStackIntegrationSupport.newDynamoDbClient();
        deleteTableIfExists(TableNames.ROLE_REPOSITORY);
        deleteTableIfExists(TableNames.ROLE_REPOSITORY_MENU);
    }

    @AfterAll
    static void tearDownClient() {
        if (rawClient != null) {
            rawClient.close();
        }
    }

    @Test
    void correrDesignValidateDosVecesNoFallaNiDuplicaTablas() {
        DesignValidateCommand command = new DesignValidateCommand(LocalStackIntegrationSupport.resolveEndpoint());

        int firstExitCode = runCommand(command);
        assertEquals(0, firstExitCode, "la primera corrida (LocalStack limpio) debe crear las tablas y salir con codigo 0");

        Set<String> tableNamesAfterFirstRun = listTableNames();
        assertEquals(
                Set.of(TableNames.ROLE_REPOSITORY, TableNames.ROLE_REPOSITORY_MENU),
                tableNamesAfterFirstRun,
                "deben existir exactamente las dos tablas del diseno canonico");
        TableDescription roleRepositoryAfterFirstRun = describeTable(TableNames.ROLE_REPOSITORY);

        int secondExitCode = runCommand(command);
        assertEquals(0, secondExitCode, "la segunda corrida NO debe fallar (idempotencia)");

        Set<String> tableNamesAfterSecondRun = listTableNames();
        assertEquals(
                tableNamesAfterFirstRun,
                tableNamesAfterSecondRun,
                "la segunda corrida no debe duplicar ni crear tablas adicionales");

        TableDescription roleRepositoryAfterSecondRun = describeTable(TableNames.ROLE_REPOSITORY);
        assertEquals(
                roleRepositoryAfterFirstRun.tableArn(),
                roleRepositoryAfterSecondRun.tableArn(),
                "debe seguir siendo la misma tabla (mismo ARN), no una tabla nueva");
        assertEquals("ACTIVE", roleRepositoryAfterSecondRun.tableStatusAsString());
        assertEquals(1, roleRepositoryAfterSecondRun.globalSecondaryIndexes().size(), "GSI1 no debe duplicarse");
    }

    private static int runCommand(DesignValidateCommand command) {
        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();
        int exitCode = command.execute(new String[0], printStream(outBuffer), printStream(errBuffer));
        assertTrue(
                errBuffer.toString(StandardCharsets.UTF_8).isBlank() || exitCode == 0,
                "si hay salida de error no deberia haber exito silencioso");
        return exitCode;
    }

    private static Set<String> listTableNames() {
        List<String> names = rawClient.listTables().join().tableNames();
        return Set.copyOf(names);
    }

    private static TableDescription describeTable(String tableName) {
        return rawClient.describeTable(DescribeTableRequest.builder().tableName(tableName).build())
                .join()
                .table();
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
