package com.entel.migration.dump.model;

import com.entel.migration.dump.ParsedRow;

/**
 * Fila tipada de la tabla {@code aplicacion} (columnas verificadas contra el
 * {@code CREATE TABLE} real del dump de referencia: {@code id, codigo,
 * aplicacion}).
 */
public record Aplicacion(Long id, String codigo, String aplicacion) {

    public static Aplicacion fromRow(ParsedRow row) {
        return new Aplicacion(row.getLong("id"), row.getString("codigo"), row.getString("aplicacion"));
    }
}
