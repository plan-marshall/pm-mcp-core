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

import static org.junit.jupiter.api.Assertions.assertFalse;
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
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Guards the rule of the {@code junit-core} skill that every assertion carries a message of 20 to 60 characters.
 * <p>
 * The scan reads the source text of the scanned test packages. A call of one of the assertion methods of the rule that
 * carries no message argument fails, and a literal message outside the 20 to 60 character range fails too. A dynamic
 * message — a supplier, a method reference, the exception's own message or a concatenation — is accepted and not
 * measured, because its length is not a property of the source. An assertion call inside a comment, a string or a text
 * block is not code and is ignored.
 * <p>
 * A plan that brings another package of this module to the rule adds that package to {@link #SCANNED_PACKAGES}.
 */
@EnableGeneratorController
@DisplayName("Every assertion carries a message")
class AssertionMessageScanTest {

    /** The test packages under the module's {@code src/test/java} that this guard scans. */
    private static final List<String> SCANNED_PACKAGES = List.of("de/planmarshall/core/store");

    private static final int MIN_MESSAGE_LENGTH = 20;

    private static final int MAX_MESSAGE_LENGTH = 60;

    private static final Set<String> ASSERTIONS = Set.of("assertTrue", "assertFalse", "assertEquals",
            "assertNotEquals", "assertNull", "assertNotNull", "assertSame", "assertThrows", "assertInstanceOf",
            "assertDoesNotThrow", "assertArrayEquals");

    /** The assertions whose signature starts with a single mandatory argument, so a message is the second argument. */
    private static final Set<String> ONE_ARGUMENT_ASSERTIONS = Set.of("assertTrue", "assertFalse", "assertNull",
            "assertNotNull", "assertDoesNotThrow");

    private static final Pattern NUMERIC = Pattern.compile("[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)[dDfF]?");

    private static final Pattern LITERAL = Pattern.compile("\"(?:[^\"\\\\]|\\\\.)*\"");

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
            violations.addAll(scanTree(root.resolve(scannedPackage)));
        }
        if (!violations.isEmpty()) {
            writeViolations(violations);
        }

        assertTrue(violations.isEmpty(), () -> "Every assertion needs a message of 20 to 60 characters; the "
                + "offenders are listed in " + VIOLATIONS);
    }

    @Test
    @DisplayName("an assertion without a message is reported")
    void assertionWithoutMessageIsReported(@TempDir Path temp) throws Exception {
        var root = packageFixture(temp, "assertTrue(refusal.isPresent());");

        assertTrue(reported(root, "no message"), "an assertion without a message must be reported");
    }

    @Test
    @DisplayName("an assertion inside an assertAll lambda is reported")
    void assertionInsideAssertAllIsReported(@TempDir Path temp) throws Exception {
        var root = packageFixture(temp,
                "assertAll(\"the refusal\", () -> assertTrue(refusal.isPresent()));");

        assertTrue(reported(root, "no message"), "an assertion inside an assertAll lambda must be reported");
    }

    @Test
    @DisplayName("a literal message shorter than 20 characters is reported")
    void shortMessageIsReported(@TempDir Path temp) throws Exception {
        var root = packageFixture(temp, "assertTrue(refusal.isPresent(), \"too short\");");

        assertTrue(reported(root, "shorter than " + MIN_MESSAGE_LENGTH), "a message below 20 characters must be "
                + "reported");
    }

    @Test
    @DisplayName("a literal message longer than 60 characters is reported")
    void longMessageIsReported(@TempDir Path temp) throws Exception {
        var root = packageFixture(temp,
                "assertTrue(refusal.isPresent(), \"" + "x".repeat(MAX_MESSAGE_LENGTH + 1) + "\");");

        assertTrue(reported(root, "longer than " + MAX_MESSAGE_LENGTH), "a message above 60 characters must be "
                + "reported");
    }

    @Test
    @DisplayName("an assertion with a message in range and a dynamic message are not reported")
    void acceptableAssertionsAreNotReported(@TempDir Path temp) throws Exception {
        var root = packageFixture(temp,
                "assertTrue(refusal.isPresent(), \"the refusal must be present\");",
                "assertEquals(\"the refusal\", refusal.orElseThrow().getMessage(), failure::getMessage);",
                "assertThrows(IllegalStateException.class, () -> store.read(file), () -> \"the store refuses\");");

        assertFalse(scanTree(root).stream().anyMatch(violation -> violation.contains("no message")),
                "a message in range and a dynamic message are accepted");
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

        assertFalse(scanTree(root).stream().anyMatch(violation -> violation.contains("no message")),
                "an assertion call in a comment or a string is not code");
    }

    private static boolean reported(Path root, String reason) {
        return scanTree(root).stream().anyMatch(violation -> violation.contains(reason));
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

    private static List<String> scanTree(Path root) {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        var violations = new ArrayList<String>();
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(Files::isRegularFile)
                    .filter(file -> file.toString().endsWith(".java"))
                    .sorted()
                    .forEach(file -> violations.addAll(scan(relative(root, file), read(file))));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return violations;
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
            var minimum = ONE_ARGUMENT_ASSERTIONS.contains(name) ? 1 : 2;
            var line = lineAt(view, start);
            if (spans.size() <= minimum) {
                violations.add(path + ":" + line + " " + name + ": no message");
            } else if (spans.size() == minimum + 1 && NUMERIC.matcher(source.substring(spans.getLast()[0],
                    spans.getLast()[1]).strip()).matches() && isNumericTolerance(name)) {
                violations.add(path + ":" + line + " " + name + ": no message (a tolerance is not a message)");
            } else {
                var message = source.substring(spans.getLast()[0], spans.getLast()[1]).strip();
                if (LITERAL.matcher(message).matches()) {
                    var length = message.length() - 2;
                    if (length < MIN_MESSAGE_LENGTH) {
                        violations.add(path + ":" + line + " " + name + ": message shorter than "
                                + MIN_MESSAGE_LENGTH + " characters (" + length + ")");
                    } else if (length > MAX_MESSAGE_LENGTH) {
                        violations.add(path + ":" + line + " " + name + ": message longer than "
                                + MAX_MESSAGE_LENGTH + " characters (" + length + ")");
                    }
                }
            }
        }
        return violations;
    }

    private static boolean isNumericTolerance(String name) {
        return "assertEquals".equals(name) || "assertNotEquals".equals(name);
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
            } else if (current == ',' && depth == 0) {
                spans.add(new int[]{start, index});
                start = index + 1;
            }
        }
        return spans;
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
