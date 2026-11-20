/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.testing.assertions;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;

/**
 * The argument splitter of {@link AssertionMessages}: the top-level arguments of a call, read from the
 * {@link CodeView code view} of a source.
 * <p>
 * A comma splits two arguments unless it sits inside brackets or inside a type argument. Whether a {@code <} opens a
 * type argument or is a comparison is decided by the heuristics of this class: the angles must read like type syntax
 * and stand in a type context — after {@code new}, after the {@code .} of an explicit method type argument, before a
 * method reference, or inside a type argument already opened.
 *
 * @since 0.1
 */
@UtilityClass
class ArgumentSpans {

    /** The primitive types, which start a type argument only as the element type of an array, as {@code int[]}. */
    private static final Set<String> PRIMITIVES = Set.of("boolean", "byte", "char", "short", "int", "long", "float",
            "double");

    /**
     * @param view the code view of the source
     * @param open the index of the call's {@code (}
     * @return the start and end index of every top-level argument of the call
     */
    static List<int[]> of(String view, int open) {
        var spans = new ArrayList<int[]>();
        var depth = 0;
        var angles = 0;
        var start = open + 1;
        for (var index = open + 1; index < view.length(); index++) {
            var current = view.charAt(index);
            if (current == '(' || current == '[' || current == '{') {
                depth++;
            } else if (current == ')' || current == ']' || current == '}') {
                if (depth == 0) {
                    if (index > start) {
                        spans.add(new int[]{start, index});
                    }
                    return spans;
                }
                depth--;
            } else if (current == ',' && depth == 0 && angles == 0) {
                spans.add(new int[]{start, index});
                start = index + 1;
            } else if (current == '<' && index > open + 1 && opensTypeArgument(view, index, angles > 0)) {
                angles++;
            } else if (current == '>' && angles > 0 && view.charAt(index - 1) != '-') {
                angles--;
            }
        }
        return spans;
    }

    /**
     * @param view the text to read
     * @param from the index to start at
     * @return the index of the first character at or after {@code from} that is not whitespace
     */
    static int nextNonWhitespace(String view, int from) {
        var index = from;
        while (index < view.length() && Character.isWhitespace(view.charAt(index))) {
            index++;
        }
        return index;
    }

    /**
     * @param nested whether the {@code <} sits inside a type argument already opened
     * @return whether the {@code <} at {@code index} opens a type argument rather than a comparison: the first token
     *         after it starts a type, every comma-separated element before the closing {@code >} starts a type, that
     *         {@code >} closes before the enclosing call ends, and the {@code <} stands in a type context
     */
    private static boolean opensTypeArgument(String view, int index, boolean nested) {
        var previous = previousNonWhitespace(view, index - 1);
        if (previous < 0) {
            return false;
        }
        var next = nextNonWhitespace(view, index + 1);
        if (next >= view.length() || !startsAType(view, next)) {
            return false;
        }
        var close = matchingAngle(view, index);
        return close > 0 && everyElementStartsAType(view, index + 1, close) && followedLikeAType(view, close)
                && inTypeContext(view, previous, close, nested);
    }

    /**
     * A name before a {@code <} is not enough to make it a type argument, since {@code Low < Max, Min > High} names
     * one too; the type context is what tells the two apart.
     *
     * @return whether the token ending at {@code previous} puts the {@code <} in a type context: a {@code .} of an
     *         explicit method type argument ({@code Map.<K, V>of()}), a {@code >}, a type name constructed with
     *         {@code new} (of any case, as {@code new KV<K, V>()}), a type name whose type argument a method reference
     *         follows ({@code HashMap<K, V>::new}), or — inside a type argument already opened — an upper-case name
     *         that carries a lower-case letter, so a constant such as {@code MAX} stays the operand of a comparison
     */
    private static boolean inTypeContext(String view, int previous, int close, boolean nested) {
        var before = view.charAt(previous);
        if (before == '.' || before == '>') {
            return true;
        }
        if (!Character.isJavaIdentifierPart(before)) {
            return false;
        }
        var first = identifierStart(view, previous);
        if (constructedWithNew(view, first) || followedByMethodReference(view, close)) {
            return true;
        }
        var name = view.substring(first, previous + 1);
        return nested && Character.isUpperCase(name.charAt(0)) && name.chars().anyMatch(Character::isLowerCase);
    }

