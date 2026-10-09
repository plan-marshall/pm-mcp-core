/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Closed vocabularies of the primitive SPI")
class SpiVocabularyTest {

    @Test
    @DisplayName("the outcome classes are the six of the contract, in its order")
    void outcomeClasses() {
        var names = Arrays.stream(OutcomeClass.values()).map(Enum::name).toList();

        assertEquals(List.of("SUCCESS", "SKIP", "FAILURE", "INDETERMINATE", "UNSETTLED", "STEPPED_OUT"), names);
    }

    @Test
    @DisplayName("the awaited events are the eight of the contract, in its order")
    void awaitedEvents() {
        var names = Arrays.stream(AwaitedEvent.values()).map(Enum::name).toList();

        assertEquals(List.of("NONE", "JOB_SETTLED", "TASK_SETTLED", "OPERATOR_WAIVER", "MODEL_SUBMIT", "TIME",
                "EXTERNAL_STATE", "QUESTION_ANSWER"), names);
    }

    @Test
    @DisplayName("the cycle measures are the four of the contract, in its order")
    void cycleMeasures() {
        var names = Arrays.stream(CycleMeasure.values()).map(Enum::name).toList();

        assertEquals(List.of("NONE", "STEP_LIST", "ARCHIVE_STEPS", "ATTEMPT_CAP"), names);
    }
}
