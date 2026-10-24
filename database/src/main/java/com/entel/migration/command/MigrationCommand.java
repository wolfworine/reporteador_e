package com.entel.migration.command;

import java.io.PrintStream;

/**
 * Contrato comun de los cuatro subcomandos de la CLI de migracion.
 *
 * <p>Cada implementacion recibe los argumentos restantes (sin el nombre del
 * subcomando ya consumido) y los streams de salida/error, y devuelve un
 * codigo de salida de proceso. Nunca debe lanzar una excepcion no
 * controlada: cualquier fallo esperable se traduce a un mensaje por
 * {@code err} y un codigo de salida distinto de cero.
 */
public interface MigrationCommand {

    int execute(String[] args, PrintStream out, PrintStream err);
}
