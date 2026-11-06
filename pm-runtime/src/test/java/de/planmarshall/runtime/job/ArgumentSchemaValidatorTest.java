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
import org.junit.jupiter.params.provider.EnumSource;

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
 * <p>
 * Contract literals that stay: the violation texts ({@code keyword at path}), the schema patterns (a lower-case word,
 * a pattern that does not compile, the catastrophic-backtracking pattern and its hostile input) and the code-point
 * boundary of {@code maxLength}. Property names, strings, numbers and bounds are generated.
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

    /** @return a generated word of lower-case letters */
    private static String lower() {
        return Generators.letterStrings(1, 16).next().toLowerCase(Locale.ROOT);
    }

    private static Numeric number(String value) {
        return new Numeric(new BigDecimal(value));
    }

    /** @return a generated whole number as its text */
    private static String wholeNumber() {
        return String.valueOf(Generators.integers(-1_000_000, 1_000_000).next());
    }

    /** An object schema with the one property of that name and schema. */
    private static ArgumentSchema object(String name, ArgumentSchema schema, List<String> required, boolean closed) {
        var properties = new TreeMap<String, ArgumentSchema>();
        properties.put(name, schema);
        return new ArgumentSchema(SchemaType.OBJECT, properties, required, closed, null, null, null, null, null);
    }

    @ParameterizedTest(name = "{0} rejects a value of another type")
    @EnumSource(SchemaType.class)
    @DisplayName("type: a value of another type is refused, a value of the type conforms")
    void type(SchemaType type) {
        var conforming = switch (type) {
            case OBJECT -> Fields.of(Map.of());
            case ARRAY -> new Items(List.of());
            case STRING -> anyText();
            case INTEGER -> number(wholeNumber());
            case NUMBER -> number(wholeNumber() + ".5");
            case BOOLEAN -> new Bool(Generators.booleans().next());
            case NULL -> new JsonNull();
        };
        Value violating = type == SchemaType.STRING ? new Bool(Generators.booleans().next()) : anyText();

        assertAll(
                () -> assertEquals(Optional.of("type at $"), validate(schema(type), violating),
                        "a value of another type is refused"),
                () -> assertEquals(CONFORMS, validate(schema(type), conforming),
                        "a value of the schema type conforms"));
    }

    @Test
    @DisplayName("type integer: a number with a fraction is refused, one with a zero fraction conforms")
    void integerFraction() {
        var whole = wholeNumber();

        assertAll(
                () -> assertEquals(Optional.of("type at $"), validate(schema(SchemaType.INTEGER), number(whole + ".5")),
                        "a number with a fraction is no integer"),
                () -> assertEquals(CONFORMS, validate(schema(SchemaType.INTEGER), number(whole + ".00")),
                        "a zero fraction still conforms as integer"));
    }

    @Test
    @DisplayName("properties: a property that violates its schema is refused with its path")
    void properties() {
        var name = lower();
        var schema = object(name, schema(SchemaType.STRING), null, false);

        assertAll(
                () -> assertEquals(Optional.of("type at $." + name),
                        validate(schema, Fields.of(Map.of(name, number(wholeNumber())))),
                        "the violating property is named by path"),
                () -> assertEquals(CONFORMS, validate(schema, Fields.of(Map.of(name, anyText()))),
                        "a conforming property conforms"));
    }

    @Test
    @DisplayName("required: a missing required property is refused")
    void required() {
        var name = lower();
        var schema = object(name, schema(SchemaType.STRING), List.of(name), false);

        assertAll(
                () -> assertEquals(Optional.of("required at $." + name), validate(schema, Fields.of(Map.of())),
                        "a missing required property is refused"),
                () -> assertEquals(CONFORMS, validate(schema, Fields.of(Map.of(name, anyText()))),
                        "the present required property conforms"));
    }

    @Test
    @DisplayName("additionalProperties false: a property the schema does not declare is refused")
    void additionalProperties() {
        var name = "declared-" + lower();
        var other = "other-" + lower();
        var closed = object(name, schema(SchemaType.STRING), null, true);
        var open = object(name, schema(SchemaType.STRING), null, false);
        var extra = Fields.of(Map.of(name, anyText(), other, anyText()));

        assertAll(
                () -> assertEquals(Optional.of("additionalProperties at $." + other), validate(closed, extra),
                        "a closed schema refuses the extra property"),
                () -> assertEquals(CONFORMS, validate(open, extra), "an open schema admits the extra property"),
                () -> assertEquals(CONFORMS, validate(closed, Fields.of(Map.of(name, anyText()))),
                        "a closed schema admits its own property"));
    }

    @Test
    @DisplayName("enum: a value outside the admitted values is refused; strings, numbers and booleans compare")
    void enumValues() {
        var admitted = "admitted-" + lower();
        var whole = wholeNumber();
        var flag = Generators.booleans().next();
        var schema = new ArgumentSchema(null, null, null, false,
                List.of(new TextValue(admitted), new NumberValue(new BigDecimal(whole)), new BooleanValue(flag)),
                null, null, null, null);

        assertAll(
                () -> assertEquals(Optional.of("enum at $"), validate(schema, new Text("other-" + lower())),
                        "a string outside the enum is refused"),
                () -> assertEquals(Optional.of("enum at $"), validate(schema, new Bool(!flag)),
                        "a boolean outside the enum is refused"),
                () -> assertEquals(CONFORMS, validate(schema, new Text(admitted)),
                        "the admitted string conforms"),
                () -> assertEquals(CONFORMS, validate(schema, number(whole + ".0")),
                        "the admitted number compares by value"),
                () -> assertEquals(CONFORMS, validate(schema, new Bool(flag)), "the admitted boolean conforms"));
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
        var upper = Generators.letterStrings(1, 15).next().toUpperCase(Locale.ROOT);

        assertAll(
                () -> assertEquals(Optional.of("pattern at $"), validate(schema, new Text(upper)),
                        "a string the pattern misses is refused"),
                () -> assertEquals(Optional.of("pattern at $"), validate(broken, new Text(lower())),
                        "a broken pattern admits no string"),
                () -> assertEquals(CONFORMS, validate(schema, new Text(lower())),
                        "a string the pattern finds conforms"));
    }

    @Test
    @DisplayName("pattern without maxLength: refused with the path, whatever the string, before any regex runs")
    void patternWithoutMaxLength() {
        var unbounded = pattern("^[a-z]+$", null);
        var catastrophic = pattern("^(a+)+$", null);
        var broken = pattern("[a-", null);
        var lower = new Text(lower());
        var name = lower();
        var hostile = new Text("a".repeat(64) + "!");

        assertAll(
                () -> assertEquals(Optional.of("pattern without maxLength at $"), validate(unbounded, lower),
                        "an unbounded pattern refuses any string"),
                () -> assertEquals(Optional.of("pattern without maxLength at $"), validate(broken, lower),
                        "an unbounded broken pattern is refused"),
                () -> assertEquals(Optional.of("pattern without maxLength at $." + name),
                        validate(object(name, unbounded, null, false), Fields.of(Map.of(name, lower))),
                        "an unbounded property is named by path"),
                () -> assertEquals(Optional.of("pattern without maxLength at $"),
                        assertTimeoutPreemptively(BACKTRACKING_BUDGET, () -> validate(catastrophic, hostile),
                                "the hostile input is refused in time"),
                        "the hostile input is refused unmatched"));
    }

    @Test
    @DisplayName("pattern with maxLength: a conforming string within the bound conforms")
    void patternWithMaxLength() {
        assertEquals(CONFORMS, validate(pattern("^[a-z]+$", 16L), new Text(lower())),
                "a bounded conforming string conforms");
    }

    @Test
    @DisplayName("pattern at the maxLength ceiling: a string of the ceiling's length is matched, not cut short")
    void patternAtMaxLengthCeiling() {
        var ceiling = ArgumentSchema.PATTERN_MAX_LENGTH_CEILING;
        var schema = pattern("^[a-z]+$", ceiling);
        var length = Math.toIntExact(ceiling);

        assertAll(
                () -> assertEquals(ceiling, schema.maxLength().longValue(), "the schema admits the ceiling"),
                () -> assertEquals(CONFORMS, validate(schema, new Text("a".repeat(length))),
                        "a string of the ceiling's length conforms"),
                () -> assertEquals(CONFORMS, validate(schema, new Text("a".repeat(length - 1))),
                        "a string below the ceiling conforms"));
    }

    @Test
    @DisplayName("pattern with maxLength: a longer string is refused by maxLength, the pattern is never evaluated")
    void maxLengthBeforePattern() {
        var catastrophic = pattern("^(a+)+$", 16L);
        var broken = pattern("[a-", 16L);
        var hostile = new Text("a".repeat(64) + "!");

        assertAll(
                () -> assertEquals(Optional.of("maxLength at $"), validate(broken, hostile),
                        "maxLength refuses before the broken pattern"),
                () -> assertEquals(Optional.of("maxLength at $"),
                        assertTimeoutPreemptively(BACKTRACKING_BUDGET, () -> validate(catastrophic, hostile),
                                "the hostile input is refused in time"),
                        "maxLength refuses before backtracking"));
    }

    @Test
    @DisplayName("minimum: a number below the minimum is refused, the minimum itself conforms")
    void minimum() {
        var bound = new BigDecimal(wholeNumber());
        var schema = new ArgumentSchema(SchemaType.NUMBER, null, null, false, null, null, bound, null, null);

        assertAll(
                () -> assertEquals(Optional.of("minimum at $"),
                        validate(schema, new Numeric(bound.subtract(new BigDecimal("0.01")))),
                        "a number below the minimum is refused"),
                () -> assertEquals(CONFORMS, validate(schema, new Numeric(bound)),
                        "the minimum itself conforms"));
    }

    @Test
    @DisplayName("maximum: a number above the maximum is refused, the maximum itself conforms")
    void maximum() {
        var bound = new BigDecimal(wholeNumber());
        var schema = new ArgumentSchema(SchemaType.NUMBER, null, null, false, null, null, null, bound, null);

        assertAll(
                () -> assertEquals(Optional.of("maximum at $"),
                        validate(schema, new Numeric(bound.add(new BigDecimal("0.01")))),
                        "a number above the maximum is refused"),
                () -> assertEquals(CONFORMS, validate(schema, new Numeric(bound)),
                        "the maximum itself conforms"));
    }

    @Test
    @DisplayName("maxLength: a longer string is refused, counted in code points (boundary literals)")
    void maxLength() {
        var schema = new ArgumentSchema(SchemaType.STRING, null, null, false, null, null, null, null, 3L);

        assertAll(
                () -> assertEquals(Optional.of("maxLength at $"), validate(schema, new Text("abcd")),
                        "four code points exceed a maxLength of 3"),
                () -> assertEquals(CONFORMS, validate(schema, new Text("a😀c")),
                        "a surrogate pair counts as one code point"));
    }

    @Test
    @DisplayName("the empty schema admits every value")
    void anySchema() {
        assertEquals(CONFORMS, validate(ArgumentSchema.any(), Fields.of(Map.of(lower(), new Items(List.of())))),
                "the empty schema admits an object");
    }
}
