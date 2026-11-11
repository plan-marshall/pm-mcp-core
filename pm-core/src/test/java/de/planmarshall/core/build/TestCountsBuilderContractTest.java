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

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The builder of {@link TestCounts.Measured} builds the value the canonical constructor builds.
 */
@EnableGeneratorController
@DisplayName("Measured test counts builder")
class TestCountsBuilderContractTest {

    @Test
    @DisplayName("the builder builds the constructor's value")
    void shouldBuildLikeTheConstructor() {
        var runner = Generators.letterStrings(3, 10).next();

        var canonical = new TestCounts.Measured(3, 1, 2, 6, Map.of(runner, 4));
        var built = TestCounts.Measured.builder().passed(canonical.passed()).failed(canonical.failed())
                .skipped(canonical.skipped()).total(canonical.total()).byRunner(canonical.byRunner()).build();

        assertEquals(canonical, built, "the builder builds the constructor's value");
    }
}
