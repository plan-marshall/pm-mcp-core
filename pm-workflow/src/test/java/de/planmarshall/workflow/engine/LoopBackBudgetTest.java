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

import static de.planmarshall.workflow.engine.EngineFixtures.EXECUTE;
import static de.planmarshall.workflow.engine.EngineFixtures.SUB_COMMIT;
import static de.planmarshall.workflow.engine.EngineFixtures.callingPlan;
import static de.planmarshall.workflow.engine.EngineFixtures.parse;
import static de.planmarshall.workflow.engine.EngineFixtures.planStatus;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import java.util.Map;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.planmarshall.core.store.JournalScopeType;
import de.planmarshall.core.store.WorkflowStatus;
import de.planmarshall.workflow.engine.LoopBackBudget.Charge;

/**
 * The loop-back accounting (PM-WF-7): {@code loop_back_iteration} is advanced by every loop-back begun and never reset,
 * the outline loop-back budget {@code 1} is exhausted on the second loop-back, and the policy counters are kept per
 * class and survive the engine re-entering a unit.
 */
@EnableGeneratorController
@DisplayName("LoopBackBudget")
class LoopBackBudgetTest {

    private final WorkflowStatus plan = planStatus(EXECUTE, "execute.replan");

    private static WorkflowStatus granted(Charge charge) {
        return assertInstanceOf(Charge.Granted.class, charge, "the charge is granted").status();
    }

    @Nested
    @DisplayName("Iteration")
    class Iteration {

        @Test
        @DisplayName("Should advance the iteration for every loop-back begun and never for a retry")
        void shouldAdvanceIterationPerLoopBack() {
            var first = granted(LoopBackBudget.policyLoopBack(plan, "ci-failure", 5));
            var second = granted(LoopBackBudget.outlineLoopBack(first));
            var retried = granted(LoopBackBudget.policyRetry(second, "review-findings", 2));

            assertAll(
                    () -> assertEquals(1, first.loopBackIteration(), "the first loop-back advances to one"),
                    () -> assertEquals(2, second.loopBackIteration(), "the second loop-back advances to two"),
                    () -> assertEquals(2, retried.loopBackIteration(), "a retry keeps the iteration"));
        }

        @Test
        @DisplayName("Should keep the iteration when a budget is spent")
        void shouldKeepIterationWhenExhausted() {
            var spent = plan.toBuilder().loopBackIteration(4).outlineLoopBacks(1).build();

            var exhausted = assertInstanceOf(Charge.Exhausted.class, LoopBackBudget.outlineLoopBack(spent),
                    "a spent budget yields an exhausted charge");

            assertAll(
                    () -> assertEquals(LoopBackBudget.EXHAUSTED, exhausted.outcome(),
                            "the charge reports exhaustion"),
                    () -> assertEquals(1, exhausted.used(), "one outline loop-back was spent"),
                    () -> assertEquals(4, spent.loopBackIteration(), "a spent budget keeps the iteration"));
        }
    }

    @Nested
    @DisplayName("Outline budget")
    class OutlineBudget {

        @Test
        @DisplayName("Should grant the first outline loop-back and exhaust the second")
        void shouldExhaustOnSecondLoopBack() {
            var once = granted(LoopBackBudget.outlineLoopBack(plan));

            var second = LoopBackBudget.outlineLoopBack(once);

            assertAll(
                    () -> assertEquals(1, once.outlineLoopBacks(), "the first loop-back is counted"),
                    () -> assertEquals(new Charge.Exhausted(1, LoopBackBudget.OUTLINE_LOOP_BACK_BUDGET), second,
                            "the second loop-back is exhausted"));
        }

        @Test
        @DisplayName("Should grant a second outline loop-back under a raised budget")
        void shouldGrantUnderRaisedBudget() {
            var once = granted(LoopBackBudget.outlineLoopBack(plan));

            assertEquals(2, granted(LoopBackBudget.outlineLoopBack(once, 2)).outlineLoopBacks(),
                    "a raised budget grants a second loop-back");
        }
    }

    @Nested
    @DisplayName("Policy counters")
    class PolicyCounters {

