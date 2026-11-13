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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Guards that the grammar of a role name and of a harness id is written once.
 * <p>
 * Every {@code src/main/java} file of the reactor is scanned for a string literal that carries the two-to-32 name
 * grammar, in its spellings, and the scan fails naming the file, the line and the two classes that hold the one
 * definition ({@code RoleName} and {@code HarnessId}) of the {@code core.id} package. Only
 * {@code core/id/ShortName.java}, which writes the text once, is excluded; the guard has no exemption for another
 * production file (test sources are not scanned at all).
 * <p>
 * A source scan rather than an ArchUnit rule: ArchUnit does not see string constants, and the class-file scan of
 * {@code ProfileNeutralityArchTest} reaches one module. <b>Limit:</b> a grammar composed at run time (two literals
 * concatenated, a constant read from a file) and a hand-written character loop are not found; they are the review's
 * business, and the guide {@code pm-core/doc/developer/core-id.adoc} states the limit.
 */
@EnableGeneratorController
@DisplayName("One definition of the role-name grammar")
class RoleNameSingleDefinitionTest {

    private static final String PRODUCTION_SOURCE = "src/main/java";

    private static final String SHORT_NAME = "core/id/ShortName.java";

    private static final List<String> MODULES = List.of("pm-core", "pm-workflow", "pm-runtime");

    /** The spellings of the two-to-32 name grammar the scan finds, as the source writes them. */
    private static final List<String> SPELLINGS = List.of("[a-z][a-z0-9-]{1,31}", "[a-z][a-z0-9\\\\-]{1,31}",
            "[a-z][-a-z0-9]{1,31}");

    @Test
    @DisplayName("no production source carries a copy of the name grammar")
    void noCopyOfTheGrammar() {
        var root = repositoryRoot();
        var offenders = new ArrayList<String>();
        var filesPerModule = new TreeMap<String, Integer>();
        for (var productionRoot : productionRoots(root)) {
            var scan = scan(root, productionRoot);
            offenders.addAll(scan.offences());
            if (scan.files() > 0) {
                filesPerModule.merge(root.relativize(productionRoot).getName(0).toString(), scan.files(),
                        Integer::sum);
            }
        }

        assertAll(
                () -> MODULES.forEach(module -> assertTrue(filesPerModule.getOrDefault(module, 0) > 0,
                        "The scan should read at least one file of " + module)),
                () -> assertTrue(offenders.isEmpty(),
                        () -> "The name grammar is defined once, in ShortName; these files carry a copy (use RoleName "
                                + "or HarnessId): " + offenders));
    }

    @Test
    @DisplayName("the one definition writes the grammar text")
    void oneDefinitionWritesTheText() {
        var shortName = sourceOf(repositoryRoot().resolve("pm-core").resolve(PRODUCTION_SOURCE), SHORT_NAME);

        assertTrue(containsSpelling(shortName), "The one definition should write the grammar text");
    }

    @ParameterizedTest
    @ValueSource(strings = {"[a-z][a-z0-9-]{1,31}", "[a-z][a-z0-9\\\\-]{1,31}", "[a-z][-a-z0-9]{1,31}",
            "^[a-z][a-z0-9-]{1,31}$", "[a-z][a-z0-9-]{1,31}+", "^[a-z][-a-z0-9]{1,31}+$"})
    @DisplayName("each spelling of the grammar in a literal is found")
    void spellingIsFound(String spelling) {
        var source = "class C {\n    Pattern P = Pattern.compile(\"" + spelling + "\");\n}\n";

        assertFalse(offences(source).isEmpty(), "A literal of the grammar should be an offence");
    }

    @ParameterizedTest
    @ValueSource(strings = {"[a-z][a-z0-9-]{0,31}", "[a-z][a-z0-9-]{1,63}", "[a-z][a-z0-9-]{2,32}"})
    @DisplayName("another bound of the character class is not the grammar")
    void anotherBoundIsNotTheGrammar(String spelling) {
        var source = "class C {\n    Pattern P = Pattern.compile(\"" + spelling + "\");\n}\n";

        assertTrue(offences(source).isEmpty(), "Another bound should not be reported as the grammar");
    }

    @Test
    @DisplayName("the grammar inside a line comment or a Javadoc is not found")
    void commentIsNotTheGrammar() {
        var source = "/*\n * Pattern P = Pattern.compile(\"[a-z][a-z0-9-]{1,31}\");\n */\nclass C {\n"
                + "    // Pattern.compile(\"[a-z][a-z0-9-]{1,31}\")\n}\n";

        assertTrue(offences(source).isEmpty(), "The grammar in a comment should not be reported");
    }

    @Test
    @DisplayName("the grammar inside a text block is found")
    void textBlockIsFound() {
        var source = "class C {\n    String text = \"\"\"\n        [a-z][a-z0-9-]{1,31}\n        \"\"\";\n}\n";

        assertFalse(offences(source).isEmpty(), "The grammar in a text block should be an offence");
    }

    @Test
    @DisplayName("a quote character literal does not hide the grammar after it")
    void quoteCharacterLiteralDoesNotHideTheGrammar() {
        var source = "class C {\n    char quote = '\"';\n"
                + "    Pattern P = Pattern.compile(\"[a-z][a-z0-9-]{1,31}\");\n}\n";

        assertFalse(offences(source).isEmpty(), "A copy after a quote character literal should be an offence");
    }

