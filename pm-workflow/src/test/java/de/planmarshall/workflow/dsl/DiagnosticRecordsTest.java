/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.dsl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;

/**
 * Proves that the builder of {@link Diagnostic} builds the value of the canonical constructor.
 */
@EnableGeneratorController
@DisplayName("Diagnostic record builder")
class DiagnosticRecordsTest {

    @Test
    @DisplayName("Diagnostic builder builds the canonical constructor's value")
    void diagnosticBuilderEqualsConstructor() {
        var rule = RefusalRule.STRUCTURE;
        var resourceName = Generators.letterStrings(1, 24).next();
        var line = Generators.integers(1, 500).next();
        var column = Generators.integers(1, 80).next();
        var message = Generators.letterStrings(1, 60).next();
        var constructed = new Diagnostic(rule, resourceName, line, column, message);
        var built = Diagnostic.builder()
                .rule(rule)
                .resourceName(resourceName)
                .line(line)
                .column(column)
                .message(message)
                .build();

        assertEquals(constructed, built, "the builder builds the same diagnostic");
    }
}
