package com.entel.migration.dump;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Una fila parseada de un dump mysqldump, generica: nombre de tabla mas
 * el mapa columna-&gt;valor, en el orden en que aparecen en la propia
 * sentencia {@code INSERT INTO} del dump (no un esquema fijo asumido de
 * antemano - ver {@link MysqlDumpParser}).
 *
 * <p>Cada valor es {@code null} (literal {@code NULL} del dump, distinto de
 * un string vacio {@code ''}), un {@link String} (valor entre comillas
 * simples, ya sin comillas ni escapes), o un {@link Long} (valor numerico
 * sin comillas). Notar que {@link Map#of} / {@link Map#copyOf} de Java NO
 * admiten valores {@code null}, por eso este mapa se envuelve con
 * {@link Collections#unmodifiableMap} sobre un {@link LinkedHashMap} en vez
 * de usar esos factory methods.
 */
public final class ParsedRow {

    private final String table;
    private final Map<String, Object> values;

    public ParsedRow(String table, Map<String, Object> values) {
        this.table = Objects.requireNonNull(table, "table");
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(values, "values")));
    }

    public String table() {
        return table;
    }

    /** Vista inmutable columna-&gt;valor (puede contener valores {@code null}). */
    public Map<String, Object> values() {
        return values;
    }

    public boolean hasColumn(String column) {
        return values.containsKey(column);
    }

    public Object get(String column) {
        return values.get(column);
    }

    public String getString(String column) {
        Object value = values.get(column);
        return value == null ? null : String.valueOf(value);
    }

    public Long getLong(String column) {
        Object value = values.get(column);
        if (value == null) {
            return null;
        }
        if (value instanceof Long l) {
            return l;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ParsedRow other)) {
            return false;
        }
        return table.equals(other.table) && values.equals(other.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(table, values);
    }

    @Override
    public String toString() {
        return "ParsedRow{table='" + table + "', values=" + values + '}';
    }
}
