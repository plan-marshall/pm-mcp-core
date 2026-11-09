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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.opentest4j.AssertionFailedError;

/**
 * The schema loader returns a present schema file unchanged and fails, never skips, on an absent resource or a file
 * that is not JSON, and its casts fail on a value of another JSON type (ADR {@code doc/adr/016-schemas-are-files-of-the-implementing-module.adoc}).
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
    private static final String INVALID_ENCODING = DIRECTORY + "invalid-encoding.schema.json";
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

    @ParameterizedTest(name = "fails with the path for the fixture {0}")
    @ValueSource(strings = {INVALID, NOT_OBJECT, INVALID_ENCODING})
    @DisplayName("fails with the path for a file that is not JSON, not an object or not UTF-8 (fixture paths)")
    void failsOnUnusableFile(String resource) {
        var failure = assertThrows(AssertionFailedError.class, () -> SchemaResource.load(resource),
                "An unusable schema file fails the test");

        assertTrue(failure.getMessage().contains(resource), "The failure names the unusable resource path");
    }

    @ParameterizedTest(name = "refuses the document ''{0}'' (JSON syntax literal)")
    @ValueSource(strings = {"", "{} {}", "{\"a\":1,\"a\":2}"})
    void refusesDocumentsThatAreNoSingleSchemaObject(String document) {
        assertThrows(UncheckedIOException.class, () -> SchemaResource.parse(document),
                "An empty, trailing or duplicate-member document is refused");
    }

    /** The failure texts "not a JSON object" and "not a JSON array" are the contract literals of the casts. */
    @Nested
    @DisplayName("casts a parsed value")
    class Casts {

        @Test
        @DisplayName("returns a JSON object as the same map")
        void objectReturnsMap() {
            var object = Map.of(Generators.letterStrings(1, 12).next(), Generators.integers().next());

            assertSame(object, SchemaResource.object(object), "The object cast returns the same map");
        }

        @Test
        @DisplayName("returns a JSON array as the same list")
        void arrayReturnsList() {
            var array = Generators.asCollectionGenerator(Generators.letterStrings(1, 12)).list(3);

            assertSame(array, SchemaResource.array(array), "The array cast returns the same list");
        }

        @Test
        @DisplayName("fails the object cast for an array, a string and null instead of skipping")
        void objectFailsOnOtherType() {
            var array = List.of(Generators.integers().next());
            var string = Generators.letterStrings(1, 12).next();

            assertAll(
                    () -> assertTrue(objectFailure(array).contains("not a JSON object"),
                            "An array fails the object cast"),
                    () -> assertTrue(objectFailure(string).contains("not a JSON object"),
                            "A string fails the object cast"),
                    () -> assertTrue(objectFailure(null).contains("not a JSON object"),
                            "A JSON null fails the object cast"));
        }

        @Test
        @DisplayName("fails the array cast for an object, a string and null instead of skipping")
        void arrayFailsOnOtherType() {
            var object = Map.of(Generators.letterStrings(1, 12).next(), Generators.integers().next());
            var string = Generators.letterStrings(1, 12).next();

            assertAll(
                    () -> assertTrue(arrayFailure(object).contains("not a JSON array"),
                            "An object fails the array cast"),
                    () -> assertTrue(arrayFailure(string).contains("not a JSON array"),
                            "A string fails the array cast"),
                    () -> assertTrue(arrayFailure(null).contains("not a JSON array"),
                            "A JSON null fails the array cast"));
        }

        private static String objectFailure(@Nullable Object value) {
            return assertThrows(AssertionFailedError.class, () -> SchemaResource.object(value),
                    "A value of another type fails the object cast").getMessage();
        }

        private static String arrayFailure(@Nullable Object value) {
            return assertThrows(AssertionFailedError.class, () -> SchemaResource.array(value),
                    "A value of another type fails the array cast").getMessage();
        }
    }
}
