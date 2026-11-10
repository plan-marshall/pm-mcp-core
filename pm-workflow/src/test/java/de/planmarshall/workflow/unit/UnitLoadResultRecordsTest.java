/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;

/**
 * Proves that the builder of {@link UnitLoadResult.Refusal} builds the value of the canonical constructor.
 */
@EnableGeneratorController
@DisplayName("Unit load result refusal record builder")
class UnitLoadResultRecordsTest {

    @Test
    @DisplayName("Refusal builder builds the canonical constructor's value")
    void refusalBuilderEqualsConstructor() {
        var code = UnitLoadResult.CONFIG_INVALID;
        var file = Generators.letterStrings(1, 24).next();
        var line = Generators.integers(1, 500).next();
        var message = Generators.letterStrings(1, 60).next();
        var constructed = new UnitLoadResult.Refusal(code, file, line, message);
        var built = UnitLoadResult.Refusal.builder()
                .code(code)
                .file(file)
                .line(line)
                .message(message)
                .build();

        assertEquals(constructed, built, "the builder builds the same load refusal");
    }
}
