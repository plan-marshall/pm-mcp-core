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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import de.planmarshall.workflow.ast.SourceLocation;
import de.planmarshall.workflow.ast.UnitFrontmatter.InterfaceElement;
import de.planmarshall.workflow.ast.UnitType;

/**
 * The reader of the unit frontmatter (PM-IMPL-5): a valid frontmatter is read field by field, and every form outside
 * its closed, line-oriented subset and every field bound is refused with the rule {@link RefusalRule#FRONTMATTER}, the
 * line and the column. A general YAML loader resolves tags, anchors, aliases and merge keys, which is the hazard the
 * reader of its own closes.
 */
@DisplayName("UnitFrontmatterReader")
class UnitFrontmatterReaderTest {

    private static final String RESOURCE = "fixture.pmwf";

    /** A valid frontmatter, one line per element; it stands on lines 2 to 11, the closing {@code ---} on line 12. */
    private static final List<String> VALID = List.of(
            "name: Commit and Push",
            "description: Commits the changes of the plan's worktree and pushes its branch.",
            "type: subworkflow",
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
        return assertInstanceOf(UnitFrontmatterReader.Result.Read.class, result, () -> "refused: " + result);
    }

    private static Diagnostic refuse(String source) {
        var result = UnitFrontmatterReader.read(RESOURCE, source);
        return assertInstanceOf(UnitFrontmatterReader.Result.Refused.class, result, "the frontmatter was read")
                .diagnostic();
    }

