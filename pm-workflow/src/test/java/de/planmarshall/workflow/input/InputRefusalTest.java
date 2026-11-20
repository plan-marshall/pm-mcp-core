/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.input;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A refusal of a value by {@link InputValidator}: the rule id, the echoed value bounded to {@link
 * InputRefusal#MAX_ECHO_LENGTH} characters, and the null-safe factory.
 */
@DisplayName("Input refusal")
class InputRefusalTest {

    @Test
    @DisplayName("a value longer than the echo bound is truncated to it")
    void echoIsTruncated() {
        var refusal = new InputRefusal("rule", "x".repeat(InputRefusal.MAX_ECHO_LENGTH + 10));

        assertEquals(InputRefusal.MAX_ECHO_LENGTH, refusal.inputValue().length(),
                "the echoed value is bounded to the constant");
    }

    @Test
    @DisplayName("a value within the echo bound is kept whole")
    void echoIsKept() {
        assertEquals("short", new InputRefusal("rule", "short").inputValue(),
                "a short value is echoed whole");
    }

    @Test
    @DisplayName("the factory echoes a null value as a marker")
    void nullValueEchoed() {
        assertEquals("<null>", InputRefusal.of(null, "rule").inputValue(),
                "a null value echoes the marker");
    }

    @Test
    @DisplayName("a missing rule id or value is refused")
    void missingComponents() {
        assertAll(
                () -> assertThrows(NullPointerException.class, () -> new InputRefusal(null, "v"),
                        "a null rule id is refused"),
                () -> assertThrows(NullPointerException.class, () -> new InputRefusal("rule", null),
                        "a null value is refused"));
    }
}