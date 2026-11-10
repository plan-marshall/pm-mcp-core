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

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The builder of {@link HarnessUsage} builds the value the canonical constructor builds, and absent input tokens are
 * refused with the same refusal.
 */
@DisplayName("Harness usage builder")
class HarnessUsageBuilderContractTest {

    @Test
    @DisplayName("the builder builds the constructor's value and refuses absent input tokens")
    void shouldBuildLikeTheConstructorAndRefuseAbsentInputTokens() {
        var canonical = new HarnessUsage(Measurement.measured(1_200), Measurement.measured(300),
                Measurement.measured(400), Measurement.notApplicable(), Measurement.notApplicable(),
                Measurement.measured(7), new BigDecimal("0.25"), null);
        var built = HarnessUsage.builder().inputTokens(canonical.inputTokens())
                .outputTokens(canonical.outputTokens()).cacheReadInputTokens(canonical.cacheReadInputTokens())
                .cacheCreationInputTokens(canonical.cacheCreationInputTokens())
                .reasoningTokens(canonical.reasoningTokens()).toolUses(canonical.toolUses())
                .costUsd(canonical.costUsd()).unmeasuredReason(canonical.unmeasuredReason()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new HarnessUsage(null, Measurement.measured(300), Measurement.measured(400),
                                    Measurement.notApplicable(), Measurement.notApplicable(), Measurement.measured(7),
                                    null, null),
                            "the constructor refuses absent input tokens");
                    var fromBuilder = assertThrows(NullPointerException.class,
                            () -> HarnessUsage.builder().outputTokens(Measurement.measured(300))
                                    .cacheReadInputTokens(Measurement.measured(400))
                                    .cacheCreationInputTokens(Measurement.notApplicable())
                                    .reasoningTokens(Measurement.notApplicable()).toolUses(Measurement.measured(7))
                                    .build(),
                            "the builder refuses absent input tokens");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse absent input tokens with the same message");
                });
    }
}
