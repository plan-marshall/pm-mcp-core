/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.endpoint;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import de.planmarshall.core.profile.CanonicalJson;
import de.planmarshall.core.profile.JsonNode;
import de.planmarshall.core.profile.JsonPointer;

/**
 * The server's own check of an answer against the role's result schema (Client Profiles § The Request): the answer is
 * validated here, never trusted because the endpoint enforces a schema.
 * <p>
 * The check accepts the JSON Schema draft 2020-12 subset a result schema uses and refuses a schema that needs a keyword
 * it does not implement, so a schema it cannot enforce never becomes a silent pass. Supported are {@code type},
 * {@code const}, {@code enum}, {@code required}, {@code properties}, {@code additionalProperties}, {@code items},
 * {@code minItems}, {@code maxItems}, {@code minLength}, {@code maxLength}, {@code minimum}, {@code maximum}, a local
 * {@code $ref} and the annotation keywords {@code $schema}, {@code $id}, {@code title}, {@code description},
 * {@code $defs} and {@code definitions}. The design is recorded in ADR runtime-endpoint/003.
 *
 * @since 0.1
 */
public final class AnswerSchema {

    /** The deepest nesting the check follows, so a recursive schema ends instead of looping. */
    private static final int MAX_DEPTH = 32;

    private static final Set<String> ANNOTATIONS = Set.of("$schema", "$id", "title", "description", "$defs",
            "definitions");

    private static final Set<String> KEYWORDS = Set.of("type", "const", "enum", "required", "properties",
            "additionalProperties", "items", "minItems", "maxItems", "minLength", "maxLength", "minimum", "maximum",
            "$ref");

    private AnswerSchema() {
    }

    /**
     * @param schema the role's result schema
     * @param answer the parsed answer
     * @return whether the answer is valid, or why the schema or the answer was refused
     */
    public static Check validate(JsonNode schema, JsonNode answer) {
        Objects.requireNonNull(schema, "schema");
        Objects.requireNonNull(answer, "answer");
        return check(schema, schema, answer, "", 0);
    }

    private static Check check(JsonNode root, JsonNode schema, JsonNode answer, String pointer, int depth) {
        if (depth > MAX_DEPTH) {
            return Check.refused(pointer, "the schema nests deeper than %d".formatted(MAX_DEPTH));
        }
        if (!schema.isObject()) {
            return Check.refused(pointer, "a schema is an object");
        }
        var unsupported = schema.members().keySet().stream()
                .filter(name -> !KEYWORDS.contains(name) && !ANNOTATIONS.contains(name)).findFirst();
        if (unsupported.isPresent()) {
            return Check.refused(pointer,
                    "the result schema uses the unsupported keyword '%s'".formatted(unsupported.get()));
        }
        if (schema.member("$ref").isPresent()) {
            var reference = schema.member("$ref").flatMap(JsonNode::text).orElse("");
            if (!reference.startsWith("#/")) {
                return Check.refused(pointer, "the result schema refers to '%s'".formatted(reference));
            }
            var target = resolve(root, reference);
            if (target == null) {
                return Check.refused(pointer,
                        "the result schema refers to '%s', which does not resolve".formatted(reference));
            }
            return check(root, target, answer, pointer, depth + 1);
        }
        for (var type : elements(schema, "type")) {
            var name = type.text().orElse("");
            if (!typeMatches(name, answer)) {
                return Check.refused(pointer, "the answer is not of type '%s'".formatted(name));
            }
        }
        var constant = schema.member("const");
        if (constant.isPresent() && !sameValue(constant.get(), answer)) {
            return Check.refused(pointer, "the answer is not the schema's const value");
        }
        var values = schema.member("enum");
        if (values.isPresent() && values.get().elements().stream().noneMatch(value -> sameValue(value, answer))) {
            return Check.refused(pointer, "the answer is none of the schema's enum values");
        }
        if (answer.isObject()) {
            return object(root, schema, answer, pointer, depth);
        }
        if (answer.isArray()) {
            return array(root, schema, answer, pointer, depth);
        }
        if (answer.isString()) {
            return string(schema, answer.text().orElse(""), pointer);
        }
        if (answer.isInteger() || answer.isNumber()) {
            return number(schema, answer.decimalValue().orElse(BigDecimal.ZERO), pointer);
        }
        return Check.ok();
    }

    private static JsonNode resolve(JsonNode root, String reference) {
        try {
            return JsonPointer.resolve(root, reference.substring(1), "result schema");
        }
        /*TODO: Catch specific not RuntimeException. Suppress: // cui-rewrite:disable InvalidExceptionUsageRecipe*/
        catch (RuntimeException _) {
            return null;
        }
    }

