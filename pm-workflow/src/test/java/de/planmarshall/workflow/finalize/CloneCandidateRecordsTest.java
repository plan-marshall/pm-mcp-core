/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.finalize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;

/**
 * Proves that the builder of {@link CloneCandidate} builds the value of its canonical constructor.
 */
@EnableGeneratorController
@DisplayName("Clone candidate record builder")
class CloneCandidateRecordsTest {

    @Test
    @DisplayName("the builder builds the clone candidate the constructor builds")
    void cloneCandidateBuilderEqualsConstructor() {
        var addedPath = Generators.letterStrings(1, 20).next();
        var addedRange = new LineRange(1, Generators.integers(2, 100).next());
        var existingPath = Generators.letterStrings(1, 20).next();
        var existingRange = new LineRange(1, Generators.integers(2, 100).next());
        var tokens = Generators.integers(CloneCandidate.MIN_TOKENS, 1_000).next();
        var constructed = new CloneCandidate(addedPath, addedRange, existingPath, existingRange, tokens);
        var built = CloneCandidate.builder()
                .addedPath(addedPath)
                .addedRange(addedRange)
                .existingPath(existingPath)
                .existingRange(existingRange)
                .tokens(tokens)
                .build();

        assertEquals(constructed, built, "the builder builds the same clone candidate");
    }

    @Test
    @DisplayName("the builder refuses a missing added path like the constructor")
    void cloneCandidateBuilderRefusesMissingAddedPath() {
        var exception = assertThrows(NullPointerException.class, () -> CloneCandidate.builder()
                .addedRange(new LineRange(1, 10))
                .existingPath(Generators.letterStrings(1, 20).next())
                .existingRange(new LineRange(2, 20))
                .tokens(Generators.integers(CloneCandidate.MIN_TOKENS, 1_000).next())
                .build());

        assertEquals("addedPath", exception.getMessage(), "the builder names the missing added path");
    }
}
