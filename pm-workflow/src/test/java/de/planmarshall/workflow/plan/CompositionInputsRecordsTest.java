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

import java.util.List;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.planmarshall.workflow.testing.generators.WorkflowGenerators;

/**
 * Proves that the builder of {@link CompositionInputs} builds the value of its canonical constructor.
 */
@EnableGeneratorController
@DisplayName("CompositionInputs builder")
class CompositionInputsRecordsTest {

    @Test
    @DisplayName("the builder builds the composition inputs the constructor builds")
    void compositionInputsBuilderEqualsConstructor() {
        var configHash = Generators.letterStrings(1, 40).next();
        var templates = List.of(WorkflowGenerators.unitReferences().next());
        var subUnits = List.of(WorkflowGenerators.unitReferences().next());
        var rulesRevision = Generators.letterStrings(1, 20).next();
        var rulesDigest = Generators.letterStrings(1, 64).next();
        var planFactsDigest = Generators.letterStrings(1, 64).next();
        var realizedFootprintDigest = Generators.letterStrings(1, 64).next();
        var changeType = Generators.letterStrings(1, 20).next();
        var providersDigest = Generators.letterStrings(1, 64).next();

        var constructed = new CompositionInputs(configHash, templates, subUnits, rulesRevision, rulesDigest,
                planFactsDigest, realizedFootprintDigest, changeType, providersDigest);
        var built = CompositionInputs.builder()
                .configHash(configHash)
                .templates(templates)
                .subUnits(subUnits)
                .compositionRulesRevision(rulesRevision)
                .compositionRulesDigest(rulesDigest)
                .planFactsDigest(planFactsDigest)
                .realizedFootprintDigest(realizedFootprintDigest)
                .changeType(changeType)
                .providersDigest(providersDigest)
                .build();

        assertEquals(constructed, built, "the builder builds the same composition inputs");
    }

    @Test
    @DisplayName("the builder refuses a missing configuration hash")
    void compositionInputsBuilderRefusesMissingConfigHash() {
        var exception = assertThrows(NullPointerException.class, () -> CompositionInputs.builder()
                .templates(List.of())
                .subUnits(List.of())
                .compositionRulesRevision(Generators.letterStrings(1, 20).next())
                .compositionRulesDigest(Generators.letterStrings(1, 64).next())
                .planFactsDigest(Generators.letterStrings(1, 64).next())
                .build());

        assertEquals("configHash", exception.getMessage(), "the builder names the missing hash");
    }
}
