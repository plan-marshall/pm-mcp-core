/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.testing.generators;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Failing exit code generator")
@EnableGeneratorController
class FailingExitCodeGeneratorTest {

    @Test
    @DisplayName("Should yield an exit code from 1 to 255")
    void shouldYieldAFailingExitCode() {
        int code = RuntimeGenerators.failures().next();

        assertTrue(code >= FailingExitCodeGenerator.LEAST && code <= FailingExitCodeGenerator.GREATEST,
                "The exit code lies in the failure range");
    }

    @Test
    @DisplayName("Should report the generated type")
    void shouldReportTheGeneratedType() {
        assertEquals(Integer.class, new FailingExitCodeGenerator().getType(), "The generator names the integer type");
    }
}
