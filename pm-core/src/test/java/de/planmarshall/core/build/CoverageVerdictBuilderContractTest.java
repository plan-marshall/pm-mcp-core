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

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The builder of {@link CoverageVerdict} builds the value the canonical constructor builds.
 */
@DisplayName("Coverage verdict builder")
class CoverageVerdictBuilderContractTest {

    @Test
    @DisplayName("the builder builds the constructor's value")
    void shouldBuildLikeTheConstructor() {
        var canonical = new CoverageVerdict(false, 80, 75.0, List.of());
        var built = CoverageVerdict.builder().passed(canonical.passed()).threshold(canonical.threshold())
                .overallLine(canonical.overallLine()).lowCoverage(canonical.lowCoverage()).build();

        assertEquals(canonical, built, "the builder builds the constructor's value");
    }
}
