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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.config.Consent;
import de.planmarshall.core.config.ConsentKind;
import de.planmarshall.runtime.config.Provider;

/**
 * Proves that the builders of {@link ProviderVerification.CheckLine} and {@link ProviderVerification.Verification}
 * build the values their canonical constructors build, including their null refusals for the check name and the
 * provider. Every value is generated; the consent kind and channel are the contract pair a remembered verification
 * carries.
 */
@EnableGeneratorController
@DisplayName("ProviderVerification record builders")
class ProviderVerificationRecordsTest {

    @Test
    @DisplayName("the check line builder builds the check line the constructor builds")
    void checkLineBuilderEqualsConstructor() {
        var check = Generators.letterStrings(1, 20).next();
        var ok = Generators.booleans().next();
        var messageKey = ProviderVerification.MESSAGE_KEY_PREFIX + Generators.letterStrings(1, 20).next();
        var arguments = List.of(Generators.letterStrings(1, 20).next());
        var answer = Generators.letterStrings(1, 40).next();

        var constructed = new ProviderVerification.CheckLine(check, ok, messageKey, arguments, answer);
        var built = ProviderVerification.CheckLine.builder()
                .check(check)
                .ok(ok)
                .messageKey(messageKey)
                .arguments(arguments)
                .answer(answer)
                .build();

        assertEquals(constructed, built, "the builder builds the same check line");
    }

    @Test
    @DisplayName("the check line builder refuses a missing check like the constructor")
    void checkLineBuilderRefusesMissingCheck() {
        var refusal = assertThrows(NullPointerException.class, () -> ProviderVerification.CheckLine.builder()
                .ok(Generators.booleans().next())
                .messageKey(ProviderVerification.MESSAGE_KEY_PREFIX + Generators.letterStrings(1, 20).next())
                .arguments(List.of(Generators.letterStrings(1, 20).next()))
                .answer(Generators.letterStrings(1, 40).next())
                .build());

        assertEquals("check", refusal.getMessage(), "the refusal names the missing check");
    }

    @Test
    @DisplayName("the verification builder builds the verification the constructor builds")
    void verificationBuilderEqualsConstructor() {
        var provider = Generators.enumValues(Provider.class).next();
        var ran = Generators.booleans().next();
        var lines = List.of(new ProviderVerification.CheckLine(Generators.letterStrings(1, 20).next(),
                Generators.booleans().next(), ProviderVerification.MESSAGE_KEY_PREFIX
                        + Generators.letterStrings(1, 20).next(),
                List.of(Generators.letterStrings(1, 20).next()), Generators.letterStrings(1, 40).next()));
        var toRecord = Consent.machine(ConsentKind.VERIFY_PROVIDERS,
                Instant.ofEpochMilli(Generators.longs(0, 1_900_000_000_000L).next()), Consent.Channel.CLI, null);

        var constructed = new ProviderVerification.Verification(provider, ran, lines, toRecord);
        var built = ProviderVerification.Verification.builder()
                .provider(provider)
                .ran(ran)
                .lines(lines)
                .toRecord(toRecord)
                .build();

        assertEquals(constructed, built, "the builder builds the same verification");
    }

    @Test
    @DisplayName("the verification builder refuses a missing provider like the constructor")
    void verificationBuilderRefusesMissingProvider() {
        var refusal = assertThrows(NullPointerException.class, () -> ProviderVerification.Verification.builder()
                .ran(Generators.booleans().next())
                .lines(List.of())
                .build());

        assertEquals("provider", refusal.getMessage(), "the refusal names the missing provider");
    }
}
