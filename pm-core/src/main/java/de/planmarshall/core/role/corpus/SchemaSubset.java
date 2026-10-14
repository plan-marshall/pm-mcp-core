/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.role.corpus;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import lombok.experimental.UtilityClass;

import de.planmarshall.core.role.corpus.JsonValue.JsonBoolean;
import de.planmarshall.core.role.corpus.JsonValue.JsonList;
import de.planmarshall.core.role.corpus.JsonValue.JsonNull;
import de.planmarshall.core.role.corpus.JsonValue.JsonNumber;
import de.planmarshall.core.role.corpus.JsonValue.JsonObject;
import de.planmarshall.core.role.corpus.JsonValue.JsonString;

/**
 * Evaluates the subset of JSON Schema that the corpus schemas use and {@code validate.py} of the corpus evaluates
 * (PM-WORK-6): {@code type}, {@code const}, {@code enum}, {@code pattern}, {@code maxLength}, {@code minimum},
 * {@code maximum}, {@code minItems}, {@code items}, {@code required}, {@code properties} and
 * {@code additionalProperties: false}. Every other keyword ({@code format}, {@code description}, ...) is not
 * evaluated.
 * <p>
 * The evaluation follows {@code validate.py}: a value of a type the schema does not admit is reported once and not
 * looked into further; a boolean is never a {@code number} nor an {@code integer}, and an {@code integer} is a number
 * written without fraction or exponent; {@code pattern} matches anywhere in the string unless anchored;
 * {@code maxLength} counts code points; two numbers are equal by value, and a boolean equals no number. Each violation
 * is reported as a message that starts with the path of the value, {@code $} for the document.
 *
 * @since 0.1
 */
@UtilityClass
public class SchemaSubset {

    /** The path of the document itself. */
    public static final String ROOT = "$";

    /**
     * @param value  the value to check
     * @param schema the schema, a JSON object of the keyword subset
     * @return the violations, each starting with the path of the value; empty if the value is valid
     */
    public static List<String> validate(JsonValue value, JsonValue schema) {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(schema, "schema");
        var errors = new ArrayList<String>();
        check(value, schema, ROOT, errors);
        return List.copyOf(errors);
    }

    /**
     * @param a a value
     * @param b another value
     * @return {@code true} if both are the same JSON value; numbers compare by value
     */
    static boolean same(JsonValue a, JsonValue b) {
        return switch (a) {
            case JsonNumber number when b instanceof JsonNumber other -> number.value().compareTo(other.value()) == 0;
            case JsonList array when b instanceof JsonList other -> sameElements(array, other);
            case JsonObject object when b instanceof JsonObject other -> object.members().keySet()
                    .equals(other.members().keySet())
                    && object.members().entrySet().stream()
                    .allMatch(member -> same(member.getValue(), other.members().get(member.getKey())));
            default -> a.equals(b);
        };
    }

    private static boolean sameElements(JsonList array, JsonList other) {
        if (array.elements().size() != other.elements().size()) {
            return false;
        }
        for (var index = 0; index < array.elements().size(); index++) {
            if (!same(array.elements().get(index), other.elements().get(index))) {
                return false;
            }
        }
        return true;
    }

    private static void check(JsonValue value, JsonValue schemaValue, String path, List<String> errors) {
        if (!(schemaValue instanceof JsonObject schema)) {
            return;
        }
        var kinds = kinds(schema);
        if (!kinds.isEmpty() && kinds.stream().noneMatch(kind -> isOfType(value, kind))) {
            errors.add("%s: expected %s".formatted(path, kinds));
            return;
        }
        schema.get("const").filter(constant -> !same(value, constant))
                .ifPresent(constant -> errors.add("%s: must be %s".formatted(path, constant.toJson())));
        schema.get("enum").filter(JsonList.class::isInstance).map(JsonList.class::cast)
                .filter(members -> members.elements().stream().noneMatch(member -> same(value, member)))
                .ifPresent(members -> errors.add("%s: %s not in %s".formatted(path, value.toJson(), members.toJson())));
        switch (value) {
            case JsonString string -> checkString(string.value(), schema, path, errors);
            case JsonNumber number -> checkNumber(number, schema, path, errors);
            case JsonList array -> checkArray(array, schema, path, errors);
            case JsonObject object -> checkObject(object, schema, path, errors);
            default -> {
                // a boolean or null has no keyword of its own in the subset
            }
        }
    }

