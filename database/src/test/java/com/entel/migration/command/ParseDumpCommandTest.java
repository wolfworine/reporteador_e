package com.entel.migration.command;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Cubre a nivel de subcomando CLI el acceptance criteria de la feature 3
 * (parse_legacy_mysql_dump): {@code parse-dump} recibe la ruta a un
 * {@code .sql}, la parsea, imprime un resumen de conteo por tabla, y
 * termina con exit code 0 en exito / distinto de 0 en error (archivo
 * inexistente o argumento faltante).
 */
class ParseDumpCommandTest {

    private static final Path REAL_DUMP_PATH =
            Path.of("D:\\Proyecto textil\\repositorio_entel\\reposrito_roles_jsf 20150814 1214.sql");

    @Test
    void sinArgumentosFallaConCodigoDistintoDeCero() {
        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();

        int exitCode = new ParseDumpCommand().execute(new String[0], printStream(outBuffer), printStream(errBuffer));

        assertEquals(1, exitCode);
        assertTrue(errBuffer.toString(StandardCharsets.UTF_8).toLowerCase().contains("uso"));
    }

    @Test
    void archivoInexistenteFallaConCodigoDistintoDeCero() {
        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();

        int exitCode = new ParseDumpCommand()
                .execute(new String[] {"no-existe-este-archivo.sql"}, printStream(outBuffer), printStream(errBuffer));

        assertEquals(1, exitCode);
        assertTrue(errBuffer.toString(StandardCharsets.UTF_8).contains("no existe"));
    }

    @Test
    void parseaElFixtureReducidoYReportaConteosCorrectosPorTabla(@org.junit.jupiter.api.io.TempDir Path tempDir)
            throws IOException {
        Path fixtureCopy = tempDir.resolve("sample-dump.sql");
        copyResourceTo("/dumps/sample-dump.sql", fixtureCopy);

        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();

        int exitCode = new ParseDumpCommand()
                .execute(new String[] {fixtureCopy.toString()}, printStream(outBuffer), printStream(errBuffer));

        assertEquals(0, exitCode, "stderr: " + errBuffer.toString(StandardCharsets.UTF_8));
        String output = outBuffer.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains(tableCountLine("aplicacion", 1)));
        assertTrue(output.contains(tableCountLine("rol", 2)));
        assertTrue(output.contains(tableCountLine("estado", 2)));
        assertTrue(output.contains(tableCountLine("detalleinbox", 0)));
        assertTrue(output.contains("Total: 5 filas en 4 tablas."));
    }

    @Test
    void parseaElDumpRealYReportaLosConteosVerificados() {
        assumeTrue(
                Files.isRegularFile(REAL_DUMP_PATH),
                () -> "El dump real no esta presente en " + REAL_DUMP_PATH + " - se omite este test.");

        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();

        int exitCode = new ParseDumpCommand()
                .execute(new String[] {REAL_DUMP_PATH.toString()}, printStream(outBuffer), printStream(errBuffer));

        assertEquals(0, exitCode, "stderr: " + errBuffer.toString(StandardCharsets.UTF_8));
        String output = outBuffer.toString(StandardCharsets.UTF_8);

        // Conteos reales verificados de forma independiente (ver
        // MysqlDumpParserRealDumpTest y progress/current.md para el detalle
        // de por que difieren de los conteos originales del brief, que en
        // realidad contaban sentencias INSERT y no filas).
        assertTrue(output.contains(tableCountLine("aplicacion", 1)));
        assertTrue(output.contains(tableCountLine("area", 69)));
        assertTrue(output.contains(tableCountLine("atributo", 15565)));
        assertTrue(output.contains(tableCountLine("cargo", 695)));
        assertTrue(output.contains(tableCountLine("detalle", 684)));
        assertTrue(output.contains(tableCountLine("detalleinbox", 0)));
        assertTrue(output.contains(tableCountLine("login", 4)));
        assertTrue(output.contains(tableCountLine("rol", 741)));
        assertTrue(output.contains(tableCountLine("sequence", 1)));
        assertTrue(output.contains(tableCountLine("suministro", 741)));
        assertTrue(output.contains(tableCountLine("ubicacion", 61)));
        assertTrue(output.contains(tableCountLine("usuario", 4)));
        assertTrue(output.contains("Total: 18566 filas en 12 tablas."));
    }

    /** Reproduce exactamente el formato de linea usado por {@code ParseDumpCommand#printSummary}. */
    private static String tableCountLine(String table, int count) {
        return String.format("%-16s %d filas", table, count);
    }

    private static void copyResourceTo(String resource, Path target) throws IOException {
        try (InputStream in = ParseDumpCommandTest.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("No se encontro el recurso de test " + resource);
            }
            Files.copy(in, target);
        }
    }

    private static PrintStream printStream(ByteArrayOutputStream buffer) {
        return new PrintStream(buffer, true, StandardCharsets.UTF_8);
    }
}
