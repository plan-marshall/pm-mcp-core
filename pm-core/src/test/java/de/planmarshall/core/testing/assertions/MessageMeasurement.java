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

import static de.planmarshall.core.testing.assertions.AssertionMessages.MAX_MESSAGE_LENGTH;
import static de.planmarshall.core.testing.assertions.AssertionMessages.MIN_MESSAGE_LENGTH;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The measurement of {@link AssertionMessages}: the verdict on the message of one assertion call of a source file.
 * <p>
 * One instance reads one source: its {@link CodeView code view} and the declarations the measurement consults — the
 * names declared {@code String}, {@code double} or {@code float}, the throwables, and the {@code static final String}
 * constants initialized with one literal — are read once. The shapes the verdict tells apart are stated on
 * {@link AssertionMessages}.
 *
 * @since 0.1
 */
final class MessageMeasurement {

    /** The assertions whose signature starts with a single mandatory argument, so a message is the second argument. */
    private static final Set<String> ONE_ARGUMENT_ASSERTIONS = Set.of("assertTrue", "assertFalse", "assertNull",
            "assertNotNull", "assertDoesNotThrow");

    /** The assertions whose third argument is a tolerance rather than a message when it is numeric. */
    private static final Set<String> TOLERANCE_ASSERTIONS = Set.of("assertEquals", "assertNotEquals",
            "assertArrayEquals");

    private static final String LITERAL_TEXT = "\"(?:[^\"\\\\]|\\\\.)*\"";

    private static final String NAME_TEXT = "[a-zA-Z_$][\\w$]*";

    private static final Pattern NUMERIC = Pattern.compile(
            "[+-]?(?:0[xX](?:[0-9a-fA-F][0-9a-fA-F_]*(?:\\.[0-9a-fA-F_]*)?|\\.[0-9a-fA-F][0-9a-fA-F_]*)[pP][+-]?\\d+"
                    + "|(?:\\d[\\d_]*(?:\\.[\\d_]*)?|\\.[\\d_]+)(?:[eE][+-]?\\d[\\d_]*)?)[dDfF]?");

    private static final Pattern LITERAL = Pattern.compile(LITERAL_TEXT);

    /** A supplier whose body is one string literal; its first group is the literal. */
    private static final Pattern SUPPLIER_OVER_LITERAL = Pattern.compile("\\(\\s*\\)\\s*->\\s*(" + LITERAL_TEXT + ")");

    /** A floating-point literal anywhere inside an operand: {@code 1.0}, {@code .5f}, {@code 1e-6}, {@code 0x1.0p-6}. */
    private static final Pattern FLOATING = Pattern.compile(
            "(?<![\\w$])(?:0[xX][0-9a-fA-F_]*\\.[0-9a-fA-F_]*[pP][+-]?\\d+"
                    + "|(?:\\d[\\d_]*\\.[\\d_]*|\\.[\\d_]+)(?:[eE][+-]?\\d[\\d_]*)?[dDfF]?"
                    + "|\\d[\\d_]*[eE][+-]?\\d[\\d_]*[dDfF]?"
                    + "|\\d[\\d_]*[dDfF])(?![\\w$])");

    /** A single bare Java name, the shape of the cause {@code fail(cause)} carries instead of a message. */
    private static final Pattern SIMPLE_NAME = Pattern.compile(NAME_TEXT);

    /** A bare or qualified name, the shape of a message the file may resolve to a constant. */
    private static final Pattern QUALIFIED_NAME = Pattern.compile(NAME_TEXT + "(?:\\s*\\.\\s*" + NAME_TEXT + ")*");

    /** A bare or qualified upper-case name, {@code EPS} or {@code Fixtures.EPS}: a tolerance in the third position. */
    private static final Pattern UPPER_CASE_NAME = Pattern.compile("(?:" + NAME_TEXT + "\\.)*([A-Z][A-Z0-9_$]*)");

    /** A message the scan does not measure: a supplier lambda or a method reference. */
    private static final Pattern DYNAMIC_MESSAGE = Pattern.compile(".*(?:->|::).*", Pattern.DOTALL);

    /** A name declared with a floating-point type in the scanned source: an operand that is a delta. */
    private static final Pattern FLOATING_DECLARATION = Pattern.compile(
            "\\b(?:double|float|Double|Float)\\s+(" + NAME_TEXT + ")");

