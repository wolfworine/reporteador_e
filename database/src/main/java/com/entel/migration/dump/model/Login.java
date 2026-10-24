package com.entel.migration.dump.model;

import com.entel.migration.dump.ParsedRow;

/**
 * Fila tipada de la tabla {@code login} (columnas verificadas contra el
 * {@code CREATE TABLE} real del dump de referencia: {@code id, email,
 * password}).
 */
public record Login(Long id, String email, String password) {

    public static Login fromRow(ParsedRow row) {
        return new Login(row.getLong("id"), row.getString("email"), row.getString("password"));
    }
}
