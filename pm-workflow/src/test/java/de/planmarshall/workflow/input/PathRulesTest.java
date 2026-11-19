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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
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

/**
 * The syntactic path rules of Decision 3 (PM-WF-16): a path is relative, uses {@code /} only, has no empty segment, no
 * {@code .} or {@code ..} segment, no NUL, control or bidi character, no leading {@code :} or {@code -}, no {@code .git}
 * segment in any case, and at most 1024 characters.
 * <p>
 * A refusal is a value, never an exception: {@link PathRules#check(String)} returns {@link InputRefusal} or
 * {@code null}. The rule identifiers are the contract of the refusal, so every refusing case names the identifier the
 * rule assigns; the refused value is the grammar boundary and stays a literal.
 */
@EnableGeneratorController
@DisplayName("Path rules of Decision 3")
class PathRulesTest {

    private static Optional<InputRefusal> refusal(String value) {
        return Optional.ofNullable(PathRules.check(value));
    }

    private static InputRefusal refused(String value) {
        return refusal(value).orElseThrow(
                () -> new AssertionError("the path '" + value + "' was expected to be refused"));
    }

    @Nested
    @DisplayName("Accepted paths")
    class AcceptedPaths {

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {
                "a",
                "src/main/java/A.java",
                "docs/adr/001-slots-and-provenance.adoc",
                ".github/workflows/ci.yml",
                "target"
        })
        @DisplayName("a relative path of non-empty segments is accepted")
        void relativePathAccepted(String path) {
            assertTrue(refusal(path).isEmpty(), "a relative path of plain segments is accepted");
        }

        @Test
        @DisplayName("a path of exactly 1024 characters is accepted")
        void maximumLengthAccepted() {
            var path = "x".repeat(PathRules.MAX_PATH_LENGTH);

            assertTrue(refusal(path).isEmpty(), "the maximum path length is accepted");
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.workflow.input.PathRulesTest#generatedPaths")
        @DisplayName("a generated relative path of letter segments is accepted")
        void generatedPathAccepted(String path) {
            assertTrue(refusal(path).isEmpty(), "a generated relative path is accepted");
        }
    }

    @Nested
    @DisplayName("Refused paths")
    class RefusedPaths {

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.workflow.input.PathRulesTest#refusals")
        @DisplayName("the path is refused with the rule identifier of Decision 3")
        void refusedWithRule(String description, String path, String ruleId) {
            var refusal = refused(path);

            assertEquals(ruleId, refusal.ruleId(), "the refusal names the rule of the path");
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.workflow.input.PathRulesTest#refusals")
        @DisplayName("the refusal echoes at most 80 characters of the refused path")
        void refusalEchoesBoundedInput(String description, String path, String ruleId) {
            var refusal = refused(path);

            assertTrue(refusal.inputValue().length() <= InputRefusal.MAX_ECHO_LENGTH,
                    "the refusal echoes at most the maximum");
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.workflow.input.PathRulesTest#dotSegmentPositions")
        @DisplayName("a dot-dot segment is refused in every position of the path")
        void dotDotInEveryPosition(String description, String path) {
            var refusal = refused(path);

            assertEquals("path.dot-segment", refusal.ruleId(), "dot-dot is refused in every position");
        }

        @Test
        @DisplayName("a path of 1025 characters is refused as too long")
        void oneCharacterTooLong() {
            var path = "x".repeat(PathRules.MAX_PATH_LENGTH + 1);

            assertEquals("path.too-long", refused(path).ruleId(), "one character above the maximum is refused");
        }

        @Test
        @DisplayName("an empty segment is refused before the double-slash rule")
        void emptySegmentBeforeDoubleSlash() {
            assertNotNull(PathRules.check("a//b"), "a doubled slash holds an empty segment");
            assertEquals("path.empty-segment", refused("a//b").ruleId(),
                    "the empty segment is the rule that refuses it");
        }
    }

    static Stream<String> generatedPaths() {
        return Stream.of(
                Generators.letterStrings(1, 8).next(),
                Generators.letterStrings(1, 8).next() + "/" + Generators.letterStrings(1, 8).next(),
                Generators.letterStrings(1, 8).next() + "/" + Generators.letterStrings(1, 8).next() + "/"
                        + Generators.letterStrings(1, 8).next());
    }

    static Stream<Arguments> refusals() {
        return Stream.of(
                Arguments.of("an empty path", "", "path.empty"),
                Arguments.of("an absolute path", "/etc/passwd", "path.absolute"),
                Arguments.of("a doubled slash", "a//b", "path.empty-segment"),
                Arguments.of("a trailing slash", "a/", "path.empty-segment"),
                Arguments.of("a backslash", "a\\b", "path.backslash"),
                Arguments.of("a NUL character", "a\u0000b", "path.nul"),
                Arguments.of("a right-to-left override", "a\u202Eb", "path.bidi"),
                Arguments.of("an isolate initiator", "a\u2066b", "path.bidi"),
                Arguments.of("a git pathspec exclusion", ":(exclude)x", "path.magic"),
                Arguments.of("a leading dash", "-rf", "path.magic"),
                Arguments.of("a .git segment in upper case", ".GIT/config", "path.git"),
                Arguments.of("a nested .git segment", "a/.git/b", "path.git"),
                Arguments.of("a current-directory segment", "a/./b", "path.dot-segment"),
                Arguments.of("a control character", "a\u0007b", "path.control"),
                Arguments.of("a space inside the path", "a b", "path.whitespace"));
    }

    static List<Arguments> dotSegmentPositions() {
        return List.of(
                Arguments.of("dot-dot alone", ".."),
                Arguments.of("dot-dot at the front", "../a"),
                Arguments.of("dot-dot at the end", "a/.."),
                Arguments.of("dot-dot in the middle", "a/../b"),
                Arguments.of("two dot-dot segments", "a/../../b"));
    }
}
