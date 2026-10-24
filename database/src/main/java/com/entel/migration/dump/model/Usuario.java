package com.entel.migration.dump.model;

import com.entel.migration.dump.ParsedRow;

/**
 * Fila tipada de la tabla {@code usuario} (columnas verificadas contra el
 * {@code CREATE TABLE} real del dump de referencia: {@code id, email,
 * firstname, lastname, password}).
 */
public record Usuario(Long id, String email, String firstname, String lastname, String password) {

    public static Usuario fromRow(ParsedRow row) {
        return new Usuario(
                row.getLong("id"),
                row.getString("email"),
                row.getString("firstname"),
                row.getString("lastname"),
                row.getString("password"));
    }
}
