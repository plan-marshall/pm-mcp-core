/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.provider.git.testing.generators;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import de.planmarshall.provider.git.primitive.CommitMessages;

@DisplayName("Commit message generator")
@EnableGeneratorController
class CommitMessageGeneratorTest {

    private final CommitMessageGenerator generator = new CommitMessageGenerator();

    @RepeatedTest(50)
    @DisplayName("Should yield valid Conventional Commit messages")
    void shouldYieldValidMessages() {
        var generated = generator.next();

        assertAll("generated message",
                () -> assertEquals(List.of(), CommitMessages.validate(generated),
                        "The generated message breaks no rule"),
                () -> assertTrue(generated.length() <= CommitMessages.MAX_LENGTH,
                        "The generated message stays within bound"));
    }

    @Test
    @DisplayName("Should report the generated type")
    void shouldReportTheGeneratedType() {
        assertEquals(String.class, generator.getType(), "The generator names the String type");
    }

    @Test
    @DisplayName("Should be registered by the module factory")
    void shouldBeRegisteredByTheModuleFactory() {
        assertEquals(String.class, GitGenerators.commitMessages().getType(),
                "The factory yields the message generator");
    }
}