    /** @return whether the possibly qualified type name starting at {@code first} follows the keyword {@code new} */
    private static boolean constructedWithNew(String view, int first) {
        var start = first;
        var before = previousNonWhitespace(view, start - 1);
        while (before >= 0 && view.charAt(before) == '.') {
            var segmentEnd = previousNonWhitespace(view, before - 1);
            if (segmentEnd < 0 || !Character.isJavaIdentifierPart(view.charAt(segmentEnd))) {
                return false;
            }
            start = identifierStart(view, segmentEnd);
            before = previousNonWhitespace(view, start - 1);
        }
        return before >= 0 && "new".equals(view.substring(identifierStart(view, before), before + 1));
    }

    /** @return whether a method reference {@code ::} follows the {@code >} at {@code close} */
    private static boolean followedByMethodReference(String view, int close) {
        var next = nextNonWhitespace(view, close + 1);
        return next + 1 < view.length() && view.charAt(next) == ':' && view.charAt(next + 1) == ':';
    }

    /** @return the first index of the identifier that ends at {@code last} */
    private static int identifierStart(String view, int last) {
        var first = last;
        while (first > 0 && Character.isJavaIdentifierPart(view.charAt(first - 1))) {
            first--;
        }
        return first;
    }

    /** @return the index of the last character at or before {@code from} that is not whitespace, or {@code -1} */
    private static int previousNonWhitespace(String view, int from) {
        var index = from;
        while (index >= 0 && Character.isWhitespace(view.charAt(index))) {
            index--;
        }
        return index;
    }

    /**
     * @return whether what follows the closing {@code >} reads like a type rather than as the right operand of a
     *         comparison: a digit or an operator after it is an expression, so the angle was a comparison
     */
    private static boolean followedLikeAType(String view, int close) {
        var next = nextNonWhitespace(view, close + 1);
        if (next >= view.length()) {
            return true;
        }
        var current = view.charAt(next);
        return !Character.isDigit(current) && "+-*/%<>=!&|^~".indexOf(current) < 0;
    }

    /** @return the index of the {@code >} that closes the {@code <} at {@code open}, or {@code -1} when none closes */
    private static int matchingAngle(String view, int open) {
        var depth = 0;
        for (var scan = open + 1; scan < view.length(); scan++) {
            var current = view.charAt(scan);
            if (current == '<') {
                depth++;
            } else if (current == '>') {
                if (depth == 0) {
                    return scan;
                }
                depth--;
            } else if (current == ')' || current == '}' || current == ';') {
                return -1;
            }
        }
        return -1;
    }

    /** @return whether every comma-separated element of a type argument starts a type; {@code b > 0} does not */
    private static boolean everyElementStartsAType(String view, int from, int to) {
        var depth = 0;
        var elementStart = -1;
        for (var scan = from; scan < to; scan++) {
            var current = view.charAt(scan);
            if (current == '<') {
                depth++;
            } else if (current == '>') {
                depth--;
            } else if (current == ',' && depth == 0) {
                if (!startsAType(view, elementStart < 0 ? scan : elementStart)) {
                    return false;
                }
                elementStart = -1;
            } else if (elementStart < 0 && !Character.isWhitespace(current)) {
                elementStart = scan;
            }
        }
        return startsAType(view, elementStart < 0 ? to : elementStart);
    }

    /**
     * @return whether a type argument starts at this index: an upper-case name, a wildcard, an annotation, a primitive
     *         array type such as {@code int[]}, or a lower-case qualified name whose chain names an upper-case segment,
     *         as {@code java.lang.String} does and the field access {@code expected.max} of a comparison does not
     */
    private static boolean startsAType(String view, int index) {
        if (index >= view.length()) {
            return false;
        }
        var current = view.charAt(index);
        if (Character.isUpperCase(current) || current == '?' || current == '@') {
            return true;
        }
        if (!Character.isJavaIdentifierStart(current)) {
            return false;
        }
        var scan = index;
        while (scan < view.length() && Character.isJavaIdentifierPart(view.charAt(scan))) {
            scan++;
        }
        if (PRIMITIVES.contains(view.substring(index, scan))) {
            var bracket = nextNonWhitespace(view, scan);
            return bracket < view.length() && view.charAt(bracket) == '[';
        }
        while (scan + 1 < view.length() && view.charAt(scan) == '.'
                && Character.isJavaIdentifierStart(view.charAt(scan + 1))) {
            scan++;
            var segmentStart = scan;
            while (scan < view.length() && Character.isJavaIdentifierPart(view.charAt(scan))) {
                scan++;
            }
            if (Character.isUpperCase(view.charAt(segmentStart))) {
                return true;
            }
        }
        return false;
    }
}
