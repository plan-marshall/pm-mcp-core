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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.role.RoleArtifact;

/**
 * Proves that the builder of {@link ActivitySignal} builds the value its canonical constructor builds, including its
 * null refusal for the signal. Every value is generated; the signal and the action are values of their closed
 * vocabularies, and the measurement and threshold are finite and not negative.
 */
@EnableGeneratorController
@DisplayName("ActivitySignal builder")
class ActivitySignalRecordsTest {

    @Test
    @DisplayName("the builder builds the activity signal the constructor builds")
    void activitySignalBuilderEqualsConstructor() {
        var signal = Generators.enumValues(RoleArtifact.OffTrackSignal.class).next();
        var action = Generators.enumValues(RoleArtifact.OffTrackAction.class).next();
        var measurement = Generators.integers(0, 10_000).next() / 8.0;
        var threshold = Generators.integers(0, 10_000).next() / 8.0;

        var constructed = new ActivitySignal(signal, action, measurement, threshold);
        var built = ActivitySignal.builder()
                .signal(signal)
                .action(action)
                .measurement(measurement)
                .threshold(threshold)
                .build();

        assertEquals(constructed, built, "the builder builds the same activity signal");
    }

    @Test
    @DisplayName("the builder refuses a missing signal like the constructor")
    void activitySignalBuilderRefusesMissingSignal() {
        var refusal = assertThrows(NullPointerException.class, () -> ActivitySignal.builder().build());

        assertEquals("signal", refusal.getMessage(), "the refusal names the off-track signal");
    }
}
