/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.store;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The builder of {@link BuildTimings.KeyTimings} builds the value the canonical constructor builds, and an absent clean
 * baseline is refused with the same refusal.
 */
@DisplayName("Build timings key timings builder")
class BuildTimingsBuilderContractTest {

    private static final Instant AT = Instant.parse("2026-10-17T10:00:00Z");

    @Test
    @DisplayName("the key timings builder builds the constructor's value and refuses an absent clean baseline")
    void shouldBuildKeyTimingsLikeTheConstructorAndRefuseAnAbsentCleanBaseline() {
        var canonical = new BuildTimings.KeyTimings(new BuildTimings.Baseline(120L, 3), BuildTimings.Baseline.empty(),
                new BuildTimings.Baseline(90L, 1), 300L, AT);
        var built = BuildTimings.KeyTimings.builder().clean(canonical.clean()).poisoned(canonical.poisoned())
                .cold(canonical.cold()).escalationSeconds(canonical.escalationSeconds())
                .lastObservedAt(canonical.lastObservedAt()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var poisoned = BuildTimings.Baseline.empty();
                    var cold = BuildTimings.Baseline.empty();
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new BuildTimings.KeyTimings(null, poisoned, cold, null, AT),
                            "the constructor refuses a null clean baseline");
                    var incomplete = BuildTimings.KeyTimings.builder()
                            .poisoned(BuildTimings.Baseline.empty()).cold(BuildTimings.Baseline.empty());
                    var fromBuilder = assertThrows(NullPointerException.class, incomplete::build,
                            "the builder refuses a missing clean baseline");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent clean baseline with the same message");
                });
    }
}
