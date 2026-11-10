/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.TreeMap;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;

/**
 * Proves that the builder of {@link CompositionRecord} builds the value of its canonical constructor.
 */
@EnableGeneratorController
@DisplayName("CompositionRecord builder")
class CompositionRecordRecordsTest {

    @Test
    @DisplayName("the builder builds the composition record the constructor builds")
    void compositionRecordBuilderEqualsConstructor() {
        var inputDigests = new TreeMap<String, String>();
        inputDigests.put(Generators.letterStrings(1, 20).next(), Generators.letterStrings(1, 64).next());
        var recordedAt = Instant.ofEpochMilli(Generators.longs(0, 2_000_000_000L).next());

        var constructed = new CompositionRecord(CompositionEvent.START, CompositionRecord.Result.COMPOSED, 1,
                CompositionEvent.Trigger.SERVER, null, inputDigests, null, recordedAt);
        var built = CompositionRecord.builder()
                .event(CompositionEvent.START)
                .result(CompositionRecord.Result.COMPOSED)
                .version(1)
                .trigger(CompositionEvent.Trigger.SERVER)
                .inputDigests(inputDigests)
                .recordedAt(recordedAt)
                .build();

        assertEquals(constructed, built, "the builder builds the same composition record");
    }

    @Test
    @DisplayName("the builder refuses a missing composition event")
    void compositionRecordBuilderRefusesMissingEvent() {
        var exception = assertThrows(NullPointerException.class, () -> CompositionRecord.builder()
                .result(CompositionRecord.Result.COMPOSED)
                .version(1)
                .trigger(CompositionEvent.Trigger.SERVER)
                .inputDigests(new TreeMap<>())
                .recordedAt(Instant.EPOCH)
                .build());

        assertEquals("event", exception.getMessage(), "the builder names the missing event");
    }
}
