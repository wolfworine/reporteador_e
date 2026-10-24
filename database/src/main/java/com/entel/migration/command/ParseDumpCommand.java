package com.entel.migration.command;

import com.entel.migration.dump.MysqlDumpParseException;
import com.entel.migration.dump.MysqlDumpParser;
import com.entel.migration.dump.ParsedDump;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Subcomando {@code parse-dump} (feature 3: parse_legacy_mysql_dump).
 *
 * <p>Lee un dump {@code .sql} estilo mysqldump / "MySQL Administrator" (ej.
 * {@code repositorio_entel/reposrito_roles_jsf 20150814 1214.sql}) y lo
 * parsea a estructuras Java tipadas ({@link ParsedDump}/{@code
 * com.entel.migration.dump.*}), sin conectarse nunca a un MySQL real (ver
 * docs/conventions.md). Imprime a stdout un resumen de conteo de filas por
 * tabla y el total.
 *
 * <p>El parser ({@link MysqlDumpParser}) es autodescriptivo: lee la lista
 * de columnas de cada tabla directamente de su propia sentencia
 * {@code INSERT INTO}, asi que no asume el esquema de {@code consulta.sql}
 * (ver progress/current.md / progress/history.md para el detalle de la
 * discrepancia de esquema encontrada entre ese archivo y el dump real de
 * referencia).
 *
 * <p>Codigos de salida: {@code 0} si el archivo existe y se parseo sin
 * errores; {@code 1} si falta el argumento, el archivo no existe, no se
 * pudo leer, o el parseo encontro un error irrecuperable (ej. una tupla de
 * un {@code INSERT} con un numero de valores distinto al de columnas
 * declaradas).
 */
public final class ParseDumpCommand implements MigrationCommand {

    @Override
    public int execute(String[] args, PrintStream out, PrintStream err) {
        if (args.length < 1 || args[0].isBlank()) {
            err.println("Error: uso: parse-dump <ruta-al-dump.sql>");
            return 1;
        }
        Path path = Path.of(args[0]);
        if (!Files.isRegularFile(path)) {
            err.println("Error: no existe el archivo '" + args[0] + "'.");
            return 1;
        }
        try {
            ParsedDump dump = new MysqlDumpParser().parse(path);
            printSummary(dump, path, out);
            return 0;
        } catch (IOException e) {
            err.println("Error leyendo '" + args[0] + "': " + e.getMessage());
            return 1;
        } catch (MysqlDumpParseException e) {
            err.println("Error parseando '" + args[0] + "': " + e.getMessage());
            return 1;
        }
    }

    private void printSummary(ParsedDump dump, Path path, PrintStream out) {
        out.println("parse-dump: OK (" + path + ")");
        int total = 0;
        for (String table : dump.tableNames()) {
            int count = dump.rowCount(table);
            total += count;
            out.printf("  %-16s %d filas%n", table, count);
        }
        out.println("Total: " + total + " filas en " + dump.tableNames().size() + " tablas.");
    }
}
