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
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The verification result the machine's provider defaults keep (Store Schemas § Machine Provider Defaults): the passed
 * flag is the outcome of the checks, so a result that claims a pass without a check, or beside a failed one, is refused.
 * <p>
 * The check names are the verification's contract; the instant is generated.
 */
@EnableGeneratorController
@DisplayName("Verification result")
class VerificationResultTest {

    private final Instant at = Instant.ofEpochSecond(
            Generators.longs(1_600_000_000L, 1_900_000_000L).next());

    @Test
    @DisplayName("a pass needs at least one check, and every one of them passed")
    void passIsTheOutcomeOfTheChecks() {
        var noCheck = assertThrows(IllegalArgumentException.class,
                () -> new VerificationResult(true, at, List.of()),
                "a pass without a check is refused");
        var failedCheck = assertThrows(IllegalArgumentException.class,
                () -> new VerificationResult(true, at,
                        List.of(new VerificationResult.Check("origin", false))),
                "a pass beside a failed check is refused");

        assertAll(
                () -> assertTrue(noCheck.getMessage().contains("passed"), "the refusal names the flag"),
                () -> assertTrue(failedCheck.getMessage().contains("passed"), "the refusal names the flag"));
    }

    @Test
    @DisplayName("a failure carries a failed check or no check at all")
    void failureCarriesOutcome() {
        var failed = new VerificationResult(false, at,
                List.of(new VerificationResult.Check("origin", false)));
        var notRun = new VerificationResult(false, at, List.of());

        assertAll(
                () -> assertEquals(1, failed.checks().size(), "a failed result keeps its check"),
                () -> assertTrue(notRun.checks().isEmpty(), "a verification that did not run has no check"));
    }
}
