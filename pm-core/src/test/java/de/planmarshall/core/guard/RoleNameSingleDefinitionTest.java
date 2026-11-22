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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.planmarshall.core.id.RoleName;

/**
 * Guards that the grammar of a role name and of a harness id is written once.
 * <p>
 * Every {@code src/main/java} file of the reactor is scanned for a string literal that carries the two-to-32 name
 * grammar, in its spellings, and the scan fails naming the file, the line and the two classes that hold the one
 * definition ({@code RoleName} and {@code HarnessId}) of the {@code core.id} package. Only
 * {@code core/id/ShortName.java}, which writes the text once, is excluded; the guard has no exemption for another
 * production file (test sources are not scanned at all).
 * <p>
 * Every JSON file below a {@code src/main/resources} of the reactor is scanned as well: each JSON string, member
 * name or value, that carries a spelling of the grammar must equal the definition {@code RoleName.PATTERN} once its
 * JSON escapes are decoded, so a schema that restates the grammar in another form — unanchored, with an escaped
 * hyphen, a reordered class or the grammar inside a larger pattern — fails naming the file and the line. A schema
 * cannot point
 * at the definition, so the schemas keep the text and this scan keeps them equal to it. A string that carries the
 * grammar inside a larger pattern of another kind cannot be compared and is recorded with its reason in
 * {@code NOT_COMPARABLE}.
 * <p>
 * A source scan rather than an ArchUnit rule: ArchUnit does not see string constants, and the class-file scan of
 * {@code ProfileNeutralityArchTest} reaches one module. <b>Limit:</b> a grammar composed at run time (two literals
 * concatenated, a constant read from a file), a hand-written character loop, a reordered character class (for example
 * {@code [a-z][0-9a-z-]{1,31}}) and a copy after a text block's escaped triple quote are not found; they are the
 * review's business, and the guide {@code pm-core/doc/developer/core-id.adoc} states the limit.
 */
@EnableGeneratorController
@DisplayName("One definition of the role-name grammar")
class RoleNameSingleDefinitionTest {

    private static final String PRODUCTION_SOURCE = "src/main/java";

    private static final String SHORT_NAME = "pm-core/src/main/java/de/planmarshall/core/id/ShortName.java";

    private static final List<String> MODULES = List.of("pm-core", "pm-workflow", "pm-runtime");

    private static final String PRODUCTION_RESOURCES = "src/main/resources";

    /** The one definition as the schemas must state it: the anchored text the public types compile. */
    private static final String DEFINITION = RoleName.PATTERN.pattern();

    /** The spellings of the two-to-32 name grammar the scan finds, as the source writes them. */
    private static final List<String> SPELLINGS = List.of("[a-z][a-z0-9-]{1,31}", "[a-z][a-z0-9\\\\-]{1,31}",
            "[a-z][-a-z0-9]{1,31}");

    /**
     * The schema strings that carry the grammar but cannot be compared with the definition, keyed by the file and the
     * decoded string, each with its reason. The scan fails for an entry it no longer finds, so none goes stale.
     */
    private static final Map<String, String> NOT_COMPARABLE = Map.of(
            "pm-core/src/main/resources/de/planmarshall/core/domain/domain-descriptor-v1.schema.json "
                    + "^skill://pm/domain/[a-z][a-z0-9-]{1,31}/[a-z0-9][a-z0-9-]*$",
            "the grammar is one segment of the skill URI pattern of a domain descriptor, not a name pattern of its "
                    + "own");

    /** The same spellings as a schema string carries them once its JSON escapes are decoded. */
    private static final List<String> SCHEMA_SPELLINGS = List.of("[a-z][a-z0-9-]{1,31}", "[a-z][a-z0-9\\-]{1,31}",
            "[a-z][-a-z0-9]{1,31}");

    @Test
    @DisplayName("no production source carries a copy of the name grammar")
    void noCopyOfTheGrammar() {
        var root = repositoryRoot();
        var offenders = new ArrayList<String>();
        var emptyRoots = new ArrayList<Path>();
        var scannedRoots = new ArrayList<Path>();
        for (var productionRoot : roots(root, PRODUCTION_SOURCE)) {
            var scan = scan(root, productionRoot);
            scannedRoots.add(root.relativize(productionRoot));
            if (scan.files() == 0) {
                emptyRoots.add(root.relativize(productionRoot));
            }
            offenders.addAll(scan.offences());
        }

        var checks = new ArrayList<Executable>();
        for (var module : MODULES) {
            var productionRoot = root.resolve(module).resolve(PRODUCTION_SOURCE);
            checks.add(() -> assertTrue(scannedRoots.contains(root.relativize(productionRoot)),
                    "The scan should reach the production root of " + module));
        }
        checks.add(() -> assertEquals(List.of(), emptyRoots,
                "Every production source root should hold a Java file"));
        checks.add(() -> assertEquals(List.of(), offenders,
                "A file copies the name grammar; use RoleName or HarnessId"));
        assertAll(checks.stream());
    }

    @Test
    @DisplayName("the one definition writes the grammar text")
    void oneDefinitionWritesTheText() {
        var shortName = read(repositoryRoot().resolve(SHORT_NAME));

        assertTrue(containsSpelling(shortName), "The one definition should write the grammar text");
    }

