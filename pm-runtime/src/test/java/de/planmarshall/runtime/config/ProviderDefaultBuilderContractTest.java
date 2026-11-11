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
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The builder of {@link ProviderDefault} builds the value the canonical constructor builds, and a missing component is
 * refused with the same refusal.
 */
@EnableGeneratorController
@DisplayName("Provider default builder")
class ProviderDefaultBuilderContractTest {

    private final Instant at = Instant.ofEpochSecond(
            Generators.longs(1_600_000_000L, 1_900_000_000L).next());

    @Test
    @DisplayName("the builder builds the constructor's value and refuses alike")
    void shouldBuildLikeTheConstructorAndRefuseAlike() {
        var verification = new VerificationResult(true, at,
                List.of(new VerificationResult.Check("origin", true)));

        var canonical = new ProviderDefault(true, ProviderVariant.NATIVE, null, verification);
        var built = ProviderDefault.builder().available(canonical.available()).variant(canonical.variant())
                .credentialKey(canonical.credentialKey()).verification(canonical.verification()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var fromConstructor = assertThrows(IllegalArgumentException.class,
                            () -> new ProviderDefault(true, ProviderVariant.NATIVE, null, null),
                            "the constructor refuses an available default without a result");
                    var incomplete = ProviderDefault.builder().available(true).variant(ProviderVariant.NATIVE);
                    var fromBuilder = assertThrows(IllegalArgumentException.class, incomplete::build,
                            "the builder refuses it alike");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse with the same message");
                });
    }
}
