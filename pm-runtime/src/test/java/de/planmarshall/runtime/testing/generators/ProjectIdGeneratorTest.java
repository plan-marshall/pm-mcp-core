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

@EnableGeneratorController
@DisplayName("Project id generator")
class ProjectIdGeneratorTest {

    @Test
    @DisplayName("Should yield an id of the identifier grammar")
    void shouldYieldAValidProjectId() {
        var projectId = RuntimeGenerators.projectIds().next();

        assertTrue(projectId.matches("[a-z0-9][a-z0-9-]{1,62}[a-z0-9]"),
                "A project id is a valid path-segment identifier");
        assertEquals(String.class, RuntimeGenerators.projectIds().getType(), "The generator names the string type");
    }
}
