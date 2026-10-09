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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SonarProvider")
class SonarProviderTest {

    @Test
    @DisplayName("the provider id is sonar, lowercase as every identifier")
    void id() {
        assertEquals("sonar", SonarProvider.ID);
        assertTrue(SonarProvider.ID.matches("[a-z][a-z0-9_]*"));
    }
}
