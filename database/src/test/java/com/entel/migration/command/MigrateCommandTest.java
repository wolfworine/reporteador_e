package com.entel.migration.command;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cubre, a nivel de subcomando CLI y SIN necesitar LocalStack corriendo, dos
 * acceptance criteria de la feature 4 (migrate_data_mysql_to_dynamodb):
 *
 * <ul>
 *   <li>"El target AWS real solo se activa con {@code --target=aws} explicito,
 *       nunca por defecto, y no se ejercita en tests" - se verifica que
 *       {@code --target=aws} falla con codigo de salida distinto de cero y un
 *       mensaje claro en stderr, ANTES de tocar el archivo de dump o abrir
 *       cualquier cliente de red (ver {@link MigrateCommand#execute}: el
 *       chequeo de {@code target} ocurre antes de leer el archivo, parsear
 *       el dump, o construir un {@code DynamoDbAsyncClient} - por eso este
 *       test puede usar una ruta de dump inexistente sin que eso enmascare
 *       lo que se esta probando).</li>
 *   <li>Ruta de dump inexistente/faltante devuelve codigo de salida distinto
 *       de cero con un mensaje claro (tampoco requiere LocalStack: el
 *       chequeo {@code Files.isRegularFile} ocurre antes de cualquier
 *       llamada de red).</li>
 * </ul>
 */
class MigrateCommandTest {

    @Test
    void targetAwsFallaConCodigoDistintoDeCeroYMensajeClaroSinTocarRed() {
        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();

        // Ruta de dump deliberadamente inexistente: si el codigo intentara
        // leer el archivo antes de rechazar --target=aws, este test fallaria
        // con un mensaje de error distinto (archivo no encontrado) en vez
        // del mensaje esperado sobre el target.
        int exitCode = new MigrateCommand().execute(
                new String[] {"no-existe-este-archivo.sql", "--target=aws"},
                printStream(outBuffer),
                printStream(errBuffer));

        assertEquals(1, exitCode, "--target=aws debe fallar, nunca ejecutarse");
        String stderr = errBuffer.toString(StandardCharsets.UTF_8);
        assertTrue(stderr.contains("--target=aws"), "el mensaje de error debe mencionar el flag rechazado: " + stderr);
        assertTrue(
                stderr.toLowerCase().contains("no esta soportado") || stderr.toLowerCase().contains("aws real"),
                "el mensaje de error debe explicar por que se rechaza: " + stderr);
        assertTrue(outBuffer.toString(StandardCharsets.UTF_8).isBlank(), "no debe haber salida de exito en stdout");
    }

    @Test
    void targetDesconocidoTambienFallaConCodigoDistintoDeCero() {
        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();

        int exitCode = new MigrateCommand().execute(
                new String[] {"no-existe-este-archivo.sql", "--target=produccion"},
                printStream(outBuffer),
                printStream(errBuffer));

        assertEquals(1, exitCode);
        assertTrue(errBuffer.toString(StandardCharsets.UTF_8).contains("target desconocido"));
    }

    @Test
    void archivoDeDumpInexistenteFallaConCodigoDistintoDeCero() {
        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();

        int exitCode = new MigrateCommand()
                .execute(new String[] {"no-existe-este-archivo.sql"}, printStream(outBuffer), printStream(errBuffer));

        assertEquals(1, exitCode);
        assertTrue(errBuffer.toString(StandardCharsets.UTF_8).contains("no existe"));
        assertTrue(outBuffer.toString(StandardCharsets.UTF_8).isBlank());
    }

    @Test
    void sinArgumentosFallaConCodigoDistintoDeCero() {
        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();

        int exitCode = new MigrateCommand().execute(new String[0], printStream(outBuffer), printStream(errBuffer));

        assertEquals(1, exitCode);
        assertTrue(errBuffer.toString(StandardCharsets.UTF_8).toLowerCase().contains("uso"));
    }

    private static PrintStream printStream(ByteArrayOutputStream buffer) {
        return new PrintStream(buffer, true, StandardCharsets.UTF_8);
    }
}
