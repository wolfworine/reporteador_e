package com.entel.migration.dump.model;

import com.entel.migration.dump.ParsedRow;

/**
 * Fila tipada de la tabla {@code atributo}.
 *
 * <p><b>Importante:</b> el esquema real de esta tabla en el dump de
 * referencia ({@code id, aplicacion, atributo_codigo, atributo_nombre,
 * id_rol, id_suministro, parametro}) NO coincide con el de
 * {@code consulta.sql} ({@code id, id_aplicacion, id_rol, id_suministro,
 * id_detalleatributo, id_estado}, con PK compuesta de 5 columnas) - ver
 * progress/current.md y progress/history.md para el detalle completo de la
 * discrepancia. Este record modela las columnas REALES del dump, no las de
 * consulta.sql: {@code aplicacion} aqui es un codigo de texto directo (no
 * una FK numerica a {@code aplicacion.id}), y no hay {@code id_estado} ni
 * {@code id_detalleatributo} porque esas columnas no existen en esta
 * captura del esquema.
 */
public record Atributo(
        Long id,
        String aplicacion,
        String atributoCodigo,
        String atributoNombre,
        Long idRol,
        Long idSuministro,
        String parametro) {

    public static Atributo fromRow(ParsedRow row) {
        return new Atributo(
                row.getLong("id"),
                row.getString("aplicacion"),
                row.getString("atributo_codigo"),
                row.getString("atributo_nombre"),
                row.getLong("id_rol"),
                row.getLong("id_suministro"),
                row.getString("parametro"));
    }
}
