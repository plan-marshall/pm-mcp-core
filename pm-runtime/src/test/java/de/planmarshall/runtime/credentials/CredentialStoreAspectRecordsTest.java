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
import java.time.Instant;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.config.Consent;
import de.planmarshall.core.config.ConsentKind;

/**
 * Proves that the builder of {@link CredentialStoreAspect.Transition} builds the value its canonical constructor
 * builds, including the null refusal for the next state. Every value is generated; the consent kind and channel are
 * the contract pair a remembered store check carries.
 */
@EnableGeneratorController
@DisplayName("CredentialStoreAspect record builder")
class CredentialStoreAspectRecordsTest {

    @Test
    @DisplayName("the transition builder builds the transition the constructor builds")
    void transitionBuilderEqualsConstructor() {
        var next = Generators.enumValues(CredentialStoreAspect.State.class).next();
        var reason = Generators.letterStrings(1, 20).next();
        var detail = Generators.letterStrings(1, 40).next();
        var toRecord = Consent.machine(ConsentKind.CHECK_CREDENTIAL_STORE,
                Instant.ofEpochMilli(Generators.longs(0, 1_900_000_000_000L).next()), Consent.Channel.CLI, null);
        var lines = List.of(new CredentialStoreCheck.CheckLine(
                Generators.enumValues(CredentialStoreCheck.Step.class).next(),
                Generators.enumValues(CredentialStoreCheck.Outcome.class).next(),
                CredentialStoreCheck.MESSAGE_KEY_PREFIX + Generators.letterStrings(1, 20).next(),
                List.of(Generators.letterStrings(1, 20).next()), Generators.letterStrings(1, 40).next()));
        var moveEntries = OptionalInt.of(Generators.integers(0, 10_000).next());

        var constructed = new CredentialStoreAspect.Transition(next, reason, detail, toRecord, lines, moveEntries);
        var built = CredentialStoreAspect.Transition.builder()
                .next(next)
                .reason(reason)
                .detail(detail)
                .toRecord(toRecord)
                .lines(lines)
                .moveEntries(moveEntries)
                .build();

        assertEquals(constructed, built, "the builder builds the same transition");
    }

    @Test
    @DisplayName("the transition builder refuses a missing next state like the constructor")
    void transitionBuilderRefusesMissingNext() {
        var refusal = assertThrows(NullPointerException.class, () -> CredentialStoreAspect.Transition.builder()
                .reason(Generators.letterStrings(1, 20).next())
                .detail(Generators.letterStrings(1, 40).next())
                .lines(List.of())
                .moveEntries(OptionalInt.of(Generators.integers(0, 10_000).next()))
                .build());

        assertEquals("next", refusal.getMessage(), "the refusal names the missing next state");
    }
}
