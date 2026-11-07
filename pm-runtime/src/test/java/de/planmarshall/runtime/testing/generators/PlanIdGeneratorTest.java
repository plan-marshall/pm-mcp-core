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

@DisplayName("Plan id generator")
@EnableGeneratorController
class PlanIdGeneratorTest {

    @Test
    @DisplayName("Should yield a number, a hyphen and a short lower-case word")
    void shouldYieldTheShapeOfAPlanId() {
        var planId = RuntimeGenerators.planIds().next();

        // The pattern is the contract of the generator: 1 to 999, a hyphen, 2 to 10 lower-case letters
        assertTrue(planId.matches("([1-9]\\d{0,2})-[a-z]{2,10}"), "The plan id is a number, hyphen and word");
    }

    @Test
    @DisplayName("Should report the generated type")
    void shouldReportTheGeneratedType() {
        assertEquals(String.class, new PlanIdGenerator().getType(), "The generator names the string type");
    }
}
