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

import de.planmarshall.workflow.testing.generators.WorkflowGenerators;

/**
 * Proves that the builder of {@link ParamSpec} builds the value its canonical constructor builds. Every value is
 * generated, and the parameter type is a permitted record of the sealed {@link ParamType} interface.
 */
@EnableGeneratorController
@DisplayName("ParamSpec builder")
class ParamSpecRecordsTest {

    @Test
    @DisplayName("the builder builds the parameter spec the constructor builds")
    void paramSpecBuilderEqualsConstructor() {
        var name = Generators.letterStrings(1, 20).next();
        var type = new ParamType.EnumOf(List.of(Generators.letterStrings(1, 10).next()));
        var optional = Generators.booleans().next();
        var location = WorkflowGenerators.sourceLocations().next();

        var constructed = new ParamSpec(name, type, optional, location);
        var built = ParamSpec.builder()
                .name(name)
                .type(type)
                .optional(optional)
                .location(location)
                .build();

        assertEquals(constructed, built, "the builder builds the same parameter spec");
    }
}
