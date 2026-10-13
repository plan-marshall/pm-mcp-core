/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.engine;

import static de.planmarshall.workflow.engine.EngineFixtures.planStatus;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.planmarshall.workflow.ast.GuardExpr;
import de.planmarshall.workflow.ast.GuardResult;
import de.planmarshall.workflow.ast.LinkSpec;
import de.planmarshall.workflow.ast.ResultGuard;
import de.planmarshall.workflow.ast.StateBehavior;

/**
 * Guards (PM-IMPL-5, PM-WF-3, PM-WF-16): a guard has three values, {@code and}, {@code or} and {@code not} propagate
 * the third unless the other operand decides, a guard that could not be evaluated raises {@code guard_indeterminate}
 * naming the guard, the link and the reason, and a result guard always decides.
 * <p>
 * The guard is proven by making it fire: the undecided {@code in_worktree} raises the condition, and the matched
 * controls, the same link with a decided {@code in_worktree}, held or not held, raise nothing.
 */
@DisplayName("GuardEvaluator")
class GuardEvaluatorTest {

    private static final GuardResult HELD = new GuardResult.Held();
    private static final GuardResult NOT_HELD = new GuardResult.NotHeld();
    private static final GuardResult UNDECIDED = new GuardResult.CouldNotEvaluate("worktree unreadable");

    private static final GuardExpr WORKTREE = new GuardExpr.InWorktree();
    private static final GuardExpr FINDINGS = new GuardExpr.HasOpenFindings();

    private final EngineFixtures fixtures = new EngineFixtures();

    /** The values the atoms read, fixed per test. */
    private record Facts(GuardResult worktree, GuardResult findings, GuardResult tasks,
    Map<String, GuardResult> predicates) implements GuardEvaluator.GuardFacts {

        Facts(GuardResult worktree, GuardResult findings) {
            this(worktree, findings, HELD, Map.of());
        }

        @Override
        public GuardResult inWorktree() {
            return worktree;
        }

        @Override
        public GuardResult hasOpenFindings() {
            return findings;
        }

        @Override
        public GuardResult allTasksCompleted() {
            return tasks;
        }

        @Override
        public GuardResult predicate(String primitiveId) {
            return predicates.get(primitiveId);
        }
    }

    static Stream<Arguments> combinations() {
        return Stream.of(
                Arguments.of(HELD, HELD, HELD, HELD),
                Arguments.of(HELD, NOT_HELD, NOT_HELD, HELD),
                Arguments.of(NOT_HELD, NOT_HELD, NOT_HELD, NOT_HELD),
                Arguments.of(UNDECIDED, NOT_HELD, NOT_HELD, UNDECIDED),
                Arguments.of(NOT_HELD, UNDECIDED, NOT_HELD, UNDECIDED),
                Arguments.of(UNDECIDED, HELD, UNDECIDED, HELD),
                Arguments.of(HELD, UNDECIDED, UNDECIDED, HELD),
                Arguments.of(UNDECIDED, UNDECIDED, UNDECIDED, UNDECIDED));
    }

    @Nested
    @DisplayName("Three values")
    class ThreeValues {

        @ParameterizedTest(name = "{0} and/or {1} -> {2} / {3}")
        @MethodSource("de.planmarshall.workflow.engine.GuardEvaluatorTest#combinations")
        @DisplayName("Should propagate could-not-evaluate through and and or unless the other operand decides")
        void shouldCombineThreeValues(GuardResult left, GuardResult right, GuardResult and, GuardResult or) {
            var facts = new Facts(left, right);

            assertAll(
                    () -> assertEquals(and, GuardEvaluator.evaluate(new GuardExpr.And(WORKTREE, FINDINGS), facts)),
                    () -> assertEquals(or, GuardEvaluator.evaluate(new GuardExpr.Or(WORKTREE, FINDINGS), facts)));
        }

        @Test
        @DisplayName("Should negate held and not held and keep could-not-evaluate")
        void shouldNegate() {
            assertAll(
                    () -> assertEquals(NOT_HELD, GuardEvaluator.evaluate(new GuardExpr.Not(WORKTREE),
                            new Facts(HELD, HELD))),
                    () -> assertEquals(HELD, GuardEvaluator.evaluate(new GuardExpr.Not(WORKTREE),
                            new Facts(NOT_HELD, HELD))),
                    () -> assertEquals(UNDECIDED, GuardEvaluator.evaluate(new GuardExpr.Not(WORKTREE),
                            new Facts(UNDECIDED, HELD))));
        }

