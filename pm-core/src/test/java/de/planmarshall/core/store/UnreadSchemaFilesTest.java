/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.store;

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
 * The schema files of this module that no codec and no parity test reads (ADR 018 places a codec-less block beside the
 * store that holds it). Each of them is well-formed by the rules a reader relies on: it parses with duplicate members
 * refused, every {@code required} name is a member of {@code properties}, every local {@code $ref} resolves, and the
 * {@code $id} it declares is unique across every schema file of the module.
 * <p>
 * The table is checked for completeness against the files under {@code src/main/resources}: the two small packages it
 * covers ({@code findings} and {@code role/qualification}) are derived from the directory, not copied, and every file
 * of them that no codec-backed parity test reads must be listed.
 */
@DisplayName("The unread schema files are well-formed")
class UnreadSchemaFilesTest {

    private static final Path RESOURCES = Path.of("src/main/resources");
    private static final String FINDINGS = "de/planmarshall/core/findings";
    private static final String QUALIFICATION = "de/planmarshall/core/role/qualification";

    /** The schema files a codec-backed parity test reads; they are not well-formedness cases. */
    private static final Set<String> CODEC_BACKED = Set.of(
            "de/planmarshall/core/findings/finding-record.schema.json",
            QUALIFICATION + "/judge-check-record.schema.json",
            QUALIFICATION + "/qualification-record.schema.json",
            "de/planmarshall/core/store/build-timings.schema.json");

    /** A schema file the #404 follow-up owns a reader for; not this plan's case. */
    private static final Set<String> OWNED_ELSEWHERE = Set.of(QUALIFICATION + "/judge-score-v1.schema.json");

    /** The codec-less files of this module, one case per file. */
    private static final List<String> CODECLESS = List.of(
            FINDINGS + "/assessment-record.schema.json",
            FINDINGS + "/review-bot-registry.schema.json",
            FINDINGS + "/review-participation.schema.json",
            QUALIFICATION + "/decision-calibration-record.schema.json",
            "de/planmarshall/core/store/fact-cost-calibration.schema.json",
            "de/planmarshall/core/store/fact-module-discovery.schema.json",
            "de/planmarshall/core/store/failure-envelope.schema.json",
            "de/planmarshall/core/store/gap-record.schema.json",
            "de/planmarshall/core/store/owed-item.schema.json",
            "de/planmarshall/core/store/plan-overrides.schema.json",
            "de/planmarshall/core/store/policy-counter.schema.json");

    static Stream<String> codecLessFiles() {
        return CODECLESS.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("codecLessFiles")
    @DisplayName("the file parses with duplicate members refused")
    void parsesWithoutDuplicateMembers(String resource) {
        assertDoesNotThrow(() -> SchemaResource.parse(SchemaResource.text(resource)),
                "the file should parse as strict JSON");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("codecLessFiles")
    @DisplayName("every required name is a member of properties")
    void requiredNamesAreProperties(String resource) {
        var schema = SchemaResource.load(resource);

        assertAll(
                () -> assertTrue(schema.containsKey("required"), "the file should declare required members"),
                () -> assertTrue(propertiesOf(schema).containsAll(requiredOf(schema)),
                        "every required name should be a property"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("codecLessFiles")
    @DisplayName("every local reference resolves")
    void localReferencesResolve(String resource) {
        var schema = SchemaResource.load(resource);
        var refs = new ArrayList<String>();
        collectLocalRefs(schema, refs);

        refs.forEach(ref -> assertDoesNotThrow(() -> resolve(schema, ref),
                "every local reference should resolve: " + ref));
    }

    @Test
    @DisplayName("every listed file exists under the module's resources")
    void listedFilesExist() {
        for (var resource : CODECLESS) {
            assertTrue(Files.exists(RESOURCES.resolve(resource)),
                    "the listed resource should exist: " + resource);
        }
    }

    @Test
    @DisplayName("the table covers every codec-less file of the two small packages")
    void tableCoversTheSmallPackages() throws IOException {
        var derived = new TreeSet<>(resourcesUnder(FINDINGS));
        derived.addAll(resourcesUnder(QUALIFICATION));
        derived.removeAll(CODEC_BACKED);
        derived.removeAll(OWNED_ELSEWHERE);
        var listed = CODECLESS.stream()
                .filter(resource -> resource.startsWith(FINDINGS) || resource.startsWith(QUALIFICATION))
                .collect(toCollection(TreeSet::new));

        assertEquals(derived, listed, "the table should cover the small packages' files");
    }

    @Test
    @DisplayName("the declared ids are unique across every schema file")
    void idsAreUnique() throws IOException {
        var seen = new LinkedHashSet<>();
        var duplicates = new LinkedHashSet<>();
        for (var resource : allSchemaFiles()) {
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
        for (var resource : CODECLESS) {
            var id = SchemaResource.load(resource).get("$id");
            var name = resource.substring(resource.lastIndexOf('/') + 1).replace(".schema.json", "");
            assertAll(
                    () -> assertTrue(id == null || String.valueOf(id).startsWith("https://"),
                            "the id should be an absolute URI"),
                    () -> assertTrue(id == null || !String.valueOf(id).endsWith(name + ".schema.json"),
                            "the id should not repeat the schema file name"));
        }
    }

    private static Set<String> propertiesOf(Map<?, ?> schema) {
        var properties = schema.get("properties");
        if (!(properties instanceof Map<?, ?> members)) {
            return Set.of();
        }
        return members.keySet().stream().map(String::valueOf).collect(toCollection(TreeSet::new));
    }

    private static Set<String> requiredOf(Map<?, ?> schema) {
        var required = schema.get("required");
        if (!(required instanceof List<?> names)) {
            return Set.of();
        }
        return names.stream().map(String::valueOf).collect(toCollection(TreeSet::new));
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

    private static Set<String> allSchemaFiles() throws IOException {
        try (var files = Files.walk(RESOURCES)) {
            return files.filter(path -> path.toString().endsWith(".schema.json"))
                    .map(path -> RESOURCES.relativize(path).toString().replace(File.separatorChar, '/'))
                    .collect(toCollection(TreeSet::new));
        }
    }
}