    private static void assertRefused(Diagnostic diagnostic, int line, int column) {
        assertAll(
                () -> assertEquals(RefusalRule.FRONTMATTER, diagnostic.rule(), diagnostic::message),
                () -> assertEquals(RESOURCE, diagnostic.resourceName()),
                () -> assertEquals(line, diagnostic.line(), diagnostic::message),
                () -> assertEquals(column, diagnostic.column(), diagnostic::message));
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
                    () -> assertEquals("Commit and Push", frontmatter.name()),
                    () -> assertEquals("Commits the changes of the plan's worktree and pushes its branch.",
                            frontmatter.description()),
                    () -> assertEquals(UnitType.SUBWORKFLOW, frontmatter.type()),
                    () -> assertEquals(List.of(new InterfaceElement("commit",
                                    Optional.of("The worktree holds the changes to commit, or is clean."))),
                            frontmatter.entries()),
                    () -> assertEquals(List.of(new InterfaceElement("done",
                            Optional.of("Commit and push are settled."))), frontmatter.exits()),
                    () -> assertEquals(LocalDate.of(2026, 10, 14), frontmatter.modificationDate()),
                    () -> assertEquals("1.0.0", frontmatter.version()),
                    () -> assertEquals("plan-marshall", frontmatter.author()),
                    () -> assertEquals(Optional.empty(), frontmatter.signature()),
                    () -> assertEquals(new SourceLocation(RESOURCE, 1, 1), frontmatter.location()),
                    () -> assertEquals(APPENDED_LINE + 1, result.bodyLine()));
        }

        @Test
        @DisplayName("keeps three entries in their order, each with its description")
        void keepsThreeEntriesInOrder() {
            var source = unit(List.of(VALID.get(0), VALID.get(1), VALID.get(2), "entry: post_run",
                    "entry_description: The run is over.", "entry: hand_back",
                    "entry_description: The branch holds commits beyond the base.", "entry: merge",
                    "entry_description: The plan is ready to merge.", VALID.get(5), VALID.get(7), VALID.get(8),
                    VALID.get(9)));

            var entries = read(source).frontmatter().entries();

            assertEquals(List.of(
                    new InterfaceElement("post_run", Optional.of("The run is over.")),
                    new InterfaceElement("hand_back", Optional.of("The branch holds commits beyond the base.")),
                    new InterfaceElement("merge", Optional.of("The plan is ready to merge."))), entries);
        }

        @Test
        @DisplayName("reads entries and exits without descriptions, and a unit without either")
        void readsElementsWithoutDescriptions() {
            var withoutDescriptions = unit(List.of(VALID.get(0), VALID.get(1), VALID.get(2), "entry: commit",
                    "exit: done", "exit: failed", VALID.get(7), VALID.get(8), VALID.get(9)));
            var withoutInterface = unit(List.of(VALID.get(0), VALID.get(1), "type: workflow", VALID.get(7),
                    VALID.get(8), VALID.get(9)));

            var described = read(withoutDescriptions).frontmatter();
            var bare = read(withoutInterface).frontmatter();

            assertAll(
                    () -> assertEquals(List.of(new InterfaceElement("commit", Optional.empty())), described.entries()),
                    () -> assertEquals(List.of(new InterfaceElement("done", Optional.empty()),
                            new InterfaceElement("failed", Optional.empty())), described.exits()),
                    () -> assertEquals(UnitType.WORKFLOW, bare.type()),
                    () -> assertTrue(bare.entries().isEmpty()),
                    () -> assertTrue(bare.exits().isEmpty()));
        }

        @Test
        @DisplayName("carries the signature and does not verify it")
        void carriesSignature() {
            var source = unit(appended("signature: not-a-verified-signature"));

            var frontmatter = read(source).frontmatter();

            assertEquals(Optional.of("not-a-verified-signature"), frontmatter.signature());
        }

        @Test
        @DisplayName("reads a double-quoted value with its two escapes")
        void readsDoubleQuotedValue() {
            var source = unit(replaced("description", "description: \"Reads \\\"a: b\\\" # and \\\\ alike\""));

            var frontmatter = read(source).frontmatter();

            assertEquals("Reads \"a: b\" # and \\ alike", frontmatter.description());
        }

        @Test
        @DisplayName("reads the frontmatter of a text whose body is not parseable")
        void readsWithoutBody() {
            var source = "---\n" + String.join("\n", VALID) + "\n---\nthis is { no unit ### at all\n";

            var result = read(source);

            assertEquals("Commit and Push", result.frontmatter().name());
        }

        @Test
        @DisplayName("lets the lines stand in any order but the descriptions")
        void readsAnyOrder() {
            var reversed = new ArrayList<>(List.of(VALID.get(9), VALID.get(8), VALID.get(7), VALID.get(5),
                    VALID.get(6), VALID.get(3), VALID.get(4), VALID.get(2), VALID.get(1), VALID.get(0)));

            var frontmatter = read(unit(reversed)).frontmatter();

            assertEquals(read(unit(VALID)).frontmatter(), frontmatter);
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(UnitType.class)
        @DisplayName("reads every one of the six types by its wire name")
        void readsEveryType(UnitType type) {
            var source = unit(replaced("type", "type: " + type.wireName()));

            assertEquals(type, read(source).frontmatter().type());
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
                    Arguments.of("an entry description below another key", appended("entry_description: x"),
                            APPENDED_LINE, 1),
                    Arguments.of("an exit description below another key", appended("exit_description: x"),
                            APPENDED_LINE, 1),
                    Arguments.of("a description as the first line",
                            List.of("exit_description: x", VALID.get(0)), 2, 1),
                    Arguments.of("an exit description below an entry",
                            replaced("entry", "entry: commit", "exit_description: x"), 6, 1),
                    Arguments.of("a second description of one entry",
                            replaced("entry_description", "entry_description: a", "entry_description: b"), 7, 1),
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

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"name", "description", "type", "modification_date", "version", "author"})
        @DisplayName("refuses a frontmatter that lacks a required key at its closing line")
        void refusesMissingKey(String key) {
            var diagnostic = refuse(unit(without(key)));

            assertRefused(diagnostic, APPENDED_LINE - 1, 1);
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

            assertInstanceOf(UnitFrontmatterReader.Result.Read.class, result, () -> "refused: " + result);
        }

        @Test
        @DisplayName("refuses an entry declared twice at the second")
        void refusesDuplicateEntry() {
            var lines = replaced("entry", "entry: commit", "entry: commit");

            var diagnostic = refuse(unit(lines));

            assertRefused(diagnostic, 6, 8);
        }

        @Test
        @DisplayName("refuses an exit declared twice at the second")
        void refusesDuplicateExit() {
            var lines = replaced("exit", "exit: done", "exit: done");

            var diagnostic = refuse(unit(lines));

            assertRefused(diagnostic, 8, 7);
        }
    }
}
