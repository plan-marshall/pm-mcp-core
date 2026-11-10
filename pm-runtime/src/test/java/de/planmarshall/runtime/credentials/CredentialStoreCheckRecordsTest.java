/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.credentials;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Proves that the builder of {@link CredentialStoreCheck.CheckLine} builds the value its canonical constructor builds,
 * including the null refusal for the step. Every value is generated; the step and the outcome are drawn from their
 * closed vocabularies.
 */
@EnableGeneratorController
@DisplayName("CredentialStoreCheck record builder")
class CredentialStoreCheckRecordsTest {

    @Test
    @DisplayName("the check line builder builds the check line the constructor builds")
    void checkLineBuilderEqualsConstructor() {
        var step = Generators.enumValues(CredentialStoreCheck.Step.class).next();
        var outcome = Generators.enumValues(CredentialStoreCheck.Outcome.class).next();
        var messageKey = CredentialStoreCheck.MESSAGE_KEY_PREFIX + Generators.letterStrings(1, 20).next();
        var arguments = List.of(Generators.letterStrings(1, 20).next());
        var answer = Generators.letterStrings(1, 40).next();

        var constructed = new CredentialStoreCheck.CheckLine(step, outcome, messageKey, arguments, answer);
        var built = CredentialStoreCheck.CheckLine.builder()
                .step(step)
                .outcome(outcome)
                .messageKey(messageKey)
                .arguments(arguments)
                .answer(answer)
                .build();

        assertEquals(constructed, built, "the builder builds the same check line");
    }

    @Test
    @DisplayName("the check line builder refuses a missing step like the constructor")
    void checkLineBuilderRefusesMissingStep() {
        var refusal = assertThrows(NullPointerException.class, () -> CredentialStoreCheck.CheckLine.builder()
                .outcome(Generators.enumValues(CredentialStoreCheck.Outcome.class).next())
                .messageKey(CredentialStoreCheck.MESSAGE_KEY_PREFIX + Generators.letterStrings(1, 20).next())
                .arguments(List.of(Generators.letterStrings(1, 20).next()))
                .answer(Generators.letterStrings(1, 40).next())
                .build());

        assertEquals("step", refusal.getMessage(), "the refusal names the missing step");
    }
}
