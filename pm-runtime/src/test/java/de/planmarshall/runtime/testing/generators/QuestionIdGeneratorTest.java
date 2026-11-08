/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.testing.generators;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@EnableGeneratorController
@DisplayName("Question id generator")
class QuestionIdGeneratorTest {

    @Test
    @DisplayName("Should yield a question id of three to six digits without a leading zero")
    void shouldYieldTheWrittenQuestionId() {
        var questionId = RuntimeGenerators.questionIds().next();

        assertTrue(questionId.matches("Q-[1-9][0-9]{2,5}"), "A question id has Q- and three to six digits");
        assertEquals(String.class, RuntimeGenerators.questionIds().getType(), "The generator names the string type");
    }
}
