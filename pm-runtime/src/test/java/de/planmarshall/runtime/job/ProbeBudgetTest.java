/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.job;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.planmarshall.core.store.JobRecord.BudgetDerivation;

/**
 * The fixed probe budgets (Job Runtime Developer Guide § Probe Jobs): a compiled constant per kind of probe, recorded
 * with the derivation {@code floor_default} and never learned or escalated.
 */
@DisplayName("Probe budgets")
class ProbeBudgetTest {

    static Stream<Arguments> kinds() {
        return Stream.of(
                Arguments.of(ProbeCandidate.Kind.VERSION, "probe:version", 15L),
                Arguments.of(ProbeCandidate.Kind.MODELS, "probe:models", 30L),
                Arguments.of(ProbeCandidate.Kind.LOGIN, "probe:login", 120L),
                Arguments.of(ProbeCandidate.Kind.SCRIPT, "probe:script", 600L));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("kinds")
    @DisplayName("is fixed per kind of probe, at the value the guide names")
    void fixed(ProbeCandidate.Kind kind, String key, long seconds) {
        var budget = ProbeBudget.of(kind);

        assertAll(
                () -> assertEquals(key, budget.key()),
                () -> assertEquals(seconds, budget.seconds()),
                () -> assertEquals(BudgetDerivation.Kind.FLOOR_DEFAULT, budget.derivation().kind()),
                () -> assertNull(budget.derivation().observations()));
    }

    @Test
    @DisplayName("the four compiled constants are the guide's values")
    void constants() {
        assertAll(
                () -> assertEquals(15, ProbeBudget.VERSION_SECONDS),
                () -> assertEquals(30, ProbeBudget.MODELS_SECONDS),
                () -> assertEquals(120, ProbeBudget.LOGIN_SECONDS),
                () -> assertEquals(600, ProbeBudget.SCRIPT_SECONDS));
    }
}
