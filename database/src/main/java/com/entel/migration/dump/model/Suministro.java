package com.entel.migration.dump.model;

import com.entel.migration.dump.ParsedRow;

/**
 * Fila tipada de la tabla {@code suministro} (columnas verificadas contra el
 * {@code CREATE TABLE} real del dump de referencia: {@code id, id_rol,
 * nombre, prioridad}).
 */
public record Suministro(Long id, Long idRol, String nombre, String prioridad) {

    public static Suministro fromRow(ParsedRow row) {
        return new Suministro(
                row.getLong("id"), row.getLong("id_rol"), row.getString("nombre"), row.getString("prioridad"));
    }
}
