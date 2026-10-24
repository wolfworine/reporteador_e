package com.entel.migration.schema;

import com.entel.migration.command.DesignValidateCommand;
import com.entel.migration.localstack.LocalStackIntegrationSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Un test de integracion por cada uno de los 5 access patterns reales de
 * docs/architecture.md, mas el Patron 2b (real, aunque no numerado en el
 * plan original), contra el LocalStack de {@code docker-compose.yml} (ver
 * {@link LocalStackIntegrationSupport} para el porque de esta eleccion en
 * vez de Testcontainers). Requiere {@code docker compose up -d localstack}
 * corrido antes de {@code mvn test} (lo hace {@code init.sh}).
 *
 * <p>Cada test inserta sus propios items de prueba con IDs con prefijo
 * distinto (P1.., P2.., ...) para poder compartir las mismas dos tablas
 * (creadas una sola vez, de forma idempotente, en {@link
 * #createTablesOnce()}) sin interferir entre tests.
 */
class AccessPatternsIntegrationTest {

    private static DynamoDbAsyncClient client;

    @BeforeAll
    static void createTablesOnce() {
        client = LocalStackIntegrationSupport.newDynamoDbClient();

        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();
        int exitCode = new DesignValidateCommand(LocalStackIntegrationSupport.resolveEndpoint())
                .execute(new String[0], new PrintStream(outBuffer, true, StandardCharsets.UTF_8),
                        new PrintStream(errBuffer, true, StandardCharsets.UTF_8));
        assertEquals(0, exitCode, "design-validate debe crear las tablas antes de los tests de patrones: "
                + errBuffer.toString(StandardCharsets.UTF_8));
    }

    @AfterAll
    static void closeClient() {
        if (client != null) {
            client.close();
        }
    }

    @Test
    @DisplayName("Patron 1: Get directo de catalogo simple por codigo")
    void patron1_catalogoSimplePorCodigo() {
        String pk = RoleRepositoryKeys.catalogPk("AREA", "P1-10");
        putItem(TableNames.ROLE_REPOSITORY, Map.of(
                ItemAttributes.PK, s(pk),
                ItemAttributes.SK, s(RoleRepositoryKeys.CATALOG_SK),
                "nombre", s("Sistemas")));

        GetItemResponse response = client.getItem(GetItemRequest.builder()
                .tableName(TableNames.ROLE_REPOSITORY)
                .key(Map.of(
                        ItemAttributes.PK, s(pk),
                        ItemAttributes.SK, s(RoleRepositoryKeys.CATALOG_SK)))
                .build())
                .join();

        assertTrue(response.hasItem(), "el catalogo insertado debe encontrarse con GetItem directo");
        assertEquals("Sistemas", response.item().get("nombre").s());

        // Negativo: un codigo no insertado no debe devolver item.
        GetItemResponse missing = client.getItem(GetItemRequest.builder()
                .tableName(TableNames.ROLE_REPOSITORY)
                .key(Map.of(
                        ItemAttributes.PK, s(RoleRepositoryKeys.catalogPk("AREA", "P1-NO-EXISTE")),
                        ItemAttributes.SK, s(RoleRepositoryKeys.CATALOG_SK)))
                .build())
                .join();
        assertFalse(missing.hasItem());
    }

    @Test
    @DisplayName("Patron 2: CargoRepository.findCargo(aplicacion, area) - cargos distintos con estado ACTIVO")
    void patron2_cargosDistintosPorAplicacionYArea() {
        String aplicacion = "P2-1";
        String area = "P2-5";
        String gsi1Pk = RoleRepositoryKeys.gsi1Pk(aplicacion, area);

        putAtributoItem("P2-100", gsi1Pk, RoleRepositoryKeys.gsi1Sk("P2-CARGO-A", "P2-ROL-A"),
                "ACTIVO", "P2-CARGO-A", "TIPO1");
        putAtributoItem("P2-101", gsi1Pk, RoleRepositoryKeys.gsi1Sk("P2-CARGO-A", "P2-ROL-B"),
                "ACTIVO", "P2-CARGO-A", "TIPO1");
        putAtributoItem("P2-102", gsi1Pk, RoleRepositoryKeys.gsi1Sk("P2-CARGO-B", "P2-ROL-C"),
                "ACTIVO", "P2-CARGO-B", "TIPO2");
        // Cargo que solo tiene un atributo INACTIVO: no debe aparecer en el resultado.
        putAtributoItem("P2-103", gsi1Pk, RoleRepositoryKeys.gsi1Sk("P2-CARGO-C", "P2-ROL-D"),
                "INACTIVO", "P2-CARGO-C", "TIPO1");

        QueryResponse response = client.query(QueryRequest.builder()
                .tableName(TableNames.ROLE_REPOSITORY)
                .indexName(RoleRepositoryKeys.GSI1_NAME)
                .keyConditionExpression("#gsi1pk = :pk")
                .expressionAttributeNames(Map.of("#gsi1pk", ItemAttributes.GSI1_PK))
                .expressionAttributeValues(Map.of(":pk", s(gsi1Pk)))
                .build())
                .join();

        Set<String> cargosActivosDistintos = response.items().stream()
                .filter(item -> "ACTIVO".equals(item.get(ItemAttributes.ESTADO).s()))
                .map(item -> item.get(ItemAttributes.ROL_CARGO_ID).s())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        assertEquals(Set.of("P2-CARGO-A", "P2-CARGO-B"), cargosActivosDistintos,
                "debe extraer los cargos distintos entre los atributos ACTIVOS, excluyendo el cargo solo-INACTIVO");
    }

    @Test
    @DisplayName("Patron 3: AtributoRepository.findAppArea(aplicacion, area) - atributos activos por app+area")
    void patron3_atributosActivosPorAplicacionYArea() {
        String aplicacion = "P3-1";
        String area = "P3-5";
        String gsi1Pk = RoleRepositoryKeys.gsi1Pk(aplicacion, area);

        putAtributoItem("P3-200", gsi1Pk, RoleRepositoryKeys.gsi1Sk("P3-CARGO-A", "P3-ROL-A"),
                "ACTIVO", "P3-CARGO-A", "TIPO1");
        putAtributoItem("P3-201", gsi1Pk, RoleRepositoryKeys.gsi1Sk("P3-CARGO-B", "P3-ROL-B"),
                "ACTIVO", "P3-CARGO-B", "TIPO1");
        putAtributoItem("P3-202", gsi1Pk, RoleRepositoryKeys.gsi1Sk("P3-CARGO-C", "P3-ROL-C"),
                "INACTIVO", "P3-CARGO-C", "TIPO1");

        QueryResponse response = client.query(QueryRequest.builder()
                .tableName(TableNames.ROLE_REPOSITORY)
                .indexName(RoleRepositoryKeys.GSI1_NAME)
                .keyConditionExpression("#gsi1pk = :pk")
                .filterExpression("#estado = :estadoActivo")
                .expressionAttributeNames(Map.of(
                        "#gsi1pk", ItemAttributes.GSI1_PK,
                        "#estado", ItemAttributes.ESTADO))
                .expressionAttributeValues(Map.of(
                        ":pk", s(gsi1Pk),
                        ":estadoActivo", s("ACTIVO")))
                .build())
                .join();

        Set<String> atributoIdsDevueltos = response.items().stream()
                .map(item -> item.get(ItemAttributes.PK).s())
                .collect(Collectors.toSet());

        assertEquals(
                Set.of(RoleRepositoryKeys.atributoPk("P3-200"), RoleRepositoryKeys.atributoPk("P3-201")),
                atributoIdsDevueltos,
                "solo deben devolverse los atributos ACTIVOS de esa aplicacion+area");
        assertFalse(atributoIdsDevueltos.contains(RoleRepositoryKeys.atributoPk("P3-202")),
                "el atributo INACTIVO no debe aparecer en el resultado del Patron 3");
    }

    @Test
    @DisplayName("Patron 4: AtributoRepository.findAppArea2(aplicacion, area, cargo) - filtra tambien por cargo")
    void patron4_atributosActivosPorAplicacionAreaYCargo() {
        String aplicacion = "P4-1";
        String area = "P4-5";
        String cargoObjetivo = "P4-CARGO-X";
        String otroCargo = "P4-CARGO-Y";
        String gsi1Pk = RoleRepositoryKeys.gsi1Pk(aplicacion, area);

        putAtributoItem("P4-300", gsi1Pk, RoleRepositoryKeys.gsi1Sk(cargoObjetivo, "P4-ROL-A"),
                "ACTIVO", cargoObjetivo, "TIPO1");
        // Mismo cargo objetivo pero INACTIVO: debe quedar excluido por el filtro de estado.
        putAtributoItem("P4-301", gsi1Pk, RoleRepositoryKeys.gsi1Sk(cargoObjetivo, "P4-ROL-B"),
                "INACTIVO", cargoObjetivo, "TIPO1");
        // Otro cargo, ACTIVO: debe quedar excluido por el begins_with de cargo.
        putAtributoItem("P4-302", gsi1Pk, RoleRepositoryKeys.gsi1Sk(otroCargo, "P4-ROL-C"),
                "ACTIVO", otroCargo, "TIPO1");

        QueryResponse response = client.query(QueryRequest.builder()
                .tableName(TableNames.ROLE_REPOSITORY)
                .indexName(RoleRepositoryKeys.GSI1_NAME)
                .keyConditionExpression("#gsi1pk = :pk AND begins_with(#gsi1sk, :cargoPrefix)")
                .filterExpression("#estado = :estadoActivo")
                .expressionAttributeNames(Map.of(
                        "#gsi1pk", ItemAttributes.GSI1_PK,
                        "#gsi1sk", ItemAttributes.GSI1_SK,
                        "#estado", ItemAttributes.ESTADO))
                .expressionAttributeValues(Map.of(
                        ":pk", s(gsi1Pk),
                        ":cargoPrefix", s(RoleRepositoryKeys.gsi1SkCargoPrefix(cargoObjetivo)),
                        ":estadoActivo", s("ACTIVO")))
                .build())
                .join();

        List<Map<String, AttributeValue>> items = response.items();
        assertEquals(1, items.size(), "solo debe devolver el atributo ACTIVO del cargo objetivo");
        assertEquals(RoleRepositoryKeys.atributoPk("P4-300"), items.get(0).get(ItemAttributes.PK).s());

        Set<String> atributoIdsDevueltos = items.stream()
                .map(item -> item.get(ItemAttributes.PK).s())
                .collect(Collectors.toSet());
        assertFalse(atributoIdsDevueltos.contains(RoleRepositoryKeys.atributoPk("P4-301")),
                "el atributo INACTIVO del mismo cargo no debe aparecer");
        assertFalse(atributoIdsDevueltos.contains(RoleRepositoryKeys.atributoPk("P4-302")),
                "un atributo ACTIVO de OTRO cargo no debe aparecer (begins_with debe excluirlo)");
    }

    @Test
    @DisplayName("Patron 5: MenuRepository.findTipo(tipo) - menu por tipo S/I")
    void patron5_menuPorTipo() {
        putItem(TableNames.ROLE_REPOSITORY_MENU, Map.of(
                ItemAttributes.PK, s(RoleRepositoryMenuKeys.menuPk("S")),
                ItemAttributes.SK, s(RoleRepositoryMenuKeys.ordenSk("P5-001")),
                ItemAttributes.TIPO_USUARIO, s("ADMIN"),
                ItemAttributes.COD_SUBMENU, s("0")));
        putItem(TableNames.ROLE_REPOSITORY_MENU, Map.of(
                ItemAttributes.PK, s(RoleRepositoryMenuKeys.menuPk("S")),
                ItemAttributes.SK, s(RoleRepositoryMenuKeys.ordenSk("P5-002")),
                ItemAttributes.TIPO_USUARIO, s("OPERADOR"),
                ItemAttributes.COD_SUBMENU, s("P5-001")));
        putItem(TableNames.ROLE_REPOSITORY_MENU, Map.of(
                ItemAttributes.PK, s(RoleRepositoryMenuKeys.menuPk("I")),
                ItemAttributes.SK, s(RoleRepositoryMenuKeys.ordenSk("P5-001")),
                ItemAttributes.TIPO_USUARIO, s("ADMIN"),
                ItemAttributes.COD_SUBMENU, s("0")));

        QueryResponse response = client.query(QueryRequest.builder()
                .tableName(TableNames.ROLE_REPOSITORY_MENU)
                .keyConditionExpression("#pk = :pk")
                .expressionAttributeNames(Map.of("#pk", ItemAttributes.PK))
                .expressionAttributeValues(Map.of(":pk", s(RoleRepositoryMenuKeys.menuPk("S"))))
                .build())
                .join();

        List<String> skDevueltos = response.items().stream()
                .map(item -> item.get(ItemAttributes.SK).s())
                .toList();

        assertEquals(
                List.of(RoleRepositoryMenuKeys.ordenSk("P5-001"), RoleRepositoryMenuKeys.ordenSk("P5-002")),
                skDevueltos,
                "debe devolver solo las entradas de tipo S, ordenadas por SK");
    }

    @Test
    @DisplayName("Patron 2b: SuministroRepository.findSuministro(idRol)")
    void patron2b_suministrosPorRol() {
        String idRolObjetivo = "P2B-1";
        String idRolOtro = "P2B-2";

        putItem(TableNames.ROLE_REPOSITORY, Map.of(
                ItemAttributes.PK, s(RoleRepositoryKeys.rolPk(idRolObjetivo)),
                ItemAttributes.SK, s(RoleRepositoryKeys.suministroSk("P2B-S1"))));
        putItem(TableNames.ROLE_REPOSITORY, Map.of(
                ItemAttributes.PK, s(RoleRepositoryKeys.rolPk(idRolObjetivo)),
                ItemAttributes.SK, s(RoleRepositoryKeys.suministroSk("P2B-S2"))));
        putItem(TableNames.ROLE_REPOSITORY, Map.of(
                ItemAttributes.PK, s(RoleRepositoryKeys.rolPk(idRolOtro)),
                ItemAttributes.SK, s(RoleRepositoryKeys.suministroSk("P2B-S9"))));

        QueryResponse response = client.query(QueryRequest.builder()
                .tableName(TableNames.ROLE_REPOSITORY)
                .keyConditionExpression("#pk = :pk")
                .expressionAttributeNames(Map.of("#pk", ItemAttributes.PK))
                .expressionAttributeValues(Map.of(":pk", s(RoleRepositoryKeys.rolPk(idRolObjetivo))))
                .build())
                .join();

        Set<String> skDevueltos = response.items().stream()
                .map(item -> item.get(ItemAttributes.SK).s())
                .collect(Collectors.toSet());

        assertEquals(
                Set.of(RoleRepositoryKeys.suministroSk("P2B-S1"), RoleRepositoryKeys.suministroSk("P2B-S2")),
                skDevueltos,
                "debe devolver solo los suministros del rol consultado");
        assertFalse(skDevueltos.contains(RoleRepositoryKeys.suministroSk("P2B-S9")),
                "no debe devolver suministros de otro Rol");
    }

    private static void putAtributoItem(String idAtributo, String gsi1Pk, String gsi1Sk,
            String estado, String cargoId, String rolTipo) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put(ItemAttributes.PK, s(RoleRepositoryKeys.atributoPk(idAtributo)));
        item.put(ItemAttributes.SK, s(RoleRepositoryKeys.detalleSk(idAtributo, "ROLES")));
        item.put(ItemAttributes.GSI1_PK, s(gsi1Pk));
        item.put(ItemAttributes.GSI1_SK, s(gsi1Sk));
        item.put(ItemAttributes.ESTADO, s(estado));
        item.put(ItemAttributes.ROL_CARGO_ID, s(cargoId));
        item.put(ItemAttributes.ROL_TIPO, s(rolTipo));
        putItem(TableNames.ROLE_REPOSITORY, item);
    }

    private static void putItem(String tableName, Map<String, AttributeValue> item) {
        client.putItem(PutItemRequest.builder()
                        .tableName(tableName)
                        .item(item)
                        .build())
                .join();
    }

    private static AttributeValue s(String value) {
        return AttributeValue.fromS(value);
    }
}
