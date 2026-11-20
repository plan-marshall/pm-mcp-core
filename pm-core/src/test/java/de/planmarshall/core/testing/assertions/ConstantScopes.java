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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * The constant resolution of {@link MessageMeasurement}: the {@code static final String} constants of one source file,
 * each bound to the type that declares it.
 * <p>
 * A bare name is resolved the way the compiler resolves it in the common case: in the type around the assertion, then
 * in the types that enclose that one, innermost first. A qualified name is resolved in the type of the file whose
 * simple name is its last qualifier, and only when one type of the file carries that name. A constant initialized with
 * anything but one literal binds its name too, so it shadows an outer constant of the same name. A name the file does
 * not bind this way is not resolved, and the message stays unmeasured instead of being measured by an unrelated
 * constant of the same name.
 *
 * @since 0.1
 */
final class ConstantScopes {

    private static final String NAME_TEXT = "[a-zA-Z_$][\\w$]*";

    /** The head of a type declaration; its body is the first brace block after it. */
    private static final Pattern TYPE_DECLARATION = Pattern.compile(
            "\\b(?:class|interface|enum|record)\\s+(" + NAME_TEXT + ")");

    /** The head of a {@code static final String} constant; the initializer follows its end. */
    private static final Pattern CONSTANT_DECLARATION = Pattern.compile(
            "\\b(?:static\\s+final|final\\s+static)\\s+String\\s+(" + NAME_TEXT + ")\\s*=\\s*");

    /** A string literal in the code view, its content blanked, so no quote stands inside it. */
    private static final Pattern VIEW_LITERAL = Pattern.compile("\"[^\"]*\"");

    private final List<TypeScope> types = new ArrayList<>();

    /** The constants each type's body declares directly, by name. */
    private final Map<TypeScope, Map<String, Constant>> constants = new HashMap<>();

    /**
     * @param source the source text
     * @param view the code view of the source
     */
    ConstantScopes(String source, String view) {
        var declaration = TYPE_DECLARATION.matcher(view);
        while (declaration.find()) {
            var before = previousNonWhitespace(view, declaration.start() - 1);
            var open = view.indexOf('{', declaration.end());
            if ((before >= 0 && view.charAt(before) == '.') || open < 0) {
                continue;
            }
            var close = matchingBrace(view, open);
            if (close > open) {
                types.add(new TypeScope(declaration.group(1), open, close));
            }
        }
        var constant = CONSTANT_DECLARATION.matcher(view);
        while (constant.find()) {
            var owner = innermost(constant.start());
            if (owner.isPresent()) {
                constants.computeIfAbsent(owner.get(), type -> new HashMap<>())
                        .put(constant.group(1), literalAfter(source, view, constant.end()));
            }
        }
    }

    /**
     * @param name the bare or qualified name a message carries, whitespace removed
     * @param position the index of the message in the view
     * @return the literal, quotes included, of the one constant the name binds to at the position
     */
    Optional<String> literalOf(String name, int position) {
        var dot = name.lastIndexOf('.');
        if (dot < 0) {
            return types.stream()
                    .filter(type -> type.bodyStart() < position && position < type.bodyEnd())
                    .sorted(Comparator.comparingInt(TypeScope::bodyStart).reversed())
                    .map(type -> constantsOf(type).get(name))
                    .filter(Objects::nonNull)
                    .findFirst()
                    .flatMap(Constant::literal);
        }
        var qualifier = name.substring(name.lastIndexOf('.', dot - 1) + 1, dot);
        var named = types.stream().filter(type -> type.name().equals(qualifier)).toList();
        if (named.size() != 1) {
            return Optional.empty();
        }
        return Optional.ofNullable(constantsOf(named.getFirst()).get(name.substring(dot + 1)))
                .flatMap(Constant::literal);
    }

    private Map<String, Constant> constantsOf(TypeScope type) {
        return constants.getOrDefault(type, Map.of());
    }

    /** @return the innermost type whose body holds the index */
    private Optional<TypeScope> innermost(int index) {
        return types.stream()
                .filter(type -> type.bodyStart() < index && index < type.bodyEnd())
                .max(Comparator.comparingInt(TypeScope::bodyStart));
    }

    /** @return the constant whose initializer starts at {@code from}: one literal ended by {@code ;}, or unmeasured */
    private static Constant literalAfter(String source, String view, int from) {
        var initializer = VIEW_LITERAL.matcher(view).region(from, view.length());
        if (!initializer.lookingAt()) {
            return new Constant(null);
        }
        var after = ArgumentSpans.nextNonWhitespace(view, initializer.end());
        if (after < view.length() && view.charAt(after) == ';') {
            return new Constant(source.substring(initializer.start(), initializer.end()));
        }
        return new Constant(null);
    }

    /** @return the index of the {@code }} that closes the {@code {} at {@code open}, or {@code -1} */
    private static int matchingBrace(String view, int open) {
        var depth = 0;
        for (var scan = open; scan < view.length(); scan++) {
            var current = view.charAt(scan);
            if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return scan;
                }
            }
        }
        return -1;
    }

    private static int previousNonWhitespace(String view, int from) {
        var index = from;
        while (index >= 0 && Character.isWhitespace(view.charAt(index))) {
            index--;
        }
        return index;
    }

    /**
     * One type declared in the file.
     *
     * @param name the simple name of the type
     * @param bodyStart the index of the brace that opens its body
     * @param bodyEnd the index of the brace that closes its body
     */
    private record TypeScope(String name, int bodyStart, int bodyEnd) {
    }

    /**
     * One {@code static final String} constant.
     *
     * @param initializer the one literal it is initialized with, quotes included, or {@code null} for any other
     *            initializer
     */
    private record Constant(@Nullable String initializer) {

        Optional<String> literal() {
            return Optional.ofNullable(initializer);
        }
    }
}
