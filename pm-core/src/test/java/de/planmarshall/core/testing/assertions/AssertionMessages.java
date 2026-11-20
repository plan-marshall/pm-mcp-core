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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

/**
 * The scanner behind the rule of the {@code junit-core} skill that every assertion carries a message of 20 to 60
 * characters.
 * <p>
 * The scan reads the source text of a module's test tree. A call of one of the assertion methods of the rule that
 * carries no message argument fails, and a measured message outside the 20 to 60 character range fails too. A message
 * is measured when it is one string literal, a supplier whose body is one string literal ({@code () -> "..."}), or the
 * name of a {@code static final String} constant the same file declares with one string literal. Any other message — a
 * name the file does not resolve to such a constant, a method reference, a supplier over more than a literal, the
 * exception's own message, a concatenation or a text block — is accepted and not measured, and the scan lists it in
 * {@link Scan#unmeasured()} with its line and shape; the guards do not fail on that listing, it is the count of what
 * the rule does not reach. An assertion call inside a comment, a string or a text block is not code and is ignored.
 * <p>
 * {@code assertAll} is not one of the measured assertions: its heading labels a group, and the assertions inside the
 * group carry their own messages, which are measured. A heading of any length is therefore accepted.
 * <p>
 * The scan reads text, not types, and the shapes it cannot tell are stated here so a later plan does not read them as
 * coverage. A {@code <} is a type argument only when the token before it is a {@code .}, a {@code >} or a type name (an
 * upper-case name that carries a lower-case letter, so a constant such as {@code MAX} is an operand), the first token
 * after it starts a type (an upper-case name, a wildcard, an annotation, a primitive array such as {@code int[]}, or a
 * lower-case name qualified by a {@code .}), every comma-separated element between it and its closing {@code >} starts
 * a type, and the {@code >} closes before the enclosing call ends; a comparison is therefore measured as the
 * comparison it is. The third argument of {@code assertEquals}, {@code assertNotEquals} and {@code assertArrayEquals}
 * is a tolerance, so such a call carries no message, when it is a numeric literal, a qualified or bare upper-case name
 * ({@code Fixtures.EPS}, a statically imported {@code EPS}) that the file does not declare as a {@code String}, or an
 * expression that is neither a literal, a text block nor supplier-shaped beside a floating-point operand. A single
 * argument passed to {@code fail} that names a throwable the source declares, or that constructs one with {@code new},
 * is the cause, not the message. A comment beside a message literal does not hide it from the measurement.
 * <p>
 * A module adds the scan to its own test tree with a thin test that names its base package; {@code pm-core}'s guard is
 * the first of them. A package that a scan names and that holds no test source fails the scan instead of passing as
 * empty. The lexer is {@link CodeView}, the argument splitter {@link ArgumentSpans}, and the verdict on one call
 * {@link MessageMeasurement}.
 *
 * @since 0.1
 */
@UtilityClass
public class AssertionMessages {

    /** The shortest message the rule accepts, in source characters between the quotes. */
    public static final int MIN_MESSAGE_LENGTH = 20;

    /** The longest message the rule accepts, in source characters between the quotes. */
    public static final int MAX_MESSAGE_LENGTH = 60;

    /** How many of the offenders a failing scan prints beside the full list's path. */
    private static final int MAX_REPORTED_VIOLATIONS = 50;

    private static final Set<String> ASSERTIONS = Set.of("assertTrue", "assertFalse", "assertEquals",
            "assertNotEquals", "assertNull", "assertNotNull", "assertSame", "assertNotSame", "assertThrows",
            "assertThrowsExactly", "assertInstanceOf", "assertDoesNotThrow", "assertArrayEquals",
            "assertIterableEquals", "assertLinesMatch", "assertTimeout", "assertTimeoutPreemptively", "fail");

    /**
     * @param sourceRoot the module's {@code src/test/java} directory
     * @param basePackage the package path below it that the scan covers, for example {@code de/planmarshall/core}
     * @param exemptions the package paths below {@code sourceRoot} the scan skips, each mapped to the reason it is
     *            exempt
     * @return the files read, the violations found and the messages accepted unmeasured below the base package, the
     *         exempt subtrees left out
     */
    public static Scan scan(Path sourceRoot, String basePackage, Map<String, String> exemptions) {
        var base = sourceRoot.resolve(basePackage);
        if (!Files.isDirectory(base)) {
            return new Scan(0, List.of(basePackage + ": no test source was scanned"), List.of());
        }
        var files = new TreeMap<String, Path>();
        try (Stream<Path> walk = Files.walk(base)) {
            walk.filter(Files::isRegularFile)
                    .filter(file -> file.toString().endsWith(".java"))
                    .filter(file -> !isExempt(relative(sourceRoot, file), exemptions))
                    .forEach(file -> files.put(relative(sourceRoot, file), file));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        if (files.isEmpty()) {
            return new Scan(0, List.of(basePackage + ": no test source was scanned"), List.of());
        }
        var violations = new ArrayList<String>();
        var unmeasured = new ArrayList<String>();
        files.forEach((path, file) -> {
            var scan = scanSource(path, read(file));
            violations.addAll(scan.violations());
            unmeasured.addAll(scan.unmeasured());
        });
        return new Scan(files.size(), violations, unmeasured);
    }

    /**
     * The walk starts at the working directory and goes up through its parents only, never down into a child module:
     * the build runs a module's tests in the module's own directory and finds the root at once, while an IDE that runs
     * the test from the repository root finds no {@code src/test/java} holding the package and gets the
     * {@link IllegalStateException}.
     *
     * @param basePackage the package path whose own {@code src/test/java} the caller's module holds
     * @return the test source root of the module this call runs in, found by walking up from the working directory
     * @throws IllegalStateException when neither the working directory nor a parent of it holds the package
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
     * @param violations the lines of the list, the violations of a scan or its unmeasured messages
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
     * @return the violations and the unmeasured messages of every assertion call in the source, as a scan of one file
     */
    static Scan scanSource(String path, String source) {
        var measurement = new MessageMeasurement(source);
        var view = measurement.view();
        var violations = new ArrayList<String>();
        var unmeasured = new ArrayList<String>();
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
            var open = ArgumentSpans.nextNonWhitespace(view, index);
            if (!ASSERTIONS.contains(name) || open >= view.length() || view.charAt(open) != '(') {
                continue;
            }
            var prefix = path + ":" + lineAt(view, start) + " " + name + ": ";
            var verdict = measurement.verdictOf(name, open);
            if (verdict.kind() == MessageMeasurement.Kind.REFUSED) {
                violations.add(prefix + verdict.text());
            } else if (verdict.kind() == MessageMeasurement.Kind.UNMEASURED) {
                unmeasured.add(prefix + verdict.text());
            }
        }
        return new Scan(1, violations, unmeasured);
    }

    private static boolean isExempt(String relativePath, Map<String, String> exemptions) {
        for (var exempt : exemptions.keySet()) {
            if (relativePath.equals(exempt) || relativePath.startsWith(exempt + "/")) {
                return true;
            }
        }
        return false;
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
     * The source files a scan read, its violations, and the messages it accepted without measuring; a package with no
     * file fails instead of passing.
     *
     * @param files how many source files the scan read
     * @param violations every violation, as {@code path:line name: reason}
     * @param unmeasured every message accepted without measuring, as {@code path:line name: shape}; report-only
     */
    public record Scan(int files, List<String> violations, List<String> unmeasured) {
    }
}
