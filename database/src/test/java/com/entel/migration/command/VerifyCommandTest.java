package com.entel.migration.command;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cubre, a nivel de subcomando CLI y SIN necesitar LocalStack corriendo, la
 * validacion de argumentos de la feature 5 (data_migration_validation) -
 * mismo patron que {@link MigrateCommandTest}: estos chequeos ocurren antes
 * de tocar el archivo de dump o abrir cualquier cliente de red (ver
 * {@link VerifyCommand#execute}), asi que no requieren LocalStack.
 */
class VerifyCommandTest {

    @Test
    void targetAwsFallaConCodigoDistintoDeCeroYMensajeClaroSinTocarRed() {
        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();

        int exitCode = new VerifyCommand().execute(
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

        int exitCode = new VerifyCommand().execute(
                new String[] {"no-existe-este-archivo.sql", "--target=produccion"},
                printStream(outBuffer),
                printStream(errBuffer));

        assertEquals(1, exitCode);
        assertTrue(errBuffer.toString(StandardCharsets.UTF_8).contains("target desconocido"));
    }

    @Test
    void rutaDeDumpInexistenteFallaConCodigoDistintoDeCeroSinTocarRed() {
        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();

        int exitCode = new VerifyCommand()
                .execute(new String[] {"no-existe-este-archivo.sql"}, printStream(outBuffer), printStream(errBuffer));

        assertEquals(1, exitCode);
        assertTrue(errBuffer.toString(StandardCharsets.UTF_8).contains("no existe"));
        assertTrue(outBuffer.toString(StandardCharsets.UTF_8).isBlank());
    }

    private static PrintStream printStream(ByteArrayOutputStream buffer) {
        return new PrintStream(buffer, true, StandardCharsets.UTF_8);
    }
}
