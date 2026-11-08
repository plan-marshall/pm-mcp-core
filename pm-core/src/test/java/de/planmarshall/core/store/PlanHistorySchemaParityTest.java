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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.networknt.schema.InputFormat;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import de.planmarshall.core.SchemaResource;
import de.planmarshall.core.store.PlanHistoryLine.Event;

/**
 * The codec of the plan's history keeps parity with the schema of Store Schemas § The Plan's History (PM-ARCH-11): the
 * members {@link PlanHistoryCodec#write(PlanHistoryLine)} emits are the schema's members in schema order and every one
 * of them is required, the values of {@code event} are the wire names of {@link Event}, the format version is the
 * schema's {@code const}, the bounds of the schema (the {@code maxItems} of the call path, the minimums of {@code seq}
 * and {@code instance_version}, the pattern of {@code outcome}) are the limits the codec enforces, and every line the
 * codec writes is valid against the schema. The codec exposes no member
 * list, so the test reads the members of a written line. The schema is the file
 * {@code de/planmarshall/core/store/history-line.schema.json} beside the codec, read from the class path.
 */
@EnableGeneratorController
@DisplayName("Plan history line schema parity")
// The schema file, its member names and the event wire names are contracts; the line values are generated.
class PlanHistorySchemaParityTest {

    private static final String SCHEMA = "de/planmarshall/core/store/history-line.schema.json";

    private static Map<?, ?> schema;
    private static Map<?, ?> properties;
    private static JsonSchema validator;

    @BeforeAll
    static void readSchema() {
        schema = SchemaResource.load(SCHEMA);
        properties = SchemaResource.object(schema.get("properties"));
        validator = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
                .getSchema(SchemaResource.text(SCHEMA));
    }

    @Test
    @DisplayName("the members the codec writes are the members of the schema in schema order")
    void membersEqualSchema() {
        var expected = names(properties.keySet());

        var written = names(writtenMembers(line(Event.LEFT, lowerWord(), lowerWord())).keySet());

        assertAll(
                () -> assertFalse(expected.isEmpty(), "The schema declares the members of a line"),
                () -> assertEquals(expected, written, "The codec writes the schema members in order"));
    }

    @Test
    @DisplayName("every member the codec writes is required by the schema")
    void requiredEqualsMembers() {
        var written = Set.copyOf(names(writtenMembers(line(Event.ENTERED, null, null)).keySet()));

        assertEquals(written, Set.copyOf(names(SchemaResource.array(schema.get("required")))),
                "The schema requires every member the codec writes");
    }

    @Test
    @DisplayName("the events and the format version are those of the schema")
    void valuesEqualSchema() {
        var events = Set.copyOf(names(
                SchemaResource.array(SchemaResource.object(properties.get("event")).get("enum"))));
        var formatVersion = ((Number) SchemaResource.object(properties.get("format_version")).get("const"))
                .intValue();

        assertAll(
                () -> assertEquals(events, Arrays.stream(Event.values()).map(Event::wireName)
                        .collect(Collectors.toUnmodifiableSet()), "The events are the wire names of the schema"),
                () -> assertEquals(PlanHistoryLine.FORMAT_VERSION, formatVersion,
                        "The format version is the schema const"));
    }

    @ParameterizedTest(name = "a written {0} line is valid with and without phase and outcome")
    @EnumSource(Event.class)
    @DisplayName("every line the codec writes is valid against the schema")
    void writtenLinesAreValid(Event event) {
        var bare = text(line(event, null, null));
        var full = text(line(event, lowerWord(), lowerWord()));

        assertAll(
                () -> assertEquals(Set.of(), validator.validate(bare, InputFormat.JSON),
                        "A line without phase and outcome is valid"),
                () -> assertEquals(Set.of(), validator.validate(full, InputFormat.JSON),
                        "A line with phase and outcome is valid"));
    }

    @Test
    @DisplayName("a line with an undeclared member is a violation of the schema")
    void undeclaredMemberIsViolation() {
        var written = text(line(Event.CALLED, lowerWord(), null));
        var extended = written.substring(0, written.length() - 1) + ",\"undeclared_" + lowerWord() + "\":1}";

        assertFalse(validator.validate(extended, InputFormat.JSON).isEmpty(),
                "An undeclared member violates the schema");
    }

    @Test
    @DisplayName("the bounds of the schema are the limits the codec enforces")
    void boundsEqualLimits() {
        var callPathMax = ((Number) SchemaResource.object(properties.get("call_path")).get("maxItems")).intValue();
        var seqMin = ((Number) SchemaResource.object(properties.get("seq")).get("minimum")).longValue();
        var versionMin = ((Number) SchemaResource.object(properties.get("instance_version")).get("minimum"))
                .intValue();
        var outcomePattern = Pattern.compile(
                String.valueOf(SchemaResource.object(properties.get("outcome")).get("pattern")));
        var word = lowerWord();
        var notWord = word.toUpperCase(Locale.ROOT);

        assertAll(
                () -> assertEquals(PlanHistoryLine.MAX_CALL_PATH, callPathMax,
                        "The call path limit is the schema maxItems"),
                () -> assertDoesNotThrow(() -> build(seqMin, versionMin, callPathMax, word),
                        "The codec accepts every schema minimum"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> build(seqMin - 1, versionMin, callPathMax, word),
                        "One below the seq minimum is refused"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> build(seqMin, versionMin - 1, callPathMax, word),
                        "One below the version minimum is refused"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> build(seqMin, versionMin, callPathMax + 1, word),
                        "One frame above the maxItems is refused"),
                () -> assertTrue(outcomePattern.matcher(word).matches(),
                        "The schema outcome pattern accepts a word"),
                () -> assertFalse(outcomePattern.matcher(notWord).matches(),
                        "The schema outcome pattern refuses capitals"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> build(seqMin, versionMin, callPathMax, notWord),
                        "The codec refuses what the pattern refuses"));
    }

    private static PlanHistoryLine build(long seq, int instanceVersion, int callFrames, String outcome) {
        var callPath = Generators.asCollectionGenerator(Generators.letterStrings(1, 16)).list(callFrames);
        return PlanHistoryLine.of(seq, Event.LEFT, Generators.letterStrings(1, 32).next(), callPath, null, outcome,
                instanceVersion, Instant.ofEpochSecond(Generators.longs(0, 4_000_000_000L).next()));
    }

    private static PlanHistoryLine line(Event event, @Nullable String phase, @Nullable String outcome) {
        var callPath = Generators.asCollectionGenerator(Generators.letterStrings(1, 16))
                .list(Generators.integers(0, PlanHistoryLine.MAX_CALL_PATH).next());
        return PlanHistoryLine.of(Generators.longs(1, 1_000_000).next(), event, Generators.letterStrings(1, 32).next(),
                callPath, phase, outcome, Generators.integers(1, 1_000).next(),
                Instant.ofEpochSecond(Generators.longs(0, 4_000_000_000L).next()));
    }

    /** A lowercase word, which fits the outcome pattern of the schema. */
    private static String lowerWord() {
        return Generators.letterStrings(1, 16).next().toLowerCase(Locale.ROOT);
    }

    private static String text(PlanHistoryLine line) {
        return new String(PlanHistoryCodec.write(line), UTF_8);
    }

    private static Map<?, ?> writtenMembers(PlanHistoryLine line) {
        return SchemaResource.object(SchemaResource.parse(text(line)));
    }

    private static List<String> names(Collection<?> values) {
        return values.stream().map(String::valueOf).toList();
    }
}
