/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.dsl;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.planmarshall.core.config.LimitProperties;
import de.planmarshall.workflow.ast.CallValue;
import de.planmarshall.workflow.ast.InputType;
import de.planmarshall.workflow.ast.SourceLocation;
import de.planmarshall.workflow.ast.UnitFrontmatter.InterfaceElement;
import de.planmarshall.workflow.ast.UnitType;
import de.planmarshall.workflow.unit.ProjectUnitLoader;

/**
 * The reader of the unit frontmatter (PM-IMPL-5): a valid frontmatter is read field by field, and every form outside
 * its closed, line-oriented subset and every field bound is refused with the rule {@link RefusalRule#FRONTMATTER}, the
 * line and the column. A general YAML loader resolves tags, anchors, aliases and merge keys, which is the hazard the
 * reader of its own closes.
 */
@EnableGeneratorController
@DisplayName("UnitFrontmatterReader")
class UnitFrontmatterReaderTest {

    private static final String RESOURCE = "fixture.pmwf";

    /** A valid frontmatter, one line per element; it stands on lines 2 to 12, the closing {@code ---} on line 13. */
    private static final List<String> VALID = List.of(
            "name: Commit and Push",
            "description: Commits the changes of the plan's worktree and pushes its branch.",
            "type: subworkflow",
            "icon: git.commit",
            "entry: commit",
            "entry_description: The worktree holds the changes to commit, or is clean.",
            "exit: done",
            "exit_description: Commit and push are settled.",
            "modification_date: 2026-10-14",
            "version: 1.0.0",
            "author: plan-marshall");

    /** The line an extra line appended to {@link #VALID} stands on. */
    private static final int APPENDED_LINE = VALID.size() + 2;

    private static final String BODY = "workflow git.commit_and_push version 1 format 1\nscope subworkflow\n";

    private static String unit(List<String> frontmatterLines) {
        return "---\n" + String.join("\n", frontmatterLines) + "\n---\n" + BODY;
    }

    /** The valid frontmatter with the given lines appended. */
    private static List<String> appended(String... extra) {
        var lines = new ArrayList<>(VALID);
        lines.addAll(List.of(extra));
        return lines;
    }

    /** The valid frontmatter with the line of the key replaced by the given lines. */
    private static List<String> replaced(String key, String... replacement) {
        var lines = new ArrayList<String>();
        for (var line : VALID) {
            if (line.startsWith(key + ":")) {
                lines.addAll(List.of(replacement));
            } else {
                lines.add(line);
            }
        }
        return lines;
    }

    /** The valid frontmatter without the line of the key. */
    private static List<String> without(String key) {
        return replaced(key);
    }

    private static UnitFrontmatterReader.Result.Read read(String source) {
        var result = UnitFrontmatterReader.read(RESOURCE, source);
        return assertInstanceOf(UnitFrontmatterReader.Result.Read.class, result, "the frontmatter is read, not refused");
    }

    private static Diagnostic refuse(String source) {
        var result = UnitFrontmatterReader.read(RESOURCE, source);
        return assertInstanceOf(UnitFrontmatterReader.Result.Refused.class, result, "the frontmatter was read")
                .diagnostic();
    }

    private static void assertRefused(Diagnostic diagnostic, int line, int column) {
        assertAll(
                () -> assertEquals(RefusalRule.FRONTMATTER, diagnostic.rule(),
                        "the refusal is under the FRONTMATTER rule"),
                () -> assertEquals(RESOURCE, diagnostic.resourceName(),
                        "the diagnostic names the resource"),
                () -> assertEquals(line, diagnostic.line(), "the diagnostic names the refused line"),
                () -> assertEquals(column, diagnostic.column(), "the diagnostic names the refused column"));
    }

    @Nested
    @DisplayName("A valid frontmatter")
    class ValidFrontmatter {

        @Test
        @DisplayName("is read field by field, with the line its body starts at")
        void readsEveryField() {
            var result = read(unit(VALID));

            var frontmatter = result.frontmatter();
            assertAll(
                    () -> assertEquals("Commit and Push", frontmatter.name(), "the frontmatter name is read"),
                    () -> assertEquals("Commits the changes of the plan's worktree and pushes its branch.",
                            frontmatter.description(), "the description is read"),
                    () -> assertEquals(UnitType.SUBWORKFLOW, frontmatter.type(), "the unit type is read"),
                    () -> assertEquals("git.commit", frontmatter.icon(), "the unit icon is read"),
                    () -> assertEquals(List.of(new InterfaceElement("commit",
                                    "The worktree holds the changes to commit, or is clean.")),
                            frontmatter.entries(), "the entry is read with its description"),
                    () -> assertEquals(List.of(new InterfaceElement("done",
                                    "Commit and push are settled.")), frontmatter.exits(),
                            "the exit is read with its description"),
                    () -> assertEquals(LocalDate.of(2026, 10, 14), frontmatter.modificationDate(),
                            "the modification date is read"),
                    () -> assertEquals("1.0.0", frontmatter.version(), "the unit version is read"),
                    () -> assertEquals("plan-marshall", frontmatter.author(), "the frontmatter author is read"),
                    () -> assertNull(frontmatter.signature(), "an unsigned frontmatter reads a null signature"),
                    () -> assertEquals(new SourceLocation(RESOURCE, 1, 1), frontmatter.location(),
                            "the location is the first line"),
                    () -> assertEquals(APPENDED_LINE + 1, result.bodyLine(),
                            "the body starts one line after the frontmatter"));
        }