    @ParameterizedTest
    @ValueSource(strings = {"[a-z][a-z0-9-]{1,31}", "[a-z][a-z0-9\\\\-]{1,31}", "[a-z][-a-z0-9]{1,31}",
            "^[a-z][a-z0-9-]{1,31}$", "[a-z][a-z0-9-]{1,31}+", "^[a-z][-a-z0-9]{1,31}+$"})
    @DisplayName("each spelling of the grammar in a literal is found")
    void spellingIsFound(String spelling) {
        var source = source("class C {", String.format("    Pattern P = Pattern.compile(\"%s\");", spelling), "}");

        assertFalse(offences(source).isEmpty(), "A literal of the grammar should be an offence");
    }

    @ParameterizedTest
    @ValueSource(strings = {"[a-z][a-z0-9-]{0,31}", "[a-z][a-z0-9-]{1,63}", "[a-z][a-z0-9-]{2,32}"})
    @DisplayName("another bound of the character class is not the grammar")
    void anotherBoundIsNotTheGrammar(String spelling) {
        var source = source("class C {", String.format("    Pattern P = Pattern.compile(\"%s\");", spelling), "}");

        assertTrue(offences(source).isEmpty(), "Another bound should not be reported as the grammar");
    }

    @Test
    @DisplayName("the grammar inside a line comment or a Javadoc is not found")
    void commentIsNotTheGrammar() {
        var source = source("/*", " * Pattern P = Pattern.compile(\"[a-z][a-z0-9-]{1,31}\");", " */", "class C {",
                "    // Pattern.compile(\"[a-z][a-z0-9-]{1,31}\")", "}");

        assertTrue(offences(source).isEmpty(), "The grammar in a comment should not be reported");
    }

    @Test
    @DisplayName("the grammar inside a text block is found")
    void textBlockIsFound() {
        var source = source("class C {", "    String text = \"\"\"", "        [a-z][a-z0-9-]{1,31}", "        \"\"\";",
                "}");

        assertFalse(offences(source).isEmpty(), "The grammar in a text block should be an offence");
    }

    @Test
    @DisplayName("a quote character literal does not hide the grammar after it")
    void quoteCharacterLiteralDoesNotHideTheGrammar() {
        var source = source("class C {", "    char quote = '\"';",
                "    Pattern P = Pattern.compile(\"[a-z][a-z0-9-]{1,31}\");", "}");

        assertFalse(offences(source).isEmpty(), "A copy after a quote character literal should be an offence");
    }

    @Test
    @DisplayName("an escaped character literal does not hide the grammar after it")
    void escapedCharacterLiteralDoesNotHideTheGrammar() {
        var source = source("class C {", "    char backslash = '\\\\';", "    char apostrophe = '\\'';",
                "    Pattern P = Pattern.compile(\"[a-z][a-z0-9-]{1,31}\");", "}");

        assertFalse(offences(source).isEmpty(), "A copy after an escaped literal is an offence");
    }

    @Test
    @DisplayName("every schema occurrence of the name grammar is the one definition")
    void schemasCarryTheDefinition() {
        var root = repositoryRoot();
        var occurrences = new ArrayList<String>();
        var offenders = new ArrayList<String>();
        var notComparableFound = new ArrayList<String>();
        var resourceRoots = roots(root, PRODUCTION_RESOURCES);
        for (var resourceRoot : resourceRoots) {
            for (var file : schemaFiles(resourceRoot)) {
                var relative = root.relativize(file).toString().replace('\\', '/');
                for (var occurrence : schemaOccurrences(read(file))) {
                    occurrences.add(relative + ":" + occurrence.line());
                    if (NOT_COMPARABLE.containsKey(relative + " " + occurrence.value())) {
                        notComparableFound.add(relative + " " + occurrence.value());
                    } else if (!DEFINITION.equals(occurrence.value())) {
                        offenders.add(relative + ":" + occurrence.line() + " states '" + occurrence.value() + "'");
                    }
                }
            }
        }

        assertAll(
                () -> assertTrue(resourceRoots.contains(root.resolve("pm-core").resolve(PRODUCTION_RESOURCES)),
                        "The scan reaches the resources of pm-core"),
                () -> assertFalse(occurrences.isEmpty(), "The scan finds the grammar in a schema"),
                () -> assertEquals(List.of(), offenders, "A schema states the grammar unlike RoleName"),
                () -> assertEquals(NOT_COMPARABLE.keySet(), Set.copyOf(notComparableFound),
                        "Every recorded uncomparable form is still found"));
    }

    @Test
    @DisplayName("a schema string equal to the definition is an occurrence that matches it")
    void schemaDefinitionMatches() {
        var schema = "{\"name\": {\"type\": \"string\", \"pattern\": \"" + DEFINITION + "\"}}";

        var occurrences = schemaOccurrences(schema);

        assertAll(
                () -> assertEquals(1, occurrences.size(), "The anchored pattern is one occurrence"),
                () -> assertEquals(DEFINITION, occurrences.getFirst().value(), "The occurrence is the definition"));
    }

