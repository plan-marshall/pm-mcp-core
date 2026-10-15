/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core;

import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the JSON schemas the specification states, from {@code doc/specification} of this repository (test helper).
 * <p>
 * The repository root is named by the system property {@value #ROOT_PROPERTY}, which the build passes from the Maven
 * property of the same name (by default the root of the multi-module build; override it with
 * {@code -Dpm.specification.root=<path>}). The specification is part of the repository, so an unset property, a
 * missing specification directory, document or schema block is a failure, never a skipped test.
 */
public final class SpecificationDocument {

    /** The system property naming the root of the repository that holds {@code doc/specification}. */
    public static final String ROOT_PROPERTY = "pm.specification.root";

    private static final String SPECIFICATION_DIRECTORY = "doc/specification";
    private static final String JSON_SOURCE_BLOCK = "[source,json]";
    private static final String BLOCK_DELIMITER = "----";
    private static final JsonFactory JSON = new JsonFactory();

    private SpecificationDocument() {
    }

    /**
     * @return the root of the repository that holds {@code doc/specification}
     */
    public static Path root() {
        var property = System.getProperty(ROOT_PROPERTY);
        if (property == null || property.isBlank()) {
            return fail("the specification root is not configured: system property " + ROOT_PROPERTY + " is unset");
        }
        var root = Path.of(property).toAbsolutePath().normalize();
        if (!Files.isDirectory(root.resolve(SPECIFICATION_DIRECTORY))) {
            return fail("the specification is absent: " + root.resolve(SPECIFICATION_DIRECTORY)
                    + " does not exist (set -D" + ROOT_PROPERTY + "=<path>)");
        }
        return root;
    }

    /**
     * Extracts the first {@code [source,json]} block after a marker line of a specification document.
     *
     * @param document the path of the {@code .adoc} file below {@code doc/specification/}
     * @param marker   the start of the line after which the block is searched, an anchor such as
     *                 {@code [#analysis-result]} or a heading such as {@code == Findings Ledger Record}
     * @return the content of the block
     */
    public static String jsonBlockAfter(String document, String marker) {
        var file = root().resolve(SPECIFICATION_DIRECTORY).resolve(document);
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("specification document cannot be read: " + file, e);
        }
        var index = 0;
        while (index < lines.size() && !lines.get(index).startsWith(marker)) {
            index++;
        }
        if (index == lines.size()) {
            fail("no line starts with '%s' in %s".formatted(marker, file));
        }
        while (index < lines.size() && !JSON_SOURCE_BLOCK.equals(lines.get(index).trim())) {
            index++;
        }
        var open = index + 1;
        if (open >= lines.size() || !BLOCK_DELIMITER.equals(lines.get(open).trim())) {
            fail("no %s block follows '%s' in %s".formatted(JSON_SOURCE_BLOCK, marker, file));
        }
        var block = new StringBuilder();
        for (var line = open + 1; line < lines.size(); line++) {
            if (BLOCK_DELIMITER.equals(lines.get(line).trim())) {
                return block.toString();
            }
            block.append(lines.get(line)).append('\n');
        }
        return fail("the %s block after '%s' in %s is not closed".formatted(JSON_SOURCE_BLOCK, marker, file));
    }

    /**
     * Parses a JSON document into maps (in member order), lists, strings, numbers, booleans and {@code null} for a
     * JSON {@code null}.
     *
     * @param json the JSON document
     * @return the parsed value
     */
    public static Object parse(String json) {
        try (var parser = JSON.createParser(json)) {
            parser.nextToken();
            return read(parser);
        } catch (IOException e) {
            throw new UncheckedIOException("specification schema is not JSON", e);
        }
    }

    private static Object read(JsonParser parser) throws IOException {
        var token = parser.currentToken();
        return switch (token) {
            case START_OBJECT -> {
                var object = new LinkedHashMap<String, Object>();
                while (parser.nextToken() == JsonToken.FIELD_NAME) {
                    var name = parser.currentName();
                    parser.nextToken();
                    object.put(name, read(parser));
                }
                yield object;
            }
            case START_ARRAY -> {
                var array = new ArrayList<>();
                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    array.add(read(parser));
                }
                yield array;
            }
            case VALUE_STRING -> parser.getText();
            case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> parser.getNumberValue();
            case VALUE_TRUE -> Boolean.TRUE;
            case VALUE_FALSE -> Boolean.FALSE;
            case VALUE_NULL -> null;
            default -> throw new IOException("unexpected token " + token);
        };
    }

    /**
     * @param value a parsed JSON value that must be an object
     * @return the object as a map in member order
     */
    public static Map<?, ?> object(Object value) {
        if (value instanceof Map<?, ?> map) {
            return map;
        }
        return fail("not a JSON object: " + value);
    }

    /**
     * @param value a parsed JSON value that must be an array
     * @return the array as a list
     */
    public static List<?> array(Object value) {
        if (value instanceof List<?> list) {
            return list;
        }
        return fail("not a JSON array: " + value);
    }
}
