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
 * Proves that the builder of {@link InstanceHeader} builds the value its canonical constructor builds. Every value is
 * generated, and the inputs are a real nested record whose unit reference names a {@code project} origin.
 */
@EnableGeneratorController
@DisplayName("InstanceHeader builder")
class InstanceHeaderRecordsTest {

    @Test
    @DisplayName("the builder builds the instance header the constructor builds")
    void instanceHeaderBuilderEqualsConstructor() {
        var planId = Generators.letterStrings(1, 20).next();
        var version = Generators.integers(1, 99).next();
        var event = Generators.letterStrings(1, 20).next();
        var inputs = new InstanceInputs(Generators.letterStrings(1, 40).next(),
                List.of(new InstanceInputs.UnitRef(Generators.letterStrings(1, 20).next(), null,
                        Generators.letterStrings(1, 64).next(), "project")),
                List.of(),
                new InstanceInputs.CompositionRules(Generators.letterStrings(1, 20).next(),
                        Generators.letterStrings(1, 64).next()),
                Generators.letterStrings(1, 64).next(), null, null, null);
        var json = Generators.letterStrings(1, 40).next();

        var constructed = new InstanceHeader(planId, version, event, inputs, json);
        var built = InstanceHeader.builder()
                .planId(planId)
                .version(version)
                .event(event)
                .inputs(inputs)
                .json(json)
                .build();

        assertEquals(constructed, built, "the builder builds the same instance header");
    }
}
