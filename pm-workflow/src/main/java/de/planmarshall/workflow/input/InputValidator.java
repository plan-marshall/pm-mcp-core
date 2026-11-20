/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.input;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import de.planmarshall.core.spi.validation.ConventionalCommitValidator;
import de.planmarshall.workflow.ast.CallValue;
import de.planmarshall.workflow.ast.InputType;
import org.jspecify.annotations.Nullable;

/**
 * Validates a value against an {@link InputType}: length in code points, the Conventional Commit and trailer refusal
 * through {@link ConventionalCommitValidator}, the path rules of Decision 3, the compiled caps and the duplicate check.
 * <p>
 * The validator is both the parser of a text value a slot or a link carries and the checker of the {@link CallValue} the
 * engine is about to bind: {@link #parse(InputType, String)} turns the text of a {@code list<path>} into its JSON array
 * form, and {@link #validate(InputType, CallValue)} checks the value against the closed type of the callee's input. A
 * refusal is returned as an {@link InputRefusal}, never as an exception.
 *
 * @since 0.1
 */
public final class InputValidator {

    /** The compiled cap of a {@code text} value: the most code points a unit may widen it to. */
    public static final int TEXT_CAP = 2000;

    /** The compiled cap of a {@code commit_message} value: the most code points a unit may widen it to. */
    public static final int COMMIT_MESSAGE_CAP = 2000;

    /** The compiled cap of a {@code path} value, in characters. */
    public static final int PATH_CAP = PathRules.MAX_PATH_LENGTH;

    /** The compiled cap of a {@code list<path>} value: the most entries a unit may widen it to. */
    public static final int PATH_LIST_CAP = 500;

    /** The rule of a value that is absent where the type demands one. */
    public static final String RULE_MISSING = "input.missing";

    /** The rule of a text shorter than its low bound. */
    public static final String RULE_TOO_SHORT = "input.too-short";

    /** The rule of a text longer than its high bound or than its compiled cap. */
    public static final String RULE_TOO_LONG = "input.too-long";

    /** The rule of a text that is no Conventional Commit, or carries a {@code Co-Authored-By:} line. */
    public static final String RULE_COMMIT_MESSAGE = "input.commit-message";

    /** The rule of a text carrying a lone surrogate. */
    public static final String RULE_SURROGATE = "input.surrogate";

    /** The rule of a commit message whose body carries a control character, ESC or NUL. */
    public static final String RULE_COMMIT_CONTROL = "input.commit-control";

    /** The rule of a list whose count lies outside its bounds or its compiled cap. */
    public static final String RULE_COUNT = "input.count";

    /** The rule of a value that occurs twice in a list. */
    public static final String RULE_DUPLICATE = "input.duplicate";

    /** The rule of an explicitly empty path list: an empty list binds no path and is refused at the bind. */
    public static final String RULE_EMPTY = "input.empty";

    /** The rule of a value outside the closed set of an enumeration. */
    public static final String RULE_ENUM = "input.enum";

    /** The rule of an integer outside the range of its type. */
    public static final String RULE_RANGE = "input.range";

    /** The rule of a slot or link text that the type cannot parse. */
    public static final String RULE_MALFORMED = "input.malformed";

    /** The rule of a path that is not confined to the worktree. */
    public static final String RULE_PATH_CONFINEMENT = "path.confinement";

    /** The rule of a path that does not belong to the scope's staging set. */
    public static final String RULE_PATH_STAGING = "path.staging";

    /** The rule of a path bound without a binding context. */
    public static final String RULE_PATH_NO_CONTEXT = "path.no-context";

    /** The rule of an empty path. */
    public static final String RULE_PATH_EMPTY = "path.empty";

    /** The rule of a path longer than {@value PathRules#MAX_PATH_LENGTH} characters. */
    public static final String RULE_PATH_TOO_LONG = "path.too-long";

    /** The rule of an absolute path. */
    public static final String RULE_PATH_ABSOLUTE = "path.absolute";

    /** The rule of a path carrying a backslash. */
    public static final String RULE_PATH_BACKSLASH = "path.backslash";

    /** The rule of a path carrying NUL. */
    public static final String RULE_PATH_NUL = "path.nul";

    /** The rule of a path carrying a git pathspec magic or a leading dash. */
    public static final String RULE_PATH_MAGIC = "path.magic";

    /** The rule of a path carrying a glob character JGit would read as a pattern. */
    public static final String RULE_PATH_GLOB = "path.glob";

    /** The rule of a path carrying a control character. */
    public static final String RULE_PATH_CONTROL = "path.control";

    /** The rule of a path carrying whitespace other than a space. */
    public static final String RULE_PATH_WHITESPACE = "path.whitespace";

    /** The rule of a path carrying a bidi control character. */
    public static final String RULE_PATH_BIDI = "path.bidi";

    /** The rule of a path carrying a format character. */
    public static final String RULE_PATH_FORMAT = "path.format";

