/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.config;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The consents one onboarding run answered {@code no} (State Catalogue § Project Onboarding, {@code
 * project.run-consent}): the refused checkout scripts are not asked again in that run, while every other kind keeps
 * its question.
 */
@DisplayName("Consents answered in one run")
class RunConsentsTest {

    @Test
    @DisplayName("Should ask every kind in a run that answered nothing yet")
    void freshRunAsksEveryKind() {
        var run = RunConsents.fresh();

        assertAll(
                () -> assertTrue(run.asksAgain(ConsentKind.RUN_CHECKOUT_SCRIPTS), "The script consent is asked"),
                () -> assertTrue(run.asksAgain(ConsentKind.INSPECT_CHECKOUT), "The inspection is asked"));
    }

    @Test
    @DisplayName("Should not ask a refused consent again in the same run")
    void refusedKindIsNotAskedAgain() {
        var run = RunConsents.fresh().declined(ConsentKind.RUN_CHECKOUT_SCRIPTS);

        assertAll(
                () -> assertFalse(run.asksAgain(ConsentKind.RUN_CHECKOUT_SCRIPTS),
                        "The refused script consent is not asked again"),
                () -> assertTrue(run.asksAgain(ConsentKind.MODEL_COST),
                        "A kind the run did not refuse keeps its question"));
    }

    @Test
    @DisplayName("Should keep the recorded refusals of the run in its value")
    void recordsTheRefusalsInTheValue() {
        var run = RunConsents.fresh().declined(ConsentKind.RUN_CHECKOUT_SCRIPTS);

        assertAll(
                () -> assertTrue(run.declined().contains(ConsentKind.RUN_CHECKOUT_SCRIPTS),
                        "The refused kind is part of the value"),
                () -> assertTrue(RunConsents.fresh().declined().isEmpty(),
                        "The fresh value stays empty, the record is immutable"));
    }
}
