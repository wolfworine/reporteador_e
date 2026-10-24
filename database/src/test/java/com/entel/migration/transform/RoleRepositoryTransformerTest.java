package com.entel.migration.transform;

import com.entel.migration.dump.ParsedDump;
import com.entel.migration.dump.ParsedRow;
import com.entel.migration.schema.ItemAttributes;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test de {@link RoleRepositoryTransformer} (feature 4:
 * migrate_data_mysql_to_dynamodb) contra un {@link ParsedDump} de fixture,
 * construido en memoria con {@link ParsedDump.Builder} (no un archivo
 * {@code .sql} nuevo: el transformer es una funcion pura sobre {@link
 * ParsedDump}, y este fixture cubre a proposito los casos borde
 * documentados en el Javadoc de la clase, verificados de forma independiente
 * contra el dump real por este mismo Implementer antes de escribir este
 * test - ver progress/current.md):
 *
 * <ul>
 *   <li>Forma de item de catalogo (Patron 1).</li>
 *   <li>Forma de item de suministro (Patron 2b).</li>
 *   <li>Forma de item de atributo resuelto (Patrones 2/3/4).</li>
 *   <li>Empate de {@code cargo.nombre} duplicado -&gt; se usa el id MENOR.</li>
 *   <li>Fallback determinista {@code NOMBRE#&lt;nombre&gt;} cuando
 *       {@code atributo.aplicacion} / {@code rol.area} / {@code rol.cargo}
 *       no resuelven a un catalogo real.</li>
 *   <li>Fallback determinista {@code ROLDESCONOCIDO#&lt;idRol&gt;} cuando
 *       {@code atributo.id_rol} no tiene fila {@code rol} correspondiente.</li>
 * </ul>
 */
class RoleRepositoryTransformerTest {

    @Test
    void transformaCatalogosSuministroYAtributoConLosFallbacksDocumentados() {
        ParsedDump dump = buildFixtureDump();

        TransformResult result = new RoleRepositoryTransformer().transform(dump);

        // --- Conteos globales ---
        // catalogos: 2 area + 3 cargo + 1 ubicacion + 1 aplicacion = 7
        // suministro: 1
        // atributo: 4
        assertEquals(12, result.roleRepositoryItems().size());
        assertTrue(result.roleRepositoryMenuItems().isEmpty(), "este dataset de fixture no trae tabla `menu`");

        TransformResult.Stats stats = result.stats();
        assertEquals(7, stats.catalogItems());
        assertEquals(1, stats.suministroItems());
        assertEquals(4, stats.atributoItems());
        assertEquals(0, stats.menuItems());
        assertEquals(1, stats.unresolvedAplicacionNames(), "atributo 9001 referencia una aplicacion inexistente");
        assertEquals(1, stats.unresolvedAreaNames(), "atributo 9001 -> rol 2001 -> area inexistente");
        assertEquals(1, stats.unresolvedCargoNames(), "atributo 9002 -> rol 2002 -> cargo inexistente");
        assertEquals(1, stats.unresolvedRolReferences(), "atributo 9003 referencia un id_rol inexistente");

        Map<String, Map<String, AttributeValue>> byPkSk = index(result.roleRepositoryItems());

        // --- Item de catalogo (Patron 1) ---
        Map<String, AttributeValue> areaUno = byPkSk.get("ENTITY#AREA#COD_AREA_1|METADATA");
        assertEquals("1", areaUno.get("id").s());
        assertEquals("COD_AREA_1", areaUno.get("codigo").s());
        assertEquals("AREA_UNO", areaUno.get("nombre").s());

        // --- Item de suministro (Patron 2b) ---
        Map<String, AttributeValue> suministroItem = byPkSk.get("ROL#2000|SUMINISTRO#500");
        assertEquals("2000", suministroItem.get("idRol").s());
        assertEquals("500", suministroItem.get("idSuministro").s());
        assertEquals("SUM NOMBRE", suministroItem.get("nombre").s());
        assertEquals("1", suministroItem.get("prioridad").s());

        // --- Atributo totalmente resuelto: aplicacion/area/cargo/rol conocidos ---
        // rol 2000: area="AREA_UNO" (id 1), cargo="JEFE DUPLICADO" (empate,
        // debe resolver al id MENOR = 10, no 20).
        Map<String, AttributeValue> attr9000 = byPkSk.get("ATRIBUTO#9000|DETALLE#9000#ROLES");
        assertEquals("APLICACION#1#AREA#1", attr9000.get(ItemAttributes.GSI1_PK).s());
        assertEquals("CARGO#10#ROL#2000", attr9000.get(ItemAttributes.GSI1_SK).s());
        assertEquals("ACTIVO", attr9000.get(ItemAttributes.ESTADO).s());
        assertEquals("10", attr9000.get(ItemAttributes.ROL_CARGO_ID).s());
        assertEquals("ROLES", attr9000.get(ItemAttributes.DETALLE_PARAMETRO).s());
        assertEquals("DINAMICO", attr9000.get(ItemAttributes.ROL_TIPO).s());
        assertEquals("9000", attr9000.get("idAtributo").s());
        assertEquals("2000", attr9000.get("idRol").s());
        assertEquals("500", attr9000.get("idSuministro").s());
        assertEquals("A1", attr9000.get("atributoCodigo").s());
        assertEquals("ATTR UNO", attr9000.get("atributoNombre").s());

        // --- Atributo con aplicacion Y area sin catalogo correspondiente ---
        Map<String, AttributeValue> attr9001 = byPkSk.get("ATRIBUTO#9001|DETALLE#9001#GRUPOS");
        assertEquals(
                "APLICACION#NOMBRE#APLICACION_DESCONOCIDA#AREA#NOMBRE#AREA_INEXISTENTE",
                attr9001.get(ItemAttributes.GSI1_PK).s());
        // cargo "CARGO UNICO" (rol 2001) SI resuelve -> id 30, sin ambiguedad.
        assertEquals("CARGO#30#ROL#2001", attr9001.get(ItemAttributes.GSI1_SK).s());
        assertEquals("ESTATICO", attr9001.get(ItemAttributes.ROL_TIPO).s());

        // --- Atributo con cargo sin catalogo correspondiente ---
        Map<String, AttributeValue> attr9002 = byPkSk.get("ATRIBUTO#9002|DETALLE#9002#INBOX");
        assertEquals("APLICACION#1#AREA#2", attr9002.get(ItemAttributes.GSI1_PK).s());
        assertEquals("CARGO#NOMBRE#CARGO_INEXISTENTE#ROL#2002", attr9002.get(ItemAttributes.GSI1_SK).s());

        // --- Atributo cuyo id_rol no tiene fila `rol` correspondiente ---
        Map<String, AttributeValue> attr9003 = byPkSk.get("ATRIBUTO#9003|DETALLE#9003#DEFAULTINBOX");
        assertEquals("APLICACION#1#AREA#ROLDESCONOCIDO#8888", attr9003.get(ItemAttributes.GSI1_PK).s());
        assertEquals("CARGO#ROLDESCONOCIDO#8888#ROL#8888", attr9003.get(ItemAttributes.GSI1_SK).s());
        assertFalse(
                attr9003.containsKey(ItemAttributes.ROL_TIPO),
                "sin fila `rol` no hay rolTipo que denormalizar - no debe inventarse un valor");
        assertNull(attr9003.get(ItemAttributes.ROL_TIPO));
    }

    /** Indexa los items por {@code PK|SK} para poder buscarlos por clave en las aserciones. */
    private static Map<String, Map<String, AttributeValue>> index(List<Map<String, AttributeValue>> items) {
        Map<String, Map<String, AttributeValue>> byKey = new LinkedHashMap<>();
        for (Map<String, AttributeValue> item : items) {
            String key = item.get(ItemAttributes.PK).s() + "|" + item.get(ItemAttributes.SK).s();
            Map<String, AttributeValue> previous = byKey.put(key, item);
            if (previous != null) {
                throw new IllegalStateException("PK/SK duplicada en el fixture, revisar datos de prueba: " + key);
            }
        }
        return byKey;
    }

    private static ParsedDump buildFixtureDump() {
        ParsedDump.Builder builder = ParsedDump.builder();

        builder.addRow(row("area", Map.of("id", 1L, "area", "AREA_UNO", "codigo", "COD_AREA_1")));
        builder.addRow(row("area", Map.of("id", 2L, "area", "AREA_DOS", "codigo", "COD_AREA_2")));

        builder.addRow(row("cargo", Map.of("id", 10L, "codigo", "COD_CARGO_10", "nombre", "JEFE DUPLICADO")));
        builder.addRow(row("cargo", Map.of("id", 20L, "codigo", "COD_CARGO_20", "nombre", "JEFE DUPLICADO")));
        builder.addRow(row("cargo", Map.of("id", 30L, "codigo", "COD_CARGO_30", "nombre", "CARGO UNICO")));

        builder.addRow(row("ubicacion", Map.of("id", 100L, "codigo", "COD_UBI_100", "ubicacion", "UBICACION UNO")));

        builder.addRow(row("aplicacion", Map.of("id", 1L, "codigo", "1", "aplicacion", "PORTAL")));

        Map<String, Object> rol2000 = new LinkedHashMap<>();
        rol2000.put("id", 2000L);
        rol2000.put("ambito", "SUBUNIDAD");
        rol2000.put("area", "AREA_UNO");
        rol2000.put("cargo", "JEFE DUPLICADO");
        rol2000.put("descripcion", "desc 2000");
        rol2000.put("nombre", "RO_2000");
        rol2000.put("propietario_persona", null);
        rol2000.put("propietario_rol", null);
        rol2000.put("regla_negocio", "(&(UNIDAD=GA0001)(CARGO=PU0019))");
        rol2000.put("tipo", "DINAMICO");
        rol2000.put("ubicacion", "COD_UBI_100");
        builder.addRow(row("rol", rol2000));

        Map<String, Object> rol2001 = new LinkedHashMap<>();
        rol2001.put("id", 2001L);
        rol2001.put("ambito", "SUBUNIDAD");
        rol2001.put("area", "AREA_INEXISTENTE");
        rol2001.put("cargo", "CARGO UNICO");
        rol2001.put("descripcion", "desc 2001");
        rol2001.put("nombre", "RO_2001");
        rol2001.put("propietario_persona", null);
        rol2001.put("propietario_rol", null);
        rol2001.put("regla_negocio", "(&(UNIDAD=GA0002)(CARGO=PU0020))");
        rol2001.put("tipo", "ESTATICO");
        rol2001.put("ubicacion", "COD_UBI_100");
        builder.addRow(row("rol", rol2001));

        Map<String, Object> rol2002 = new LinkedHashMap<>();
        rol2002.put("id", 2002L);
        rol2002.put("ambito", "SUBUNIDAD");
        rol2002.put("area", "AREA_DOS");
        rol2002.put("cargo", "CARGO_INEXISTENTE");
        rol2002.put("descripcion", "desc 2002");
        rol2002.put("nombre", "RO_2002");
        rol2002.put("propietario_persona", null);
        rol2002.put("propietario_rol", null);
        rol2002.put("regla_negocio", "(&(UNIDAD=GA0003)(CARGO=PU0021))");
        rol2002.put("tipo", "DINAMICO");
        rol2002.put("ubicacion", "COD_UBI_100");
        builder.addRow(row("rol", rol2002));

        builder.addRow(row("suministro", Map.of("id", 500L, "id_rol", 2000L, "nombre", "SUM NOMBRE", "prioridad", "1")));

        Map<String, Object> attr9000 = new LinkedHashMap<>();
        attr9000.put("id", 9000L);
        attr9000.put("aplicacion", "PORTAL");
        attr9000.put("atributo_codigo", "A1");
        attr9000.put("atributo_nombre", "ATTR UNO");
        attr9000.put("id_rol", 2000L);
        attr9000.put("id_suministro", 500L);
        attr9000.put("parametro", "ROLES");
        builder.addRow(row("atributo", attr9000));

        Map<String, Object> attr9001 = new LinkedHashMap<>();
        attr9001.put("id", 9001L);
        attr9001.put("aplicacion", "APLICACION_DESCONOCIDA");
        attr9001.put("atributo_codigo", "A2");
        attr9001.put("atributo_nombre", "ATTR DOS");
        attr9001.put("id_rol", 2001L);
        attr9001.put("id_suministro", 501L);
        attr9001.put("parametro", "GRUPOS");
        builder.addRow(row("atributo", attr9001));

        Map<String, Object> attr9002 = new LinkedHashMap<>();
        attr9002.put("id", 9002L);
        attr9002.put("aplicacion", "PORTAL");
        attr9002.put("atributo_codigo", "A3");
        attr9002.put("atributo_nombre", "ATTR TRES");
        attr9002.put("id_rol", 2002L);
        attr9002.put("id_suministro", 502L);
        attr9002.put("parametro", "INBOX");
        builder.addRow(row("atributo", attr9002));

        Map<String, Object> attr9003 = new LinkedHashMap<>();
        attr9003.put("id", 9003L);
        attr9003.put("aplicacion", "PORTAL");
        attr9003.put("atributo_codigo", "A4");
        attr9003.put("atributo_nombre", "ATTR CUATRO");
        attr9003.put("id_rol", 8888L);
        attr9003.put("id_suministro", 503L);
        attr9003.put("parametro", "DEFAULTINBOX");
        builder.addRow(row("atributo", attr9003));

        return builder.build();
    }

    private static ParsedRow row(String table, Map<String, Object> values) {
        return new ParsedRow(table, values);
    }
}
