/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.plan;

import static java.util.stream.Collectors.toCollection;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import de.planmarshall.core.SchemaResource;

/**
 * The two schema files of this package (ADR 018 places a codec-less block beside the store that holds it) are
 * well-formed by the rules a reader relies on: each parses with duplicate members refused, every {@code required} name
 * is a member of {@code properties}, every local {@code $ref} resolves, and the {@code $id} it declares is unique
 * across the two. The table is the package's whole set, derived from {@code src/main/resources}, not copied.
 */
@DisplayName("The plan package schema files are well-formed")
class PlanSchemaFilesTest {

    private static final Path RESOURCES = Path.of("src/main/resources");
    private static final String PACKAGE = "de/planmarshall/workflow/plan";

    private static final List<String> SCHEMAS = List.of(
            PACKAGE + "/plan-instance-header.schema.json",
            PACKAGE + "/composition-record.schema.json");

    static Stream<String> schemaFiles() {
        return SCHEMAS.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("schemaFiles")
    @DisplayName("the file parses with duplicate members refused")
    void parsesWithoutDuplicateMembers(String resource) {
        assertDoesNotThrow(() -> SchemaResource.parse(SchemaResource.text(resource)),
                "the file should parse as strict JSON");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("schemaFiles")
    @DisplayName("every required name is a member of properties")
    void requiredNamesAreProperties(String resource) {
        var schema = SchemaResource.load(resource);
        var violations = new ArrayList<String>();

        assertAll(
                () -> assertTrue(schema.containsKey("required"), "the file should declare required members"),
                () -> {
                    collectRequiredViolations(schema, "#", violations);
                    assertEquals(List.of(), violations, "every required name should be a property");
                });
    }

    private static void collectRequiredViolations(Object value, String pointer, List<String> violations) {
        switch (value) {
            case null -> {
                // a JSON null carries no declaration
            }
            case Map<?, ?> map -> {
                if (map.get("required") instanceof List<?> names && map.get("properties") instanceof Map<?, ?> declared) {
                    names.stream().map(String::valueOf).filter(name -> !declared.containsKey(name))
                            .forEach(name -> violations.add(pointer + "/" + name));
                }
                map.forEach((name, child) -> collectRequiredViolations(child, pointer + "/" + name, violations));
            }
            case List<?> list -> {
                for (var index = 0; index < list.size(); index++) {
                    collectRequiredViolations(list.get(index), pointer + "/" + index, violations);
                }
            }
            default -> {
                // a scalar carries no declaration
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("schemaFiles")
    @DisplayName("every local reference resolves")
    void localReferencesResolve(String resource) {
        var schema = SchemaResource.load(resource);
        var refs = new ArrayList<String>();
        collectLocalRefs(schema, refs);

        refs.forEach(ref -> assertDoesNotThrow(() -> resolve(schema, ref),
                "every local reference should resolve: " + ref));
    }

    @Test
    @DisplayName("the table is the package's whole schema set")
    void tableIsTheWholePackage() throws Exception {
        var derived = resourcesUnder(PACKAGE);

        assertEquals(derived, new TreeSet<>(SCHEMAS), "the table should be the package's whole set");
    }

    @Test
    @DisplayName("the declared ids are unique across the package")
    void idsAreUnique() {
        var seen = new LinkedHashSet<>();
        var duplicates = new LinkedHashSet<>();
        for (var resource : SCHEMAS) {
            var id = SchemaResource.load(resource).get("$id");
            if (id != null && !seen.add(String.valueOf(id))) {
                duplicates.add(String.valueOf(id));
            }
        }

        assertEquals(Set.of(), duplicates, "the schema ids should be unique");
    }

    @Test
    @DisplayName("the id of a file is a URI, not its file name")
    void idsAreUris() {
        for (var resource : SCHEMAS) {
            var id = SchemaResource.load(resource).get("$id");
            var name = resource.substring(resource.lastIndexOf('/') + 1).replace(".schema.json", "");
            assertAll(
                    () -> assertTrue(id == null || String.valueOf(id).startsWith("https://"),
                            "the id should be an absolute URI"),
                    () -> assertTrue(id == null || !String.valueOf(id).endsWith(name + ".schema.json"),
                            "the id should not repeat the schema file name"));
        }
    }

    private static void collectLocalRefs(Object value, List<String> refs) {
        switch (value) {
            case null -> {
                // a JSON null carries no reference
            }
            case Map<?, ?> map -> map.forEach((name, child) -> {
                if ("$ref".equals(String.valueOf(name)) && child instanceof String ref && ref.startsWith("#/")) {
                    refs.add(ref);
                } else {
                    collectLocalRefs(child, refs);
                }
            });
            case List<?> list -> list.forEach(element -> collectLocalRefs(element, refs));
            default -> {
                // a scalar carries no reference
            }
        }
    }

    private static Object resolve(Map<?, ?> schema, String ref) {
        Object current = schema;
        for (var token : ref.substring(2).split("/")) {
            var name = token.replace("~1", "/").replace("~0", "~");
            current = switch (current) {
                case Map<?, ?> map -> map.get(name);
                case List<?> list -> list.get(Integer.parseInt(name));
                default -> null;
            };
            if (current == null) {
                throw new IllegalArgumentException("the reference does not resolve: " + ref);
            }
        }
        return current;
    }

    private static Set<String> resourcesUnder(String packagePath) throws IOException {
        try (var files = Files.walk(RESOURCES.resolve(packagePath))) {
            return files.filter(path -> path.toString().endsWith(".schema.json"))
                    .map(path -> RESOURCES.relativize(path).toString().replace(File.separatorChar, '/'))
                    .collect(toCollection(TreeSet::new));
        }
    }
}
