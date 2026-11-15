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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

/**
 * The scanner behind the rule of the {@code junit-core} skill that every assertion carries a message of 20 to 60
 * characters.
 * <p>
 * The scan reads the source text of a module's test tree. A call of one of the assertion methods of the rule that
 * carries no message argument fails, and a message written as one string literal outside the 20 to 60 character range
 * fails too. A message that is not a single literal — a supplier, a method reference, the exception's own message, a
 * concatenation, a constant or a text block — is accepted and not measured. An assertion call inside a comment, a
 * string or a text block is not code and is ignored.
 * <p>
 * The scan reads text, not types, and the shapes it cannot tell are stated here so a later plan does not read them as
 * coverage. A {@code <} is a type argument only when the token before it can end a type, the first token after it starts
 * a type (an upper-case name, a wildcard, an annotation, or a lower-case name qualified by a {@code .}), every
 * comma-separated element between it and its closing {@code >} starts a type, and the {@code >} closes before the
 * enclosing call ends; a comparison is therefore measured as the comparison it is. The third argument of
 * {@code assertEquals}, {@code assertNotEquals} and {@code assertArrayEquals} that is neither a string literal, a text
 * block nor a supplier-shaped expression is a tolerance, so such a call carries no message: a message constant in that
 * position is refused and an inline literal, a lambda or a method reference is the way to write it. A single bare name
 * passed to {@code fail} is the cause, not the message. A comment beside a message literal does not hide it from the
 * measurement.
 * <p>
 * A module adds the scan to its own test tree with a thin test that names its base package, as {@code pm-core}'s guard
 * and the thin tests of {@code pm-workflow} and the provider modules do. A package that a scan names and that holds no
 * test source fails the scan instead of passing as empty.
 *
 * @since 0.1
 */
@UtilityClass
public class AssertionMessages {

    /** The shortest message the rule accepts, in source characters between the quotes. */
    public static final int MIN_MESSAGE_LENGTH = 20;

    /** The longest message the rule accepts, in source characters between the quotes. */
    public static final int MAX_MESSAGE_LENGTH = 60;

    private static final Set<String> ASSERTIONS = Set.of("assertTrue", "assertFalse", "assertEquals",
            "assertNotEquals", "assertNull", "assertNotNull", "assertSame", "assertNotSame", "assertThrows",
            "assertThrowsExactly", "assertInstanceOf", "assertDoesNotThrow", "assertArrayEquals",
            "assertIterableEquals", "assertLinesMatch", "assertTimeout", "assertTimeoutPreemptively", "fail");

    /** The assertions whose signature starts with a single mandatory argument, so a message is the second argument. */
    private static final Set<String> ONE_ARGUMENT_ASSERTIONS = Set.of("assertTrue", "assertFalse", "assertNull",
            "assertNotNull", "assertDoesNotThrow");

    /** The assertions whose first argument is the message or the cause, so a call with no argument carries neither. */
    private static final Set<String> MESSAGE_FIRST_ASSERTIONS = Set.of("fail");

    private static final Pattern NUMERIC = Pattern.compile(
            "[+-]?(?:0[xX](?:[0-9a-fA-F][0-9a-fA-F_]*(?:\\.[0-9a-fA-F_]*)?|\\.[0-9a-fA-F][0-9a-fA-F_]*)[pP][+-]?\\d+"
                    + "|(?:\\d[\\d_]*(?:\\.[\\d_]*)?|\\.[\\d_]+)(?:[eE][+-]?\\d[\\d_]*)?)[dDfF]?");

    private static final Pattern LITERAL = Pattern.compile("\"(?:[^\"\\\\]|\\\\.)*\"");

