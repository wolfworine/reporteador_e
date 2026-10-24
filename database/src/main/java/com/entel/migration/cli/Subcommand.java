package com.entel.migration.cli;

import java.util.Arrays;
import java.util.Optional;

/**
 * Subcomandos reconocidos por la herramienta de migración.
 *
 * <p>La lógica real de cada uno se implementa en features separadas del
 * track de migración (ver {@code feature_list.json}):
 * <ul>
 *   <li>{@code design-validate} - feature 2 (design_dynamodb_schema)</li>
 *   <li>{@code parse-dump} - feature 3 (parse_legacy_mysql_dump)</li>
 *   <li>{@code migrate} - feature 4 (migrate_data_mysql_to_dynamodb)</li>
 *   <li>{@code verify} - feature 5 (data_migration_validation)</li>
 * </ul>
 * Esta feature (1, bootstrap_migration_cli) solo exige que los cuatro
 * existan y sean reconocidos por el parser de argumentos.
 */
public enum Subcommand {
    DESIGN_VALIDATE("design-validate", "Valida y crea el diseno de tablas DynamoDB (single-table + GSIs)."),
    PARSE_DUMP("parse-dump", "Parsea un dump .sql (mysqldump) a estructuras Java tipadas."),
    MIGRATE("migrate", "Carga los datos parseados a DynamoDB de forma idempotente."),
    VERIFY("verify", "Valida conteos e integridad referencial post-migracion.");

    private final String commandName;
    private final String summary;

    Subcommand(String commandName, String summary) {
        this.commandName = commandName;
        this.summary = summary;
    }

    public String commandName() {
        return commandName;
    }

    public String summary() {
        return summary;
    }

    /**
     * Busca un subcomando por su nombre en linea de comandos (ej. "migrate").
     * Devuelve {@link Optional#empty()} si no coincide con ninguno de los
     * subcomandos reconocidos (no lanza excepcion).
     */
    public static Optional<Subcommand> fromCommandName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(sub -> sub.commandName.equals(name))
                .findFirst();
    }
}