    private static Check object(JsonNode root, JsonNode schema, JsonNode answer, String pointer, int depth) {
        for (var required : elements(schema, "required")) {
            var name = required.text().orElse("");
            if (answer.member(name).isEmpty()) {
                return Check.refused(pointer + "/" + name, "the required member '%s' is missing".formatted(name));
            }
        }
        var properties = schema.member("properties");
        var closed = schema.member("additionalProperties").flatMap(JsonNode::booleanValue).orElse(true);
        for (var entry : answer.members().entrySet()) {
            var declared = properties.flatMap(propertiesNode -> propertiesNode.member(entry.getKey()));
            if (declared.isPresent()) {
                var result = check(root, declared.get(), entry.getValue(), pointer + "/" + entry.getKey(), depth + 1);
                if (!result.valid()) {
                    return result;
                }
            } else if (!closed) {
                return Check.refused(pointer + "/" + entry.getKey(),
                        "the member '%s' is not declared by the schema".formatted(entry.getKey()));
            }
        }
        return Check.ok();
    }

    private static Check array(JsonNode root, JsonNode schema, JsonNode answer, String pointer, int depth) {
        var elements = answer.elements();
        var minimum = integer(schema, "minItems");
        if (minimum != null && elements.size() < minimum) {
            return Check.refused(pointer, "the array has fewer than %d items".formatted(minimum));
        }
        var maximum = integer(schema, "maxItems");
        if (maximum != null && elements.size() > maximum) {
            return Check.refused(pointer, "the array has more than %d items".formatted(maximum));
        }
        var items = schema.member("items");
        if (items.isPresent()) {
            for (var index = 0; index < elements.size(); index++) {
                var result = check(root, items.get(), elements.get(index), pointer + "/" + index, depth + 1);
                if (!result.valid()) {
                    return result;
                }
            }
        }
        return Check.ok();
    }

    private static Check string(JsonNode schema, String value, String pointer) {
        var minimum = integer(schema, "minLength");
        if (minimum != null && value.length() < minimum) {
            return Check.refused(pointer, "the string is shorter than %d characters".formatted(minimum));
        }
        var maximum = integer(schema, "maxLength");
        if (maximum != null && value.length() > maximum) {
            return Check.refused(pointer, "the string is longer than %d characters".formatted(maximum));
        }
        return Check.ok();
    }

    private static Check number(JsonNode schema, BigDecimal value, String pointer) {
        var minimum = decimal(schema, "minimum");
        if (minimum != null && value.compareTo(minimum) < 0) {
            return Check.refused(pointer, "the number is below %s".formatted(minimum));
        }
        var maximum = decimal(schema, "maximum");
        if (maximum != null && value.compareTo(maximum) > 0) {
            return Check.refused(pointer, "the number is above %s".formatted(maximum));
        }
        return Check.ok();
    }

    private static List<JsonNode> elements(JsonNode schema, String member) {
        return schema.member(member).filter(JsonNode::isArray).map(JsonNode::elements).orElseGet(List::of);
    }

    private static Integer integer(JsonNode schema, String member) {
        var value = schema.member(member).orElse(null);
        if (value == null || !value.isInteger()) {
            return null;
        }
        return (int) value.integerValue().orElseThrow();
    }

    private static BigDecimal decimal(JsonNode schema, String member) {
        var value = schema.member(member).orElse(null);
        return value == null ? null : value.decimalValue().orElse(null);
    }

    private static boolean typeMatches(String type, JsonNode answer) {
        return switch (type) {
            case "object" -> answer.isObject();
            case "array" -> answer.isArray();
            case "string" -> answer.isString();
            case "integer" -> answer.isInteger();
            case "number" -> answer.isNumber();
            case "boolean" -> answer.isBoolean();
            case "null" -> answer.isNull();
            default -> false;
        };
    }

    private static boolean sameValue(JsonNode left, JsonNode right) {
        return CanonicalJson.write(left).equals(CanonicalJson.write(right));
    }

    /**
     * The result of one check.
     *
     * @param valid  whether the answer is valid
     * @param detail what failed; empty when the answer is valid
     * @since 0.1
     */
    public record Check(boolean valid, String detail) {

        /** Copies the detail. */
        public Check {
            Objects.requireNonNull(detail, "detail");
        }

        static Check ok() {
            return new Check(true, "");
        }

        static Check refused(String pointer, String detail) {
            return new Check(false, "%s: %s".formatted(pointer.isEmpty() ? "/" : pointer, detail));
        }
    }
}
