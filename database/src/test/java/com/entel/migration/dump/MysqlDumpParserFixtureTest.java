package com.entel.migration.dump;

import com.entel.migration.dump.model.Aplicacion;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cubre el acceptance criteria "Test unitario con un fixture de dump
 * reducido... que verifica el parseo campo a campo de al menos 3 tablas" y
 * "Manejo explicito de valores NULL / campos opcionales del dump" de la
 * feature 3 (parse_legacy_mysql_dump).
 *
 * <p>Fixture: {@code src/test/resources/dumps/sample-dump.sql}, escrito a
 * mano imitando el formato real (ver comentario de cabecera del propio
 * archivo), con 4 tablas: {@code aplicacion}, {@code rol}, {@code estado}
 * (con un NULL explicito) y {@code detalleinbox} (0 filas).
 */
class MysqlDumpParserFixtureTest {

    private static final String FIXTURE_RESOURCE = "/dumps/sample-dump.sql";

    @Test
    void parseaAplicacionCampoACampo() {
        ParsedDump dump = parseFixture();

        List<ParsedRow> rows = dump.rows("aplicacion");
        assertEquals(1, rows.size());

        ParsedRow row = rows.get(0);
        assertEquals(1L, row.getLong("id"));
        assertEquals("1", row.getString("codigo"));
        assertEquals("PORTAL", row.getString("aplicacion"));

        // La estructura tipada tambien debe poder construirse desde la fila generica.
        Aplicacion aplicacion = Aplicacion.fromRow(row);
        assertEquals(1L, aplicacion.id());
        assertEquals("1", aplicacion.codigo());
        assertEquals("PORTAL", aplicacion.aplicacion());
    }

    @Test
    void parseaRolCampoACampoIncluyendoEscapesYParentesisDentroDeString() {
        ParsedDump dump = parseFixture();

        List<ParsedRow> rows = dump.rows("rol");
        assertEquals(2, rows.size());

        ParsedRow first = rows.get(0);
        assertEquals(2000L, first.getLong("id"));
        assertEquals("RO_GERENTE DE ARQUITECTURA", first.getString("nombre"));
        // El valor citado trae parentesis literales - el parser no debe
        // confundirlos con el cierre de la tupla.
        assertEquals("(&(UNIDAD=GA0001)(CARGO=PU0019))", first.getString("regla_negocio"));

        ParsedRow second = rows.get(1);
        assertEquals(2001L, second.getLong("id"));
        // La comilla escapada (\') dentro del literal debe des-escaparse a un solo caracter comilla.
        assertEquals("RO_ANALISTA'S TEAM", second.getString("nombre"));
        assertEquals("(&(UNIDAD=GA0002)(CARGO=PU0020))", second.getString("regla_negocio"));
    }

    @Test
    void manejaElLiteralNullExplicitamenteDistintoDeStringVacio() {
        ParsedDump dump = parseFixture();

        List<ParsedRow> rows = dump.rows("estado");
        assertEquals(2, rows.size());

        ParsedRow activo = rows.get(0);
        assertEquals(1L, activo.getLong("id"));
        assertEquals("ACTIVO", activo.getString("nombre"));
        assertTrue(activo.hasColumn("observacion"), "la columna debe existir en el mapa aunque su valor sea null");
        assertNull(activo.get("observacion"), "NULL sin comillas debe parsear a null, no a string vacio");
        assertNull(activo.getString("observacion"));

        ParsedRow inactivo = rows.get(1);
        assertEquals(2L, inactivo.getLong("id"));
        assertEquals("revisar manualmente", inactivo.getString("observacion"));
        assertFalse(inactivo.getString("observacion").isEmpty());
    }

    @Test
    void tablaConCreateTableSinInsertsSeReportaConCeroFilasNoSeOmite() {
        ParsedDump dump = parseFixture();

        assertTrue(dump.hasTable("detalleinbox"), "detalleinbox debe estar registrada (tiene CREATE TABLE)");
        assertEquals(0, dump.rowCount("detalleinbox"));
        assertTrue(dump.rows("detalleinbox").isEmpty());
    }

    @Test
    void elTotalDeFilasEsLaSumaDeLasCuatroTablas() {
        ParsedDump dump = parseFixture();

        // aplicacion=1, rol=2, estado=2, detalleinbox=0
        assertEquals(5, dump.totalRowCount());
        assertEquals(List.of("aplicacion", "rol", "estado", "detalleinbox"), dump.tableNames());
    }

    private static ParsedDump parseFixture() {
        try (InputStream in = MysqlDumpParserFixtureTest.class.getResourceAsStream(FIXTURE_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("No se encontro el fixture de test " + FIXTURE_RESOURCE);
            }
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return new MysqlDumpParser().parse(sql);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
