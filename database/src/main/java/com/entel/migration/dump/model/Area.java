package com.entel.migration.dump.model;

import com.entel.migration.dump.ParsedRow;

/**
 * Fila tipada de la tabla {@code area} (columnas verificadas contra el
 * {@code CREATE TABLE} real del dump de referencia: {@code id, area,
 * codigo}).
 */
public record Area(Long id, String area, String codigo) {

    public static Area fromRow(ParsedRow row) {
        return new Area(row.getLong("id"), row.getString("area"), row.getString("codigo"));
    }
}
