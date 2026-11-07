/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.provider.sonar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.junit.EnableGeneratorController;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The provider id of Sonar. Both literals are contract, so no datum here is generated: the id {@code sonar} is the
 * name the provider registry and the configuration address it by, and the pattern {@code [a-z][a-z0-9_]*} is the
 * identifier grammar every provider id follows.
 */
@EnableGeneratorController
@DisplayName("SonarProvider")
class SonarProviderTest {

    @Test
    @DisplayName("the provider id is sonar, lowercase as every identifier (the id and its grammar are the contract)")
    void id() {
        assertEquals("sonar", SonarProvider.ID, "the provider is addressed by the id sonar");
        assertTrue(SonarProvider.ID.matches("[a-z][a-z0-9_]*"), "the provider id follows the identifier grammar");
    }
}
