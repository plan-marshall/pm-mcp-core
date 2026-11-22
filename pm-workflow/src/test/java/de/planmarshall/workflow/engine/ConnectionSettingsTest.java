/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.engine;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// Every duration in this class is a contract: the 30-second margin and the values the refusal message renders.
@DisplayName("ConnectionSettings")
class ConnectionSettingsTest {

    @Test
    @DisplayName("A bounded wait less than the margin below the idle timeout is refused with the whole message")
    void boundedWaitCloserThanTheMarginIsRefused() {
        var refusal = assertThrows(IllegalArgumentException.class,
                () -> new ConnectionSettings(Duration.ofSeconds(60), Duration.ofSeconds(80), false),
                "a wait 20 s below the idle timeout is refused");

        assertEquals("the bounded wait PT1M is not at least PT30S below the idle timeout PT1M20S of a harness whose"
                + " progress resets no timer", refusal.getMessage(), "the refusal names the harness and the margin");
    }

    @Test
    @DisplayName("A bounded wait exactly the margin below the idle timeout is admitted")
    void boundedWaitExactlyTheMarginBelowIsAdmitted() {
        assertDoesNotThrow(() -> new ConnectionSettings(Duration.ofSeconds(60), Duration.ofSeconds(90), false),
                "a wait exactly 30 s below the idle timeout passes");
    }
}
