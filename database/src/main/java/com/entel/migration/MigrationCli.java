package com.entel.migration;

import com.entel.migration.cli.Subcommand;
import com.entel.migration.command.DesignValidateCommand;
import com.entel.migration.command.MigrateCommand;
import com.entel.migration.command.MigrationCommand;
import com.entel.migration.command.ParseDumpCommand;
import com.entel.migration.command.VerifyCommand;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.Optional;

/**
 * Punto de entrada de la herramienta de migracion (MySQL 5.6 -&gt; DynamoDB).
 *
 * <p>Feature 1 (bootstrap_migration_cli): solo se exige que el parser
 * reconozca los cuatro subcomandos ({@code design-validate}, {@code
 * parse-dump}, {@code migrate}, {@code verify}) y que {@code --help} liste
 * el uso. La logica real de cada subcomando se agrega en las features 2-5.
 *
 * <p>Regla dura (docs/architecture.md): el target AWS real nunca es el
 * default. Esta feature no introduce todavia ningun flag {@code --target},
 * asi que no hay ninguna ruta de codigo que se conecte a AWS real.
 */
public final class MigrationCli {

    private static final String PROGRAM_NAME = "migration-cli";

    private MigrationCli() {
    }

    public static void main(String[] args) {
        int exitCode = run(args, System.out, System.err);
        System.exit(exitCode);
    }

    /**
     * Logica de la CLI extraida de {@code main} para poder testearla sin
     * depender de {@code System.exit}.
     */
    public static int run(String[] args, PrintStream out, PrintStream err) {
        if (args.length == 0) {
            err.println("Error: falta el subcomando.");
            printUsage(err);
            return 1;
        }

        String first = args[0];
        if (isHelpFlag(first)) {
            printUsage(out);
            return 0;
        }

        Optional<Subcommand> subcommand = Subcommand.fromCommandName(first);
        if (subcommand.isEmpty()) {
            err.println("Error: subcomando desconocido '" + first + "'.");
            printUsage(err);
            return 1;
        }

        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        MigrationCommand handler = createHandler(subcommand.get());
        return handler.execute(rest, out, err);
    }

    private static boolean isHelpFlag(String arg) {
        return "--help".equals(arg) || "-h".equals(arg);
    }

    private static MigrationCommand createHandler(Subcommand subcommand) {
        return switch (subcommand) {
            case DESIGN_VALIDATE -> new DesignValidateCommand();
            case PARSE_DUMP -> new ParseDumpCommand();
            case MIGRATE -> new MigrateCommand();
            case VERIFY -> new VerifyCommand();
        };
    }

    private static void printUsage(PrintStream stream) {
        stream.println("Uso: " + PROGRAM_NAME + " <subcomando> [opciones]");
        stream.println();
        stream.println("Subcomandos disponibles:");
        for (Subcommand subcommand : Subcommand.values()) {
            stream.printf("  %-16s %s%n", subcommand.commandName(), subcommand.summary());
        }
        stream.println();
        stream.println("Ejemplo: " + PROGRAM_NAME + " design-validate --help");
    }
}