        @Test
        @DisplayName("keeps three entries in their order, each with its description")
        void keepsThreeEntriesInOrder() {
            var source = unit(List.of(VALID.get(0), VALID.get(1), VALID.get(2), VALID.get(3), "entry: post_run",
                    "entry_description: The run is over.", "entry: hand_back",
                    "entry_description: The branch holds commits beyond the base.", "entry: merge",
                    "entry_description: The plan is ready to merge.", VALID.get(6), VALID.get(8), VALID.get(9),
                    VALID.get(10)));

            var entries = read(source).frontmatter().entries();

            assertEquals(List.of(
                            new InterfaceElement("post_run", "The run is over."),
                            new InterfaceElement("hand_back", "The branch holds commits beyond the base."),
                            new InterfaceElement("merge", "The plan is ready to merge.")), entries,
                    "the three entries keep their order");
        }

        @Test
        @DisplayName("reads entries and exits without descriptions, and a unit without either")
        void readsElementsWithoutDescriptions() {
            var withoutDescriptions = unit(List.of(VALID.get(0), VALID.get(1), VALID.get(2), VALID.get(3),
                    "entry: commit", "exit: done", "exit: failed", VALID.get(8), VALID.get(9), VALID.get(10)));
            var withoutInterface = unit(List.of(VALID.get(0), VALID.get(1), "type: workflow",
                    "icon: workflow.default", VALID.get(8), VALID.get(9), VALID.get(10)));

            var described = read(withoutDescriptions).frontmatter();
            var bare = read(withoutInterface).frontmatter();

            assertAll(
                    () -> assertEquals(List.of(new InterfaceElement("commit", null)), described.entries(),
                            "an entry without description reads null"),
                    () -> assertEquals(List.of(new InterfaceElement("done", null),
                                    new InterfaceElement("failed", null)), described.exits(),
                            "an exit without description reads null"),
                    () -> assertEquals(UnitType.WORKFLOW, bare.type(), "a unit without interface keeps its type"),
                    () -> assertTrue(bare.entries().isEmpty(), "a unit without entries reads none"),
                    () -> assertTrue(bare.exits().isEmpty(), "a unit without exits reads none"));
        }

        @Test
        @DisplayName("carries the signature and does not verify it")
        void carriesSignature() {
            var source = unit(appended("signature: not-a-verified-signature"));

            var frontmatter = read(source).frontmatter();

            assertEquals("not-a-verified-signature", frontmatter.signature(), "the signature is carried as read");
        }

        @Test
        @DisplayName("reads a double-quoted value with its two escapes")
        void readsDoubleQuotedValue() {
            var source = unit(replaced("description", "description: \"Reads \\\"a: b\\\" # and \\\\ alike\""));

            var frontmatter = read(source).frontmatter();

            assertEquals("Reads \"a: b\" # and \\ alike", frontmatter.description(),
                    "the quoted value is unescaped");
        }

        @Test
        @DisplayName("reads the frontmatter of a text whose body is not parseable")
        void readsWithoutBody() {
            var source = "---\n" + String.join("\n", VALID) + "\n---\nthis is { no unit ### at all\n";

            var result = read(source);

            assertEquals("Commit and Push", result.frontmatter().name(),
                    "the frontmatter is read without its body");
        }

        @Test
        @DisplayName("lets the lines stand in any order but the descriptions")
        void readsAnyOrder() {
            var reversed = new ArrayList<>(List.of(VALID.get(10), VALID.get(9), VALID.get(8), VALID.get(6),
                    VALID.get(7), VALID.get(4), VALID.get(5), VALID.get(2), VALID.get(3), VALID.get(1),
                    VALID.get(0)));

            var frontmatter = read(unit(reversed)).frontmatter();

            assertEquals(read(unit(VALID)).frontmatter(), frontmatter,
                    "the line order does not change the result");
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(UnitType.class)
        @DisplayName("reads every one of the six types by its wire name")
        void readsEveryType(UnitType type) {
            var source = unit(replaced("type", "type: " + type.wireName()));

            assertEquals(type, read(source).frontmatter().type(), "the wire name reads the type");
        }
    }

    @Nested
    @DisplayName("The closed format")
    class ClosedFormat {

