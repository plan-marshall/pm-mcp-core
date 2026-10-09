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
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("SonarIssueKey")
class SonarIssueKeyTest {

    @ParameterizedTest
    @ValueSource(strings = {"AYx3kQ9hZ1mB-7tPq_2c", "01fc972e-2a3c-433e-bcae-0bd7f88f5123", "k"})
    @DisplayName("keeps a key as Sonar reports it")
    void accepts(String value) {
        assertEquals(value, new SonarIssueKey(value).value());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "AYx3 kQ9h", "AYx3\tkQ9h", "AYx3\nkQ9h", "AYx3\u0000kQ9h", "AYx3\u00a0kQ9h", "AYx3\u2007kQ9h",
            "AYx3\u202fkQ9h"})
    @DisplayName("refuses a key that is empty or holds white space or a control character")
    void refuses(String value) {
        assertThrows(IllegalArgumentException.class, () -> new SonarIssueKey(value));
    }

    @Test
    @DisplayName("refuses no value")
    void refusesNull() {
        assertThrows(NullPointerException.class, () -> new SonarIssueKey(null));
    }
}
