/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.job;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import de.planmarshall.core.store.ProjectEnrolment.ArgumentSchema;
import de.planmarshall.core.store.ProjectEnrolment.BooleanValue;
import de.planmarshall.core.store.ProjectEnrolment.NumberValue;
import de.planmarshall.core.store.ProjectEnrolment.SchemaType;
import de.planmarshall.core.store.ProjectEnrolment.TextValue;
import de.planmarshall.runtime.job.JobRequest.Bool;
import de.planmarshall.runtime.job.JobRequest.Fields;
import de.planmarshall.runtime.job.JobRequest.Items;
import de.planmarshall.runtime.job.JobRequest.JsonNull;
import de.planmarshall.runtime.job.JobRequest.Numeric;
import de.planmarshall.runtime.job.JobRequest.Text;
import de.planmarshall.runtime.job.JobRequest.Value;

/**
 * The argument schema subset of a project script (PM-EXEC-1, PM-EXT-5): one test per keyword, each with a violating
 * and a conforming argument set; a violation names the keyword and the path, never the value.
 */
@EnableGeneratorController
@DisplayName("ArgumentSchemaValidator")
class ArgumentSchemaValidatorTest {

    private static final Optional<String> CONFORMS = Optional.empty();

    /**
     * Far longer than the validator needs without matching, far shorter than {@code ^(a+)+$} needs to backtrack over
     * 64 {@code a} and a {@code !}: a validator that matched would fail the assertion instead of hanging the build.
     */
    private static final Duration BACKTRACKING_BUDGET = Duration.ofSeconds(5);

    private static ArgumentSchema schema(SchemaType type) {
        return new ArgumentSchema(type, null, null, false, null, null, null, null, null);
    }

    private static Optional<String> validate(ArgumentSchema schema, Value value) {
        return ArgumentSchemaValidator.validate(schema, value);
    }

    /** @return a generated string no keyword of the schema constrains */
    private static Text anyText() {
        return new Text(Generators.letterStrings(1, 16).next());
    }

    private static Numeric number(String value) {
        return new Numeric(new BigDecimal(value));
    }

    /** An object schema with the one property {@code name} of the given schema. */
    private static ArgumentSchema object(ArgumentSchema name, List<String> required, boolean closed) {
        var properties = new TreeMap<String, ArgumentSchema>();
        properties.put("name", name);
        return new ArgumentSchema(SchemaType.OBJECT, properties, required, closed, null, null, null, null, null);
    }

    @ParameterizedTest(name = "{0} rejects a value of another type")
    @CsvSource({"OBJECT", "ARRAY", "STRING", "INTEGER", "NUMBER", "BOOLEAN", "NULL"})
    @DisplayName("type: a value of another type is refused, a value of the type conforms")
    void type(SchemaType type) {
        var conforming = switch (type) {
            case OBJECT -> Fields.of(Map.of());
            case ARRAY -> new Items(List.of());
            case STRING -> anyText();
            case INTEGER -> number("7");
            case NUMBER -> number("7.5");
            case BOOLEAN -> new Bool(true);
            case NULL -> new JsonNull();
        };
        Value violating = type == SchemaType.STRING ? new Bool(false) : anyText();

        assertAll(
                () -> assertEquals(Optional.of("type at $"), validate(schema(type), violating)),
                () -> assertEquals(CONFORMS, validate(schema(type), conforming)));
    }

    @Test
    @DisplayName("type integer: a number with a fraction is refused, one with a zero fraction conforms")
    void integerFraction() {
        assertAll(
                () -> assertEquals(Optional.of("type at $"), validate(schema(SchemaType.INTEGER), number("7.5"))),
                () -> assertEquals(CONFORMS, validate(schema(SchemaType.INTEGER), number("7.00"))));
    }

    @Test
    @DisplayName("properties: a property that violates its schema is refused with its path")
    void properties() {
        var schema = object(schema(SchemaType.STRING), null, false);

        assertAll(
                () -> assertEquals(Optional.of("type at $.name"),
                        validate(schema, Fields.of(Map.of("name", number("1"))))),
                () -> assertEquals(CONFORMS, validate(schema, Fields.of(Map.of("name", anyText())))));
    }

    @Test
    @DisplayName("required: a missing required property is refused")
    void required() {
        var schema = object(schema(SchemaType.STRING), List.of("name"), false);

        assertAll(
                () -> assertEquals(Optional.of("required at $.name"), validate(schema, Fields.of(Map.of()))),
                () -> assertEquals(CONFORMS, validate(schema, Fields.of(Map.of("name", anyText())))));
    }

    @Test
    @DisplayName("additionalProperties false: a property the schema does not declare is refused")
    void additionalProperties() {
        var closed = object(schema(SchemaType.STRING), null, true);
        var open = object(schema(SchemaType.STRING), null, false);
        var extra = Fields.of(Map.of("name", anyText(), "other", anyText()));

        assertAll(
                () -> assertEquals(Optional.of("additionalProperties at $.other"), validate(closed, extra)),
                () -> assertEquals(CONFORMS, validate(open, extra)),
                () -> assertEquals(CONFORMS, validate(closed, Fields.of(Map.of("name", anyText())))));
    }

