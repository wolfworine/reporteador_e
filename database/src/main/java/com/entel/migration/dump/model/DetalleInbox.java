package com.entel.migration.dump.model;

import com.entel.migration.dump.ParsedRow;

/**
 * Fila tipada de la tabla {@code detalleinbox} (columnas verificadas contra
 * el {@code CREATE TABLE} real del dump de referencia: {@code id_atributo,
 * id_detalleatributo, id, tipo_nombre, vista, tipo}, PK compuesta de las
 * primeras tres).
 *
 * <p>En el dump real de referencia esta tabla tiene {@code CREATE TABLE}
 * pero cero filas ({@code INSERT}) - por eso no hay valores reales de
 * ejemplo documentados aqui, a diferencia de las demas tablas.
 */
public record DetalleInbox(
        String idAtributo, Long idDetalleAtributo, Long id, String tipoNombre, String vista, String tipo) {

    public static DetalleInbox fromRow(ParsedRow row) {
        return new DetalleInbox(
                row.getString("id_atributo"),
                row.getLong("id_detalleatributo"),
                row.getLong("id"),
                row.getString("tipo_nombre"),
                row.getString("vista"),
                row.getString("tipo"));
    }
}