    /** A name declared with the type {@code String} in the scanned source: a third argument that is a message. */
    private static final Pattern STRING_DECLARATION = Pattern.compile("\\bString\\s+(" + NAME_TEXT + ")");

    /** The head of a {@code static final String} constant; the initializer follows its end. */
    private static final Pattern CONSTANT_DECLARATION = Pattern.compile(
            "\\b(?:static\\s+final|final\\s+static)\\s+String\\s+(" + NAME_TEXT + ")\\s*=\\s*");

    /** A name declared with a throwable type in the scanned source: an argument that is a cause, not a message. */
    private static final Pattern THROWABLE_DECLARATION = Pattern.compile(
            "\\b((?:[A-Z][\\w$.]*)?(?:Exception|Throwable|Error))\\s+(" + NAME_TEXT + ")");

    /** A constructed throwable, the other shape of the cause {@code fail(cause)} carries instead of a message. */
    private static final Pattern CONSTRUCTED_THROWABLE = Pattern.compile(
            "new\\s+(?:" + NAME_TEXT + "\\.)*[A-Z][\\w$]*(?:Exception|Throwable|Error)\\s*\\(");

    private final String source;
    private final String view;
    private final Set<String> strings;
    private final Set<String> floatingPoints;
    private final Set<String> throwables;
    private final Map<String, String> constants;

    /** @param source the source text the calls are read from */
    MessageMeasurement(String source) {
        this.source = source;
        this.view = CodeView.of(source);
        this.strings = declared(STRING_DECLARATION, 1);
        this.floatingPoints = declared(FLOATING_DECLARATION, 1);
        this.throwables = declared(THROWABLE_DECLARATION, 2);
        this.constants = constants();
    }

    /** @return the code view of the source, the text a scan walks for assertion calls */
    String view() {
        return view;
    }

    /**
     * @param name the assertion method's name
     * @param open the index of the call's {@code (} in the view
     * @return the violation the call carries, the shape of a message accepted without measuring, or neither
     */
    Verdict verdictOf(String name, int open) {
        var spans = ArgumentSpans.of(view, open);
        if ("fail".equals(name)) {
            if (spans.isEmpty() || carriesNoCode(spans.getFirst())) {
                return Verdict.refused("no message");
            }
            var first = argument(spans.getFirst());
            if ((SIMPLE_NAME.matcher(first).matches() && throwables.contains(first))
                    || CONSTRUCTED_THROWABLE.matcher(first).find()) {
                return Verdict.refused("no message (a cause is not a message)");
            }
            return measure(spans.getFirst());
        }
        var minimum = ONE_ARGUMENT_ASSERTIONS.contains(name) ? 1 : 2;
        if (spans.size() <= minimum || carriesNoCode(spans.getLast())) {
            return Verdict.refused("no message");
        }
        if (isTolerance(name, spans)) {
            return Verdict.refused("no message (a tolerance is not a message)");
        }
        return measure(spans.getLast());
    }

    /** @return the verdict on a message: a literal is measured directly, a resolvable supplier or constant through it */
    private Verdict measure(int[] span) {
        var message = argument(span);
        var literal = literalOf(message);
        if (literal.isEmpty()) {
            return Verdict.unmeasured(shapeOf(message, code(span)));
        }
        var length = literal.get().length() - 2;
        if (length < MIN_MESSAGE_LENGTH) {
            return Verdict.refused("message shorter than " + MIN_MESSAGE_LENGTH + " characters (" + length + ")");
        }
        if (length > MAX_MESSAGE_LENGTH) {
            return Verdict.refused("message longer than " + MAX_MESSAGE_LENGTH + " characters (" + length + ")");
        }
        return Verdict.accepted();
    }

    /**
     * @return the literal a message is measured by: the message itself, the body of a supplier over one literal, or
     *         the initializer of the same file's {@code static final String} constant it names
     */
    private Optional<String> literalOf(String message) {
        if (LITERAL.matcher(message).matches()) {
            return Optional.of(message);
        }
        var supplier = SUPPLIER_OVER_LITERAL.matcher(message);
        if (supplier.matches()) {
            return Optional.of(supplier.group(1));
        }
        return Optional.ofNullable(constants.get(message));
    }

