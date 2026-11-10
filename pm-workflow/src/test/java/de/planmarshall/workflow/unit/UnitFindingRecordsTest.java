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
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;

/**
 * Proves that the builder of {@link UnitFinding} builds the value of the canonical constructor.
 */
@EnableGeneratorController
@DisplayName("Unit finding record builder")
class UnitFindingRecordsTest {

    @Test
    @DisplayName("UnitFinding builder builds the canonical constructor's value")
    void unitFindingBuilderEqualsConstructor() {
        var kind = UnitFinding.Kind.RULE;
        var code = UnitFinding.FORMAT_OUTDATED;
        var file = Generators.letterStrings(1, 24).next();
        var line = Generators.integers(1, 500).next();
        var message = Generators.letterStrings(1, 60).next();
        var constructed = new UnitFinding(kind, code, file, line, message);
        var built = UnitFinding.builder()
                .kind(kind)
                .code(code)
                .file(file)
                .line(line)
                .message(message)
                .build();

        assertEquals(constructed, built, "the builder builds the same unit finding");
    }

    @Test
    @DisplayName("UnitFinding builder refuses a missing kind")
    void unitFindingBuilderRefusesMissingKind() {
        var exception = assertThrows(NullPointerException.class, () -> UnitFinding.builder()
                .code(UnitFinding.FORMAT_OUTDATED)
                .file(Generators.letterStrings(1, 24).next())
                .line(Generators.integers(1, 500).next())
                .message(Generators.letterStrings(1, 60).next())
                .build());

        assertEquals("kind", exception.getMessage(), "the builder names the missing kind");
    }
}