        static Stream<Arguments> refusedForms() {
            return Stream.of(
                    Arguments.of("a tag", appended("signature: !binary abc"), APPENDED_LINE, 12),
                    Arguments.of("an anchor", appended("signature: &anchor abc"), APPENDED_LINE, 12),
                    Arguments.of("an alias", appended("signature: *anchor"), APPENDED_LINE, 12),
                    Arguments.of("a merge key", appended("<<: *base"), APPENDED_LINE, 1),
                    Arguments.of("a flow sequence", appended("entry: [push, land]"), APPENDED_LINE, 8),
                    Arguments.of("a flow mapping", appended("signature: {a: b}"), APPENDED_LINE, 12),
                    Arguments.of("a literal block scalar", appended("signature: |"), APPENDED_LINE, 12),
                    Arguments.of("a folded block scalar", appended("signature: >"), APPENDED_LINE, 12),
                    Arguments.of("a comment line", appended("# a comment"), APPENDED_LINE, 1),
                    Arguments.of("a trailing comment", appended("signature: abc # note"), APPENDED_LINE, 16),
                    Arguments.of("a blank line", appended(""), APPENDED_LINE, 1),
                    Arguments.of("an indented line", appended("  signature: abc"), APPENDED_LINE, 1),
                    Arguments.of("a control character", appended("signature: ab\u0007c"), APPENDED_LINE, 14),
                    Arguments.of("a tab", appended("signature:\tabc"), APPENDED_LINE, 11),
                    Arguments.of("an unknown key", appended("owner: someone"), APPENDED_LINE, 1),
                    Arguments.of("a repeated single key", appended("name: Second Name"), APPENDED_LINE, 1),
                    Arguments.of("a repeated signature", appended("signature: a", "signature: b"),
                            APPENDED_LINE + 1, 1),
                    Arguments.of("a repeated icon", appended("icon: phase.init"), APPENDED_LINE, 1),
                    Arguments.of("an entry description below another key", appended("entry_description: x"),
                            APPENDED_LINE, 1),
                    Arguments.of("an exit description below another key", appended("exit_description: x"),
                            APPENDED_LINE, 1),
                    Arguments.of("a description as the first line",
                            List.of("exit_description: x", VALID.get(0)), 2, 1),
                    Arguments.of("an exit description below an entry",
                            replaced("entry", "entry: commit", "exit_description: x"), 7, 1),
                    Arguments.of("a second description of one entry",
                            replaced("entry_description", "entry_description: a", "entry_description: b"), 8, 1),
                    Arguments.of("a single-quoted value", appended("signature: 'abc'"), APPENDED_LINE, 12),
                    Arguments.of("a value opening with an indicator", appended("signature: %abc"), APPENDED_LINE,
                            12),
                    Arguments.of("a key without ': '", appended("signature:abc"), APPENDED_LINE, 11),
                    Arguments.of("a line without a key", appended("signature abc"), APPENDED_LINE, 1),
                    Arguments.of("a plain value holding ': '", appended("signature: a: b"), APPENDED_LINE, 13),
                    Arguments.of("a plain value with a trailing blank", appended("signature: abc "), APPENDED_LINE,
                            15),
                    Arguments.of("a blank that starts no value", appended("signature: "), APPENDED_LINE, 11),
                    Arguments.of("a leading blank of a value", appended("signature:  abc"), APPENDED_LINE, 11),
                    Arguments.of("an escape other than the two", appended("signature: \"a\\nb\""), APPENDED_LINE,
                            14),
                    Arguments.of("content after the closing quote", appended("signature: \"ab\"c"), APPENDED_LINE,
                            16),
                    Arguments.of("an unclosed quote", appended("signature: \"abc"), APPENDED_LINE, 12));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("refusedForms")
        @DisplayName("refuses every form outside the closed subset with line and column")
        void refusesForm(String form, List<String> lines, int line, int column) {
            var diagnostic = refuse(unit(lines));

            assertRefused(diagnostic, line, column);
        }

        @Test
        @DisplayName("refuses a unit that opens with no frontmatter at line 1, column 1")
        void refusesMissingFrontmatter() {
            var diagnostic = refuse(BODY);

            assertRefused(diagnostic, 1, 1);
        }

        @Test
        @DisplayName("refuses a frontmatter that is never closed")
        void refusesUnclosedFrontmatter() {
            var diagnostic = refuse("---\n" + String.join("\n", VALID) + "\n");

            assertRefused(diagnostic, APPENDED_LINE, 1);
        }

        @Test
        @DisplayName("refuses a frontmatter whose last line is unclosed without a trailing line feed")
        void refusesUnclosedWithoutTrailingLineFeed() {
            var diagnostic = refuse("---\nname: Commit and Push");

            assertRefused(diagnostic, 2, 1);
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"name", "description", "type", "icon", "modification_date", "version", "author"})
        @DisplayName("refuses a frontmatter that lacks a required key at its closing line")
        void refusesMissingKey(String key) {
            var diagnostic = refuse(unit(without(key)));

            assertRefused(diagnostic, APPENDED_LINE - 1, 1);
        }
    }

    @Nested
    @DisplayName("The line endings")
    class LineEndings {

        @Test
        @DisplayName("reads a unit with CRLF line endings to the frontmatter and body line of its LF twin")
        void readsCrlfLikeLf() {
            var lf = unit(VALID);
            var crlf = lf.replace("\n", "\r\n");

            var expected = read(lf);
            var actual = read(crlf);

            assertAll(
                    () -> assertEquals(expected.frontmatter(), actual.frontmatter(),
                            "the CRLF frontmatter reads the same"),
                    () -> assertEquals(expected.bodyLine(), actual.bodyLine(),
                            "the CRLF body starts on the same line"));
        }

        @Test
        @DisplayName("refuses a carriage return inside a value as a control character, also in a CRLF unit")
        void refusesLoneCarriageReturn() {
            var lf = unit(appended("signature: ab\rc"));
            var crlf = lf.replace("\n", "\r\n");

            var inLf = refuse(lf);
            var inCrlf = refuse(crlf);

            assertAll(
                    () -> assertRefused(inLf, APPENDED_LINE, 14),
                    () -> assertRefused(inCrlf, APPENDED_LINE, 14));
        }
    }

    @Nested
    @DisplayName("The field rules")
    class FieldRules {

