/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.workflow.ast.CallValue;

/**
 * Proves that the builder of {@link CompositionRule} builds the value of its canonical constructor.
 */
@EnableGeneratorController
@DisplayName("CompositionRule builder")
class CompositionRuleRecordsTest {

    @Test
    @DisplayName("the builder builds the composition rule the constructor builds")
    void compositionRuleBuilderEqualsConstructor() {
        var id = Generators.letterStrings(1, 20).next();
        var condition = new CompositionRule.Empty(Generators.letterStrings(1, 20).next());
        var prunes = List.of(new CompositionRule.Prune(PrunedElement.Kind.STEP,
                Generators.letterStrings(1, 20).next(), PruneReason.POSTURE));
        var bindings = List.of(new CompositionRule.Binding(Generators.letterStrings(1, 20).next(),
                Generators.letterStrings(1, 20).next(), new CallValue.IntValue(Generators.integers(0, 100).next())));

        var constructed = new CompositionRule(id, condition, prunes, bindings);
        var built = CompositionRule.builder()
                .id(id)
                .when(condition)
                .prunes(prunes)
                .bindings(bindings)
                .build();

        assertEquals(constructed, built, "the builder builds the same composition rule");
    }

    @Test
    @DisplayName("the builder refuses a missing rule id")
    void compositionRuleBuilderRefusesMissingId() {
        var exception = assertThrows(NullPointerException.class, () -> CompositionRule.builder()
                .when(new CompositionRule.Empty(Generators.letterStrings(1, 20).next()))
                .prunes(List.of())
                .bindings(List.of())
                .build());

        assertEquals("id", exception.getMessage(), "the builder names the missing rule id");
    }
}
