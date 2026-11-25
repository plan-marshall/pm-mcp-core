/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.provider.git;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The textual rule of a commit path: the pathspec magic, a backslash, a control character and a non-normal segment are
 * refused, while a glob character, an option-shaped name and a plain name are literal paths.
 */
@EnableGeneratorController
@DisplayName("CommitPaths")
class CommitPathsTest {

    @Nested
    @DisplayName("refused")
    class Refused {

        // the paths of the contract's refusal rule, written as the literals a caller can hand in
        @ParameterizedTest
        @ValueSource(strings = {"", ":magic", ":(exclude)x", "/absolute", "a\\b", "a\u0001b", "a\nb", "a\rb", "dir/",
                "a//b", ".", "a/./b", "..", "../x", "a/../b"})
        @DisplayName("a name breaking the textual rule of a commit path is refused")
        void refusedText(String path) {
            assertTrue(CommitPaths.isRefusedText(path), "The name is no literal commit path");
        }

        @Test
        @DisplayName("a NUL character is a control character and is refused")
        void nulIsRefused() {
            assertTrue(CommitPaths.isRefusedText("a\u0000b"), "A NUL breaks the textual rule");
        }
    }

    @Nested
    @DisplayName("admitted")
    class Admitted {

        // the specification's literal path examples: glob characters and an option-shaped name are plain names
        @ParameterizedTest
        @ValueSource(strings = {"app/[slug]/page.tsx", "notes*.md", "what?.md", "-rf", "a b.txt", "é.txt"})
        @DisplayName("a literal name with a glob or other character is admitted")
        void admittedText(String path) {
            assertFalse(CommitPaths.isRefusedText(path), "The literal name is a commit path");
        }

        @Test
        @DisplayName("a generated path of letters is admitted")
        void generatedNameIsAdmitted() {
            var path = Generators.letterStrings(1, 12).next() + "/" + Generators.letterStrings(1, 12).next() + ".java";

            assertFalse(CommitPaths.isRefusedText(path), "A plain relative path is a commit path");
        }
    }
}
