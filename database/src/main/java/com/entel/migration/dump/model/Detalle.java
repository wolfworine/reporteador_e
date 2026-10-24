package com.entel.migration.dump.model;

import com.entel.migration.dump.ParsedRow;

/**
 * Fila tipada de la tabla {@code detalle} (columnas verificadas contra el
 * {@code CREATE TABLE} real del dump de referencia: {@code id,
 * atributo_codigo, atributo_nombre, parametro}).
 */
public record Detalle(Long id, String atributoCodigo, String atributoNombre, String parametro) {

    public static Detalle fromRow(ParsedRow row) {
        return new Detalle(
                row.getLong("id"),
                row.getString("atributo_codigo"),
                row.getString("atributo_nombre"),
                row.getString("parametro"));
    }
}
