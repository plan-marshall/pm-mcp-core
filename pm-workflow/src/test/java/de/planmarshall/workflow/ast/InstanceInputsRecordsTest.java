/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.ast;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Proves that the builders of {@link InstanceInputs} and its nested {@link InstanceInputs.UnitRef} build the values
 * their canonical constructors build. Every value is generated, and the unit origins are the specification's
 * {@code default} and {@code project} wire names.
 */
@EnableGeneratorController
@DisplayName("InstanceInputs builders")
class InstanceInputsRecordsTest {

    @Test
    @DisplayName("the builder builds the instance inputs the constructor builds")
    void instanceInputsBuilderEqualsConstructor() {
        var configHash = Generators.letterStrings(1, 40).next();
        var templates = List.of(new InstanceInputs.UnitRef(Generators.letterStrings(1, 20).next(),
                Generators.letterStrings(1, 20).next(), Generators.letterStrings(1, 64).next(), "default"));
        var subUnits = List.of(new InstanceInputs.UnitRef(Generators.letterStrings(1, 20).next(), null,
                Generators.letterStrings(1, 64).next(), "project"));
        var compositionRules = new InstanceInputs.CompositionRules(Generators.letterStrings(1, 20).next(),
                Generators.letterStrings(1, 64).next());
        var planFactsDigest = Generators.letterStrings(1, 64).next();
        var realizedFootprintDigest = Generators.letterStrings(1, 64).next();
        var changeType = Generators.letterStrings(1, 20).next();
        var providersDigest = Generators.letterStrings(1, 64).next();

        var constructed = new InstanceInputs(configHash, templates, subUnits, compositionRules, planFactsDigest,
                realizedFootprintDigest, changeType, providersDigest);
        var built = InstanceInputs.builder()
                .configHash(configHash)
                .templates(templates)
                .subUnits(subUnits)
                .compositionRules(compositionRules)
                .planFactsDigest(planFactsDigest)
                .realizedFootprintDigest(realizedFootprintDigest)
                .changeType(changeType)
                .providersDigest(providersDigest)
                .build();

        assertEquals(constructed, built, "the builder builds the same instance inputs");
    }

    @Test
    @DisplayName("the builder builds the unit reference the constructor builds")
    void unitRefBuilderEqualsConstructor() {
        var unit = Generators.letterStrings(1, 20).next();
        var file = Generators.letterStrings(1, 20).next();
        var digest = Generators.letterStrings(1, 64).next();
        var origin = "project";

        var constructed = new InstanceInputs.UnitRef(unit, file, digest, origin);
        var built = InstanceInputs.UnitRef.builder()
                .unit(unit)
                .file(file)
                .digest(digest)
                .origin(origin)
                .build();

        assertEquals(constructed, built, "the builder builds the same unit reference");
    }
}
