/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.setup;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.config.SetupRun;

/**
 * The {@code setup_run} a setup call records from server facts and the entry (State Catalogue § Project Setup, {@code
 * setup.entry}): an interrupted first run keeps its recorded run, a checkout without {@code .marshall/config/} is a
 * first run, an aspect entry is that aspect, and every other call is a reconcile run.
 */
@DisplayName("Setup run derivation")
class SetupRunDerivationTest {

    @Test
    @DisplayName("Should keep the recorded run of an interrupted first run")
    void interruptedFirstRunKeepsItsRun() {
        var run = SetupRunDerivation.derive(SetupRun.FIRST_RUN, true, SetupRun.FIRST_RUN);

        assertEquals(SetupRun.FIRST_RUN, run, "An interrupted first run stays a first run");
    }

    @Test
    @DisplayName("Should derive the first run from a checkout without the config directory")
    void absentConfigDirectoryIsTheFirstRun() {
        var run = SetupRunDerivation.derive(SetupRun.FIRST_RUN, false, null);

        assertEquals(SetupRun.FIRST_RUN, run, "A checkout without .marshall/config is a first run");
    }

    @Test
    @DisplayName("Should continue the first run at the integrations entry")
    void integrationsEntryContinuesTheFirstRun() {
        var run = SetupRunDerivation.derive(SetupRun.INTEGRATIONS, true, null);

        assertEquals(SetupRun.FIRST_RUN, run, "The integrations entry continues the first run");
    }

    @Test
    @DisplayName("Should derive a reconcile run from a configured checkout")
    void configuredCheckoutIsAReconcileRun() {
        var run = SetupRunDerivation.derive(SetupRun.RECONCILE, true, null);

        assertEquals(SetupRun.RECONCILE, run, "A configured checkout reconciles");
    }

    @Test
    @DisplayName("Should derive the one aspect of an aspect entry")
    void aspectEntryIsOneAspect() {
        var run = SetupRunDerivation.derive(SetupRun.ASPECT, true, null);

        assertEquals(SetupRun.ASPECT, run, "An aspect entry runs one aspect");
    }

    @Test
    @DisplayName("Should keep the precedence of the missing directory and the recorded run")
    void appliesThePrecedence() {
        assertAll(
                () -> assertEquals(SetupRun.FIRST_RUN,
                        SetupRunDerivation.derive(SetupRun.ASPECT, false, null),
                        "A missing config directory wins over the aspect entry"),
                () -> assertEquals(SetupRun.RECONCILE,
                        SetupRunDerivation.derive(SetupRun.INTEGRATIONS, true, SetupRun.RECONCILE),
                        "A recorded run wins over the integrations entry"));
    }

    @Test
    @DisplayName("Should refuse the integrations entry as a recorded run")
    void refusesIntegrationsAsRecorded() {
        assertThrows(IllegalArgumentException.class,
                () -> SetupRunDerivation.derive(SetupRun.FIRST_RUN, true, SetupRun.INTEGRATIONS),
                "The integrations entry is never a recorded setup_run");
    }
}
