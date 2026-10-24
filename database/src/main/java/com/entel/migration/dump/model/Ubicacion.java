package com.entel.migration.dump.model;

import com.entel.migration.dump.ParsedRow;

/**
 * Fila tipada de la tabla {@code ubicacion} (columnas verificadas contra el
 * {@code CREATE TABLE} real del dump de referencia: {@code id, codigo,
 * ubicacion}).
 */
public record Ubicacion(Long id, String codigo, String ubicacion) {

    public static Ubicacion fromRow(ParsedRow row) {
        return new Ubicacion(row.getLong("id"), row.getString("codigo"), row.getString("ubicacion"));
    }
}
