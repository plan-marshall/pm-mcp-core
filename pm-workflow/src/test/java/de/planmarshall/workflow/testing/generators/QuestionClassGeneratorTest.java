/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.testing.generators;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.planmarshall.workflow.verify.QuestionClass;

@DisplayName("Question class generator")
@EnableGeneratorController
class QuestionClassGeneratorTest {

    private final QuestionClassGenerator generator = new QuestionClassGenerator();

    @Test
    @DisplayName("Should yield valid and distinct question classes")
    void shouldYieldValidAndDistinctQuestionClasses() {
        var first = generator.next();
        var second = generator.next();

        assertAll("generated question classes",
                () -> assertNotNull(first, "The generator yields a value"),
                () -> assertNotEquals(first, second, "Two calls yield distinct values"),
                () -> assertFalse(first.name().isBlank(), "The question class carries a name"),
                () -> assertFalse(first.outcomes().isEmpty(), "The question class carries outcomes"));
    }

    @Test
    @DisplayName("Should report the generated type")
    void shouldReportTheGeneratedType() {
        assertEquals(QuestionClass.class, generator.getType(), "The generator names the question type");
    }
}