    @Test
    @DisplayName("an escaped character literal does not hide the grammar after it")
    void escapedCharacterLiteralDoesNotHideTheGrammar() {
        var source = "class C {\n    char backslash = '\\\\';\n    char apostrophe = '\\'';\n"
                + "    Pattern P = Pattern.compile(\"[a-z][a-z0-9-]{1,31}\");\n}\n";

        assertFalse(offences(source).isEmpty(), "A copy after an escaped character literal should be an offence");
    }

    private static Scan scan(Path root, Path productionRoot) {
        var offenders = new ArrayList<String>();
        var files = 0;
        try (var walk = Files.walk(productionRoot)) {
            for (var file : walk.filter(Files::isRegularFile).filter(RoleNameSingleDefinitionTest::isJava)
                    .toList()) {
                files++;
                if (isShortName(file)) {
                    continue;
                }
                for (var offence : offences(read(file))) {
                    offenders.add(root.relativize(file) + offence);
                }
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return new Scan(List.copyOf(offenders), files);
    }

    private static boolean isShortName(Path file) {
        return file.toString().replace('\\', '/').endsWith("/" + SHORT_NAME);
    }

    private static List<String> offences(String source) {
        var offences = new ArrayList<String>();
        var literal = new StringBuilder();
        var line = 1;
        var literalLine = 1;
        var inLiteral = false;
        var inTextBlock = false;
        var inCharacterLiteral = false;
        var escaped = false;
        var inBlockComment = false;
        var inLineComment = false;
        for (var index = 0; index < source.length(); index++) {
            var character = source.charAt(index);
            if (character == '\n') {
                line++;
            }
            if (inBlockComment) {
                if (character == '*' && index + 1 < source.length() && source.charAt(index + 1) == '/') {
                    inBlockComment = false;
                    index++;
                }
                continue;
            }
            if (inLineComment) {
                inLineComment = character != '\n';
                continue;
            }
            if (inCharacterLiteral) {
                if (escaped) {
                    escaped = false;
                } else if (character == '\\') {
                    escaped = true;
                } else if (character == '\'') {
                    inCharacterLiteral = false;
                }
                continue;
            }
            if (inLiteral) {
                if (!inTextBlock) {
                    if (escaped) {
                        escaped = false;
                        literal.append(character);
                        continue;
                    }
                    if (character == '\\') {
                        escaped = true;
                        literal.append(character);
                        continue;
                    }
                }
                if (character == '"') {
                    if (inTextBlock && index + 2 < source.length() && source.startsWith("\"\"\"", index)) {
                        inLiteral = false;
                        inTextBlock = false;
                        index += 2;
                        offences.addAll(offencesOf(literal.toString(), literalLine));
                        literal.setLength(0);
                        continue;
                    }
                    if (!inTextBlock) {
                        inLiteral = false;
                        offences.addAll(offencesOf(literal.toString(), literalLine));
                        literal.setLength(0);
                        continue;
                    }
                }
                literal.append(character);
                continue;
            }
            if (character == '/' && index + 1 < source.length()) {
                var next = source.charAt(index + 1);
                if (next == '/') {
                    inLineComment = true;
                    index++;
                    continue;
                }
                if (next == '*') {
                    inBlockComment = true;
                    index++;
                    continue;
                }
            }
            if (character == '\'') {
                inCharacterLiteral = true;
                escaped = false;
                continue;
            }
            if (character == '"') {
                inLiteral = true;
                inTextBlock = index + 2 < source.length() && source.startsWith("\"\"\"", index);
                if (inTextBlock) {
                    index += 2;
                }
                escaped = false;
                literalLine = line;
                literal.setLength(0);
            }
        }
        return offences;
    }

    private static List<String> offencesOf(String literal, int line) {
        for (var spelling : SPELLINGS) {
            if (literal.contains(spelling)) {
                return List.of(":" + line + " carries the name grammar");
            }
        }
        return List.of();
    }

    private static boolean containsSpelling(String source) {
        return SPELLINGS.stream().anyMatch(source::contains);
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static String sourceOf(Path productionRoot, String suffix) {
        try (var files = Files.walk(productionRoot)) {
            var match = files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().replace('\\', '/').endsWith(suffix)).findFirst();
            return match.map(RoleNameSingleDefinitionTest::read)
                    .orElseGet(() -> fail("the one definition was not found: " + suffix));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static boolean isJava(Path path) {
        return path.toString().endsWith(".java");
    }

    private static Path repositoryRoot() {
        var configured = System.getProperty("pm.root");
        if (configured != null && !configured.isBlank()) {
            var candidate = Path.of(configured).toAbsolutePath().normalize();
            if (Files.isDirectory(candidate.resolve("pm-core"))) {
                return candidate;
            }
        }
        var current = Path.of("").toAbsolutePath().normalize();
        for (var candidate = current; candidate != null; candidate = candidate.getParent()) {
            if (Files.isDirectory(candidate.resolve("pm-core"))
                    && Files.isDirectory(candidate.resolve("pm-providers"))) {
                return candidate;
            }
        }
        return fail("the root of the pm-mcp-core reactor was not found");
    }

    private static List<Path> productionRoots(Path root) {
        var roots = new ArrayList<Path>();
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                    if (!directory.equals(root) && isSkipped(directory)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    if (directory.endsWith(PRODUCTION_SOURCE)) {
                        roots.add(directory);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return roots;
    }

    private static boolean isSkipped(Path directory) {
        var name = directory.getFileName().toString();
        return name.startsWith(".") || "target".equals(name) || "node_modules".equals(name);
    }

    /** @param offences one repo-root-relative file, line and reason per copy found */
    private record Scan(List<String> offences, int files) {
    }
}
