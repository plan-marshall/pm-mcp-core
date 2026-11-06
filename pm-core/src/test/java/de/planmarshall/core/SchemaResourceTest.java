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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.opentest4j.AssertionFailedError;

/**
 * The schema loader returns a present schema file unchanged and fails, never skips, on an absent resource or a file
 * that is not JSON (ADR {@code doc/adr/016-schemas-are-files-of-the-implementing-module.adoc}).
 * <p>
 * The resource paths are fixture paths and the {@code $id} is the contract literal of the fixture written for this
 * test; both stay literal by the rule for fixture paths and schema strings.
 */
@EnableGeneratorController
@DisplayName("SchemaResource")
class SchemaResourceTest {

    private static final String DIRECTORY = "de/planmarshall/core/schema-resource/";
    private static final String PRESENT = DIRECTORY + "present.schema.json";
    private static final String INVALID = DIRECTORY + "invalid.schema.json";
    private static final String NOT_OBJECT = DIRECTORY + "not-object.schema.json";
    private static final String PRESENT_ID = "https://plan-marshall.de/schema/test/present.schema.json";

    @Test
    @DisplayName("loads the present fixture with its $id and type in member order (fixture path)")
    void loadsPresentSchema() {
        var schema = SchemaResource.load(PRESENT);

        assertAll(
                () -> assertEquals(PRESENT_ID, schema.get("$id"), "The loaded schema carries the fixture $id"),
                () -> assertEquals("object", schema.get("type"), "The loaded schema carries the object type"),
                () -> assertEquals(List.of("$schema", "$id", "type", "properties", "additionalProperties"),
                        List.copyOf(schema.keySet()), "The members keep the order of the fixture"));
    }

    @Test
    @DisplayName("returns the bytes of the present fixture unchanged (fixture path)")
    void returnsTextUnchanged() throws Exception {
        var location = Objects.requireNonNull(SchemaResourceTest.class.getClassLoader().getResource(PRESENT),
                "The fixture is on the class path");
        var expected = Files.readString(Path.of(location.toURI()), StandardCharsets.UTF_8);

        var text = SchemaResource.text(PRESENT);

        assertEquals(expected, text, "The text equals the fixture file byte for byte");
    }

    @Test
    @DisplayName("fails with the path for an absent resource instead of skipping")
    void failsOnAbsentResource() {
        var absent = DIRECTORY + Generators.letterStrings(8, 16).next() + ".schema.json";

        var failure = assertThrows(AssertionFailedError.class, () -> SchemaResource.load(absent),
                "An absent schema resource fails the test");

        assertTrue(failure.getMessage().contains(absent), "The failure names the absent resource path");
    }

    @Test
    @DisplayName("fails with the path for a file that is not JSON (fixture path)")
    void failsOnInvalidJson() {
        var failure = assertThrows(AssertionFailedError.class, () -> SchemaResource.load(INVALID),
                "A schema file that is not JSON fails the test");

        assertTrue(failure.getMessage().contains(INVALID), "The failure names the invalid resource path");
    }

    @Test
    @DisplayName("fails with the path for a file whose JSON value is not an object (fixture path)")
    void failsOnNonObject() {
        var failure = assertThrows(AssertionFailedError.class, () -> SchemaResource.load(NOT_OBJECT),
                "A schema file that is not an object fails the test");

        assertTrue(failure.getMessage().contains(NOT_OBJECT), "The failure names the non-object resource path");
    }

    @ParameterizedTest(name = "refuses the document ''{0}'' (JSON syntax literal)")
    @ValueSource(strings = {"", "{} {}", "{\"a\":1,\"a\":2}"})
    void refusesDocumentsThatAreNoSingleSchemaObject(String document) {
        assertThrows(UncheckedIOException.class, () -> SchemaResource.parse(document),
                "An empty, trailing or duplicate-member document is refused");
    }
}
