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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.planmarshall.workflow.ast.InputType;

/**
 * {@link InputValidator} over the five call-time input types (PM-WF-16): the length in code points of {@code text} and
 * {@code commit_message}, the Conventional Commit refusal of {@code commit_message}, the path rules of {@link
 * PathRules}, and the count and duplicate rules of {@code list<path>}.
 * <p>
 * Every refusal is a value ({@link Optional} of {@link InputRefusal}), never an exception. A bound of the
 * Conventional Commit grammar, an input bound and a path fixture are the contract, so their literals stay; the rule
 * identifiers are asserted where they are stable.
 */
@EnableGeneratorController
@DisplayName("Input validator")
class InputValidatorTest {

    private static InputRefusal refusal(Optional<InputRefusal> result) {
        return result.orElseThrow(() -> new AssertionError("the value was expected to be refused"));
    }

    @Nested
    @DisplayName("Missing values")
    class MissingValues {

        @Test
        @DisplayName("a missing text is refused as input.missing")
        void missingText() {
            var refusal = refusal(InputValidator.validateText(new InputType.Text(null, null), null));

            assertEquals("input.missing", refusal.ruleId(), "a missing text is refused as missing");
        }

        @Test
        @DisplayName("a missing path is refused as input.missing")
        void missingPath() {
            var refusal = refusal(InputValidator.validatePath(null));

            assertEquals("input.missing", refusal.ruleId(), "a missing path is refused as missing");
        }

        @Test
        @DisplayName("a missing path list is refused as input.missing")
        void missingPathList() {
            var refusal = refusal(InputValidator.validatePathList(new InputType.PathList(null, null), null));

            assertEquals("input.missing", refusal.ruleId(), "a missing list is refused as missing");
        }
    }

    @Nested
    @DisplayName("Text length")
    class TextLength {

        @ParameterizedTest(name = "[{0}..{1}] \"{2}\"")
        @MethodSource("de.planmarshall.workflow.input.InputValidatorTest#textWithinBounds")
        @DisplayName("a text whose code points lie within the bounds is accepted")
        void withinBoundsAccepted(int min, int max, String value) {
            assertTrue(InputValidator.validateText(new InputType.Text(min, max), value).isEmpty(),
                    "a text within the bounds is accepted");
        }

        @Test
        @DisplayName("a text of exactly the upper bound is accepted")
        void upperBoundAccepted() {
            var value = Generators.letterStrings(10, 10).next();

            assertTrue(InputValidator.validateText(new InputType.Text(10, 10), value).isEmpty(),
                    "the upper bound itself is accepted");
        }

        @Test
        @DisplayName("a text of three emoji counts three code points")
        void codePointsNotChars() {
            var emoji = "\uD83D\uDE00";
            var value = emoji.repeat(3);

            assertTrue(InputValidator.validateText(new InputType.Text(3, 3), value).isEmpty(),
                    "the length is counted in code points");
        }

        @Test
        @DisplayName("a text below the lower bound is refused as too short")
        void tooShort() {
            var refusal = refusal(InputValidator.validateText(new InputType.Text(5, 10), "abc"));

            assertEquals("input.too-short", refusal.ruleId(), "a short text is refused as too short");
        }

        @Test
        @DisplayName("a text above the upper bound is refused as too long")
        void tooLong() {
            var value = Generators.letterStrings(11, 11).next();
            var refusal = refusal(InputValidator.validateText(new InputType.Text(5, 10), value));

            assertEquals("input.too-long", refusal.ruleId(), "a long text is refused as too long");
        }

        @Test
        @DisplayName("an unbounded text admits an empty value")
        void unboundedAdmitsEmpty() {
            assertTrue(InputValidator.validateText(new InputType.Text(null, null), "").isEmpty(),
                    "an unbounded text admits the empty value");
        }
    }

    @Nested
    @DisplayName("Commit messages")
    class CommitMessages {

        private static final InputType COMMIT_MESSAGE = new InputType.CommitMessage(null, null);

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {
                "feat(input): bind the slot from the literal value",
                "fix: refuse the duplicate path",
                "feat(api)!: drop the body param declaration",
                "chore: refresh the frozen fixtures\n\nA body follows one blank line."
        })
        @DisplayName("a Conventional Commit without a trailer is accepted")
        void conventionalCommitAccepted(String value) {
            assertTrue(InputValidator.validateText(COMMIT_MESSAGE, value).isEmpty(),
                    "a Conventional Commit is accepted");
        }

        @Test
        @DisplayName("a message of exactly 2000 code points is accepted")
        void upperBoundAccepted() {
            var value = "feat: " + "x".repeat(2000 - "feat: ".length());

            assertTrue(InputValidator.validateText(new InputType.CommitMessage(1, 2000), value).isEmpty(),
                    "the commit-message upper bound is accepted");
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.workflow.input.InputValidatorTest#refusedCommitMessages")
        @DisplayName("a message the Conventional Commit validator refuses is input.commit-message")
        void conventionalCommitRefused(String description, String value) {
            var refusal = refusal(InputValidator.validateText(COMMIT_MESSAGE, value));

            assertEquals("input.commit-message", refusal.ruleId(),
                    "the refusal is the commit-message refusal");
        }

        @Test
        @DisplayName("a blank message is refused as input.commit-message")
        void blankRefused() {
            var refusal = refusal(InputValidator.validateText(COMMIT_MESSAGE, "   "));

            assertEquals("input.commit-message", refusal.ruleId(), "a blank message is refused");
        }

        @Test
        @DisplayName("a message of 2001 code points is refused as too long")
        void oneCodePointTooLong() {
            var value = "feat: " + "x".repeat(2001 - "feat: ".length());

            assertEquals("input.too-long",
                    refusal(InputValidator.validateText(new InputType.CommitMessage(1, 2000), value)).ruleId(),
                    "one code point above the bound is refused");
        }

        @Test
        @DisplayName("a lone surrogate has no Conventional Commit header")
        void loneSurrogateRefused() {
            var refusal = refusal(InputValidator.validateText(COMMIT_MESSAGE, "\uD800"));

            assertEquals("input.commit-message", refusal.ruleId(),
                    "a lone surrogate is no commit header");
        }

        @Test
        @DisplayName("the commit-message range admits exactly its minimum")
        void minimumBoundAccepted() {
            assertTrue(InputValidator.validateText(new InputType.CommitMessage(10, 72), "feat: a bc").isEmpty(),
                    "the minimum commit-message length is accepted");
        }
    }

