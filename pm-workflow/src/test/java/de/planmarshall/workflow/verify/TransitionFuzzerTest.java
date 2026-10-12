/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.verify;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import de.planmarshall.workflow.ast.WorkflowDefinition;

/**
 * The seeded transition fuzzer (PM-TEST-3): it finds the dead state planted in {@code planted-dead-state.pmwf} and
 * names it with its location and a witnessing assignment, it reports nothing on the matched control
 * {@code control.pmwf} with the same seed, and the same configuration gives the same report.
 */
@DisplayName("TransitionFuzzer")
class TransitionFuzzerTest {

    private static final String PLANTED = "verify/fuzz/planted-dead-state.pmwf";

    private static final String CONTROL = "verify/fuzz/control.pmwf";

    private static final String PLANTED_GUARD = "        guard in_worktree";

    private static final String DEAD_STATE = "verify.gate";

    private static final long SEED = 42L;

    private static final int RUNS = 64;

    private static final int MAX_STEPS = 16;

    private static final FuzzConfiguration CONFIGURATION = new FuzzConfiguration(SEED, RUNS, MAX_STEPS);

    private final TransitionFuzzer fuzzer = new TransitionFuzzer();

    private static WorkflowDefinition planted() {
        return VerifyFixtures.parse(PLANTED);
    }

    private static WorkflowDefinition control() {
        return VerifyFixtures.parse(CONTROL);
    }

    private static int lineOf(String resource, String text) {
        var lines = VerifyFixtures.read(resource).lines().toList();
        return IntStream.range(0, lines.size())
                .filter(index -> lines.get(index).equals(text))
                .findFirst()
                .orElseThrow() + 1;
    }

    private static List<String> statements(String resource) {
        return VerifyFixtures.read(resource).lines().filter(line -> !line.startsWith("#")).toList();
    }

    @Nested
    @DisplayName("Fixtures")
    class Fixtures {

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {PLANTED, CONTROL})
        @DisplayName("Should pass the static verifier")
        void shouldPassTheStaticVerifier(String resource) {
            var validator = new WorkflowValidator(TestPrimitives.registry(), List.of("verify.entry"));

            var violations = validator.verify(VerifyFixtures.parse(resource)).violations().stream()
                    .filter(v -> v.rule() != VerificationRule.SESSION_DRIVEN_LINKS)
                    .toList();

            assertTrue(violations.isEmpty(), violations::toString);
        }

