package com.entel.migration.command;

import com.entel.migration.localstack.LocalStackIntegrationSupport;
import com.entel.migration.schema.ItemAttributes;
import com.entel.migration.schema.TableNames;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DeleteTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cubre, contra el LocalStack real de {@code docker-compose.yml} (ver
 * {@link LocalStackIntegrationSupport}), los acceptance criteria de la
 * feature 5 (data_migration_validation) que se pueden verificar con un
 * fixture pequeno y deliberado (en vez del dump real completo, que cubre
 * {@code VerifyCommandRealDumpIntegrationTest}):
 *
 * <ol>
 *   <li>Un dataset limpio (todas las referencias resuelven) hace que
 *       {@code verify} termine en codigo 0.</li>
 *   <li>Acceptance criteria 2/3: una referencia deliberadamente rota
 *       (un item {@code ATRIBUTO#} cuyo {@code idSuministro}/{@code idRol}
 *       apunta a un item {@code SUMINISTRO#} que no existe) hace que
 *       {@code verify} termine en codigo distinto de cero, y el mensaje de
 *       error nombra el item exacto (PK/SK) y la referencia exacta que
 *       falta - no un "referencia rota" generico.</li>
 *   <li>Un item {@code ATRIBUTO#} con el marcador de fallback deterministico
 *       de {@code RoleRepositoryTransformer} (calidad de datos preexistente,
 *       ver Javadoc de {@code VerifyCommand}) NO se reporta como referencia
 *       rota (regresion del trap #2 documentado).</li>
 * </ol>
 *
 * <p>Cada test method limpia y siembra su propio fixture pequeno (borra y
 * recrea las tablas antes de cada test) para no depender del orden de
 * ejecucion frente a otras clases de test de integracion que comparten el
 * mismo LocalStack persistente (mismo patron que
 * {@code DesignValidateCommandIdempotencyTest}/{@code MigrateCommandIntegrationTest}).
 *
 * <p><b>Requiere</b> {@code docker compose up -d localstack} corrido antes de
 * {@code mvn test} (lo hace {@code init.sh}).
 */
class VerifyCommandIntegrationTest {

    private static DynamoDbAsyncClient rawClient;

    @BeforeAll
    static void setUpClient() {
        rawClient = LocalStackIntegrationSupport.newDynamoDbClient();
    }

    @AfterAll
    static void tearDownClient() {
        if (rawClient != null) {
            rawClient.close();
        }
    }

    @BeforeEach
    void cleanTables() {
        deleteTableIfExists(TableNames.ROLE_REPOSITORY);
        deleteTableIfExists(TableNames.ROLE_REPOSITORY_MENU);
        int designExit = new DesignValidateCommand(LocalStackIntegrationSupport.resolveEndpoint())
                .execute(new String[0], nullStream(), nullStream());
        assertEquals(0, designExit, "design-validate debe dejar las tablas listas para sembrar el fixture");
    }

    @Test
    void datasetLimpioSinReferenciasRotasTerminaEnCodigoCero() {
        putItem(catalogItem("AREA", "A1", "1", "Area Uno"));
        putItem(catalogItem("CARGO", "C1", "1", "Cargo Uno"));
        putItem(catalogItem("APLICACION", "AP1", "1", "App Uno"));
        putItem(suministroItem("100", "500", "Suministro Uno", "1"));
        putItem(atributoItem("900", "ROLES", "1", "1", "1", "100", "500"));

        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();
        int exitCode = new VerifyCommand(LocalStackIntegrationSupport.resolveEndpoint())
                .execute(new String[0], printStream(outBuffer), printStream(errBuffer));

        assertEquals(0, exitCode, "un dataset sin referencias rotas debe terminar en codigo 0: stderr="
                + errBuffer.toString(StandardCharsets.UTF_8));
        String stdout = outBuffer.toString(StandardCharsets.UTF_8);
        assertTrue(stdout.contains("Referencias rotas encontradas: 0"), stdout);
        assertTrue(errBuffer.toString(StandardCharsets.UTF_8).isBlank());
    }

    @Test
    void atributoConSuministroInexistenteEsDetectadoYNombraItemYReferencia() {
        // Dataset limpio de base...
        putItem(catalogItem("AREA", "A1", "1", "Area Uno"));
        putItem(catalogItem("CARGO", "C1", "1", "Cargo Uno"));
        putItem(catalogItem("APLICACION", "AP1", "1", "App Uno"));
        putItem(suministroItem("100", "500", "Suministro Uno", "1"));
        putItem(atributoItem("900", "ROLES", "1", "1", "1", "100", "500"));

        // ...mas un item ATRIBUTO# deliberadamente roto: referencia
        // idRol=999/idSuministro=888, para el cual NUNCA se sembro un item
        // SUMINISTRO# (PK=ROL#999, SK=SUMINISTRO#888 no existe).
        putItem(atributoItem("901", "ROLES", "1", "1", "1", "999", "888"));

        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();
        int exitCode = new VerifyCommand(LocalStackIntegrationSupport.resolveEndpoint())
                .execute(new String[0], printStream(outBuffer), printStream(errBuffer));

        assertEquals(1, exitCode, "una referencia rota debe hacer fallar verify con codigo distinto de cero");
        String stderr = errBuffer.toString(StandardCharsets.UTF_8);
        assertTrue(stderr.contains("ATRIBUTO#901"), "el mensaje debe nombrar el item exacto (PK): " + stderr);
        assertTrue(stderr.contains("DETALLE#901#ROLES"), "el mensaje debe nombrar el item exacto (SK): " + stderr);
        assertTrue(stderr.contains("idSuministro=888") || stderr.contains("id=888"),
                "el mensaje debe nombrar la referencia especifica que falta (idSuministro): " + stderr);
        assertTrue(stderr.contains("idRol=999") || stderr.contains("Rol id=999"),
                "el mensaje debe nombrar la referencia especifica que falta (idRol): " + stderr);
        // El item limpio (ATRIBUTO#900) no debe aparecer como roto.
        assertFalse(stderr.contains("ATRIBUTO#900"), "el item limpio no debe reportarse como roto: " + stderr);
    }

    @Test
    void atributoConCatalogoInexistenteEsDetectadoYNombraItemYReferencia() {
        putItem(catalogItem("AREA", "A1", "1", "Area Uno"));
        putItem(catalogItem("CARGO", "C1", "1", "Cargo Uno"));
        putItem(catalogItem("APLICACION", "AP1", "1", "App Uno"));
        putItem(suministroItem("100", "500", "Suministro Uno", "1"));

        // ATRIBUTO# cuyo GSI1PK referencia APLICACION id=99, que no existe
        // como item de catalogo (solo existe id=1). idArea=1/idCargo=1 SI
        // resuelven, para que el unico quiebre sea el de APLICACION.
        putItem(atributoItem("902", "ROLES", "99", "1", "1", "100", "500"));

        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();
        int exitCode = new VerifyCommand(LocalStackIntegrationSupport.resolveEndpoint())
                .execute(new String[0], printStream(outBuffer), printStream(errBuffer));

        assertEquals(1, exitCode);
        String stderr = errBuffer.toString(StandardCharsets.UTF_8);
        assertTrue(stderr.contains("ATRIBUTO#902"), stderr);
        assertTrue(stderr.contains("APLICACION id=99"), "debe nombrar exactamente el tipo y el id que falta: " + stderr);
        assertFalse(stderr.contains("AREA id=1 "), "idArea=1 si resuelve, no deberia reportarse como rota: " + stderr);
        assertFalse(stderr.contains("CARGO id=1 "), "idCargo=1 si resuelve, no deberia reportarse como rota: " + stderr);
    }

    @Test
    void marcadorDeFallbackConocidoNoSeReportaComoReferenciaRota() {
        putItem(catalogItem("CARGO", "C1", "1", "Cargo Uno"));
        putItem(catalogItem("APLICACION", "AP1", "1", "App Uno"));
        putItem(suministroItem("100", "500", "Suministro Uno", "1"));

        // GSI1PK con idArea = "NOMBRE#LEGAL" (marcador de fallback
        // deterministico de RoleRepositoryTransformer para nombres de area
        // sin catalogo correspondiente) - NO debe reportarse como rota.
        Map<String, AttributeValue> atributo = new LinkedHashMap<>();
        atributo.put(ItemAttributes.PK, s("ATRIBUTO#903"));
        atributo.put(ItemAttributes.SK, s("DETALLE#903#ROLES"));
        atributo.put(ItemAttributes.GSI1_PK, s("APLICACION#1#AREA#NOMBRE#LEGAL"));
        atributo.put(ItemAttributes.GSI1_SK, s("CARGO#1#ROL#100"));
        atributo.put(ItemAttributes.ESTADO, s("ACTIVO"));
        atributo.put(ItemAttributes.ROL_CARGO_ID, s("1"));
        atributo.put(ItemAttributes.DETALLE_PARAMETRO, s("ROLES"));
        atributo.put("idAtributo", s("903"));
        atributo.put("idRol", s("100"));
        atributo.put("idSuministro", s("500"));
        atributo.put("atributoCodigo", s("X"));
        atributo.put("atributoNombre", s("Y"));
        putItem(atributo);

        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();
        int exitCode = new VerifyCommand(LocalStackIntegrationSupport.resolveEndpoint())
                .execute(new String[0], printStream(outBuffer), printStream(errBuffer));

        assertEquals(0, exitCode, "un marcador de fallback conocido no debe contarse como referencia rota: stderr="
                + errBuffer.toString(StandardCharsets.UTF_8));
        String stdout = outBuffer.toString(StandardCharsets.UTF_8);
        assertTrue(stdout.contains("Referencias rotas encontradas: 0"), stdout);
        assertTrue(stdout.contains("AREA=1"), "debe reportar el marcador conocido aparte, no invisible: " + stdout);
    }

    private static Map<String, AttributeValue> catalogItem(String tipo, String codigo, String id, String nombre) {
        Map<String, AttributeValue> item = new LinkedHashMap<>();
        item.put(ItemAttributes.PK, s("ENTITY#" + tipo + "#" + codigo));
        item.put(ItemAttributes.SK, s("METADATA"));
        item.put("id", s(id));
        item.put("codigo", s(codigo));
        item.put("nombre", s(nombre));
        return item;
    }

    private static Map<String, AttributeValue> suministroItem(String idRol, String idSuministro, String nombre, String prioridad) {
        Map<String, AttributeValue> item = new LinkedHashMap<>();
        item.put(ItemAttributes.PK, s("ROL#" + idRol));
        item.put(ItemAttributes.SK, s("SUMINISTRO#" + idSuministro));
        item.put("idRol", s(idRol));
        item.put("idSuministro", s(idSuministro));
        item.put("nombre", s(nombre));
        item.put("prioridad", s(prioridad));
        return item;
    }

    private static Map<String, AttributeValue> atributoItem(
            String idAtributo, String categoria, String idAplicacion, String idArea, String idCargo,
            String idRol, String idSuministro) {
        Map<String, AttributeValue> item = new LinkedHashMap<>();
        item.put(ItemAttributes.PK, s("ATRIBUTO#" + idAtributo));
        item.put(ItemAttributes.SK, s("DETALLE#" + idAtributo + "#" + categoria));
        item.put(ItemAttributes.GSI1_PK, s("APLICACION#" + idAplicacion + "#AREA#" + idArea));
        item.put(ItemAttributes.GSI1_SK, s("CARGO#" + idCargo + "#ROL#" + idRol));
        item.put(ItemAttributes.ESTADO, s("ACTIVO"));
        item.put(ItemAttributes.ROL_CARGO_ID, s(idCargo));
        item.put(ItemAttributes.DETALLE_PARAMETRO, s(categoria));
        item.put("idAtributo", s(idAtributo));
        item.put("idRol", s(idRol));
        item.put("idSuministro", s(idSuministro));
        item.put("atributoCodigo", s("X"));
        item.put("atributoNombre", s("Y"));
        return item;
    }

    private static void putItem(Map<String, AttributeValue> item) {
        rawClient.putItem(PutItemRequest.builder().tableName(TableNames.ROLE_REPOSITORY).item(item).build())
                .join();
    }

    private static AttributeValue s(String value) {
        return AttributeValue.fromS(value);
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

    private static PrintStream nullStream() {
        return new PrintStream(java.io.OutputStream.nullOutputStream());
    }
}
