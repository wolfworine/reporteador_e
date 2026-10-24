package com.entel.migration.dump.model;

import com.entel.migration.dump.ParsedRow;

/**
 * Fila tipada de la tabla {@code cargo} (columnas verificadas contra el
 * {@code CREATE TABLE} real del dump de referencia: {@code id, codigo,
 * nombre}).
 */
public record Cargo(Long id, String codigo, String nombre) {

    public static Cargo fromRow(ParsedRow row) {
        return new Cargo(row.getLong("id"), row.getString("codigo"), row.getString("nombre"));
    }
}
