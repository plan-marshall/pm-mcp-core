/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("AnalysisStatus")
class AnalysisStatusTest {

    @ParameterizedTest(name = "{0} is {1} on the wire")
    @CsvSource({
            "RUNNING, running",
            "PASSED, passed",
            "GATE_FAILED, gate_failed",
            "FINDINGS, findings",
            "UNDECIDABLE, undecidable",
            "NOT_RUN, not_run"})
    @DisplayName("carries the value of the wait dimension")
    void wireNames(AnalysisStatus status, String wireName) {
        assertEquals(wireName, status.wireName());
    }

    @Test
    @DisplayName("is closed at six values")
    void closed() {
        assertEquals(6, AnalysisStatus.values().length);
    }
}
