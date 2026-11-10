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


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.planmarshall.workflow.ast.CallValue;

/**
 * Proves that the builder of {@link BoundArgument} builds the value of its canonical constructor.
 */
@EnableGeneratorController
@DisplayName("BoundArgument builder")
class BoundArgumentRecordsTest {

    @Test
    @DisplayName("the builder builds the bound argument the constructor builds")
    void boundArgumentBuilderEqualsConstructor() {
        var value = new CallValue.IntValue(Generators.integers(0, 100).next());
        var source = BoundArgument.Source.TEMPLATE;
        var file = Generators.letterStrings(1, 20).next();
        var waiverId = Generators.letterStrings(1, 20).next();

        var constructed = new BoundArgument(value, source, file, waiverId);
        var built = BoundArgument.builder()
                .value(value)
                .source(source)
                .file(file)
                .waiverId(waiverId)
                .build();

        assertEquals(constructed, built, "the builder builds the same bound argument");
    }

    @Test
    @DisplayName("the builder refuses a missing bound value")
    void boundArgumentBuilderRefusesMissingValue() {
        var builder = BoundArgument.builder()
                .source(BoundArgument.Source.TEMPLATE);
        var exception = assertThrows(NullPointerException.class, builder::build);

        assertEquals("value", exception.getMessage(), "the builder names the missing value");
    }
}
