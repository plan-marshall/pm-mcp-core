/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.guard;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Guards the rule of the {@code junit-core} skill that every assertion carries a message of 20 to 60 characters.
 * <p>
 * The scan reads the source text of the scanned test packages. A call of one of the assertion methods of the rule that
 * carries no message argument fails, and a message written as one string literal outside the 20 to 60 character range
 * fails too. A message that is not a single literal — a supplier, a method reference, the exception's own message, a
 * concatenation, a constant or a text block — is accepted and not measured. An assertion call inside a comment, a
 * string or a text block is not code and is ignored.
 * <p>
 * The scan reads text, not types, so three shapes stay outside what it can tell. A comma in a top-level type argument
 * is tracked only when the {@code <} follows an identifier, a {@code >} or a {@code .}; a comparison written without
 * spaces ({@code a<b}) can therefore be read as the start of a type argument. A third argument of an assertion on
 * floating-point operands is read as a tolerance when one of the first two operands is written as a floating-point
 * literal, so {@code assertEquals(1.0, value, DELTA)} is measured while {@code assertEquals(EXPECTED, value, DELTA)}
 * is not — a message constant in that position is refused, and an inline literal, a lambda or a method reference is
 * the way to write it. The measurement is of the source spelling: an escape counts by its spelling, and a comment
 * beside a message literal makes it read as a non-literal. A plan that brings another package to the rule adds it to
 * {@link #SCANNED_PACKAGES}; a package with no test source fails the scan instead of passing as empty.
 * <p>
 * The class keeps the controls of every tracked shape beside the guard, so it is longer than the 400-line budget of
 * {@code junit-core}: splitting it would separate the fixture of a shape from the code the fixture proves, which is the
 * one property the controls exist for; the budget is reported, not enforced (standing rule R20).
 */
@EnableGeneratorController
@DisplayName("Every assertion carries a message")
class AssertionMessageScanTest {

    /** The test packages under the module's {@code src/test/java} that this guard scans. */
    private static final List<String> SCANNED_PACKAGES = List.of("de/planmarshall/core/store",
            "de/planmarshall/core/analysis", "de/planmarshall/core/build", "de/planmarshall/core/config",
            "de/planmarshall/core/findings", "de/planmarshall/core/guard", "de/planmarshall/core/log",
            "de/planmarshall/core/profile", "de/planmarshall/core/queue", "de/planmarshall/core/service",
            "de/planmarshall/core/spi", "de/planmarshall/core/spi/validation", "de/planmarshall/core/toon");

    private static final int MIN_MESSAGE_LENGTH = 20;

    private static final int MAX_MESSAGE_LENGTH = 60;

    /** How many of the offenders a failing scan prints beside the full list's path. */
    private static final int MAX_REPORTED_VIOLATIONS = 50;

    private static final Set<String> ASSERTIONS = Set.of("assertTrue", "assertFalse", "assertEquals",
            "assertNotEquals", "assertNull", "assertNotNull", "assertSame", "assertNotSame", "assertThrows",
            "assertThrowsExactly", "assertInstanceOf", "assertDoesNotThrow", "assertArrayEquals",
            "assertIterableEquals", "assertLinesMatch", "assertTimeout", "assertTimeoutPreemptively", "fail");

    /** The assertions whose signature starts with a single mandatory argument, so a message is the second argument. */
    private static final Set<String> ONE_ARGUMENT_ASSERTIONS = Set.of("assertTrue", "assertFalse", "assertNull",
            "assertNotNull", "assertDoesNotThrow");

    /** The assertions whose first argument is the message itself, so a call with no argument carries none. */
    private static final Set<String> MESSAGE_FIRST_ASSERTIONS = Set.of("fail");

    private static final Pattern NUMERIC = Pattern.compile(
            "[+-]?(?:0[xX](?:[0-9a-fA-F][0-9a-fA-F_]*(?:\\.[0-9a-fA-F_]*)?|\\.[0-9a-fA-F][0-9a-fA-F_]*)[pP][+-]?\\d+"
                    + "|(?:\\d[\\d_]*(?:\\.[\\d_]*)?|\\.[\\d_]+)(?:[eE][+-]?\\d[\\d_]*)?)[dDfF]?");

    private static final Pattern LITERAL = Pattern.compile("\"(?:[^\"\\\\]|\\\\.)*\"");

    /** A message the scan does not measure: a supplier lambda or a method reference. */
    private static final Pattern DYNAMIC_MESSAGE = Pattern.compile(".*(?:->|::).*", Pattern.DOTALL);

    /** Where the offenders of the last failing scan are written, so the failure names a readable list. */
    private static final Path VIOLATIONS = Path.of("target/assertion-message-violations.txt");

    /** The lexical state of {@link #codeView(String)}. */
    private enum State {
        CODE, LINE_COMMENT, BLOCK_COMMENT, TEXT_BLOCK, STRING, CHARACTER
    }

    @Test
    @DisplayName("every assertion of the scanned packages carries a 20 to 60 character message")
    void everyAssertionOfTheScannedPackagesCarriesAMessage() {
        var root = testSourceRoot();
        var violations = new ArrayList<String>();
        for (var scannedPackage : SCANNED_PACKAGES) {
            var scan = scanTree(root.resolve(scannedPackage));
            if (scan.files() == 0) {
                violations.add(scannedPackage + ": no test source was scanned");
            }
            violations.addAll(scan.violations());
        }
        if (!violations.isEmpty()) {
            writeViolations(violations);
        }

        assertTrue(violations.isEmpty(), () -> failureMessage(violations));
    }

    @Test
    @DisplayName("a missing message, an out-of-range message, a boundary and a planted shape are measured")
    void missingAndOutOfRangeMessagesAreReported(@TempDir Path temp) throws Exception {
        assertReported(temp.resolve("missing"), "assertTrue: no message", "assertTrue(refusal.isPresent());");
        assertReported(temp.resolve("nested"), "assertTrue: no message",
                "assertAll(\"the refusal\", () -> assertTrue(refusal.isPresent()));");
        assertReported(temp.resolve("short"), "shorter than " + MIN_MESSAGE_LENGTH,
                "assertTrue(refusal.isPresent(), \"too short\");");
        assertReported(temp.resolve("long"), "longer than " + MAX_MESSAGE_LENGTH,
                "assertTrue(refusal.isPresent(), \"" + "x".repeat(MAX_MESSAGE_LENGTH + 1) + "\");");
        assertReported(temp.resolve("array-tolerance"), "assertArrayEquals: no message",
                "assertArrayEquals(EXPECTED, value, 0.001);");
        assertReported(temp.resolve("tolerance-identifier"), "assertEquals: no message",
                "assertEquals(1.0, value, DELTA);");
        assertReported(temp.resolve("fail-missing"), "fail: no message", "fail();");
        assertReported(temp.resolve("assert-not-same"), "assertNotSame: no message",
                "assertNotSame(EXPECTED, value);");
        assertReported(temp.resolve("assert-names"), "assertIterableEquals: no message",
                "assertIterableEquals(EXPECTED, value);");
        assertReported(temp.resolve("assert-lines"), "assertLinesMatch: no message",
                "assertLinesMatch(EXPECTED, value);");
        assertReported(temp.resolve("assert-timeout"), "assertTimeout: no message",
                "assertTimeout(TIMEOUT, () -> store.read(file));");
        assertReported(temp.resolve("assert-preemptively"), "assertTimeoutPreemptively: no message",
                "assertTimeoutPreemptively(TIMEOUT, () -> store.read(file));");
        assertReported(temp.resolve("assert-throws-exactly"), "assertThrowsExactly: no message",
                "assertThrowsExactly(IllegalStateException.class, () -> store.read(file));");
        for (var tolerance : List.of("1e-6", "1_000.0", "0x1.0p-6")) {
            assertReported(temp.resolve("tolerance-" + tolerance), "assertEquals: no message",
                    "assertEquals(1.0, value, " + tolerance + ");");
        }
        assertReported(temp.resolve("type-argument"), "assertEquals: no message",
                "assertEquals(Map.of(), new HashMap<String, String>());");
        assertReported(temp.resolve("generic-call"), "assertTrue: no message",
                "assertTrue(Map.<String, String>of().isEmpty());");
        assertReported(temp.resolve("multi-line"), "assertTrue: no message", "assertTrue(\n                value);");
        assertReported(temp.resolve("qualified"), "assertTrue: no message", "Assertions.assertTrue(value);");
        assertNotReported(temp.resolve("twenty"), "assertTrue(value, \"" + "m".repeat(20) + "\");");
        assertNotReported(temp.resolve("sixty"), "assertTrue(value, \"" + "m".repeat(60) + "\");");
        assertReported(temp.resolve("nineteen"), "shorter than " + MIN_MESSAGE_LENGTH,
                "assertTrue(value, \"" + "m".repeat(19) + "\");");
        assertReported(temp.resolve("sixty-one"), "longer than " + MAX_MESSAGE_LENGTH,
                "assertTrue(value, \"" + "m".repeat(61) + "\");");
    }

    private static void assertReported(Path root, String reason, String statement) throws IOException {
        assertTrue(reported(packageFixture(root, statement), reason),
                () -> "expected '" + reason + "' for " + statement);
    }

    private static void assertNotReported(Path root, String statement) throws IOException {
        var violations = scanTree(packageFixture(root, statement)).violations();

        assertTrue(violations.isEmpty(), () -> "expected no violation for " + statement + " but got "
                + violations);
    }

    @Test
    @DisplayName("an assertion with a message in range, a dynamic message and a planted accepted shape are not reported")
    void acceptableAssertionsAreNotReported(@TempDir Path temp) throws Exception {
        var root = packageFixture(temp,
                "assertTrue(refusal.isPresent(), \"the refusal must be present\");",
                "assertEquals(\"the refusal\", refusal.orElseThrow().getMessage(), failure::getMessage);",
                "assertThrows(IllegalStateException.class, () -> store.read(file), () -> \"the store refuses\");",
                "fail(\"the root of the test sources was not found\");");

        assertTrue(scanTree(root).violations().isEmpty(),
                "a message in range and a dynamic message are accepted");
        assertNotReported(temp.resolve("type-argument-message"),
                "assertEquals(Map.of(), new HashMap<String, String>(), \"both maps are equal here\");");
        assertNotReported(temp.resolve("multi-line-message"),
                "assertTrue(value,\n                \"the message spans two lines\");");
        assertNotReported(temp.resolve("qualified-message"),
                "Assertions.assertTrue(value, \"the value is present here\");");
        assertNotReported(temp.resolve("text-block"),
                "assertTrue(value, \"\"\"\n                the message is a text block\n                \"\"\");");
        assertNotReported(temp.resolve("tolerance-message"),
                "assertEquals(1.0, value, DELTA, \"the value stays within the delta\");");
        var message = failureMessage(List.of("A.java:3 assertTrue: no message", "B.java:4 fail: no message"));

        assertTrue(message.contains("2 assertion-message violation(s)"), "the failure counts the offenders");
        assertTrue(message.contains("A.java:3"), "the failure names the first offender");
        assertTrue(message.contains("B.java:4"), "the failure names the second offender");
        assertTrue(message.contains(VIOLATIONS.toString()), "the failure names the file of the full list");
    }

    @Test
    @DisplayName("an assertion call inside a comment or a string is not reported")
    void assertionTextOutsideCodeIsNotReported(@TempDir Path temp) throws Exception {
        var root = packageFixture(temp,
                "// assertTrue(refusal.isPresent());",
                "/* assertTrue(refusal.isPresent()); */",
                "assertTrue(text.contains(\"assertTrue(value);\"), \"the text carries the assertion text\");",
                "var block = \"\"\"\n                assertTrue(value);\n                \"\"\";",
                "assertFalse(block.isBlank(), \"the planted text block is not blank\");");

        assertTrue(scanTree(root).violations().isEmpty(),
                "an assertion call in a comment or a string is not code");
    }

    private static boolean reported(Path root, String reason) {
        return scanTree(root).violations().stream().anyMatch(violation -> violation.contains(reason));
    }

    private static String failureMessage(List<String> violations) {
        var shown = violations.stream().limit(MAX_REPORTED_VIOLATIONS).collect(Collectors.joining("\n    "));
        var prefix = violations.size() + " assertion-message violation(s)";
        return violations.size() > MAX_REPORTED_VIOLATIONS
                ? prefix + "; the first " + MAX_REPORTED_VIOLATIONS + " follow; the full list is in " + VIOLATIONS
                        + ":\n    " + shown
                : prefix + "; the full list is in " + VIOLATIONS + ":\n    " + shown;
    }

    private static void writeViolations(List<String> violations) {
        try {
            Files.createDirectories(VIOLATIONS.getParent());
            Files.write(VIOLATIONS, violations);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static Path packageFixture(Path root, String... statements) throws IOException {
        var directory = root.resolve("de/planmarshall/fixture");
        Files.createDirectories(directory);
        var className = "C" + Generators.letterStrings(3, 10).next();
        var source = "class " + className + " {\n    void planted() {\n        "
                + String.join("\n        ", statements) + "\n    }\n}\n";
        Files.writeString(directory.resolve(className + ".java"), source);
        return root;
    }

    private static Path testSourceRoot() {
        var current = Path.of("").toAbsolutePath().normalize();
        for (var candidate = current; candidate != null; candidate = candidate.getParent()) {
            var moduleRelative = candidate.resolve("src/test/java");
            if (Files.isDirectory(moduleRelative.resolve(SCANNED_PACKAGES.getFirst()))) {
                return moduleRelative;
            }
            var reactorRelative = candidate.resolve("pm-core/src/test/java");
            if (Files.isDirectory(reactorRelative.resolve(SCANNED_PACKAGES.getFirst()))) {
                return reactorRelative;
            }
        }
        return fail("the root of the pm-core test sources was not found");
    }

    private static Scan scanTree(Path root) {
        if (!Files.isDirectory(root)) {
            return new Scan(0, List.of());
        }
        var violations = new ArrayList<String>();
        var files = new ArrayList<Path>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                    .filter(file -> file.toString().endsWith(".java"))
                    .sorted()
                    .forEach(files::add);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        files.forEach(file -> violations.addAll(scan(relative(root, file), read(file))));
        return new Scan(files.size(), violations);
    }

    /** The source files a package scan read, and its violations; a package with no file fails instead of passing. */
    private record Scan(int files, List<String> violations) {
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

    private static List<String> scan(String path, String source) {
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
            var violation = violationOf(name, spans, source);
            if (violation != null) {
                violations.add(path + ":" + line + " " + name + ": " + violation);
            }
        }
        return violations;
    }

    /**
     * @param name the assertion method's name
     * @param spans the top-level argument spans of the call
     * @param source the unblanked source text the spans index into
     * @return the violation the call carries, or {@code null} when it carries a message the rule accepts
     */
    private static String violationOf(String name, List<int[]> spans, String source) {
        if (MESSAGE_FIRST_ASSERTIONS.contains(name)) {
            return spans.isEmpty() ? "no message" : measure(spans.getFirst(), source);
        }
        var minimum = ONE_ARGUMENT_ASSERTIONS.contains(name) ? 1 : 2;
        if (spans.size() <= minimum) {
            return "no message";
        }
        var messageSpan = spans.getLast();
        var message = argument(messageSpan, source);
        if (isTolerance(name, spans, source, message)) {
            return "no message (a tolerance is not a message)";
        }
        return measure(messageSpan, source);
    }

    /** @return the violation of a literal message's length, or {@code null} for an accepted message */
    private static String measure(int[] span, String source) {
        var message = argument(span, source);
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

    private static String argument(int[] span, String source) {
        return source.substring(span[0], span[1]).strip();
    }

    /**
     * @return whether the third argument is the tolerance of an assertion on floating-point operands rather than its
     *         message: a numeric literal, or any argument that is neither a literal nor a supplier-shaped expression
     *         when one of the two operands is written as a floating-point literal
     */
    private static boolean isTolerance(String name, List<int[]> spans, String source, String message) {
        if (!isNumericTolerance(name) || spans.size() != 3) {
            return false;
        }
        if (NUMERIC.matcher(message).matches()) {
            return true;
        }
        if (LITERAL.matcher(message).matches() || DYNAMIC_MESSAGE.matcher(message).matches()) {
            return false;
        }
        return isFloatingLiteral(argument(spans.get(0), source)) || isFloatingLiteral(argument(spans.get(1), source));
    }

    /** @return whether the operand of the source text is written as a floating-point literal */
    private static boolean isFloatingLiteral(String operand) {
        return NUMERIC.matcher(operand).matches() && (operand.indexOf('.') >= 0 || operand.indexOf('e') >= 0
                || operand.indexOf('E') >= 0 || operand.indexOf('p') >= 0 || operand.indexOf('P') >= 0
                || operand.endsWith("d") || operand.endsWith("D") || operand.endsWith("f") || operand.endsWith("F"));
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
            } else if (current == '<' && index > open + 1 && opensTypeArgument(view.charAt(index - 1))) {
                angles++;
            } else if (current == '>' && angles > 0 && view.charAt(index - 1) != '-') {
                angles--;
            }
        }
        return spans;
    }

    /** @return whether a {@code <} after this character opens a type argument rather than a comparison */
    private static boolean opensTypeArgument(char previous) {
        return Character.isJavaIdentifierPart(previous) || previous == '>' || previous == '.';
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
}
