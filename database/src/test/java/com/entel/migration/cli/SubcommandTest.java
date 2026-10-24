package com.entel.migration.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubcommandTest {

    @ParameterizedTest
    @ValueSource(strings = {"design-validate", "parse-dump", "migrate", "verify"})
    void reconoceLosCuatroSubcomandosValidos(String commandName) {
        Optional<Subcommand> resolved = Subcommand.fromCommandName(commandName);
        assertTrue(resolved.isPresent(), "Deberia reconocer el subcomando: " + commandName);
        assertEquals(commandName, resolved.get().commandName());
    }

    @Test
    void rechazaUnSubcomandoInvalidoSinLanzarExcepcion() {
        Optional<Subcommand> resolved = Subcommand.fromCommandName("no-existe");
        assertTrue(resolved.isEmpty());
    }

    @Test
    void rechazaNombreNuloSinLanzarExcepcion() {
        Optional<Subcommand> resolved = Subcommand.fromCommandName(null);
        assertTrue(resolved.isEmpty());
    }

    @Test
    void existenExactamenteCuatroSubcomandos() {
        assertEquals(4, Subcommand.values().length);
    }
}