        @Test
        @DisplayName("Should count fixes and retries per class and exhaust each budget on its own")
        void shouldCountPerClass() {
            var fixed = granted(LoopBackBudget.policyLoopBack(plan, "ci-failure", 1));
            var retried = granted(LoopBackBudget.policyRetry(fixed, "ci-failure", 1));
            var other = granted(LoopBackBudget.policyLoopBack(retried, "build-failure", 1));

            var ci = other.policyCounters().get("ci-failure");
            assertAll(
                    () -> assertEquals(1, ci.fixUsed(), "the policy fix is counted once"),
                    () -> assertEquals(1, ci.retryUsed(), "the policy retry is counted once"),
                    () -> assertEquals(1, other.policyCounters().get("build-failure").fixUsed(),
                            "the other class counts its own fix"),
                    () -> assertEquals(new Charge.Exhausted(1, 1),
                            LoopBackBudget.policyLoopBack(other, "ci-failure", 1),
                            "the exhausted policy refuses another loop-back"),
                    () -> assertEquals(new Charge.Exhausted(1, 1),
                            LoopBackBudget.policyRetry(other, "ci-failure", 1),
                            "the exhausted policy refuses another retry"));
        }

        @Test
        @DisplayName("Should keep the counters when the engine re-enters a unit")
        void shouldSurviveUnitReEntry() {
            var fixtures = new EngineFixtures();
            var engine = fixtures.engine(callingPlan("execute.commit", """
                    state execute.commit
                        call sub.commit
                            done -> execute.commit-again
                            stepped_out -> plan.return

                    state execute.commit-again
                        call sub.commit
                            done -> completed
                            stepped_out -> plan.return
                    """), parse("sub.pmwf", SUB_COMMIT));
            var charged = granted(LoopBackBudget.policyLoopBack(planStatus(EXECUTE, "execute.commit"),
                    "review-findings", 3));

            var ended = engine.advance(charged);

            assertAll(
                    () -> assertEquals("completed", ended.status().state(), "the re-entered call completes"),
                    () -> assertEquals(2, fixtures.mark.calls(), "the callee ran on both entries"),
                    () -> assertEquals(charged.policyCounters(), ended.status().policyCounters(),
                            "the counters survive the re-entry"),
                    () -> assertEquals(1, ended.status().loopBackIteration(),
                            "the iteration survives the re-entry"));
        }
    }

    @Nested
    @DisplayName("Refusals")
    class Refusals {

        @Test
        @DisplayName("Should refuse another scope than a plan, an unknown class and a negative budget")
        void shouldRefuseInvalidCharges() {
            var epic = plan.toBuilder().scopeType(JournalScopeType.EPIC)
                    .scopeId("scope-" + Generators.letterStrings(3, 12).next().toLowerCase(Locale.ROOT))
                    .phase("epic").build();

            assertAll(
                    () -> assertThrows(IllegalArgumentException.class, () -> LoopBackBudget.outlineLoopBack(epic),
                            "another scope than a plan is refused"),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> LoopBackBudget.policyLoopBack(plan, "unknown-class", 1),
                            "an unknown policy class is refused"),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> LoopBackBudget.policyRetry(plan, "ci-failure", -1),
                            "a negative budget is refused"),
                    () -> assertThrows(NullPointerException.class, () -> new Charge.Granted(null),
                            "a grant without a status is refused"),
                    () -> assertEquals(Map.of(), plan.policyCounters(), "a refusal leaves the counters untouched"));
        }

        @Test
        @DisplayName("Should refuse the retired policy class spend for a loop-back, a retry and the counters")
        void shouldRefuseSpend() {
            var loopBack = assertThrows(IllegalArgumentException.class,
                    () -> LoopBackBudget.policyLoopBack(plan, "spend", 1), "the retired class is refused");
            var retry = assertThrows(IllegalArgumentException.class,
                    () -> LoopBackBudget.policyRetry(plan, "spend", 1), "the retired class is refused for a retry");
            var builder = plan.toBuilder()
                    .policyCounters(Map.of("spend", new WorkflowStatus.PolicyCounter(0, 0, null, null)));

            assertAll(
                    () -> assertTrue(loopBack.getMessage().contains("no remediation policy class"),
                            "the loop-back refusal names the retired class"),
                    () -> assertTrue(retry.getMessage().contains("no remediation policy class"),
                            "the retry refusal names the retired class"),
                    () -> assertThrows(IllegalArgumentException.class, builder::build,
                            "a stored spend counter is refused"));
        }
    }
}
