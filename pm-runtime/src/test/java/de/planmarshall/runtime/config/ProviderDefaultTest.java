/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.config;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * One provider of the machine's defaults (Store Schemas § Machine Provider Defaults, ADR {@code runtime-config/001}): a
 * provider is recorded available only with a passed verification result, and recorded not available on the operator's
 * word with either a failed result or none.
 * <p>
 * The check names are the verification's contract; the credential key and the instant are generated.
 */
@EnableGeneratorController
@DisplayName("Provider default")
class ProviderDefaultTest {

    private final Instant at = Instant.ofEpochSecond(Generators.longs(1_600_000_000L, 1_900_000_000L).next());
    private final String credentialKey = Generators.letterStrings(3, 12).next().toLowerCase(Locale.ROOT);

    private VerificationResult passed() {
        return new VerificationResult(true, at, List.of(new VerificationResult.Check("origin", true)));
    }

    private VerificationResult failed() {
        return new VerificationResult(false, at, List.of(new VerificationResult.Check("origin", false)));
    }

    @Test
    @DisplayName("an available provider is refused without a verification result")
    void availableWithoutResult() {
        var refused = assertThrows(IllegalArgumentException.class,
                () -> new ProviderDefault(true, ProviderVariant.NATIVE, null, null),
                "a provider recorded available carries a passed result");

        assertTrue(refused.getMessage().contains("verification"), "the refusal must name the verification");
    }

    @Test
    @DisplayName("an available provider is refused with a failed verification result")
    void availableWithFailedResult() {
        var result = failed();

        var refused = assertThrows(IllegalArgumentException.class,
                () -> new ProviderDefault(true, ProviderVariant.NATIVE, null, result),
                "a failed result cannot make a provider available");

        assertTrue(refused.getMessage().contains("verification"), "the refusal must name the verification");
    }

    @Test
    @DisplayName("a provider recorded unavailable on the operator's word accepts a failed result and none")
    void unavailableAcceptsBoth() {
        var withFailed = new ProviderDefault(false, ProviderVariant.NATIVE, null, failed());
        var without = new ProviderDefault(false, ProviderVariant.NATIVE, null, null);

        assertAll(
                () -> assertFalse(withFailed.available(), "a failed result records the provider not available"),
                () -> assertNull(without.verification(), "the operator's word carries no result"));
    }

    @Test
    @DisplayName("the verified and the unavailable form are the two the rule leaves")
    void forms() {
        var verified = ProviderDefault.verified(passed(), ProviderVariant.NATIVE).withCredentialKey(credentialKey);
        var unavailable = ProviderDefault.unavailable(ProviderVariant.NATIVE);

        assertAll(
                () -> assertTrue(verified.available(), "a verified provider is available"),
                () -> assertEquals(passed(), verified.verification(), "the verified form carries its result"),
                () -> assertEquals(credentialKey, verified.credentialKey(), "the key is the one given"),
                () -> assertFalse(unavailable.available(), "the unavailable form records not available"),
                () -> assertNull(unavailable.verification(), "the unavailable form carries no result"));
    }

    @Test
    @DisplayName("the verified form refuses a failed result")
    void verifiedRefusesFailed() {
        var result = failed();

        assertThrows(IllegalArgumentException.class,
                () -> ProviderDefault.verified(result, ProviderVariant.NATIVE),
                "a failed result cannot take the verified form");
    }
}
