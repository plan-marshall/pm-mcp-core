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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Reads the JSON schemas the specification states, from {@code doc/specification} of this repository (test helper).
 * <p>
 * The repository root is named by the system property {@value #ROOT_PROPERTY}, which the build passes from the Maven
 * property of the same name (by default the root of the multi-module build; override it with
 * {@code -Dpm.specification.root=<path>}). The specification is part of the repository, so an unset property, a
 * missing specification directory, document or schema block is a failure, never a skipped test.
 */
// Every datum of this class is a contract: specification paths, AsciiDoc markers and error messages.
public final class SpecificationDocument {

    /** The system property naming the root of the repository that holds {@code doc/specification}. */
    public static final String ROOT_PROPERTY = "pm.specification.root";

    private static final String SPECIFICATION_DIRECTORY = "doc/specification";
    private static final String JSON_SOURCE_BLOCK = "[source,json]";
    private static final String BLOCK_DELIMITER = "----";

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
     * Parses a JSON document; delegates to {@link SchemaResource#parse(String)}, so the parser exists once.
     *
     * @param json the JSON document
     * @return the parsed value: maps (in member order), lists, strings, numbers, booleans and {@code null} for a JSON
     *         {@code null}
     */
    public static Object parse(String json) {
        return SchemaResource.parse(json);
    }

    /**
     * Casts a parsed JSON value that must be an object; delegates to {@link SchemaResource#object(Object)}, so the
     * cast exists once.
     *
     * @param value a parsed JSON value that must be an object
     * @return the object as a map in member order
     */
    public static Map<?, ?> object(@Nullable Object value) {
        return SchemaResource.object(value);
    }

    /**
     * Casts a parsed JSON value that must be an array; delegates to {@link SchemaResource#array(Object)}, so the cast
     * exists once.
     *
     * @param value a parsed JSON value that must be an array
     * @return the array as a list
     */
    public static List<?> array(@Nullable Object value) {
        return SchemaResource.array(value);
    }
}