    /** The rule of a path carrying a lone surrogate. */
    public static final String RULE_PATH_SURROGATE = "path.surrogate";

    /** The rule of a path with an empty segment. */
    public static final String RULE_PATH_EMPTY_SEGMENT = "path.empty-segment";

    /** The rule of a path with a {@code .} or {@code ..} segment. */
    public static final String RULE_PATH_DOT_SEGMENT = "path.dot-segment";

    /** The rule of a path with a {@code .git} segment. */
    public static final String RULE_PATH_GIT = "path.git";

    private static final String NULL_ECHO = "<null>";

    private InputValidator() {
    }

    /**
     * Validates a value against the closed type of an input.
     *
     * @param type  the type of the input
     * @param value the value about to be bound
     * @return empty when the value is accepted, the refusal otherwise
     */
    public static Optional<InputRefusal> validate(InputType type, @Nullable CallValue value) {
        Objects.requireNonNull(type, "type");
        if (value == null) {
            return Optional.of(new InputRefusal(RULE_MISSING, NULL_ECHO));
        }
        return switch (type) {
            case InputType.Text _, InputType.CommitMessage _ -> value instanceof CallValue.EnumValue(var text)
                    ? validateText(type, text)
                    : malformed(value.toString());
            case InputType.Path _ -> value instanceof CallValue.EnumValue(var text)
                    ? validatePath(text)
                    : malformed(value.toString());
            case InputType.PathList _ -> value instanceof CallValue.EnumList(var texts)
                    ? validatePathList(type, texts)
                    : malformed(value.toString());
            case InputType.Enumeration(var values) -> value instanceof CallValue.EnumValue(var text)
                    && values.contains(text) ? Optional.empty() : refused(RULE_ENUM, value.toString());
            case InputType.IntRange(var min, var max) -> value instanceof CallValue.IntValue(var number)
                    && (min == null || number >= min) && (max == null || number <= max) ? Optional.empty()
                    : refused(RULE_RANGE, value.toString());
            case InputType.EnumList(var values) -> value instanceof CallValue.EnumList(var list)
                    && values.containsAll(list) ? Optional.empty() : refused(RULE_ENUM, value.toString());
        };
    }

    /**
     * The value a slot or a link text carries, or the rule that refused it.
     *
     * @param value   the value; {@code null} exactly when {@code refusal} is set
     * @param refusal the refusal; {@code null} when the text carries a value
     */
    public record Parsed(@Nullable CallValue value, @Nullable InputRefusal refusal) {

        static Parsed of(CallValue value) {
            return new Parsed(value, null);
        }

        static Parsed refused(String rule, String text) {
            return new Parsed(null, new InputRefusal(rule, text));
        }
    }

    /**
     * Parses the text a slot or a link parameter carries into the value of the given type. A {@code list<...>} value
     * is the JSON array text of its entries; an entry that occurs twice is refused {@link #RULE_DUPLICATE} rather than
     * left to the value type's constructor.
     *
     * @param type the type of the input the value is bound to
     * @param text the text as it was filled or submitted
     * @return the value, or the refusal
     */
    public static Parsed parse(InputType type, @Nullable String text) {
        Objects.requireNonNull(type, "type");
        if (text == null) {
            return Parsed.refused(RULE_MISSING, NULL_ECHO);
        }
        return switch (type) {
            case InputType.Text _, InputType.CommitMessage _, InputType.Path _, InputType.Enumeration _ ->
                Parsed.of(new CallValue.EnumValue(text));
            case InputType.IntRange _ -> parseInt(text).map(Parsed::of)
                    .orElseGet(() -> Parsed.refused(RULE_MALFORMED, text));
            case InputType.PathList _, InputType.EnumList _ -> parseList(text)
                    .map(values -> Parsed.of(new CallValue.EnumList(values)))
                    .orElseGet(() -> duplicate(text) ? Parsed.refused(RULE_DUPLICATE, text)
                            : Parsed.refused(RULE_MALFORMED, text));
        };
    }

    /**
     * Validates a single text value against the given text-like input type.
     *
     * @param type  the input type ({@link InputType.Text} or {@link InputType.CommitMessage})
     * @param value the value to validate
     * @return empty when the value is accepted, the refusal otherwise
     */
    public static Optional<InputRefusal> validateText(InputType type, @Nullable String value) {
        Objects.requireNonNull(type, "type");
        if (value == null) {
            return Optional.of(new InputRefusal(RULE_MISSING, NULL_ECHO));
        }
        if (loneSurrogate(value)) {
            return refused(RULE_SURROGATE, value);
        }
        var bounds = bounds(type);
        var codePoints = value.codePointCount(0, value.length());
        if (codePoints < bounds[0]) {
            return refused(RULE_TOO_SHORT, value);
        }
        if (codePoints > bounds[1]) {
            return refused(RULE_TOO_LONG, value);
        }
        if (type instanceof InputType.CommitMessage) {
            if (controlInBody(value)) {
                return refused(RULE_COMMIT_CONTROL, value);
            }
            if (!new ConventionalCommitValidator().validate(value).isEmpty()) {
                return refused(RULE_COMMIT_MESSAGE, value);
            }
        }
        return Optional.empty();
    }

