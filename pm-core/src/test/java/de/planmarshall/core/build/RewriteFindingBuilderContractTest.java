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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The builder of {@link RewriteFinding} builds the value the canonical constructor builds, and a missing required
 * component is refused with the same refusal.
 */
@EnableGeneratorController
@DisplayName("Rewrite finding builder")
class RewriteFindingBuilderContractTest {

    @Test
    @DisplayName("the builder builds the constructor's value and refuses alike")
    void shouldBuildLikeTheConstructorAndRefuseAlike() {
        var path = "src/" + Generators.letterStrings(3, 10).next() + ".java";
        var recipe = Generators.letterStrings(5, 20).next();

        var canonical = new RewriteFinding(path, 0, 0, recipe, "", RewriteClassification.NEWLY_DETECTED);
        var built = RewriteFinding.builder().path(canonical.path()).line(canonical.line()).column(canonical.column())
                .recipe(canonical.recipe()).message(canonical.message())
                .classification(canonical.classification()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new RewriteFinding(null, 0, 0, recipe, "", RewriteClassification.NEWLY_DETECTED),
                            "the constructor refuses a null path");
                    var fromBuilder = assertThrows(NullPointerException.class,
                            () -> RewriteFinding.builder().line(0).column(0).recipe(recipe).message("")
                                    .classification(RewriteClassification.NEWLY_DETECTED).build(),
                            "the builder refuses a missing path");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent path with the same message");
                });
    }
}