    @Nested
    @DisplayName("Path delegation")
    class PathDelegation {

        @Test
        @DisplayName("a relative path is accepted")
        void relativePathAccepted() {
            assertTrue(InputValidator.validatePath("src/main/java/A.java").isEmpty(),
                    "a relative path is accepted by the path type");
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.workflow.input.InputValidatorTest#refusedPaths")
        @DisplayName("a refused path carries the rule of PathRules unchanged")
        void pathRuleDelegated(String description, String path, String ruleId) {
            var refusal = refusal(InputValidator.validatePath(path));

            assertEquals(ruleId, refusal.ruleId(), "the path rule is delegated unchanged");
        }
    }

    @Nested
    @DisplayName("Path lists")
    class PathLists {

        @Test
        @DisplayName("a list of distinct relative paths is accepted")
        void accepted() {
            var values = List.of("src/main/java/A.java", "src/test/java/B.java");

            assertTrue(InputValidator.validatePathList(new InputType.PathList(1, 500), values).isEmpty(),
                    "a list of distinct relative paths is accepted");
        }

        @Test
        @DisplayName("a list of the upper count bound is accepted")
        void upperCountAccepted() {
            var values = paths(500);

            assertTrue(InputValidator.validatePathList(new InputType.PathList(1, 500), values).isEmpty(),
                    "the upper count bound is accepted");
        }

        @Test
        @DisplayName("a duplicate path is refused as input.duplicate")
        void duplicateRefused() {
            var refusal = refusal(InputValidator.validatePathList(new InputType.PathList(null, null),
                    List.of("a/b", "a/b")));

            assertEquals("input.duplicate", refusal.ruleId(), "a duplicate path is refused");
        }

        @Test
        @DisplayName("a list of 501 entries is refused as input.count")
        void aboveCountRefused() {
            var refusal = refusal(InputValidator.validatePathList(new InputType.PathList(1, 500), paths(501)));

            assertEquals("input.count", refusal.ruleId(), "one entry above the count bound is refused");
        }

        @Test
        @DisplayName("a list below its count bound is refused as input.count")
        void belowCountRefused() {
            var refusal = refusal(InputValidator.validatePathList(new InputType.PathList(2, 500),
                    List.of("a/b")));

            assertEquals("input.count", refusal.ruleId(), "a list below the count bound is refused");
        }

        @Test
        @DisplayName("an entry that breaks a path rule refuses the whole list")
        void entryRuleRefusesList() {
            var refusal = refusal(InputValidator.validatePathList(new InputType.PathList(null, null),
                    List.of("a/b", "/absolute")));

            assertEquals("path.absolute", refusal.ruleId(), "the entry's path rule refuses the list");
        }
    }

    static Stream<Arguments> textWithinBounds() {
        return Stream.of(
                Arguments.of(0, 5, ""),
                Arguments.of(1, 5, "abcde"),
                Arguments.of(1, 5, Generators.letterStrings(1, 5).next()),
                Arguments.of(3, 3, "abc"),
                Arguments.of(0, 0, ""));
    }

    static Stream<Arguments> refusedCommitMessages() {
        return Stream.of(
                // The header grammar and the trailer rule are the contract of the Conventional Commit.
                Arguments.of("a header without a type", ": add the slot"),
                Arguments.of("a header without a description", "feat:"),
                Arguments.of("a header without a space after the colon", "feat:add the slot"),
                Arguments.of("a body without a blank line", "feat: add the slot\nbody text"),
                Arguments.of("a trailer at the end", "feat: add the slot\n\nCo-Authored-By: someone <a@b.c>"),
                Arguments.of("a trailer in lower case", "feat: add the slot\n\nco-authored-by: someone <a@b.c>"),
                Arguments.of("a trailer in upper case at the end", "feat: add the slot\n\nbody\n\nCO-AUTHORED-BY: X"),
                Arguments.of("a trailer directly after the header", "feat: add the slot\n\nCo-Authored-By: X"));
    }

    static Stream<Arguments> refusedPaths() {
        return Stream.of(
                Arguments.of("an absolute path", "/etc/passwd", "path.absolute"),
                Arguments.of("a dot-dot segment", "a/../b", "path.dot-segment"),
                Arguments.of("a backslash", "a\\b", "path.backslash"),
                Arguments.of("a .git segment", ".git/config", "path.git"));
    }

    private static List<String> paths(int count) {
        return IntStream.range(0, count).mapToObj(index -> "file" + index + ".txt").toList();
    }
}