    /** A floating-point literal anywhere inside an operand: {@code 1.0}, {@code .5f}, {@code 1e-6}, {@code 0x1.0p-6}. */
    private static final Pattern FLOATING = Pattern.compile(
            "(?<![\\w$])(?:0[xX][0-9a-fA-F_]*\\.[0-9a-fA-F_]*[pP][+-]?\\d+"
                    + "|(?:\\d[\\d_]*\\.[\\d_]*|\\.[\\d_]+)(?:[eE][+-]?\\d[\\d_]*)?[dDfF]?"
                    + "|\\d[\\d_]*[eE][+-]?\\d[\\d_]*[dDfF]?"
                    + "|\\d[\\d_]*[dDfF])(?![\\w$])");

    /** A single bare Java name, the shape of the cause {@code fail(cause)} carries instead of a message. */
    private static final Pattern SIMPLE_NAME = Pattern.compile("[a-zA-Z_$][a-zA-Z0-9_$]*");

    /** A message the scan does not measure: a supplier lambda or a method reference. */
    private static final Pattern DYNAMIC_MESSAGE = Pattern.compile(".*(?:->|::).*", Pattern.DOTALL);

    /** A name declared with a floating-point type in the scanned source: an operand that is a delta. */
    private static final Pattern FLOATING_DECLARATION = Pattern.compile(
            "\\b(?:double|float|Double|Float)\\s+([a-zA-Z_$][\\w$]*)");

    /** A name declared with a throwable type in the scanned source: an argument that is a cause, not a message. */
    private static final Pattern THROWABLE_DECLARATION = Pattern.compile(
            "\\b((?:[A-Z][\\w$.]*)?(?:Exception|Throwable|Error))\\s+([a-zA-Z_$][\\w$]*)");

