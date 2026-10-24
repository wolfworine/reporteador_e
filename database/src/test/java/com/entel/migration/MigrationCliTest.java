package com.entel.migration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cubre el acceptance criteria de la feature 1 (bootstrap_migration_cli):
 * el parser de la CLI reconoce los cuatro subcomandos, rechaza uno
 * invalido, y el smoke test de --help lista los subcomandos.
 */
class MigrationCliTest {

    @Test
    void sinArgumentosImprimeUsoYFallaConCodigoDistintoDeCero() {
        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();

        int exitCode = MigrationCli.run(new String[] {}, printStream(outBuffer), printStream(errBuffer));

        assertEquals(1, exitCode);
        assertTrue(errBuffer.toString(StandardCharsets.UTF_8).contains("subcomando"));
    }

    @Test
    void helpListaLosCuatroSubcomandosYTerminaEnCero() {
        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();

        int exitCode = MigrationCli.run(new String[] {"--help"}, printStream(outBuffer), printStream(errBuffer));

        String output = outBuffer.toString(StandardCharsets.UTF_8);
        assertEquals(0, exitCode);
        assertTrue(output.contains("design-validate"), "debe listar design-validate");
        assertTrue(output.contains("parse-dump"), "debe listar parse-dump");
        assertTrue(output.contains("migrate"), "debe listar migrate");
        assertTrue(output.contains("verify"), "debe listar verify");
    }

    @Test
    void subcomandoInvalidoNoRompeYDevuelveCodigoDistintoDeCero() {
        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();

        int exitCode = assertDoesNotThrow(() ->
                MigrationCli.run(new String[] {"no-existe"}, printStream(outBuffer), printStream(errBuffer)));

        assertEquals(1, exitCode);
        assertTrue(errBuffer.toString(StandardCharsets.UTF_8).contains("no-existe"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"design-validate", "parse-dump", "migrate", "verify"})
    void cadaSubcomandoValidoSeEjecutaSinLanzarExcepcion(String commandName) {
        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();

        assertDoesNotThrow(() ->
                MigrationCli.run(new String[] {commandName}, printStream(outBuffer), printStream(errBuffer)));
    }

    private static PrintStream printStream(ByteArrayOutputStream buffer) {
        return new PrintStream(buffer, true, StandardCharsets.UTF_8);
    }
}