    // the patterns are the contract the scan compares, so they are literals
    @ParameterizedTest
    @ValueSource(strings = {"[a-z][a-z0-9-]{1,31}", "^[a-z][a-z0-9\\\\-]{1,31}$", "^[a-z][-a-z0-9]{1,31}$",
            "^[a-z][a-z0-9-]{1,31}(/[a-z]+)?$"})
    @DisplayName("a schema string that carries the grammar in another form differs from the definition")
    void schemaOtherFormDiffers(String pattern) {
        var schema = "{\"propertyNames\": {\"pattern\": \"" + pattern + "\"}}";

        var occurrences = schemaOccurrences(schema);

        assertAll(
                () -> assertEquals(1, occurrences.size(), "The other form is found as an occurrence"),
                () -> assertNotEquals(DEFINITION, occurrences.getFirst().value(),
                        "The other form is not the definition"));
    }

    @Test
    @DisplayName("a schema string with another bound is not an occurrence")
    void schemaOtherBoundIsNoOccurrence() {
        var schema = "{\"harness\": {\"pattern\": \"^[a-z][a-z0-9-]{0,31}$\"}}";

        assertTrue(schemaOccurrences(schema).isEmpty(), "Another bound is not the name grammar");
    }

    @Test
    @DisplayName("a schema spelling written with a unicode escape is decoded and found")
    void schemaUnicodeEscapesAreDecoded() {
        // JSON escapes of '{' and '}', written as the schema file carries them
        var openBrace = "\\" + "u007b";
        var closeBrace = "\\" + "u007d";
        var schema = "{\"propertyNames\": {\"pattern\": \"^[a-z][a-z0-9-]" + openBrace + "1,31" + closeBrace + "$\"}}";

        var occurrences = schemaOccurrences(schema);

        assertAll(
                () -> assertEquals(1, occurrences.size(), "The escaped spelling is one occurrence"),
                () -> assertEquals(DEFINITION, occurrences.getFirst().value(),
                        "The escapes decode to the one definition"));
    }

    /** @param lines the source as its lines; a trailing newline is not needed by the scan */
    private static String source(String... lines) {
        return String.join("\n", lines);
    }

    private static Scan scan(Path root, Path productionRoot) {
        var offenders = new ArrayList<String>();
        var files = 0;
        try (var walk = Files.walk(productionRoot)) {
            for (var file : walk.filter(Files::isRegularFile).filter(RoleNameSingleDefinitionTest::isJava)
                    .toList()) {
                files++;
                if (isShortName(root, file)) {
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

    private static boolean isShortName(Path root, Path file) {
        return SHORT_NAME.equals(root.relativize(file).toString().replace('\\', '/'));
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

    private static List<Path> schemaFiles(Path resourceRoot) {
        try (var walk = Files.walk(resourceRoot)) {
            return walk.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".json")).sorted()
                    .toList();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /**
     * Reads every JSON string of a document — member names and values alike — and keeps the ones that carry a spelling
     * of the grammar, decoded from their JSON escapes so they compare with the definition.
     *
     * @param document the JSON text
     * @return each string that carries the grammar, with the line it starts on
     */
    private static List<SchemaString> schemaOccurrences(String document) {
        var occurrences = new ArrayList<SchemaString>();
        var value = new StringBuilder();
        var line = 1;
        var valueLine = 1;
        var inString = false;
        for (var index = 0; index < document.length(); index++) {
            var character = document.charAt(index);
            if (character == '\n') {
                line++;
            }
            if (!inString) {
                if (character == '"') {
                    inString = true;
                    valueLine = line;
                    value.setLength(0);
                }
                continue;
            }
            if (character == '\\' && index + 1 < document.length()) {
                if (document.charAt(index + 1) == 'u' && index + 5 < document.length()) {
                    var hex = document.substring(index + 2, index + 6);
                    if (isHex(hex)) {
                        value.append((char) Integer.parseInt(hex, 16));
                        index += 5;
                        continue;
                    }
                }
                index++;
                value.append(unescape(document.charAt(index)));
                continue;
            }
            if (character == '"') {
                inString = false;
                var text = value.toString();
                if (SCHEMA_SPELLINGS.stream().anyMatch(text::contains)) {
                    occurrences.add(new SchemaString(text, valueLine));
                }
                continue;
            }
            value.append(character);
        }
        return occurrences;
    }

    private static char unescape(char escaped) {
        return switch (escaped) {
            case 'n' -> '\n';
            case 't' -> '\t';
            case 'r' -> '\r';
            case 'b' -> '\b';
            case 'f' -> '\f';
            default -> escaped;
        };
    }

    private static boolean isHex(String text) {
        return text.chars().allMatch(character -> Character.digit(character, 16) >= 0);
    }

    private static List<Path> roots(Path root, String suffix) {
        var roots = new ArrayList<Path>();
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                    if (!directory.equals(root) && isSkipped(directory)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    if (directory.endsWith(suffix)) {
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

    /**
     * @param value the decoded JSON string
     * @param line  the line it starts on
     */
    private record SchemaString(String value, int line) {
    }
}