    /**
     * Validates a single path value against {@link InputType.Path}.
     *
     * @param value the value to validate
     * @return empty when the value is accepted, the refusal otherwise
     */
    public static Optional<InputRefusal> validatePath(@Nullable String value) {
        if (value == null) {
            return Optional.of(new InputRefusal(RULE_MISSING, NULL_ECHO));
        }
        return PathRules.check(value);
    }

    /**
     * Validates a list of paths against {@link InputType.PathList}: its count within the bounds and the compiled cap,
     * every entry a legal path, no entry twice, and at least one entry.
     *
     * @param type   the path list type
     * @param values the values to validate
     * @return empty when the value is accepted, the refusal otherwise
     */
    public static Optional<InputRefusal> validatePathList(InputType type, @Nullable List<String> values) {
        Objects.requireNonNull(type, "type");
        if (values == null) {
            return Optional.of(new InputRefusal(RULE_MISSING, NULL_ECHO));
        }
        var bounds = bounds(type);
        if (values.size() < bounds[0] || values.size() > bounds[1]) {
            return refused(RULE_COUNT, values.toString());
        }
        if (values.isEmpty()) {
            return Optional.empty();
        }
        var seen = new HashSet<String>();
        for (var value : values) {
            var refusal = PathRules.check(value);
            if (refusal.isPresent()) {
                return refusal;
            }
            if (!seen.add(value)) {
                return refused(RULE_DUPLICATE, value);
            }
        }
        return Optional.empty();
    }

    /**
     * @param type a bounded input type
     * @return the low and high bound; the compiled cap where the type declares none, so no type is unbounded
     */
    private static int[] bounds(InputType type) {
        var compiled = switch (type) {
            case InputType.Text _, InputType.CommitMessage _ -> TEXT_CAP;
            case InputType.PathList _ -> PATH_LIST_CAP;
            default -> Integer.MAX_VALUE;
        };
        var min = 0;
        var max = compiled;
        var low = switch (type) {
            case InputType.Text(var lo, _) -> lo;
            case InputType.CommitMessage(var lo, _) -> lo;
            case InputType.PathList(var lo, _) -> lo;
            default -> null;
        };
        var high = switch (type) {
            case InputType.Text(_, var hi) -> hi;
            case InputType.CommitMessage(_, var hi) -> hi;
            case InputType.PathList(_, var hi) -> hi;
            default -> null;
        };
        if (low != null) {
            min = low;
        }
        if (high != null) {
            max = Math.min(high, compiled);
        }
        return new int[]{min, max};
    }

    private static Optional<CallValue> parseInt(String text) {
        try {
            return Optional.of(new CallValue.IntValue(Integer.parseInt(text)));
        } catch (NumberFormatException _) {
            return Optional.empty();
        }
    }

    /** @return whether the JSON array text names an entry twice */
    private static boolean duplicate(String text) {
        return parseList(text).map(values -> new HashSet<>(values).size() < values.size()).orElse(false);
    }

    /** Reads a JSON array of strings; anything else, including a trailing token, is no list. */
    private static Optional<List<String>> parseList(String text) {
        try (JsonParser parser = new JsonFactory().createParser(text)) {
            if (parser.nextToken() != JsonToken.START_ARRAY) {
                return Optional.empty();
            }
            var values = new ArrayList<String>();
            var next = parser.nextToken();
            while (next == JsonToken.VALUE_STRING) {
                values.add(parser.getText());
                next = parser.nextToken();
            }
            if (next != JsonToken.END_ARRAY || parser.nextToken() != null) {
                return Optional.empty();
            }
            return Optional.of(List.copyOf(values));
        } catch (IOException _) {
            return Optional.empty();
        }
    }

    private static boolean loneSurrogate(String value) {
        for (var index = 0; index < value.length(); index++) {
            var character = value.charAt(index);
            if (Character.isHighSurrogate(character)) {
                if (index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(index + 1))) {
                    return true;
                }
                index++;
            } else if (Character.isLowSurrogate(character)) {
                return true;
            }
        }
        return false;
    }

    /** Whether a commit message carries a control character outside a line break, ESC and NUL included. */
    private static boolean controlInBody(String value) {
        return value.codePoints().anyMatch(codePoint -> (codePoint < 0x20 && codePoint != '\n' && codePoint != '\r')
                || (codePoint >= 0x7f && codePoint <= 0x9f));
    }

    private static Optional<InputRefusal> malformed(String value) {
        return refused(RULE_MALFORMED, value);
    }

    private static Optional<InputRefusal> refused(String rule, String value) {
        return Optional.of(new InputRefusal(rule, value));
    }
}
