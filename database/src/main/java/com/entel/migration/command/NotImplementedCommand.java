package com.entel.migration.command;

import java.io.PrintStream;

/**
 * Placeholder comun para subcomandos cuya logica real todavia no fue
 * implementada (queda documentado a que feature del track de migracion
 * pertenece cada uno). No lanza excepciones ni hace nada destructivo:
 * imprime un aviso claro y devuelve un codigo de salida distinto de cero
 * para dejar explicito que el subcomando existe pero no hace trabajo real
 * todavia.
 *
 * <p>Las subclases concretas ({@link DesignValidateCommand},
 * {@link ParseDumpCommand}, {@link MigrateCommand}, {@link VerifyCommand})
 * se iran reemplazando por implementaciones reales en las features 2-5,
 * sin tocar el parser de la CLI (feature 1).
 */
abstract class NotImplementedCommand implements MigrationCommand {

    private final String commandName;
    private final String featureReference;

    protected NotImplementedCommand(String commandName, String featureReference) {
        this.commandName = commandName;
        this.featureReference = featureReference;
    }

    @Override
    public final int execute(String[] args, PrintStream out, PrintStream err) {
        out.println("'" + commandName + "' todavia no esta implementado (" + featureReference + ").");
        return 1;
    }
}
