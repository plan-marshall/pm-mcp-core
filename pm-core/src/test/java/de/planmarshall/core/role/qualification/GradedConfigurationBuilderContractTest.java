/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.role.qualification;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.role.RoleClass;

/**
 * The builder of {@link GradedConfiguration} builds the value the canonical constructor builds, and a missing harness is
 * refused with the same refusal.
 */
@EnableGeneratorController
@DisplayName("Graded configuration builder")
class GradedConfigurationBuilderContractTest {

    @Test
    @DisplayName("the builder builds the constructor's value and refuses an absent harness alike")
    void shouldBuildLikeTheConstructorAndRefuseAnAbsentHarnessAlike() {
        var canonical = new GradedConfiguration("claude", "1.4", "opus", "high", List.of("pm_wait"),
                RoleClass.READ_ONLY,
                Map.of("skill://pm/role/review-code/SKILL.md", "%064x".formatted(Generators.longs().next())));
        var built = GradedConfiguration.builder().harness(canonical.harness())
                .harnessVersion(canonical.harnessVersion()).model(canonical.model()).effort(canonical.effort())
                .tools(canonical.tools()).roleClass(canonical.roleClass())
                .skillDigests(canonical.skillDigests()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new GradedConfiguration(null, canonical.harnessVersion(), canonical.model(),
                                    canonical.effort(), canonical.tools(), canonical.roleClass(),
                                    canonical.skillDigests()),
                            "the constructor refuses a null harness");
                    var fromBuilder = assertThrows(NullPointerException.class,
                            () -> GradedConfiguration.builder().harnessVersion(canonical.harnessVersion())
                                    .model(canonical.model()).effort(canonical.effort()).tools(canonical.tools())
                                    .roleClass(canonical.roleClass())
                                    .skillDigests(canonical.skillDigests()).build(),
                            "the builder refuses a missing harness");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent harness with the same message");
                });
    }
}