    private static List<String> kinds(JsonObject schema) {
        return switch (schema.get("type").orElse(null)) {
            case JsonString kind -> List.of(kind.value());
            case JsonList kinds -> kinds.elements().stream().filter(JsonString.class::isInstance)
                    .map(kind -> ((JsonString) kind).value()).toList();
            case null, default -> List.of();
        };
    }

    private static boolean isOfType(JsonValue value, String kind) {
        return switch (kind) {
            case "object" -> value instanceof JsonObject;
            case "array" -> value instanceof JsonList;
            case "string" -> value instanceof JsonString;
            case "number" -> value instanceof JsonNumber;
            case "integer" -> value instanceof JsonNumber number && number.integral();
            case "boolean" -> value instanceof JsonBoolean;
            case "null" -> value instanceof JsonNull;
            default -> false;
        };
    }

    private static void checkString(String value, JsonObject schema, String path, List<String> errors) {
        if (schema.get("pattern").orElse(null) instanceof JsonString pattern) {
            try {
                if (!Pattern.compile(pattern.value()).matcher(value).find()) {
                    errors.add("%s: does not match %s".formatted(path, pattern.value()));
                }
            } catch (PatternSyntaxException e) {
                errors.add("%s: the schema pattern %s does not compile: %s".formatted(path, pattern.value(),
                        e.getDescription()));
            }
        }
        if (schema.get("maxLength").orElse(null) instanceof JsonNumber maxLength
                && maxLength.value().compareTo(BigDecimal.valueOf(value.codePointCount(0, value.length()))) < 0) {
            errors.add("%s: longer than %s".formatted(path, maxLength.value().toPlainString()));
        }
    }

    private static void checkNumber(JsonNumber value, JsonObject schema, String path, List<String> errors) {
        var below = schema.get("minimum").orElse(null) instanceof JsonNumber minimum
                && value.value().compareTo(minimum.value()) < 0;
        var above = schema.get("maximum").orElse(null) instanceof JsonNumber maximum
                && value.value().compareTo(maximum.value()) > 0;
        if (below || above) {
            errors.add("%s: out of range".formatted(path));
        }
    }

    private static void checkArray(JsonList value, JsonObject schema, String path, List<String> errors) {
        if (schema.get("minItems").orElse(null) instanceof JsonNumber minItems
                && minItems.value().compareTo(BigDecimal.valueOf(value.elements().size())) > 0) {
            errors.add("%s: fewer than %s items".formatted(path, minItems.value().toPlainString()));
        }
        var items = schema.get("items");
        if (items.isPresent()) {
            for (var index = 0; index < value.elements().size(); index++) {
                check(value.elements().get(index), items.get(), "%s[%d]".formatted(path, index), errors);
            }
        }
    }

    private static void checkObject(JsonObject value, JsonObject schema, String path, List<String> errors) {
        if (schema.get("required").orElse(null) instanceof JsonList required) {
            for (var key : required.elements()) {
                if (key instanceof JsonString name && !value.members().containsKey(name.value())) {
                    errors.add("%s: missing %s".formatted(path, name.value()));
                }
            }
        }
        var properties = schema.get("properties").orElse(null) instanceof JsonObject declared ? declared
                : new JsonObject(Map.of());
        var closed = schema.get("additionalProperties").orElse(null) instanceof JsonBoolean open && !open.value();
        for (var member : value.members().entrySet()) {
            var property = properties.get(member.getKey());
            if (property.isPresent()) {
                check(member.getValue(), property.get(), "%s.%s".formatted(path, member.getKey()), errors);
            } else if (closed) {
                errors.add("%s: unknown key %s".formatted(path, member.getKey()));
            }
        }
    }
}