    @Test
    @DisplayName("enum: a value outside the admitted values is refused; strings, numbers and booleans compare")
    void enumValues() {
        var schema = new ArgumentSchema(null, null, null, false,
                List.of(new TextValue("fast"), new NumberValue(new BigDecimal("3")), new BooleanValue(true)), null,
                null, null, null);

        assertAll(
                () -> assertEquals(Optional.of("enum at $"), validate(schema, new Text("slow"))),
                () -> assertEquals(Optional.of("enum at $"), validate(schema, new Bool(false))),
                () -> assertEquals(CONFORMS, validate(schema, new Text("fast"))),
                () -> assertEquals(CONFORMS, validate(schema, number("3.0"))),
                () -> assertEquals(CONFORMS, validate(schema, new Bool(true))));
    }

    /** A string schema with this pattern and this maxLength. */
    private static ArgumentSchema pattern(String pattern, Long maxLength) {
        return new ArgumentSchema(SchemaType.STRING, null, null, false, null, pattern, null, null, maxLength);
    }

    @Test
    @DisplayName("pattern: a string the pattern does not find is refused; a pattern that does not compile admits none")
    void pattern() {
        var schema = pattern("^[a-z]+$", 16L);
        var broken = pattern("[a-", 16L);

        assertAll(
                () -> assertEquals(Optional.of("pattern at $"), validate(schema, new Text("Upper"))),
                () -> assertEquals(Optional.of("pattern at $"), validate(broken, new Text("a"))),
                () -> assertEquals(CONFORMS, validate(schema, new Text("lower"))));
    }

    @Test
    @DisplayName("pattern without maxLength: refused with the path, whatever the string, before any regex runs")
    void patternWithoutMaxLength() {
        var unbounded = pattern("^[a-z]+$", null);
        var catastrophic = pattern("^(a+)+$", null);
        var broken = pattern("[a-", null);
        var lower = new Text(Generators.letterStrings(1, 16).next().toLowerCase(Locale.ROOT));
        var hostile = new Text("a".repeat(64) + "!");

        assertAll(
                () -> assertEquals(Optional.of("pattern without maxLength at $"), validate(unbounded, lower)),
                () -> assertEquals(Optional.of("pattern without maxLength at $"), validate(broken, lower)),
                () -> assertEquals(Optional.of("pattern without maxLength at $.name"),
                        validate(object(unbounded, null, false), Fields.of(Map.of("name", lower)))),
                () -> assertEquals(Optional.of("pattern without maxLength at $"),
                        assertTimeoutPreemptively(BACKTRACKING_BUDGET, () -> validate(catastrophic, hostile))));
    }

    @Test
    @DisplayName("pattern with maxLength: a conforming string within the bound conforms")
    void patternWithMaxLength() {
        var lower = new Text(Generators.letterStrings(1, 16).next().toLowerCase(Locale.ROOT));

        assertEquals(CONFORMS, validate(pattern("^[a-z]+$", 16L), lower));
    }

    @Test
    @DisplayName("pattern at the maxLength ceiling: a string of the ceiling's length is matched, not cut short")
    void patternAtMaxLengthCeiling() {
        var ceiling = ArgumentSchema.PATTERN_MAX_LENGTH_CEILING;
        var schema = pattern("^[a-z]+$", ceiling);
        var length = Math.toIntExact(ceiling);

        assertAll(
                () -> assertEquals(ceiling, schema.maxLength().longValue()),
                () -> assertEquals(CONFORMS, validate(schema, new Text("a".repeat(length)))),
                () -> assertEquals(CONFORMS, validate(schema, new Text("a".repeat(length - 1)))));
    }

    @Test
    @DisplayName("pattern with maxLength: a longer string is refused by maxLength, the pattern is never evaluated")
    void maxLengthBeforePattern() {
        var catastrophic = pattern("^(a+)+$", 16L);
        var broken = pattern("[a-", 16L);
        var hostile = new Text("a".repeat(64) + "!");

        assertAll(
                () -> assertEquals(Optional.of("maxLength at $"), validate(broken, hostile)),
                () -> assertEquals(Optional.of("maxLength at $"),
                        assertTimeoutPreemptively(BACKTRACKING_BUDGET, () -> validate(catastrophic, hostile))));
    }

    @Test
    @DisplayName("minimum: a number below the minimum is refused, the minimum itself conforms")
    void minimum() {
        var schema = new ArgumentSchema(SchemaType.NUMBER, null, null, false, null, null, new BigDecimal("10"), null,
                null);

        assertAll(
                () -> assertEquals(Optional.of("minimum at $"), validate(schema, number("9.99"))),
                () -> assertEquals(CONFORMS, validate(schema, number("10"))));
    }

    @Test
    @DisplayName("maximum: a number above the maximum is refused, the maximum itself conforms")
    void maximum() {
        var schema = new ArgumentSchema(SchemaType.NUMBER, null, null, false, null, null, null, new BigDecimal("10"),
                null);

        assertAll(
                () -> assertEquals(Optional.of("maximum at $"), validate(schema, number("10.01"))),
                () -> assertEquals(CONFORMS, validate(schema, number("10"))));
    }

    @Test
    @DisplayName("maxLength: a longer string is refused, counted in code points")
    void maxLength() {
        var schema = new ArgumentSchema(SchemaType.STRING, null, null, false, null, null, null, null, 3L);

        assertAll(
                () -> assertEquals(Optional.of("maxLength at $"), validate(schema, new Text("abcd"))),
                () -> assertEquals(CONFORMS, validate(schema, new Text("a😀c"))));
    }

    @Test
    @DisplayName("the empty schema admits every value")
    void anySchema() {
        assertEquals(CONFORMS, validate(ArgumentSchema.any(), Fields.of(Map.of("x", new Items(List.of())))));
    }
}
