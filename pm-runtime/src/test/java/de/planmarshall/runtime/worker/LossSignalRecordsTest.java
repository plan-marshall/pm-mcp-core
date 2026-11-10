/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Proves that the builder of {@link LossSignal} builds the value its canonical constructor builds, including its null
 * refusal for the reason. Every value is generated; the reason is the observed exit, which crosses no deadline.
 */
@EnableGeneratorController
@DisplayName("LossSignal builder")
class LossSignalRecordsTest {

    @Test
    @DisplayName("the builder builds the loss signal the constructor builds")
    void lossSignalBuilderEqualsConstructor() {
        var reason = LossReason.EXIT;
        var generationId = Generators.letterStrings(1, 40).next();
        var measuredAt = Instant.ofEpochSecond(Generators.longs(0, 1_900_000_000).next());
        var deadline = measuredAt.plusSeconds(Generators.longs(0, 600).next());
        var firstCallMade = Generators.booleans().next();
        var leasedTask = Generators.letterStrings(1, 40).next();

        var constructed = new LossSignal(reason, generationId, measuredAt, deadline, firstCallMade, leasedTask);
        var built = LossSignal.builder()
                .reason(reason)
                .generationId(generationId)
                .measuredAt(measuredAt)
                .deadline(deadline)
                .firstCallMade(firstCallMade)
                .leasedTask(leasedTask)
                .build();

        assertEquals(constructed, built, "the builder builds the same loss signal");
    }

    @Test
    @DisplayName("the builder refuses a missing reason like the constructor")
    void lossSignalBuilderRefusesMissingReason() {
        var builder = LossSignal.builder();
        var refusal = assertThrows(NullPointerException.class, builder::build);

        assertEquals("reason", refusal.getMessage(), "the refusal names the loss reason");
    }
}
