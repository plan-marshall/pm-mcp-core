/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.build;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The builder of {@link CoverageMetrics} builds the value the canonical constructor builds.
 */
@DisplayName("Coverage metrics builder")
class CoverageMetricsBuilderContractTest {

    @Test
    @DisplayName("the builder builds the constructor's value")
    void shouldBuildLikeTheConstructor() {
        var canonical = new CoverageMetrics(80.0, 70.0, 75.0, 60.0, 65.0);
        var built = CoverageMetrics.builder().instruction(canonical.instruction()).branch(canonical.branch())
                .line(canonical.line()).function(canonical.function()).statement(canonical.statement()).build();

        assertEquals(canonical, built, "the builder builds the constructor's value");
    }
}
