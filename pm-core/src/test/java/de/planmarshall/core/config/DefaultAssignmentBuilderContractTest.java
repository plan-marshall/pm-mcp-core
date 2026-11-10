/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.config;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The builder of {@link DefaultAssignment.DefaultRole} builds the value the canonical constructor builds, and a
 * missing required component is refused with the same refusal.
 */
@EnableGeneratorController
@DisplayName("Default role builder")
class DefaultAssignmentBuilderContractTest {

    @Test
    @DisplayName("the builder builds the constructor's value and refuses alike")
    void shouldBuildLikeTheConstructorAndRefuseAlike() {
        var preferredHarness = Generators.letterStrings(1, 10).next();
        var advisory = Generators.letterStrings(1, 10).next();

        var canonical = new DefaultAssignment.DefaultRole("standard", null, preferredHarness, advisory);
        var built = DefaultAssignment.DefaultRole.builder().tier(canonical.tier()).effort(canonical.effort())
                .preferredHarness(canonical.preferredHarness()).advisory(canonical.advisory()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new DefaultAssignment.DefaultRole(null, null, preferredHarness, advisory),
                            "the constructor refuses a null tier");
                    var incomplete = DefaultAssignment.DefaultRole.builder().preferredHarness(preferredHarness)
                            .advisory(advisory);
                    var fromBuilder = assertThrows(NullPointerException.class, incomplete::build,
                            "the builder refuses a missing tier");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent tier with the same message");
                });
    }
}