    /**
     * @param message the message argument's text
     * @param code the message argument in the code view, its literals blanked
     * @return the shape of a message the scan accepts without measuring
     */
    private static String shapeOf(String message, String code) {
        if (message.startsWith("\"\"\"")) {
            return "text block";
        }
        if (message.contains("->")) {
            return "supplier";
        }
        if (code.contains("::")) {
            return "method reference";
        }
        if (code.contains("+")) {
            return "concatenation";
        }
        if (QUALIFIED_NAME.matcher(message).matches()) {
            return "identifier";
        }
        return message.endsWith(")") ? "method call" : "expression";
    }

    /**
     * @return whether the third argument is the tolerance of a numeric assertion rather than its message: a numeric
     *         literal, an upper-case name the file does not declare as a {@code String}, or an expression that is
     *         neither a literal nor a supplier-shaped message when one of the first two operands is a floating-point
     *         value — a floating-point literal, or a name the scanned source declares as {@code double} or
     *         {@code float}
     */
    private boolean isTolerance(String name, List<int[]> spans) {
        if (!TOLERANCE_ASSERTIONS.contains(name) || spans.size() != 3) {
            return false;
        }
        var message = argument(spans.getLast());
        if (NUMERIC.matcher(message).matches()) {
            return true;
        }
        if (LITERAL.matcher(message).matches() || message.startsWith("\"\"\"")
                || DYNAMIC_MESSAGE.matcher(message).matches()) {
            return false;
        }
        var upperCase = UPPER_CASE_NAME.matcher(message);
        if (upperCase.matches()) {
            return !strings.contains(upperCase.group(1));
        }
        return isFloatingOperand(code(spans.get(0))) || isFloatingOperand(code(spans.get(1)))
                || floatingPoints.contains(message);
    }

    /** @return whether an operand of the code view carries a floating-point literal or names a floating-point value */
    private boolean isFloatingOperand(String operand) {
        return FLOATING.matcher(operand).find() || floatingPoints.contains(operand.strip());
    }

    /**
     * @return whether the argument carries no code at all — whitespace, or a comment the view blanks — while not being
     *         a string or a text block, whose delimiters the view keeps and whose content is a message
     */
    private boolean carriesNoCode(int[] span) {
        return code(span).strip().isEmpty() && !source.substring(span[0], span[1]).contains("\"");
    }

    /** @return the argument in the code view, its literals and comments blanked */
    private String code(int[] span) {
        return view.substring(span[0], span[1]);
    }

    /** @return the argument's own text: the view decides where it starts and ends, so a comment does not join it */
    private String argument(int[] span) {
        var begin = span[0];
        while (begin < span[1] && Character.isWhitespace(view.charAt(begin))) {
            begin++;
        }
        var end = span[1];
        while (end > begin && Character.isWhitespace(view.charAt(end - 1))) {
            end--;
        }
        return begin >= end ? source.substring(span[0], span[1]).strip() : source.substring(begin, end).strip();
    }

    private Set<String> declared(Pattern declaration, int group) {
        var names = new HashSet<String>();
        var matcher = declaration.matcher(view);
        while (matcher.find()) {
            names.add(matcher.group(group));
        }
        return names;
    }

    /** @return each {@code static final String} constant initialized with one literal, mapped to that literal */
    private Map<String, String> constants() {
        var found = new HashMap<String, String>();
        var matcher = CONSTANT_DECLARATION.matcher(view);
        while (matcher.find()) {
            var initializer = LITERAL.matcher(source).region(matcher.end(), source.length());
            if (!initializer.lookingAt()) {
                continue;
            }
            var after = ArgumentSpans.nextNonWhitespace(view, initializer.end());
            if (after < view.length() && view.charAt(after) == ';') {
                found.put(matcher.group(1), initializer.group());
            }
        }
        return found;
    }

    /**
     * The verdict on one assertion call.
     *
     * @param kind whether the call was refused, accepted unmeasured, or measured in range
     * @param text the violation of a refused call, the shape of an unmeasured message, empty for an accepted one
     */
    record Verdict(Kind kind, String text) {

        static Verdict refused(String violation) {
            return new Verdict(Kind.REFUSED, violation);
        }

        static Verdict unmeasured(String shape) {
            return new Verdict(Kind.UNMEASURED, shape);
        }

        static Verdict accepted() {
            return new Verdict(Kind.ACCEPTED, "");
        }
    }

    /** The three outcomes of a {@link Verdict}. */
    enum Kind {
        REFUSED, UNMEASURED, ACCEPTED
    }
}
