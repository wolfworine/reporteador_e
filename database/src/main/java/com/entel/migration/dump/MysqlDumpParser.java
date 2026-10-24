package com.entel.migration.dump;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser simple (deliberadamente NO un parser SQL completo, ver
 * docs/conventions.md) de dumps {@code mysqldump} / "MySQL Administrator"
 * como el usado por el sistema legado ({@code repositorio_entel/*.sql}).
 *
 * <p><b>Autodescriptivo por diseno:</b> la lista de columnas de cada tabla
 * se lee directamente de la propia sentencia {@code INSERT INTO
 * `tabla` (`col1`,`col2`,...) VALUES (...)}, nunca se asume un esquema fijo
 * (ej. el de {@code consulta.sql}). Esto es necesario porque el dump real de
 * referencia tiene un esquema distinto al de {@code consulta.sql} para
 * varias tablas (ver progress/current.md y progress/history.md para el
 * detalle de la discrepancia) - con este enfoque el parser funciona igual
 * sin importar cual de los dos esquemas trae el archivo.
 *
 * <p>Nunca se conecta a un MySQL real: todo el parseo ocurre sobre el texto
 * del archivo {@code .sql} (docs/conventions.md).
 *
 * <p>Sentencias reconocidas:
 * <ul>
 *   <li>{@code CREATE TABLE `tabla` (...)}: solo se registra el nombre de
 *       la tabla (para poder reportar tablas con cero filas, ej.
 *       {@code detalleinbox} en el dump real), no se parsean los tipos de
 *       columna - no hace falta porque el tipo real se infiere del propio
 *       valor en el {@code INSERT} (entero sin comillas -&gt; {@code Long},
 *       string entre comillas -&gt; {@code String}, literal {@code NULL}
 *       sin comillas -&gt; {@code null}).</li>
 *   <li>{@code INSERT INTO `tabla` (`col1`,...) VALUES (...),(...),...}: se
 *       parsea completo, incluyendo INSERTs multi-row (varias tuplas
 *       separadas por coma en una sola sentencia).</li>
 *   <li>Todo lo demas ({@code DROP TABLE}, {@code CREATE DATABASE},
 *       {@code USE}, {@code SET}, comentarios {@code --} y
 *       {@code /*! ... *}{@code /}, sentencias {@code ALTER TABLE ...
 *       DISABLE/ENABLE KEYS} envueltas en comentarios condicionales de
 *       MySQL, etc.) se ignora explicitamente.</li>
 * </ul>
 */
public final class MysqlDumpParser {

    private static final Pattern CREATE_TABLE_HEADER =
            Pattern.compile("^CREATE\\s+TABLE\\s+`([A-Za-z0-9_]+)`", Pattern.CASE_INSENSITIVE);

    private static final Pattern INSERT_HEADER =
            Pattern.compile("^INSERT\\s+INTO\\s+`([A-Za-z0-9_]+)`\\s*\\(([^)]*)\\)\\s*VALUES\\b",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    public ParsedDump parse(Path file) throws IOException {
        String sql = Files.readString(file, StandardCharsets.UTF_8);
        return parse(sql);
    }

    public ParsedDump parse(String sql) {
        ParsedDump.Builder builder = ParsedDump.builder();
        for (String statement : splitStatements(sql)) {
            processStatement(statement, builder);
        }
        return builder.build();
    }

    // ------------------------------------------------------------------
    // Fase 0: separar el texto completo en sentencias top-level (por ';',
    // ignorando comentarios y sin cortar dentro de strings citados).
    // ------------------------------------------------------------------

    private List<String> splitStatements(String sql) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        int length = sql.length();
        int i = 0;
        while (i < length) {
            char c = sql.charAt(i);
            if (inLineComment) {
                if (c == '\n') {
                    inLineComment = false;
                }
                i++;
                continue;
            }
            if (inBlockComment) {
                if (c == '*' && i + 1 < length && sql.charAt(i + 1) == '/') {
                    inBlockComment = false;
                    i += 2;
                    continue;
                }
                i++;
                continue;
            }
            if (!inSingleQuote && c == '-' && i + 1 < length && sql.charAt(i + 1) == '-') {
                inLineComment = true;
                i += 2;
                continue;
            }
            if (!inSingleQuote && c == '/' && i + 1 < length && sql.charAt(i + 1) == '*') {
                inBlockComment = true;
                i += 2;
                continue;
            }
            if (inSingleQuote && c == '\\' && i + 1 < length) {
                current.append(c).append(sql.charAt(i + 1));
                i += 2;
                continue;
            }
            if (c == '\'') {
                if (inSingleQuote && i + 1 < length && sql.charAt(i + 1) == '\'') {
                    // comilla escapada como '' (doblada) dentro de un string: no cierra el string.
                    current.append("''");
                    i += 2;
                    continue;
                }
                inSingleQuote = !inSingleQuote;
                current.append(c);
                i++;
                continue;
            }
            if (c == ';' && !inSingleQuote) {
                statements.add(current.toString());
                current.setLength(0);
                i++;
                continue;
            }
            current.append(c);
            i++;
        }
        if (!current.toString().isBlank()) {
            statements.add(current.toString());
        }
        return statements;
    }

    // ------------------------------------------------------------------
    // Fase 1: clasificar y procesar cada sentencia.
    // ------------------------------------------------------------------

    private void processStatement(String rawStatement, ParsedDump.Builder builder) {
        String statement = rawStatement.trim();
        if (statement.isEmpty()) {
            return;
        }
        Matcher createMatcher = CREATE_TABLE_HEADER.matcher(statement);
        if (createMatcher.lookingAt()) {
            builder.registerTable(createMatcher.group(1));
            return;
        }
        Matcher insertMatcher = INSERT_HEADER.matcher(statement);
        if (insertMatcher.lookingAt()) {
            parseInsert(statement, insertMatcher, builder);
            return;
        }
        // DROP TABLE, CREATE DATABASE, USE, SET, y cualquier otra sentencia
        // no relevante para extraer datos: se ignora explicitamente.
    }

    private void parseInsert(String statement, Matcher header, ParsedDump.Builder builder) {
        String table = header.group(1);
        List<String> columns = parseColumnList(header.group(2));
        String tuplesBlob = statement.substring(header.end());
        builder.registerTable(table);

        int tupleIndex = 0;
        for (String tupleContent : extractTuples(tuplesBlob)) {
            tupleIndex++;
            List<String> rawValues = splitTupleValues(tupleContent);
            if (rawValues.size() != columns.size()) {
                throw new MysqlDumpParseException(
                        "tabla '" + table + "': la tupla #" + tupleIndex + " tiene " + rawValues.size()
                                + " valores pero el INSERT declara " + columns.size() + " columnas " + columns);
            }
            Map<String, Object> values = new LinkedHashMap<>();
            for (int i = 0; i < columns.size(); i++) {
                values.put(columns.get(i), parseValue(rawValues.get(i)));
            }
            builder.addRow(new ParsedRow(table, values));
        }
    }

    private List<String> parseColumnList(String rawColumnList) {
        List<String> columns = new ArrayList<>();
        for (String part : rawColumnList.split(",")) {
            String trimmed = part.trim();
            if (trimmed.startsWith("`") && trimmed.endsWith("`") && trimmed.length() >= 2) {
                trimmed = trimmed.substring(1, trimmed.length() - 1);
            }
            if (!trimmed.isEmpty()) {
                columns.add(trimmed);
            }
        }
        return columns;
    }

    // ------------------------------------------------------------------
    // Fase 2: dentro del bloque "VALUES (...),(...),...", separar cada
    // tupla top-level (respetando parentesis/comas dentro de strings
    // citados, ej. el valor 'regla_negocio' de `rol` trae parentesis
    // literales: '(&(UNIDAD=GA0001)(CARGO=PU0019))').
    // ------------------------------------------------------------------

    private List<String> extractTuples(String tuplesBlob) {
        List<String> tuples = new ArrayList<>();
        int depth = 0;
        boolean inSingleQuote = false;
        StringBuilder current = null;
        int length = tuplesBlob.length();
        int i = 0;
        while (i < length) {
            char c = tuplesBlob.charAt(i);
            if (inSingleQuote) {
                if (c == '\\' && i + 1 < length) {
                    current.append(c).append(tuplesBlob.charAt(i + 1));
                    i += 2;
                    continue;
                }
                if (c == '\'') {
                    if (i + 1 < length && tuplesBlob.charAt(i + 1) == '\'') {
                        current.append("''");
                        i += 2;
                        continue;
                    }
                    inSingleQuote = false;
                    current.append(c);
                    i++;
                    continue;
                }
                current.append(c);
                i++;
                continue;
            }
            if (c == '\'') {
                inSingleQuote = true;
                if (depth > 0) {
                    current.append(c);
                }
                i++;
                continue;
            }
            if (c == '(') {
                depth++;
                if (depth == 1) {
                    current = new StringBuilder();
                } else {
                    current.append(c);
                }
                i++;
                continue;
            }
            if (c == ')') {
                if (depth == 1) {
                    tuples.add(current.toString());
                    current = null;
                } else if (depth > 1) {
                    current.append(c);
                }
                depth = Math.max(0, depth - 1);
                i++;
                continue;
            }
            if (depth > 0) {
                current.append(c);
            }
            i++;
        }
        return tuples;
    }

    // ------------------------------------------------------------------
    // Fase 3: separar el contenido de una tupla en valores individuales
    // por coma top-level (respetando comas dentro de strings citados).
    // ------------------------------------------------------------------

    private List<String> splitTupleValues(String tupleContent) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingleQuote = false;
        int length = tupleContent.length();
        int i = 0;
        while (i < length) {
            char c = tupleContent.charAt(i);
            if (inSingleQuote) {
                current.append(c);
                if (c == '\\' && i + 1 < length) {
                    current.append(tupleContent.charAt(i + 1));
                    i += 2;
                    continue;
                }
                if (c == '\'') {
                    if (i + 1 < length && tupleContent.charAt(i + 1) == '\'') {
                        current.append('\'');
                        i += 2;
                        continue;
                    }
                    inSingleQuote = false;
                }
                i++;
                continue;
            }
            if (c == '\'') {
                inSingleQuote = true;
                current.append(c);
                i++;
                continue;
            }
            if (c == ',') {
                tokens.add(current.toString().trim());
                current.setLength(0);
                i++;
                continue;
            }
            current.append(c);
            i++;
        }
        tokens.add(current.toString().trim());
        return tokens;
    }

    // ------------------------------------------------------------------
    // Fase 4: interpretar un valor crudo (con comillas/escapes todavia
    // literales) como null / String / Long.
    // ------------------------------------------------------------------

    private Object parseValue(String rawToken) {
        String token = rawToken.trim();
        if (token.equalsIgnoreCase("NULL")) {
            return null;
        }
        if (token.length() >= 2 && token.charAt(0) == '\'' && token.charAt(token.length() - 1) == '\'') {
            String inner = token.substring(1, token.length() - 1);
            return unescapeMysqlString(inner);
        }
        try {
            return Long.parseLong(token);
        } catch (NumberFormatException notLong) {
            // El dump real de referencia no trae numeros decimales sin
            // comillas (ej. `sequence`.SEQ_COUNT, aunque es decimal(38,0)
            // en el DDL, se exporta como string '0'), pero por robustez no
            // fallamos si algun dump futuro sí los trae: se preserva el
            // literal crudo como String en vez de perder el dato.
            return token;
        }
    }

    private String unescapeMysqlString(String raw) {
        StringBuilder sb = new StringBuilder(raw.length());
        int length = raw.length();
        int i = 0;
        while (i < length) {
            char c = raw.charAt(i);
            if (c == '\\' && i + 1 < length) {
                char next = raw.charAt(i + 1);
                switch (next) {
                    case '0' -> sb.append('\0');
                    case '\'' -> sb.append('\'');
                    case '"' -> sb.append('"');
                    case 'b' -> sb.append('\b');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'Z' -> sb.append((char) 26);
                    case '\\' -> sb.append('\\');
                    default -> sb.append(next);
                }
                i += 2;
                continue;
            }
            if (c == '\'' && i + 1 < length && raw.charAt(i + 1) == '\'') {
                sb.append('\'');
                i += 2;
                continue;
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }
}
