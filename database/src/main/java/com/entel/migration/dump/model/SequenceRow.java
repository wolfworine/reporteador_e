package com.entel.migration.dump.model;

import com.entel.migration.dump.ParsedRow;

/**
 * Fila tipada de la tabla {@code sequence} (columnas verificadas contra el
 * {@code CREATE TABLE} real del dump de referencia: {@code SEQ_NAME,
 * SEQ_COUNT}). Es la muleta de EclipseLink para generacion de IDs (ver
 * docs/migration-plan.md y docs/architecture.md): esta feature (3, solo
 * parseo) SI la parsea porque es una tabla mas del dump, pero
 * docs/conventions.md prohibe migrarla literalmente a DynamoDB en la
 * feature 4 (migrate) - esa decision es scope de esa otra feature, no de
 * esta.
 *
 * <p>{@code seqCount} se modela como {@link String} (no {@link Long})
 * porque en el dump real el valor viene entre comillas simples (ej.
 * {@code ('SEQ_GEN','0')}) a pesar de que la columna es {@code decimal
 * (38,0)} en el DDL - se preserva tal cual el parser lo ve, sin adivinar
 * una conversion numerica no pedida por esta feature.
 */
public record SequenceRow(String seqName, String seqCount) {

    public static SequenceRow fromRow(ParsedRow row) {
        return new SequenceRow(row.getString("SEQ_NAME"), row.getString("SEQ_COUNT"));
    }
}
