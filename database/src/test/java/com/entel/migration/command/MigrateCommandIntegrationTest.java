package com.entel.migration.command;

import com.entel.migration.localstack.LocalStackIntegrationSupport;
import com.entel.migration.schema.ItemAttributes;
import com.entel.migration.schema.TableNames;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DeleteTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;
import software.amazon.awssdk.services.dynamodb.model.Select;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Cubre, contra el LocalStack real de {@code docker-compose.yml} (ver
 * {@link LocalStackIntegrationSupport}), los acceptance criteria centrales
 * de la feature 4 (migrate_data_mysql_to_dynamodb):
 *
 * <ol>
 *   <li>"{@code migrate} sobre el dump real puebla {@code RoleRepository} y
 *       {@code RoleRepositoryMenu} con el conteo de items esperado."</li>
 *   <li>"Correr {@code migrate} dos veces seguidas no duplica items (test de
 *       idempotencia obligatorio)."</li>
 * </ol>
 *
 * <p>Los conteos esperados (17132 en {@code RoleRepository}, 0 en {@code
 * RoleRepositoryMenu}) fueron verificados de forma independiente por este
 * Implementer con un script ad-hoc sobre el dump real ANTES de escribir este
 * test (no se copiaron ciegamente de progress/current.md) - ver
 * progress/current.md para el detalle: 826 items de catalogo (area=69 +
 * cargo=695 + ubicacion=61 + aplicacion=1) + 741 de suministro + 15565 de
 * atributo = 17132; {@code RoleRepositoryMenu} queda vacio porque este dump
 * no trae tabla {@code menu}.
 *
 * <p><b>Requiere</b> {@code docker compose up -d localstack} corrido antes
 * de {@code mvn test} (lo hace {@code init.sh}) y el dump real presente en
 * {@code D:\Proyecto textil\repositorio_entel\reposrito_roles_jsf 20150814
 * 1214.sql} - si no esta presente, este test se omite limpiamente (igual
 * patron que {@code MysqlDumpParserRealDumpTest}).
 *
 * <p>Escribir ~17132 items dos veces via {@code BatchWriteItem} sobre HTTP
 * (aunque sea LocalStack local) toma un tiempo real no trivial - no se usa
 * ningun timeout corto para no forzar un fallo espurio.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MigrateCommandIntegrationTest {

    private static final Path REAL_DUMP_PATH =
            Path.of("D:\\Proyecto textil\\repositorio_entel\\reposrito_roles_jsf 20150814 1214.sql");

    private static final int EXPECTED_ROLE_REPOSITORY_ITEMS = 17132;
    private static final int EXPECTED_ROLE_REPOSITORY_MENU_ITEMS = 0;

    /** Item deterministico conocido (primera fila de `atributo` del dump real, ver MysqlDumpParserRealDumpTest). */
    private static final String KNOWN_ITEM_PK = "ATRIBUTO#41868";

    private static final String KNOWN_ITEM_SK = "DETALLE#41868#ROLES";

    private static DynamoDbAsyncClient rawClient;

    @BeforeAll
    static void setUpClientAndCleanState() {
        assumeTrue(
                Files.isRegularFile(REAL_DUMP_PATH),
                () -> "El dump real de referencia no esta presente en " + REAL_DUMP_PATH + " - se omite este test.");
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
    @Order(1)
    void migrarSobreElDumpRealPueblaElConteoDeItemsEsperado() {
        int exitCode = runMigrate();
        assertEquals(0, exitCode, "la primera corrida sobre LocalStack limpio debe terminar en exito");

        assertEquals(
                EXPECTED_ROLE_REPOSITORY_ITEMS,
                countItems(TableNames.ROLE_REPOSITORY),
                "conteo de items en RoleRepository tras la primera corrida");
        assertEquals(
                EXPECTED_ROLE_REPOSITORY_MENU_ITEMS,
                countItems(TableNames.ROLE_REPOSITORY_MENU),
                "conteo de items en RoleRepositoryMenu tras la primera corrida (0: este dump no trae tabla `menu`)");
    }

    @Test
    @Order(2)
    void correrMigrateDosVecesSeguidasNoDuplicaItems() {
        Map<String, AttributeValue> knownItemAfterFirstRun = getKnownItem();
        assertNotNull(knownItemAfterFirstRun, "el item conocido debe existir tras la primera corrida (test @Order(1))");

        int exitCode = runMigrate();
        assertEquals(0, exitCode, "la segunda corrida no debe fallar (idempotencia)");

        assertEquals(
                EXPECTED_ROLE_REPOSITORY_ITEMS,
                countItems(TableNames.ROLE_REPOSITORY),
                "el conteo tras la segunda corrida debe ser IGUAL al de la primera, no el doble - no debe duplicar items");
        assertEquals(EXPECTED_ROLE_REPOSITORY_MENU_ITEMS, countItems(TableNames.ROLE_REPOSITORY_MENU));

        // Spot-check: el item conocido sigue siendo un unico item con los
        // mismos atributos, no un item nuevo/duplicado - GetItem por PK/SK
        // exacta solo puede devolver 0 o 1 resultado, asi que junto con el
        // conteo total sin cambios esto demuestra que la segunda corrida
        // sobreescribio el mismo item en vez de crear uno nuevo.
        Map<String, AttributeValue> knownItemAfterSecondRun = getKnownItem();
        assertNotNull(knownItemAfterSecondRun);
        assertEquals(knownItemAfterFirstRun, knownItemAfterSecondRun, "el item conocido no debe cambiar entre corridas");
        assertEquals("41868", knownItemAfterSecondRun.get("idAtributo").s());
        assertEquals("2000", knownItemAfterSecondRun.get("idRol").s());
        assertEquals("1899", knownItemAfterSecondRun.get("idSuministro").s());
        assertEquals("36|36|351", knownItemAfterSecondRun.get("atributoCodigo").s());
        assertEquals("ROLES", knownItemAfterSecondRun.get(ItemAttributes.DETALLE_PARAMETRO).s());
        assertEquals("ACTIVO", knownItemAfterSecondRun.get(ItemAttributes.ESTADO).s());
    }

    private static int runMigrate() {
        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();
        MigrateCommand command = new MigrateCommand(LocalStackIntegrationSupport.resolveEndpoint());
        int exitCode = command.execute(
                new String[] {REAL_DUMP_PATH.toString()}, printStream(outBuffer), printStream(errBuffer));
        if (exitCode != 0) {
            throw new AssertionError(
                    "migrate fallo (exit=" + exitCode + "): stderr=" + errBuffer.toString(StandardCharsets.UTF_8)
                            + " stdout=" + outBuffer.toString(StandardCharsets.UTF_8));
        }
        return exitCode;
    }

    private static Map<String, AttributeValue> getKnownItem() {
        GetItemResponse response = rawClient
                .getItem(GetItemRequest.builder()
                        .tableName(TableNames.ROLE_REPOSITORY)
                        .key(Map.of(
                                ItemAttributes.PK, AttributeValue.fromS(KNOWN_ITEM_PK),
                                ItemAttributes.SK, AttributeValue.fromS(KNOWN_ITEM_SK)))
                        .build())
                .join();
        return response.hasItem() ? response.item() : null;
    }

    /** Cuenta todos los items de una tabla con Scan paginado (Select=COUNT). */
    private static int countItems(String tableName) {
        int total = 0;
        Map<String, AttributeValue> exclusiveStartKey = null;
        do {
            ScanRequest.Builder requestBuilder =
                    ScanRequest.builder().tableName(tableName).select(Select.COUNT);
            if (exclusiveStartKey != null) {
                requestBuilder.exclusiveStartKey(exclusiveStartKey);
            }
            ScanResponse response = rawClient.scan(requestBuilder.build()).join();
            total += response.count();
            exclusiveStartKey = response.hasLastEvaluatedKey() && !response.lastEvaluatedKey().isEmpty()
                    ? response.lastEvaluatedKey()
                    : null;
        } while (exclusiveStartKey != null);
        return total;
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
