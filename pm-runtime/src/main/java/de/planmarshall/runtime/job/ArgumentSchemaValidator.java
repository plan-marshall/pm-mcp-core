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

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import de.planmarshall.core.store.ProjectEnrolment.ArgumentSchema;
import de.planmarshall.core.store.ProjectEnrolment.BooleanValue;
import de.planmarshall.core.store.ProjectEnrolment.EnumValue;
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
import lombok.experimental.UtilityClass;

/**
 * Validates the arguments of a project script job against the script's enrolled argument schema (PM-EXEC-1,
 * PM-EXEC-2, PM-EXT-5), in the subset of JSON Schema an enrolment admits: {@code type}, {@code properties},
 * {@code required}, {@code additionalProperties: false}, {@code enum}, {@code pattern}, {@code minimum},
 * {@code maximum} and {@code maxLength}.
 * <p>
 * As in JSON Schema, a keyword that constrains one type applies only to a value of that type, so {@code type} is what
 * makes a schema demand one; {@code pattern} matches anywhere in the string, and a pattern that does not compile
 * admits no value.
 * <p>
 * A {@code pattern} is a Java regular expression the operator enrolled, matched by a backtracking engine with no time
 * budget, so its cost grows with the length of the string it is matched against. It is therefore matched only against
 * a string whose length {@code maxLength} has already bounded: {@code maxLength} is checked first, and a string
 * constrained by a {@code pattern} on a schema without {@code maxLength} is refused as
 * {@code pattern without maxLength at <path>} before any regular expression is compiled or evaluated. The subset has
 * no keyword for the elements of an array, so they are not constrained. The answer is
 * the first violated keyword and the path of the value in the arguments ({@code $} for the arguments themselves,
 * {@code $.name} for a property), never the value, so it can be logged and returned.
 *
 * @since 0.1
 */
@UtilityClass
class ArgumentSchemaValidator {

    /** The path of the arguments themselves. */
    static final String ROOT = "$";

    /** The violation of a {@code pattern} on a schema without {@code maxLength}, which is never matched. */
    static final String UNBOUNDED_PATTERN = "pattern without maxLength";

    /**
     * @param schema    the enrolled argument schema
     * @param arguments the arguments of the job
     * @return the violation as {@code <keyword> at <path>}; empty if the arguments conform
     */
    static Optional<String> validate(ArgumentSchema schema, Value arguments) {
        return validate(schema, arguments, ROOT);
    }

    private static Optional<String> validate(ArgumentSchema schema, Value value, String path) {
        if (schema.type() != null && !hasType(value, schema.type())) {
            return violation("type", path);
        }
        if (schema.enumValues() != null && schema.enumValues().stream().noneMatch(admitted -> equal(admitted, value))) {
            return violation("enum", path);
        }
        return switch (value) {
            case Text text -> text(schema, text.value(), path);
            case Numeric numeric -> number(schema, numeric.value(), path);
            case Fields fields -> object(schema, fields, path);
            case Items _, Bool _, JsonNull _ -> Optional.empty();
        };
    }

    private static Optional<String> text(ArgumentSchema schema, String text, String path) {
        if (schema.maxLength() != null && text.codePointCount(0, text.length()) > schema.maxLength()) {
            return violation("maxLength", path);
        }
        if (schema.pattern() != null && schema.maxLength() == null) {
            return violation(UNBOUNDED_PATTERN, path);
        }
        if (schema.pattern() != null && !matches(schema.pattern(), text)) {
            return violation("pattern", path);
        }
        return Optional.empty();
    }

    private static Optional<String> number(ArgumentSchema schema, BigDecimal number, String path) {
        if (schema.minimum() != null && number.compareTo(schema.minimum()) < 0) {
            return violation("minimum", path);
        }
        if (schema.maximum() != null && number.compareTo(schema.maximum()) > 0) {
            return violation("maximum", path);
        }
        return Optional.empty();
    }

    private static Optional<String> object(ArgumentSchema schema, Fields fields, String path) {
        var members = fields.fields();
        var required = schema.required() == null ? List.<String>of() : schema.required();
        for (var name : required) {
            if (!members.containsKey(name)) {
                return violation("required", property(path, name));
            }
        }
        var properties = schema.properties();
        for (var member : members.entrySet()) {
            var memberPath = property(path, member.getKey());
            var memberSchema = properties == null ? null : properties.get(member.getKey());
            if (memberSchema == null) {
                if (schema.closed()) {
                    return violation("additionalProperties", memberPath);
                }
                continue;
            }
            var violation = validate(memberSchema, member.getValue(), memberPath);
            if (violation.isPresent()) {
                return violation;
            }
        }
        return Optional.empty();
    }

    private static boolean hasType(Value value, SchemaType type) {
        return switch (type) {
            case OBJECT -> value instanceof Fields;
            case ARRAY -> value instanceof Items;
            case STRING -> value instanceof Text;
            case INTEGER -> value instanceof Numeric numeric && isInteger(numeric.value());
            case NUMBER -> value instanceof Numeric;
            case BOOLEAN -> value instanceof Bool;
            case NULL -> value instanceof JsonNull;
        };
    }

    private static boolean isInteger(BigDecimal number) {
        return number.signum() == 0 || number.stripTrailingZeros().scale() <= 0;
    }

    private static boolean equal(EnumValue admitted, Value value) {
        return switch (admitted) {
            case TextValue text -> value instanceof Text candidate && candidate.value().equals(text.value());
            case NumberValue number -> value instanceof Numeric candidate
                    && candidate.value().compareTo(number.value()) == 0;
            case BooleanValue bool -> value instanceof Bool candidate && candidate.value() == bool.value();
        };
    }

    /** A pattern that does not compile admits no value. */
    private static boolean matches(String pattern, String text) {
        try {
            return Pattern.compile(pattern).matcher(text).find();
        } catch (PatternSyntaxException e) {
            return false;
        }
    }

    private static String property(String path, String name) {
        return path + "." + name;
    }

    private static Optional<String> violation(String keyword, String path) {
        return Optional.of(keyword + " at " + path);
    }
}