        static Stream<Arguments> refusedFields() {
            return Stream.of(
                    Arguments.of("a name of 2 characters", "name", "name: ab", 7),
                    Arguments.of("a name of 101 characters", "name", "name: " + "n".repeat(101), 7),
                    Arguments.of("an empty description", "description", "description:", 13),
                    Arguments.of("a description of 1001 characters", "description",
                            "description: " + "d".repeat(1001), 14),
                    Arguments.of("an entry description of 501 characters", "entry_description",
                            "entry_description: " + "e".repeat(501), 20),
                    Arguments.of("an exit description of 501 characters", "exit_description",
                            "exit_description: " + "e".repeat(501), 19),
                    Arguments.of("an empty author", "author", "author:", 8),
                    Arguments.of("an author of 101 characters", "author", "author: " + "a".repeat(101), 9),
                    Arguments.of("a type outside the closed set", "type", "type: process", 7),
                    Arguments.of("an icon with a path segment", "icon", "icon: ../x", 7),
                    Arguments.of("an icon with a slash", "icon", "icon: a/b", 7),
                    Arguments.of("an icon that starts upper case", "icon", "icon: Phase.Init", 7),
                    Arguments.of("an icon with a backslash", "icon", "icon: a\\\\b", 7),
                    Arguments.of("an icon of 65 characters", "icon", "icon: a" + "b".repeat(64), 7),
                    Arguments.of("an entry that is no identifier", "entry", "entry: Commit", 8),
                    Arguments.of("an entry of 33 characters", "entry", "entry: " + "e".repeat(33), 8),
                    Arguments.of("an exit that is no outcome id", "exit", "exit: 1done", 7),
                    Arguments.of("the exit stepped_out", "exit", "exit: stepped_out", 7),
                    Arguments.of("a date in the future", "modification_date", "modification_date: 9999-12-31",
                            20),
                    Arguments.of("an invalid calendar date", "modification_date",
                            "modification_date: 2026-02-30", 20),
                    Arguments.of("a date not in the form YYYY-MM-DD", "modification_date",
                            "modification_date: 2026-1-5", 20),
                    Arguments.of("a version of two numbers", "version", "version: 1.0", 10),
                    Arguments.of("a version with a prefix", "version", "version: v1.0.0", 10),
                    Arguments.of("a version of 65 characters", "version", "version: 1.0.0-" + "r".repeat(59), 10));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("refusedFields")
        @DisplayName("refuses a value outside its field's bounds at the column of the value")
        void refusesField(String field, String key, String line, int column) {
            var lines = replaced(key, line);
            var number = lines.indexOf(line) + 2;

            var diagnostic = refuse(unit(lines));

            assertRefused(diagnostic, number, column);
        }

        static Stream<Arguments> acceptedBounds() {
            return Stream.of(
                    Arguments.of("a name of 3 characters", "name", "name: abc"),
                    Arguments.of("a name of 100 characters", "name", "name: " + "n".repeat(100)),
                    Arguments.of("a description of 1 character", "description", "description: d"),
                    Arguments.of("a description of 1000 characters", "description",
                            "description: " + "d".repeat(1000)),
                    Arguments.of("an entry description of 500 characters", "entry_description",
                            "entry_description: " + "e".repeat(500)),
                    Arguments.of("an exit description of 500 characters", "exit_description",
                            "exit_description: " + "e".repeat(500)),
                    Arguments.of("an author of 1 character", "author", "author: a"),
                    Arguments.of("an author of 100 characters", "author", "author: " + "a".repeat(100)),
                    Arguments.of("an icon of 64 characters", "icon", "icon: a" + "b".repeat(63)),
                    Arguments.of("a dotted icon id", "icon", "icon: axis.method.test-first"),
                    Arguments.of("an entry of 32 characters", "entry", "entry: " + "e".repeat(32)),
                    Arguments.of("today as the date", "modification_date",
                            "modification_date: " + LocalDate.now(ZoneOffset.UTC)),
                    Arguments.of("a pre-release version", "version", "version: 1.0.0-rc.1"));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("acceptedBounds")
        @DisplayName("accepts a value at its field's bounds")
        void acceptsBound(String field, String key, String line) {
            var result = UnitFrontmatterReader.read(RESOURCE, unit(replaced(key, line)));

            assertInstanceOf(UnitFrontmatterReader.Result.Read.class, result, "a value at its bound is read");
        }

        @Test
        @DisplayName("refuses an entry declared twice at the second")
        void refusesDuplicateEntry() {
            var lines = replaced("entry", "entry: commit", "entry: commit");

            var diagnostic = refuse(unit(lines));

            assertRefused(diagnostic, 7, 8);
        }

        @Test
        @DisplayName("refuses an exit declared twice at the second")
        void refusesDuplicateExit() {
            var lines = replaced("exit", "exit: done", "exit: done");

            var diagnostic = refuse(unit(lines));

            assertRefused(diagnostic, 9, 7);
        }
    }

    @Nested
    @DisplayName("The refused characters")
    class RefusedCharacters {

        /** The fields a refused character is tried in, with the line of the valid frontmatter it replaces. */
        private static final List<String> FIELDS = List.of("name", "description", "author", "entry_description",
                "exit_description");

        /** The characters the decision refuses beside the ISO control characters, with their category. */
        private static final List<String[]> CHARACTERS = List.of(
                new String[]{"bidi override", "\u202E"},
                new String[]{"zero width space", "\u200B"},
                new String[]{"byte order mark", "\uFEFF"},
                new String[]{"line separator", "\u2028"},
                new String[]{"paragraph separator", "\u2029"},
                new String[]{"supplementary language tag", "\uDB40\uDC01"},
                new String[]{"control", "\u0007"});

        static Stream<Arguments> refusedCharacters() {
            var arguments = Stream.<Arguments>builder();
            for (var field : FIELDS) {
                for (var character : CHARACTERS) {
                    arguments.add(Arguments.of(field, character[0], character[1]));
                }
            }
            return arguments.build();
        }

        @ParameterizedTest(name = "{1} in {0}")
        @MethodSource("refusedCharacters")
        @DisplayName("refuses the character in every field with its code point and its line, also in a CRLF unit")
        void refusesCharacter(String field, String category, String character) {
            var line = field + ": " + character + "x";
            var lines = replaced(field, line);
            var expectedLine = lines.indexOf(line) + 2;
            var expectedColumn = field.length() + 3;
            var codePoint = "U+%04X".formatted(character.codePointAt(0));

            var lf = refuse(unit(lines));
            var crlf = refuse(unit(lines).replace("\n", "\r\n"));

            assertAll(
                    () -> assertRefused(lf, expectedLine, expectedColumn),
                    () -> assertTrue(lf.message().contains(codePoint), "the LF refusal names the code point"),
                    () -> assertRefused(crlf, expectedLine, expectedColumn),
                    () -> assertTrue(crlf.message().contains(codePoint), "the CRLF refusal names the code point"));
        }

        static Stream<Arguments> acceptedLetters() {
            return FIELDS.stream().flatMap(field -> Stream.of("a", "\u00E4").map(letter ->
                    Arguments.of(field, letter)));
        }

        @ParameterizedTest(name = "{0} with {1}")
        @MethodSource("acceptedLetters")
        @DisplayName("accepts a plain ASCII and a plain non-ASCII letter in every field")
        void acceptsLetter(String field, String letter) {
            var prefix = Generators.letterStrings(1, 3).next();
            var suffix = Generators.letterStrings(1, 3).next();
            var value = switch (field) {
                case "name" -> prefix + letter + suffix;
                case "description" -> prefix + letter;
                case "author" -> prefix + letter;
                default -> prefix + letter + suffix;
            };
            var frontmatter = read(unit(replaced(field, field + ": " + value))).frontmatter();

            var actual = switch (field) {
                case "name" -> frontmatter.name();
                case "description" -> frontmatter.description();
                case "author" -> frontmatter.author();
                case "entry_description" -> frontmatter.entries().getFirst().description();
                default -> frontmatter.exits().getFirst().description();
            };
            assertEquals(value, actual, "the field reads the value it was given");
        }
    }

    @Nested
    @DisplayName("Inputs and reads")
    class InputsAndReads {

        /** A frontmatter that declares what the unit reads and its inputs, every input sub-key in its order. */
        private static final List<String> WITH_INPUTS = List.of(
                "name: Commit and Push",
                "description: Commits the changes of the plan's worktree and pushes its branch.",
                "type: subworkflow",
                "icon: git.commit",
                "entry: commit",
                "exit: done",
                "reads_config: project.review.currency",
                "reads_fact: push_remote",
                "input: push_mode",
                "input_type: enum[push|no_push]",
                "input_description: Whether the branch is pushed.",
                "input: attempts",
                "input_type: int[1..5]",
                "input_default: 2",
                "modification_date: 2026-10-14",
                "version: 1.0.0",
                "author: plan-marshall");

        /** The valid frontmatter with the given lines directly above {@code modification_date}. */
        private static List<String> inserted(String... extra) {
            var lines = new ArrayList<String>();
            for (var line : VALID) {
                if (line.startsWith("modification_date:")) {
                    lines.addAll(List.of(extra));
                }
                lines.add(line);
            }
            return lines;
        }

        @Test
        @DisplayName("reads the reads_config, reads_fact and input keys of a frontmatter")
        void readsTheReadsAndInputs() {
            var result = read(unit(WITH_INPUTS));
            var frontmatter = result.frontmatter();
            var pushMode = frontmatter.inputs().getFirst();
            var attempts = frontmatter.inputs().get(1);

            assertAll(
                    () -> assertEquals(List.of("project.review.currency"), frontmatter.readsConfig(),
                            "the reads_config value is read"),
                    () -> assertEquals(List.of("push_remote"), frontmatter.readsFact(),
                            "the reads_fact value is read"),
                    () -> assertEquals(2, frontmatter.inputs().size(), "both inputs are read"),
                    () -> assertEquals("push_mode", pushMode.name(), "the input name is read"),
                    () -> assertEquals(new InputType.Enumeration(List.of("push", "no_push")), pushMode.type(),
                            "the enum input type is read"),
                    () -> assertFalse(pushMode.optional(), "the input is not optional"),
                    () -> assertNull(pushMode.defaultValue(), "an input without default reads null"),
                    () -> assertEquals("Whether the branch is pushed.", pushMode.description(),
                            "the input description is read"),
                    () -> assertEquals(new SourceLocation(RESOURCE, 10, 8), pushMode.location(),
                            "the input carries its line and column"),
                    () -> assertEquals(new SourceLocation(RESOURCE, 13, 8), attempts.location(),
                            "the second input carries its location"),
                    () -> assertEquals(new InputType.IntRange(1, 5), attempts.type(),
                            "the int range of the input is read"),
                    () -> assertEquals(new CallValue.IntValue(2), attempts.defaultValue(),
                            "the integer default is read"),
                    () -> assertFalse(attempts.required(), "an input with a default is not required"));
        }

        @Test
        @DisplayName("reads every type form and the optional form, a fallback and an empty list default")
        void readsEveryTypeForm() {
            var result = read(unit(inserted(
                    "reads_config: project.review.currency",
                    "reads_config: local-config.mode",
                    "reads_fact: push_remote",
                    "input: mode",
                    "input_type: enum[strict|Lenient]",
                    "input: depth",
                    "input_type: int",
                    "input: rounds",
                    "input_type: int[0..9]",
                    "input: targets",
                    "input_type: list<enum[origin|Mirror]>",
                    "input: skip",
                    "input_type: int[1..3]?",
                    "input: retries",
                    "input_type: int[0..5]",
                    "input_fallback: project.review.retries",
                    "input: notes",
                    "input_type: list<enum[a|B]>",
                    "input_default: []")));
            var inputs = result.frontmatter().inputs();

            assertAll(
                    () -> assertEquals(List.of("project.review.currency", "local-config.mode"),
                            result.frontmatter().readsConfig(), "both reads_config values are read"),
                    () -> assertEquals(new InputType.Enumeration(List.of("strict", "Lenient")),
                            inputs.get(0).type(), "the enum values keep their case"),
                    () -> assertEquals(new InputType.IntRange(null, null), inputs.get(1).type(),
                            "an unbounded int range reads null bounds"),
                    () -> assertEquals(new InputType.IntRange(0, 9), inputs.get(2).type(),
                            "an int range reads both of its bounds"),
                    () -> assertEquals(new InputType.EnumList(List.of("origin", "Mirror")), inputs.get(3).type(),
                            "the list of enums is read"),
                    () -> assertTrue(inputs.get(4).optional(), "the optional form marks the input optional"),
                    () -> assertFalse(inputs.get(4).required(), "the optional input is not required"),
                    () -> assertEquals("project.review.retries", inputs.get(5).fallback(),
                            "the fallback key is read"),
                    () -> assertEquals(new CallValue.EnumList(List.of()), inputs.get(6).defaultValue(),
                            "the empty list default is read"),
                    () -> assertEquals(7, inputs.size(), "all seven inputs are read"));
        }

        @Test
        @DisplayName("reads the CRLF twin of a frontmatter with every input key as the same frontmatter")
        void readsTheCrlfTwin() {
            var lf = read(unit(WITH_INPUTS)).frontmatter();
            var crlf = read(unit(WITH_INPUTS).replace("\n", "\r\n")).frontmatter();

            assertEquals(lf, crlf, "the CRLF twin must read the same frontmatter");
        }

        static Stream<Arguments> refusedInputs() {
            return Stream.of(
                    Arguments.of("a default before its type",
                            List.of("input: a", "input_default: 1", "input_type: int"), 11, 1),
                    Arguments.of("a fallback before its type",
                            List.of("input: a", "input_fallback: project.mode", "input_type: int"), 11, 1),
                    Arguments.of("a description before its type",
                            List.of("input: a", "input_description: x", "input_type: int"), 11, 1),
                    Arguments.of("a sub-key without an input line",
                            List.of("input_type: int"), 10, 1),
                    Arguments.of("a second input_type",
                            List.of("input: a", "input_type: int", "input_type: int"), 12, 1),
                    Arguments.of("a second input_default",
                            List.of("input: a", "input_type: int", "input_default: 1", "input_default: 2"), 13, 1),
                    Arguments.of("a second input_fallback",
                            List.of("input: a", "input_type: int", "input_fallback: project.a",
                                    "input_fallback: project.b"), 13, 1),
                    Arguments.of("a second input_description",
                            List.of("input: a", "input_type: int", "input_description: x", "input_description: y"),
                            13, 1),
                    Arguments.of("a missing input_type",
                            List.of("input: a", "input: b", "input_type: int"), 11, 1),
                    Arguments.of("an input name with a hyphen",
                            List.of("input: max-rounds", "input_type: int"), 10, 8),
                    Arguments.of("an input name in upper case",
                            List.of("input: Mode", "input_type: int"), 10, 8),
                    Arguments.of("a reserved input name",
                            List.of("input: state", "input_type: int"), 10, 8),
                    Arguments.of("a repeated input name",
                            List.of("input: a", "input_type: int", "input: a", "input_type: int"), 12, 8),
                    Arguments.of("a default outside an enum",
                            List.of("input: a", "input_type: enum[x|y]", "input_default: z"), 12, 16),
                    Arguments.of("an integer default outside its range",
                            List.of("input: a", "input_type: int[1..5]", "input_default: 6"), 12, 16),
                    Arguments.of("an integer default beyond the range of an int",
                            List.of("input: a", "input_type: int", "input_default: 2147483648"), 12, 16),
                    Arguments.of("an empty list default outside a list type",
                            List.of("input: a", "input_type: enum[x]", "input_default: []"), 12, 16),
                    Arguments.of("a default beside the optional form",
                            List.of("input: a", "input_type: int?", "input_default: 1"), 12, 16),
                    Arguments.of("a fallback beside the optional form",
                            List.of("input: a", "input_type: int?", "input_fallback: project.a"), 12, 17),
                    Arguments.of("an enum value twice",
                            List.of("input: a", "input_type: enum[x|x]"), 11, 13),
                    Arguments.of("an empty enum",
                            List.of("input: a", "input_type: enum[]"), 11, 13),
                    Arguments.of("a range with its high bound zero",
                            List.of("input: a", "input_type: int[1..0]"), 11, 13),
                    Arguments.of("a range with its low bound above its high bound",
                            List.of("input: a", "input_type: int[9..1]"), 11, 13),
                    Arguments.of("a range without its digits",
                            List.of("input: a", "input_type: int[..]"), 11, 13),
                    Arguments.of("a list of a type other than enum",
                            List.of("input: a", "input_type: list<int>"), 11, 13),
                    Arguments.of("a configuration key with an empty segment",
                            List.of("reads_config: project..mode"), 10, 15),
                    Arguments.of("a configuration key with a reserved segment",
                            List.of("reads_config: project.state"), 10, 15),
                    Arguments.of("a configuration key in upper case",
                            List.of("reads_config: Project.mode"), 10, 15),
                    Arguments.of("a fact kind in upper case",
                            List.of("reads_fact: Push"), 10, 13),
                    Arguments.of("a repeated reads_config value",
                            List.of("reads_config: project.mode", "reads_config: project.mode"), 11, 15),
                    Arguments.of("a repeated reads_fact value",
                            List.of("reads_fact: push_remote", "reads_fact: push_remote"), 11, 13));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("refusedInputs")
        @DisplayName("refuses an input or read outside its closed form with the rule, line and column")
        void refuses(String description, List<String> lines, int line, int column) {
            var diagnostic = refuse(unit(inserted(lines.toArray(String[]::new))));

            assertRefused(diagnostic, line, column);
        }

        @Test
        @DisplayName("refuses an input whose input_type is missing at the closing delimiter")
        void refusesAMissingTypeAtTheClosingDelimiter() {
            var diagnostic = refuse(unit(appended("input: a")));

            assertRefused(diagnostic, APPENDED_LINE + 1, 1);
        }

        @Test
        @DisplayName("refuses more than 16 inputs")
        void refusesMoreThanSixteenInputs() {
            var lines = new ArrayList<String>();
            for (var index = 1; index <= UnitFrontmatterReader.MAX_INPUTS; index++) {
                lines.add("input: in_" + index);
                lines.add("input_type: int");
            }
            lines.add("input: overflow");
            lines.add("input_type: int");

            var diagnostic = refuse(unit(inserted(lines.toArray(String[]::new))));

            assertAll(
                    () -> assertEquals(RefusalRule.FRONTMATTER, diagnostic.rule(),
                            "the input overflow is a FRONTMATTER refusal"),
                    () -> assertTrue(diagnostic.message().contains(Integer.toString(UnitFrontmatterReader.MAX_INPUTS)),
                            "the refusal names the input cap"));
        }

        @Test
        @DisplayName("refuses more than 32 reads_config values")
        void refusesMoreThanThirtyTwoReadsConfigValues() {
            var lines = new ArrayList<String>();
            for (var index = 1; index <= UnitFrontmatterReader.MAX_READS; index++) {
                lines.add("reads_config: project.key" + index);
            }
            lines.add("reads_config: project.overflow");

            var diagnostic = refuse(unit(inserted(lines.toArray(String[]::new))));

            assertAll(
                    () -> assertEquals(RefusalRule.FRONTMATTER, diagnostic.rule(),
                            "the reads_config overflow is a FRONTMATTER refusal"),
                    () -> assertTrue(diagnostic.message().contains(Integer.toString(UnitFrontmatterReader.MAX_READS)),
                            "the refusal names the reads cap"));
        }

        @Test
        @DisplayName("refuses more than 32 reads_fact values")
        void refusesMoreThanThirtyTwoReadsFactValues() {
            var lines = new ArrayList<String>();
            for (var index = 1; index <= UnitFrontmatterReader.MAX_READS; index++) {
                lines.add("reads_fact: kind" + index);
            }
            lines.add("reads_fact: overflow");

            var diagnostic = refuse(unit(inserted(lines.toArray(String[]::new))));

            assertAll(
                    () -> assertEquals(RefusalRule.FRONTMATTER, diagnostic.rule(),
                            "the reads_fact overflow is a FRONTMATTER refusal"),
                    () -> assertTrue(diagnostic.message().contains(Integer.toString(UnitFrontmatterReader.MAX_READS)),
                            "the refusal names the reads cap"));
        }

        @Test
        @DisplayName("pins the caps to the numbers the specification states")
        void pinsTheSpecificationNumbers() {
            assertAll(
                    () -> assertEquals(16, UnitFrontmatterReader.MAX_INPUTS, "the input cap is the specification's 16"),
                    () -> assertEquals(32, UnitFrontmatterReader.MAX_READS,
                            "the reads cap of each key is the specification's 32"),
                    () -> assertEquals(500, UnitFrontmatterReader.MAX_INPUT_DESCRIPTION,
                            "the input description cap is the specification's 500"));
        }

        @Test
        @DisplayName("reads a 500-character input description and refuses 501")
        void boundsTheInputDescription() {
            var longest = "d".repeat(UnitFrontmatterReader.MAX_INPUT_DESCRIPTION);
            var accepted = read(unit(inserted("input: a", "input_type: int", "input_description: " + longest)));
            var tooLong = refuse(unit(inserted("input: a", "input_type: int", "input_description: " + longest + "d")));

            assertAll(
                    () -> assertEquals(longest, accepted.frontmatter().inputs().getFirst().description(),
                            "the longest description is accepted"),
                    () -> assertRefused(tooLong, 12, 20));
        }
    }

    @Nested
    @DisplayName("The bounds")
    class Bounds {

        /** The field lines of a frontmatter of exactly {@code total} lines, the two delimiters included. */
        private static List<String> fieldLines(int total) {
            var lines = new ArrayList<>(VALID);
            for (var index = 1; lines.size() < total - 2; index++) {
                lines.add("entry: e" + index);
            }
            return lines;
        }

        @Test
        @DisplayName("reads a frontmatter at the line cap, both delimiters included, on lines 1 and 200")
        void acceptsTheLineCap() {
            var lines = fieldLines(UnitFrontmatterReader.MAX_FRONTMATTER_LINES);

            var result = read(unit(lines));

            assertAll(
                    () -> assertEquals(UnitFrontmatterReader.MAX_FRONTMATTER_LINES, lines.size() + 2,
                            "the frontmatter fills the line cap"),
                    () -> assertEquals(UnitFrontmatterReader.MAX_FRONTMATTER_LINES + 1, result.bodyLine(),
                            "the body starts after the cap"));
        }

        @Test
        @DisplayName("refuses a frontmatter of one more line than the cap, at the first line past it")
        void refusesOnePastTheLineCap() {
            var lines = fieldLines(UnitFrontmatterReader.MAX_FRONTMATTER_LINES + 1);

            var diagnostic = refuse(unit(lines));

            assertRefused(diagnostic, UnitFrontmatterReader.MAX_FRONTMATTER_LINES + 1, 1);
            assertTrue(diagnostic.message().contains(Integer.toString(UnitFrontmatterReader.MAX_FRONTMATTER_LINES)),
                    "the refusal names the line cap");
        }

        @Test
        @DisplayName("refuses a frontmatter that never closes within the line cap, also far past it")
        void refusesUnclosedBeyondTheLineCap() {
            var field = "entry: e";
            var lines = new ArrayList<String>();
            for (var index = 1; lines.size() <= UnitFrontmatterReader.MAX_FRONTMATTER_LINES + 100; index++) {
                lines.add(field + index);
            }
            var source = "---\n" + String.join("\n", lines) + "\n";

            var diagnostic = refuse(source);

            assertRefused(diagnostic, UnitFrontmatterReader.MAX_FRONTMATTER_LINES + 1, 1);
        }

        @Test
        @DisplayName("echoes an unknown key at the cap in full and truncates it one past the cap")
        void boundsTheUnknownKeyEcho() {
            var atCap = "k".repeat(UnitFrontmatterReader.MAX_ECHO_LENGTH);
            var onePast = atCap + "k";

            var inFull = refuse(unit(appended(atCap + ": x")));
            var truncated = refuse(unit(appended(onePast + ": x")));

            assertAll(
                    () -> assertRefused(inFull, APPENDED_LINE, 1),
                    () -> assertTrue(inFull.message().contains("'" + atCap + "'"), "a key at the cap is echoed in full"),
                    () -> assertFalse(inFull.message().contains("..."), "a key at the cap carries no ellipsis"),
                    () -> assertRefused(truncated, APPENDED_LINE, 1),
                    () -> assertTrue(truncated.message().contains("'" + atCap + "...'"),
                            "a key past the cap is cut with an ellipsis"),
                    () -> assertFalse(truncated.message().contains(onePast),
                            "a key past the cap is not echoed in full"));
        }

        @Test
        @DisplayName("truncates the unknown version echoed into the refusal to the cap")
        void boundsTheVersionEcho() {
            var value = "1.0.0-" + "r".repeat(UnitFrontmatterReader.MAX_ECHO_LENGTH);
            var echoed = value.substring(0, UnitFrontmatterReader.MAX_ECHO_LENGTH);

            var diagnostic = refuse(unit(replaced("version", "version: " + value)));

            assertAll(
                    () -> assertRefused(diagnostic, 11, 10),
                    () -> assertTrue(diagnostic.message().contains(echoed + "..."),
                            "the version echo is cut at the cap"),
                    () -> assertFalse(diagnostic.message().contains(value), "the full version is not echoed"));
        }

        @Test
        @DisplayName("truncates an echo far past the cap and counts the cap in code points")
        void boundsAnEchoFarPastTheCap() {
            var far = "k".repeat(1000);
            var supplement = "\uD83D\uDE00";
            var codePoints = "k".repeat(UnitFrontmatterReader.MAX_ECHO_LENGTH - 1) + supplement + "k";

            var farDiagnostic = refuse(unit(appended(far + ": x")));
            var codePointDiagnostic = refuse(unit(appended(codePoints + ": x")));

            assertAll(
                    () -> assertTrue(farDiagnostic.message().contains(
                            "k".repeat(UnitFrontmatterReader.MAX_ECHO_LENGTH) + "..."),
                            "a far-too-long key is cut at the cap"),
                    () -> assertFalse(farDiagnostic.message().contains(far), "the far-too-long key is not echoed"),
                    () -> assertTrue(codePointDiagnostic.message().contains(
                                    "k".repeat(UnitFrontmatterReader.MAX_ECHO_LENGTH - 1) + supplement + "..."),
                            "the cap counts the supplement as one point"),
                    () -> assertFalse(codePointDiagnostic.message().contains(codePoints),
                            "the code-point key is not echoed in full"));
        }

        @Test
        @DisplayName("reads the frontmatter of a unit whose body after the closing line would not be accepted, "
                + "without reading the body")
        void neverReadsTheBody() {
            var source = "---\n" + String.join("\n", VALID) + "\n---\n\u0007 no unit at all\n";

            var result = read(source);

            assertAll(
                    () -> assertEquals("Commit and Push", result.frontmatter().name(),
                            "the body is never read"),
                    () -> assertEquals(APPENDED_LINE + 1, result.bodyLine(),
                            "the body line is still counted"));
        }

        @Test
        @DisplayName("reads the three limits from the module's limits resource")
        void constantsAreReadFromTheLimitsResource() {
            assertAll(
                    () -> assertEquals(
                            ProjectUnitLoader.MAX_UNIT_FILE_BYTES,
                            LimitProperties.read(UnitLimits.RESOURCE, UnitLimits.MAX_UNIT_FILE_BYTES), "the file bound must come from the resource"),
                    () -> assertEquals(
                            UnitFrontmatterReader.MAX_FRONTMATTER_LINES,
                            LimitProperties.read(UnitLimits.RESOURCE, UnitLimits.MAX_FRONTMATTER_LINES), "the line bound must come from the resource"),
                    () -> assertEquals(
                            UnitFrontmatterReader.MAX_ECHO_LENGTH,
                            LimitProperties.read(UnitLimits.RESOURCE, UnitLimits.MAX_ECHO_LENGTH), "the echo bound must come from the resource"));
        }

        @Test
        @DisplayName("an alternate resource yields its own value, never a fixed one")
        void readsAnAlternateResource() {
            var alternate = "/de/planmarshall/workflow/dsl/alternate-limits.properties";

            assertEquals(42, LimitProperties.read(alternate, UnitLimits.MAX_ECHO_LENGTH),
                    "the reader must return the value of the resource");
        }

        @Test
        @DisplayName("an absent limits resource fails the load instead of defaulting")
        void anAbsentLimitsResourceFailsTheLoad() {
            var absent = "/de/planmarshall/workflow/dsl/absent-limits.properties";

            assertThrows(IllegalStateException.class,
                    () -> LimitProperties.read(absent, UnitLimits.MAX_ECHO_LENGTH),
                    "an absent limits resource must fail the load");
        }

        @Test
        @DisplayName("an absent key fails the load instead of defaulting")
        void anAbsentKeyFailsTheLoad() {
            var properties = new Properties();

            assertThrows(IllegalStateException.class,
                    () -> LimitProperties.value(properties, UnitLimits.MAX_ECHO_LENGTH, "limits"),
                    "an absent limit key must fail the load");
        }

        @Test
        @DisplayName("a value that is not a number fails the load instead of defaulting")
        void aNonNumericValueFailsTheLoad() {
            var properties = new Properties();
            properties.setProperty(UnitLimits.MAX_ECHO_LENGTH, "not-a-number");

            assertThrows(IllegalStateException.class,
                    () -> LimitProperties.value(properties, UnitLimits.MAX_ECHO_LENGTH, "limits"),
                    "a non-numeric limit must fail the load");
        }
    }
}
