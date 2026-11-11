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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The builder of {@link CoverageItem} builds the value the canonical constructor builds, and a missing required
 * component is refused with the same refusal.
 */
@EnableGeneratorController
@DisplayName("Coverage item builder")
class CoverageItemBuilderContractTest {

    @Test
    @DisplayName("the builder builds the constructor's value and refuses alike")
    void shouldBuildLikeTheConstructorAndRefuseAlike() {
        var name = Generators.letterStrings(1, 10).next();

        var canonical = new CoverageItem(name, null, 75.0, null, List.of());
        var built = CoverageItem.builder().name(canonical.name()).file(canonical.file())
                .line(canonical.line()).branch(canonical.branch()).missedMethods(canonical.missedMethods()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new CoverageItem(null, null, 75.0, null, List.of()),
                            "the constructor refuses a null name");
                    var fromBuilder = assertThrows(NullPointerException.class,
                            () -> CoverageItem.builder().line(75.0).missedMethods(List.of()).build(),
                            "the builder refuses a missing name");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent name with the same message");
                });
    }
}
