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

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Proves that the builder of {@link StartFailureState} builds the value its canonical constructor builds. The state
 * refuses no component for null, so there is no refusal to mirror; it validates the hold and the probe together, and
 * the built value is a holding state with its probe due and in flight.
 */
@EnableGeneratorController
@DisplayName("StartFailureState builder")
class StartFailureStateRecordsTest {

    @Test
    @DisplayName("the builder builds the start failure state the constructor builds")
    void startFailureStateBuilderEqualsConstructor() {
        var consecutiveFailures = Generators.integers(1, 10).next();
        var held = true;
        var probeDue = Instant.ofEpochSecond(Generators.longs(0, 1_900_000_000).next());
        var probeInFlight = true;
        var lastFailure = Generators.letterStrings(1, 40).next();

        var constructed = new StartFailureState(consecutiveFailures, held, probeDue, probeInFlight, lastFailure);
        var built = StartFailureState.builder()
                .consecutiveFailures(consecutiveFailures)
                .held(held)
                .probeDue(probeDue)
                .probeInFlight(probeInFlight)
                .lastFailure(lastFailure)
                .build();

        assertEquals(constructed, built, "the builder builds the same start failure state");
    }
}
