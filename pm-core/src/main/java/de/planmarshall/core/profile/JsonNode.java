/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.profile;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * One value of a client profile or of a profile verification record (PM-CLIENT-5): the profile package's own tree,
 * because a profile is read with the streaming API of {@code jackson-core} and validated value by value.
 * <p>
 * A value is one of the seven JSON kinds. An object keeps its members in document order and refuses a member twice; an
 * array keeps its elements in order. The tree is immutable: the factory copies what it is given and the accessors
 * return unmodifiable views.
 * <p>
 * A number is kept as the integer it is or as the decimal it is, so the canonical writer never guesses. A profile
 * declares integers only; a decimal is kept for the canonical form of an untrusted document, which is hashed before
 * any value of it is used.
 *
 * @since 0.1
 */
public final class JsonNode {

    /** The JSON kind of a value. The set is closed. */
    public enum Kind {
        /** An object with named members. */
        OBJECT,
        /** An array of values. */
        ARRAY,
        /** A string. */
        STRING,
        /** A number without a fraction. */
        INTEGER,
        /** A number with a fraction or an exponent. */
        DECIMAL,
        /** {@code true} or {@code false}. */
        BOOLEAN,
        /** The JSON {@code null}. */
        NULL
    }

    private static final JsonNode NULL = new JsonNode(Kind.NULL, null, null, null, null, null, null);

    private final Kind kind;
    private final @Nullable Map<String, JsonNode> members;
    private final @Nullable List<JsonNode> elements;
    private final @Nullable String text;
    private final @Nullable Long integer;
    private final @Nullable BigDecimal decimal;
    private final @Nullable Boolean bool;

    private JsonNode(Kind kind, @Nullable Map<String, JsonNode> members, @Nullable List<JsonNode> elements,
            @Nullable String text, @Nullable Long integer, @Nullable BigDecimal decimal, @Nullable Boolean bool) {
        this.kind = kind;
        this.members = members;
        this.elements = elements;
        this.text = text;
        this.integer = integer;
        this.decimal = decimal;
        this.bool = bool;
    }

    /**
     * @param members the members in document order
     * @return the object
     * @throws NullPointerException     if a member name or value is {@code null}
     * @throws IllegalArgumentException if a member name occurs twice
     */
    public static JsonNode object(Map<String, JsonNode> members) {
        Objects.requireNonNull(members, "members");
        var copy = new LinkedHashMap<String, JsonNode>();
        for (var member : members.entrySet()) {
            var name = Objects.requireNonNull(member.getKey(), "member name");
            if (copy.put(name, Objects.requireNonNull(member.getValue(), name)) != null) {
                throw new IllegalArgumentException("Member '%s' occurs twice".formatted(name));
            }
        }
        return new JsonNode(Kind.OBJECT, Collections.unmodifiableMap(copy), null, null, null, null, null);
    }

    /**
     * @param elements the elements in order
     * @return the array
     * @throws NullPointerException if an element is {@code null}
     */
    public static JsonNode array(List<JsonNode> elements) {
        Objects.requireNonNull(elements, "elements");
        var copy = elements.stream().map(element -> Objects.requireNonNull(element, "element")).toList();
        return new JsonNode(Kind.ARRAY, null, copy, null, null, null, null);
    }

    /**
     * @param value the string
     * @return the string value
     */
    public static JsonNode string(String value) {
        return new JsonNode(Kind.STRING, null, null, Objects.requireNonNull(value, "value"), null, null, null);
    }

    /**
     * @param value the integer
     * @return the integer value
     */
    public static JsonNode integer(long value) {
        return new JsonNode(Kind.INTEGER, null, null, null, value, null, null);
    }

    /**
     * @param value the decimal
     * @return the decimal value
     */
    public static JsonNode decimal(BigDecimal value) {
        return new JsonNode(Kind.DECIMAL, null, null, null, null, Objects.requireNonNull(value, "value"), null);
    }

    /**
     * @param value the boolean
     * @return the boolean value
     */
    public static JsonNode bool(boolean value) {
        return new JsonNode(Kind.BOOLEAN, null, null, null, null, null, value);
    }

    /** @return the JSON {@code null} */
    public static JsonNode nullValue() {
        return NULL;
    }

    /** @return the kind of this value */
    public Kind kind() {
        return kind;
    }

    /** @return whether this value is an object */
    public boolean isObject() {
        return kind == Kind.OBJECT;
    }

    /** @return whether this value is an array */
    public boolean isArray() {
        return kind == Kind.ARRAY;
    }

    /** @return whether this value is a string */
    public boolean isString() {
        return kind == Kind.STRING;
    }

    /** @return whether this value is an integer */
    public boolean isInteger() {
        return kind == Kind.INTEGER;
    }

    /** @return whether this value is a number of either spelling */
    public boolean isNumber() {
        return kind == Kind.INTEGER || kind == Kind.DECIMAL;
    }

    /** @return whether this value is a boolean */
    public boolean isBoolean() {
        return kind == Kind.BOOLEAN;
    }

    /** @return whether this value is the JSON {@code null} */
    public boolean isNull() {
        return kind == Kind.NULL;
    }

    /** @return the members of an object in document order; unmodifiable, empty for another kind */
    public Map<String, JsonNode> members() {
        return members == null ? Map.of() : members;
    }

    /** @return one member of an object; empty if this value is not an object or has no such member */
    public Optional<JsonNode> member(String name) {
        return Optional.ofNullable(members == null ? null : members.get(name));
    }

    /** @return the elements of an array; unmodifiable, empty for another kind */
    public List<JsonNode> elements() {
        return elements == null ? List.of() : elements;
    }

    /** @return the string; empty for another kind */
    public Optional<String> text() {
        return Optional.ofNullable(text);
    }

    /** @return the integer; empty for another kind */
    public OptionalLong integerValue() {
        return integer == null ? OptionalLong.empty() : OptionalLong.of(integer);
    }

    /** @return the number as a decimal; empty for another kind */
    public Optional<BigDecimal> decimalValue() {
        if (integer != null) {
            return Optional.of(BigDecimal.valueOf(integer));
        }
        return Optional.ofNullable(decimal);
    }

    /** @return the boolean; empty for another kind */
    public Optional<Boolean> booleanValue() {
        return Optional.ofNullable(bool);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof JsonNode node
                && kind == node.kind
                && Objects.equals(members, node.members)
                && Objects.equals(elements, node.elements)
                && Objects.equals(text, node.text)
                && Objects.equals(integer, node.integer)
                && (decimal == null ? node.decimal == null : decimal.compareTo(node.decimal) == 0)
                && Objects.equals(bool, node.bool);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, members, elements, text, integer, decimal == null ? null : decimal.stripTrailingZeros(),
                bool);
    }

    @Override
    public String toString() {
        return switch (kind) {
            case OBJECT -> "object" + members.keySet();
            case ARRAY -> "array[" + elements.size() + "]";
            case STRING -> "'" + text + "'";
            case INTEGER -> Long.toString(integer);
            case DECIMAL -> decimal.toPlainString();
            case BOOLEAN -> Boolean.toString(bool);
            case NULL -> "null";
        };
    }
}
