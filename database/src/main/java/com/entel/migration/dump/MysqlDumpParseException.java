package com.entel.migration.dump;

/**
 * Error irrecuperable durante el parseo de un dump {@code .sql} (feature 3:
 * parse_legacy_mysql_dump). Se lanza, por ejemplo, cuando una tupla de un
 * {@code INSERT INTO} trae un numero de valores distinto al numero de
 * columnas declaradas en la propia sentencia.
 *
 * <p>Es una {@link RuntimeException} deliberadamente: {@code ParseDumpCommand}
 * la captura explicitamente y la traduce a un mensaje por {@code err} y un
 * codigo de salida distinto de cero (ver {@code MigrationCommand}), nunca
 * deja que se propague sin control.
 */
public final class MysqlDumpParseException extends RuntimeException {

    public MysqlDumpParseException(String message) {
        super(message);
    }

    public MysqlDumpParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
