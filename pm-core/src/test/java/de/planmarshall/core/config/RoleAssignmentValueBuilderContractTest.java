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
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The builder of {@link RoleAssignmentValue} builds the value the canonical constructor builds, and a missing
 * required component is refused with the same refusal.
 */
@EnableGeneratorController
@DisplayName("Role assignment value builder")
class RoleAssignmentValueBuilderContractTest {

    @Test
    @DisplayName("the builder builds the constructor's value and refuses alike")
    void shouldBuildLikeTheConstructorAndRefuseAlike() {
        var harness = Generators.letterStrings(2, 10).next().toLowerCase(Locale.ROOT);
        var model = Generators.letterStrings(1, 20).next();

        var canonical = new RoleAssignmentValue(harness, model, EffortSetting.INHERIT, null, null);
        var built = RoleAssignmentValue.builder().harness(canonical.harness()).model(canonical.model())
                .effort(canonical.effort()).effortValue(canonical.effortValue())
                .escalation(canonical.escalation()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new RoleAssignmentValue(null, model, EffortSetting.INHERIT, null, null),
                            "the constructor refuses a null harness");
                    var fromBuilder = assertThrows(NullPointerException.class,
                            () -> RoleAssignmentValue.builder().model(model).effort(EffortSetting.INHERIT).build(),
                            "the builder refuses a missing harness");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent harness with the same message");
                });
    }
}
