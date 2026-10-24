package com.entel.migration.dump.model;

import com.entel.migration.dump.ParsedRow;

/**
 * Fila tipada de la tabla {@code rol} (columnas verificadas contra el
 * {@code CREATE TABLE} real del dump de referencia: {@code id, ambito,
 * area, cargo, descripcion, nombre, propietario_persona, propietario_rol,
 * regla_negocio, tipo, ubicacion}).
 *
 * <p>Nota: {@code area}, {@code cargo} y {@code ubicacion} aqui son
 * columnas de texto denormalizadas de {@code rol} (no referencias a las
 * tablas {@code area}/{@code cargo}/{@code ubicacion}) - asi esta el dato
 * en el dump real, no es un error del modelo.
 */
public record Rol(
        Long id,
        String ambito,
        String area,
        String cargo,
        String descripcion,
        String nombre,
        String propietarioPersona,
        String propietarioRol,
        String reglaNegocio,
        String tipo,
        String ubicacion) {

    public static Rol fromRow(ParsedRow row) {
        return new Rol(
                row.getLong("id"),
                row.getString("ambito"),
                row.getString("area"),
                row.getString("cargo"),
                row.getString("descripcion"),
                row.getString("nombre"),
                row.getString("propietario_persona"),
                row.getString("propietario_rol"),
                row.getString("regla_negocio"),
                row.getString("tipo"),
                row.getString("ubicacion"));
    }
}