    /**
     * @param sourceRoot the module's {@code src/test/java} directory
     * @param basePackage the package path below it that the scan covers, for example {@code de/planmarshall/core}
     * @param exemptions the package paths below {@code sourceRoot} the scan skips, each mapped to the reason it is
     *            exempt
     * @return the files read and the violations found below the base package, the exempt subtrees left out
     */
    public static Scan scan(Path sourceRoot, String basePackage, Map<String, String> exemptions) {
        var base = sourceRoot.resolve(basePackage);
        if (!Files.isDirectory(base)) {
            return new Scan(0, List.of(basePackage + ": no test source was scanned"));
        }
        var files = new TreeMap<String, Path>();
        try (Stream<Path> walk = Files.walk(base)) {
            walk.filter(Files::isRegularFile)
                    .filter(file -> file.toString().endsWith(".java"))
                    .filter(file -> !isExempt(sourceRoot.relativize(file).toString().replace('\\', '/'), exemptions))
                    .sorted()
                    .forEach(file -> files.put(relative(sourceRoot, file), file));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        if (files.isEmpty()) {
            return new Scan(0, List.of(basePackage + ": no test source was scanned"));
        }
        var violations = new ArrayList<String>();
        files.forEach((path, file) -> violations.addAll(scanSource(path, read(file))));
        return new Scan(files.size(), violations);
    }

    /**
     * @param basePackage the package path whose own {@code src/test/java} the caller's module holds
     * @return the test source root of the module this call runs in, found by walking up from the working directory
     */
    public static Path testSourceRoot(String basePackage) {
        var current = Path.of("").toAbsolutePath().normalize();
        for (var candidate = current; candidate != null; candidate = candidate.getParent()) {
            var relative = candidate.resolve("src/test/java");
            if (Files.isDirectory(relative.resolve(basePackage))) {
                return relative;
            }
        }
        throw new IllegalStateException("the root of the test sources for " + basePackage + " was not found");
    }

    /**
     * @param violations the violations of the scan
     * @param violationsFile where the scan writes the full list
     * @return the failure message of the guard, naming the first offenders and the file of the full list
     */
    public static String failureMessage(List<String> violations, Path violationsFile) {
        var shown = violations.stream().limit(MAX_REPORTED_VIOLATIONS).toList();
        var prefix = violations.size() + " assertion-message violation(s)";
        var text = violations.size() > MAX_REPORTED_VIOLATIONS
                ? prefix + "; the first " + MAX_REPORTED_VIOLATIONS + " follow"
                : prefix;
        return text + "; the full list is in " + violationsFile + ":\n    " + String.join("\n    ", shown);
    }

    /**
     * @param violationsFile where the full list goes
     * @param violations the violations of the scan
     */
    public static void writeViolations(Path violationsFile, List<String> violations) {
        try {
            var parent = violationsFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(violationsFile, violations);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /**
     * @param path the path a violation is reported under
     * @param source the source text
     * @return the violations of every assertion call in the source
     */
    static List<String> scanSource(String path, String source) {
        var view = codeView(source);
        var violations = new ArrayList<String>();
        var index = 0;
        while (index < view.length()) {
            if (!Character.isJavaIdentifierStart(view.charAt(index))) {
                index++;
                continue;
            }
            var start = index;
            while (index < view.length() && Character.isJavaIdentifierPart(view.charAt(index))) {
                index++;
            }
            var name = view.substring(start, index);
            var open = nextNonWhitespace(view, index);
            if (!ASSERTIONS.contains(name) || open >= view.length() || view.charAt(open) != '(') {
                continue;
            }
            var spans = argumentSpans(view, open);
            var line = lineAt(view, start);
            var violation = violationOf(name, spans, view, source);
            if (violation != null) {
                violations.add(path + ":" + line + " " + name + ": " + violation);
            }
        }
        return violations;
    }

    private static boolean isExempt(String relativePath, Map<String, String> exemptions) {
        for (var exempt : exemptions.keySet()) {
            if (relativePath.equals(exempt) || relativePath.startsWith(exempt + "/")) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param name the assertion method's name
     * @param spans the top-level argument spans of the call
     * @param view the blanked source the spans index into
     * @param source the unblanked source text the spans index into
     * @return the violation the call carries, or {@code null} when it carries a message the rule accepts
     */
    private static String violationOf(String name, List<int[]> spans, String view, String source) {
        if (MESSAGE_FIRST_ASSERTIONS.contains(name)) {
            if (spans.isEmpty() || carriesNoCode(spans.getFirst(), view, source)) {
                return "no message";
            }
            var first = argument(spans.getFirst(), view, source);
            if (SIMPLE_NAME.matcher(first).matches() && declaredThrowables(source).contains(first)) {
                return "no message (a cause is not a message)";
            }
            return measure(spans.getFirst(), view, source);
        }
        var minimum = ONE_ARGUMENT_ASSERTIONS.contains(name) ? 1 : 2;
        if (spans.size() <= minimum) {
            return "no message";
        }
        var messageSpan = spans.getLast();
        if (carriesNoCode(messageSpan, view, source)) {
            return "no message";
        }
        if (isTolerance(name, spans, view, source)) {
            return "no message (a tolerance is not a message)";
        }
        return measure(messageSpan, view, source);
    }

    /**
     * @return whether the argument carries no code at all — whitespace, or a comment whose text the view blanks — while
     *         not being a string or a text block, whose delimiters the view keeps and whose content is a message
     */
    private static boolean carriesNoCode(int[] span, String view, String source) {
        return view.substring(span[0], span[1]).strip().isEmpty()
                && !source.substring(span[0], span[1]).contains("\"");
    }

    /** @return the violation of a literal message's length, or {@code null} for an accepted message */
    private static String measure(int[] span, String view, String source) {
        var message = argument(span, view, source);
        if (!LITERAL.matcher(message).matches()) {
            return null;
        }
        var length = message.length() - 2;
        if (length < MIN_MESSAGE_LENGTH) {
            return "message shorter than " + MIN_MESSAGE_LENGTH + " characters (" + length + ")";
        }
        if (length > MAX_MESSAGE_LENGTH) {
            return "message longer than " + MAX_MESSAGE_LENGTH + " characters (" + length + ")";
        }
        return null;
    }

    /**
     * @return the argument's own text: the blanked view decides where it starts and ends, so a comment beside the
     *         argument does not join it and a text block stays whole
     */
    private static String argument(int[] span, String view, String source) {
        var begin = span[0];
        while (begin < span[1] && Character.isWhitespace(view.charAt(begin))) {
            begin++;
        }
        var end = span[1];
        while (end > begin && Character.isWhitespace(view.charAt(end - 1))) {
            end--;
        }
        if (begin >= end) {
            return source.substring(span[0], span[1]).strip();
        }
        return source.substring(begin, end).strip();
    }

    /**
     * @return whether the third argument is the tolerance of a numeric assertion rather than its message: a tolerance is
     *         a numeric literal, or an expression that is neither a literal nor a supplier-shaped message when one of the
     *         first two operands is a floating-point value — a floating-point literal, or a name the scanned source
     *         declares as {@code double} or {@code float}
     */
    private static boolean isTolerance(String name, List<int[]> spans, String view, String source) {
        if (!isNumericTolerance(name) || spans.size() != 3) {
            return false;
        }
        var message = argument(spans.getLast(), view, source);
        if (NUMERIC.matcher(message).matches()) {
            return true;
        }
        if (LITERAL.matcher(message).matches() || message.startsWith("\"\"\"")
                || DYNAMIC_MESSAGE.matcher(message).matches()) {
            return false;
        }
        var floating = declaredFloatingPoints(source);
        return isFloatingOperand(argument(spans.get(0), view, view), floating)
                || isFloatingOperand(argument(spans.get(1), view, view), floating);
    }

    /** @return whether an operand of the blanked view carries a floating-point literal or names a floating-point value */
    private static boolean isFloatingOperand(String operand, Set<String> floating) {
        return FLOATING.matcher(operand).find() || floating.contains(operand.strip());
    }

    /** @return the names the scanned source declares with a floating-point type */
    private static Set<String> declaredFloatingPoints(String source) {
        var names = new HashSet<String>();
        var matcher = FLOATING_DECLARATION.matcher(source);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    /** @return the names the scanned source declares with a throwable type */
    private static Set<String> declaredThrowables(String source) {
        var names = new HashSet<String>();
        var matcher = THROWABLE_DECLARATION.matcher(source);
        while (matcher.find()) {
            names.add(matcher.group(2));
        }
        return names;
    }

    private static boolean isNumericTolerance(String name) {
        return "assertEquals".equals(name) || "assertNotEquals".equals(name)
                || "assertArrayEquals".equals(name);
    }

    private static int nextNonWhitespace(String view, int from) {
        var index = from;
        while (index < view.length() && Character.isWhitespace(view.charAt(index))) {
            index++;
        }
        return index;
    }

    /** @return the start and end index of every top-level argument of the call whose {@code (} is at {@code open} */
    private static List<int[]> argumentSpans(String view, int open) {
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
            } else if (current == '<' && index > open + 1 && opensTypeArgument(view, index)) {
                angles++;
            } else if (current == '>' && angles > 0 && view.charAt(index - 1) != '-') {
                angles--;
            }
        }
        return spans;
    }

    /**
     * @return whether the {@code <} at {@code index} opens a type argument rather than a comparison: the token before
     *         it (whitespace skipped) can end a type, the first token after it starts one, every comma-separated
     *         element before the closing {@code >} starts a type, and that {@code >} closes before the enclosing call
     *         ends
     */
    private static boolean opensTypeArgument(String view, int index) {
        var previous = index - 1;
        while (previous >= 0 && Character.isWhitespace(view.charAt(previous))) {
            previous--;
        }
        if (previous < 0) {
            return false;
        }
        var before = view.charAt(previous);
        if (!(Character.isJavaIdentifierPart(before) || before == '>' || before == '.')) {
            return false;
        }
        var next = nextNonWhitespace(view, index + 1);
        if (next >= view.length() || !startsAType(view, next)) {
            return false;
        }
        var close = matchingAngle(view, index);
        return close > 0 && everyElementStartsAType(view, index + 1, close);
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
     * @return whether a type argument starts at this index: an upper-case name, a wildcard, an annotation, or a
     *         lower-case name that a {@code .} qualifies, as in {@code java.lang.String}
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
        return scan < view.length() && view.charAt(scan) == '.';
    }

    private static int lineAt(String view, int position) {
        var line = 1;
        for (var index = 0; index < position; index++) {
            if (view.charAt(index) == '\n') {
                line++;
            }
        }
        return line;
    }

    private static String relative(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /**
     * @param source the source text
     * @return the same text with comments and the content of string, character and text-block literals blanked, so a
     *         call inside one is not read as code; the length and the line breaks are kept, so an index into the view
     *         is an index into the source
     */
    private static String codeView(String source) {
        var view = new StringBuilder(source);
        var state = State.CODE;
        for (var index = 0; index < source.length(); index++) {
            var current = source.charAt(index);
            var next = index + 1 < source.length() ? source.charAt(index + 1) : '\0';
            switch (state) {
                case CODE -> {
                    if (current == '/' && next == '/') {
                        state = State.LINE_COMMENT;
                        view.setCharAt(index, ' ');
                    } else if (current == '/' && next == '*') {
                        state = State.BLOCK_COMMENT;
                        view.setCharAt(index, ' ');
                    } else if (current == '"' && next == '"' && index + 2 < source.length()
                            && source.charAt(index + 2) == '"') {
                        state = State.TEXT_BLOCK;
                        view.setCharAt(index, ' ');
                        view.setCharAt(index + 1, ' ');
                        view.setCharAt(index + 2, ' ');
                        index += 2;
                    } else if (current == '"') {
                        state = State.STRING;
                    } else if (current == '\'') {
                        state = State.CHARACTER;
                    }
                }
                case LINE_COMMENT -> {
                    if (current == '\n') {
                        state = State.CODE;
                    } else {
                        view.setCharAt(index, ' ');
                    }
                }
                case BLOCK_COMMENT -> {
                    if (current == '*' && next == '/') {
                        view.setCharAt(index, ' ');
                        view.setCharAt(index + 1, ' ');
                        index++;
                        state = State.CODE;
                    } else if (current != '\n') {
                        view.setCharAt(index, ' ');
                    }
                }
                case TEXT_BLOCK -> {
                    if (current == '\\' && index + 1 < source.length()) {
                        view.setCharAt(index, ' ');
                        view.setCharAt(index + 1, ' ');
                        index++;
                    } else if (current == '"' && next == '"' && index + 2 < source.length()
                            && source.charAt(index + 2) == '"') {
                        view.setCharAt(index, ' ');
                        view.setCharAt(index + 1, ' ');
                        view.setCharAt(index + 2, ' ');
                        index += 2;
                        state = State.CODE;
                    } else if (current != '\n') {
                        view.setCharAt(index, ' ');
                    }
                }
                case STRING -> {
                    if (current == '\\' && index + 1 < source.length()) {
                        view.setCharAt(index, ' ');
                        view.setCharAt(index + 1, ' ');
                        index++;
                    } else if (current == '"') {
                        state = State.CODE;
                    } else if (current != '\n') {
                        view.setCharAt(index, ' ');
                    }
                }
                case CHARACTER -> {
                    if (current == '\\' && index + 1 < source.length()) {
                        view.setCharAt(index, ' ');
                        view.setCharAt(index + 1, ' ');
                        index++;
                    } else if (current == '\'') {
                        state = State.CODE;
                    } else if (current != '\n') {
                        view.setCharAt(index, ' ');
                    }
                }
            }
        }
        return view.toString();
    }

    /** How many of the offenders a failing scan prints beside the full list's path. */
    private static final int MAX_REPORTED_VIOLATIONS = 50;

    /** The source files a scan read, and its violations; a package with no file fails instead of passing. */
    public record Scan(int files, List<String> violations) {
    }

    /** The lexical state of {@link #codeView(String)}. */
    private enum State {
        CODE, LINE_COMMENT, BLOCK_COMMENT, TEXT_BLOCK, STRING, CHARACTER
    }
}