        @Test
        @DisplayName("Should differ only in the planted guard")
        void shouldDifferOnlyInThePlantedGuard() {
            var planted = statements(PLANTED);

            var withoutGuard = planted.stream().filter(line -> !PLANTED_GUARD.equals(line)).toList();

            assertAll(() -> assertEquals(planted.size() - 1, withoutGuard.size()),
                    () -> assertEquals(statements(CONTROL), withoutGuard));
        }
    }

    @Nested
    @DisplayName("Dead states")
    class DeadStates {

        @Test
        @DisplayName("Should find the planted dead state with its location and a witnessing assignment")
        void shouldFindThePlantedDeadState() {
            var report = fuzzer.fuzz(planted(), CONFIGURATION);

            assertTrue(report.foundDeadState(), report::toString);
            assertEquals(1, report.deadStates().size(), report::toString);
            var dead = report.deadStates().getFirst();
            assertAll(() -> assertEquals(DEAD_STATE, dead.stateId()),
                    () -> assertEquals(PLANTED, dead.location().resourceName()),
                    () -> assertEquals(lineOf(PLANTED, "state " + DEAD_STATE), dead.location().line()),
                    () -> assertEquals(Boolean.FALSE, dead.assignment().get(UnitGraph.IN_WORKTREE)),
                    () -> assertTrue(dead.run() >= 1 && dead.run() <= RUNS, () -> "run " + dead.run()),
                    () -> assertEquals(CONFIGURATION, report.configuration()),
                    () -> assertEquals(RUNS, report.runsExecuted()));
        }

        @Test
        @DisplayName("Should draw the guard atoms of the unit in the order of their first occurrence")
        void shouldDrawEveryAtomInOrder() {
            var dead = fuzzer.fuzz(planted(), CONFIGURATION).deadStates().getFirst();

            assertEquals(List.of(UnitGraph.HAS_OPEN_FINDINGS, UnitGraph.PREDICATE_PREFIX + TestPrimitives.READY,
                            UnitGraph.ALL_TASKS_COMPLETED, UnitGraph.IN_WORKTREE),
                    List.copyOf(dead.assignment().keySet()));
        }

        @Test
        @DisplayName("Should draw only the atoms the guards of the unit name")
        void shouldDrawOnlyTheNamedAtoms() {
            var atoms = TransitionFuzzer.atomsOf(control());

            assertEquals(List.of(UnitGraph.HAS_OPEN_FINDINGS, UnitGraph.PREDICATE_PREFIX + TestPrimitives.READY,
                    UnitGraph.ALL_TASKS_COMPLETED), atoms);
        }

        @Test
        @DisplayName("Should count the witness run from one")
        void shouldCountTheWitnessRunFromOne() {
            var source = VerifyFixtures.read(PLANTED).replace("start verify.entry", "start " + DEAD_STATE)
                    .replace(PLANTED_GUARD, PLANTED_GUARD + " and not in_worktree");

            var report = fuzzer.fuzz(VerifyFixtures.parseSource(PLANTED, source), new FuzzConfiguration(SEED, 3, 1));

            assertAll(() -> assertEquals(List.of(DEAD_STATE),
                            report.deadStates().stream().map(DeadState::stateId).toList(), report::toString),
                    () -> assertEquals(1, report.deadStates().getFirst().run(), report::toString),
                    () -> assertEquals(3, report.runsExecuted()));
        }

        @Test
        @DisplayName("Should report no dead state on the control with the same seed")
        void shouldReportNothingOnTheControl() {
            var report = fuzzer.fuzz(control(), CONFIGURATION);

            assertAll(() -> assertFalse(report.foundDeadState(), report::toString),
                    () -> assertEquals(RUNS, report.runsExecuted()));
        }

        @Test
        @DisplayName("Should not report the planted state when a sub-workflow can step out of it")
        void shouldTakeTheImplicitStepOut() {
            var source = VerifyFixtures.read(PLANTED).replace("scope plan", "scope subworkflow");

            var report = fuzzer.fuzz(VerifyFixtures.parseSource(PLANTED, source), CONFIGURATION);

            assertFalse(report.foundDeadState(), report::toString);
        }

        @Test
        @DisplayName("Should stop a run at the greatest number of steps")
        void shouldStopAtTheGreatestNumberOfSteps() {
            var report = fuzzer.fuzz(planted(), new FuzzConfiguration(SEED, RUNS, 1));

            assertFalse(report.foundDeadState(), report::toString);
        }
    }

    @Nested
    @DisplayName("Seeds")
    class Seeds {

        @Test
        @DisplayName("Should give equal reports for the same seed")
        void shouldGiveEqualReportsForTheSameSeed() {
            var first = fuzzer.fuzz(planted(), CONFIGURATION);

            var second = fuzzer.fuzz(planted(), CONFIGURATION);

            assertEquals(first, second);
        }

        @ParameterizedTest(name = "seed {0}")
        @ValueSource(longs = {1L, 7L, -3L, Long.MAX_VALUE})
        @DisplayName("Should find the planted state with a different seed within the configured runs")
        void shouldFindThePlantedStateWithAnotherSeed(long seed) {
            var report = fuzzer.fuzz(planted(), new FuzzConfiguration(seed, RUNS, MAX_STEPS));

            assertAll(() -> assertEquals(List.of(DEAD_STATE),
                            report.deadStates().stream().map(DeadState::stateId).toList(), report::toString),
                    () -> assertFalse(report.deadStates().getFirst().assignment().get(UnitGraph.IN_WORKTREE)));
        }
    }

    @Nested
    @DisplayName("Configuration")
    class Configuration {

        @ParameterizedTest(name = "runs {0}, maxSteps {1}")
        @CsvSource({"0, 16", "-1, 16", "64, 0", "64, -5"})
        @DisplayName("Should refuse a count that is not positive")
        void shouldRefuseANonPositiveCount(int runs, int maxSteps) {
            assertThrows(IllegalArgumentException.class, () -> new FuzzConfiguration(SEED, runs, maxSteps));
        }

        @Test
        @DisplayName("Should refuse a missing argument")
        void shouldRefuseAMissingArgument() {
            var definition = planted();

            assertAll(() -> assertThrows(NullPointerException.class, () -> fuzzer.fuzz(null, CONFIGURATION)),
                    () -> assertThrows(NullPointerException.class, () -> fuzzer.fuzz(definition, null)));
        }
    }
}