        @Test
        @DisplayName("Should read each atom from its own fact")
        void shouldReadEachAtom() {
            var facts = new Facts(HELD, NOT_HELD, UNDECIDED, Map.of("test.ready", NOT_HELD));
            var unknownPredicate = new GuardExpr.CustomPredicate("test.unknown");

            assertAll(
                    () -> assertEquals(HELD, GuardEvaluator.evaluate(WORKTREE, facts)),
                    () -> assertEquals(NOT_HELD, GuardEvaluator.evaluate(FINDINGS, facts)),
                    () -> assertEquals(UNDECIDED, GuardEvaluator.evaluate(new GuardExpr.AllTasksCompleted(), facts)),
                    () -> assertEquals(NOT_HELD, GuardEvaluator.evaluate(new GuardExpr.CustomPredicate("test.ready"),
                            facts)),
                    () -> assertThrows(NullPointerException.class,
                            () -> GuardEvaluator.evaluate(unknownPredicate, facts)));
        }
    }

    @Nested
    @DisplayName("Condition")
    class Condition {

        private final List<LinkSpec> links = links();

        private static List<LinkSpec> links() {
            var unit = EngineFixtures.parse("session.pmwf", """
                    workflow session.guards version 1 format 1
                    scope subworkflow
                    start session.choose

                    state session.choose
                        phase inherit
                        skill pm/test/choose
                        link next free ()
                            do test.proceed
                                done -> session.done
                                failed -> session.done
                        link next guarded ()
                            guard (in_worktree or has_open_findings) and not predicate:test.ready
                            do test.proceed
                                done -> session.done
                                failed -> session.done

                    state session.done terminal done success
                        phase inherit
                    """);
            return ((StateBehavior.InteractiveLinks) unit.states().get("session.choose").behavior()).links();
        }

        @Test
        @DisplayName("Should raise guard_indeterminate naming the guard, the link and the reason")
        void shouldRaiseGuardIndeterminate() {
            var raisedAt = fixtures.clock.tick(Duration.ofSeconds(3));
            var status = planStatus(EngineFixtures.EXECUTE, "session.choose");
            var facts = new Facts(UNDECIDED, NOT_HELD, HELD, Map.of("test.ready", NOT_HELD));

            var raised = new GuardEvaluator(fixtures.clock).raiseUndecided(status, links, facts).orElseThrow();

            assertAll(
                    () -> assertEquals(EngineStep.GUARD_INDETERMINATE, raised.kind()),
                    () -> assertEquals("{\"guard\":\"(in_worktree or has_open_findings) and not predicate:test.ready\","
                            + "\"link\":\"guarded\",\"reason\":\"worktree unreadable\"}", raised.facts()),
                    () -> assertEquals(raisedAt, raised.status().condition().orElseThrow().raisedAt()),
                    () -> assertEquals(status.state(), raised.status().state()),
                    () -> assertEquals(status.callStack(), raised.status().callStack()));
        }

        @ParameterizedTest(name = "in_worktree {0}")
        @MethodSource("de.planmarshall.workflow.engine.GuardEvaluatorTest#decided")
        @DisplayName("Should raise nothing when every guard decided, held or not held")
        void shouldRaiseNothingWhenDecided(GuardResult worktree) {
            var status = planStatus(EngineFixtures.EXECUTE, "session.choose");
            var facts = new Facts(worktree, NOT_HELD, HELD, Map.of("test.ready", NOT_HELD));

            assertTrue(new GuardEvaluator(fixtures.clock).raiseUndecided(status, links, facts).isEmpty());
        }
    }

    static Stream<GuardResult> decided() {
        return Stream.of(HELD, NOT_HELD);
    }

    @Nested
    @DisplayName("Result guards")
    class ResultGuards {

        @Test
        @DisplayName("Should hold a boolean field when true, any other field when present, and invert for not")
        void shouldDecideResultGuard() {
            var pushed = new ResultGuard("pushed", false);
            var notPushed = new ResultGuard("pushed", true);

            assertAll(
                    () -> assertTrue(GuardEvaluator.holds(pushed, Map.of("pushed", true))),
                    () -> assertFalse(GuardEvaluator.holds(pushed, Map.of("pushed", false))),
                    () -> assertTrue(GuardEvaluator.holds(pushed, Map.of("pushed", "a1b2c3"))),
                    () -> assertFalse(GuardEvaluator.holds(pushed, Map.of())),
                    () -> assertTrue(GuardEvaluator.holds(notPushed, Map.of())),
                    () -> assertFalse(GuardEvaluator.holds(notPushed, Map.of("pushed", true))));
        }
    }
}
