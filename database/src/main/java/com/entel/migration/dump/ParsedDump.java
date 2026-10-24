package com.entel.migration.dump;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resultado completo de parsear un dump {@code .sql}: filas agrupadas por
 * tabla, en el orden en que las tablas se vieron por primera vez en el
 * archivo (via {@code CREATE TABLE} o el primer {@code INSERT INTO}).
 *
 * <p>Una tabla puede existir con cero filas (ej. {@code detalleinbox} en el
 * dump real de referencia: tiene {@code CREATE TABLE} pero ningun
 * {@code INSERT}) - eso no es un error, es el estado real de esa captura de
 * datos, y se refleja como una entrada con lista vacia, no como ausencia de
 * la tabla.
 */
public final class ParsedDump {

    private final Map<String, List<ParsedRow>> rowsByTable;

    private ParsedDump(Map<String, List<ParsedRow>> rowsByTable) {
        this.rowsByTable = rowsByTable;
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<String> tableNames() {
        return List.copyOf(rowsByTable.keySet());
    }

    public boolean hasTable(String table) {
        return rowsByTable.containsKey(table);
    }

    public List<ParsedRow> rows(String table) {
        return rowsByTable.getOrDefault(table, List.of());
    }

    public int rowCount(String table) {
        return rows(table).size();
    }

    public int totalRowCount() {
        return rowsByTable.values().stream().mapToInt(List::size).sum();
    }

    public static final class Builder {
        private final Map<String, List<ParsedRow>> rowsByTable = new LinkedHashMap<>();

        private Builder() {
        }

        /** Registra una tabla vista (via CREATE TABLE o INSERT) aunque todavia no tenga filas. */
        public Builder registerTable(String table) {
            rowsByTable.computeIfAbsent(table, t -> new ArrayList<>());
            return this;
        }

        public Builder addRow(ParsedRow row) {
            rowsByTable.computeIfAbsent(row.table(), t -> new ArrayList<>()).add(row);
            return this;
        }

        public ParsedDump build() {
            Map<String, List<ParsedRow>> copy = new LinkedHashMap<>();
            for (Map.Entry<String, List<ParsedRow>> entry : rowsByTable.entrySet()) {
                copy.put(entry.getKey(), List.copyOf(entry.getValue()));
            }
            return new ParsedDump(Collections.unmodifiableMap(copy));
        }
    }
}
