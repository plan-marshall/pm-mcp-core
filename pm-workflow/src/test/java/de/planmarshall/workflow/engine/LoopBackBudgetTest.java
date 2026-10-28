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

import java.util.Map;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.store.JournalScopeType;
import de.planmarshall.core.store.WorkflowStatus;
import de.planmarshall.workflow.engine.LoopBackBudget.Charge;

/**
 * The loop-back accounting (PM-WF-7): {@code loop_back_iteration} is advanced by every loop-back begun and never reset,
 * the outline loop-back budget {@code 1} is exhausted on the second loop-back, and the policy counters are kept per
 * class and survive the engine re-entering a unit.
 */
@DisplayName("LoopBackBudget")
class LoopBackBudgetTest {

    private final WorkflowStatus plan = planStatus(EXECUTE, "execute.replan");

    private static WorkflowStatus granted(Charge charge) {
        return assertInstanceOf(Charge.Granted.class, charge).status();
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
                    () -> assertEquals(1, first.loopBackIteration()),
                    () -> assertEquals(2, second.loopBackIteration()),
                    () -> assertEquals(2, retried.loopBackIteration()));
        }

        @Test
        @DisplayName("Should keep the iteration when a budget is spent")
        void shouldKeepIterationWhenExhausted() {
            var spent = plan.toBuilder().loopBackIteration(4).outlineLoopBacks(1).build();

            var exhausted = assertInstanceOf(Charge.Exhausted.class, LoopBackBudget.outlineLoopBack(spent));

            assertAll(
                    () -> assertEquals(LoopBackBudget.EXHAUSTED, exhausted.outcome()),
                    () -> assertEquals(1, exhausted.used()),
                    () -> assertEquals(4, spent.loopBackIteration()));
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
                    () -> assertEquals(1, once.outlineLoopBacks()),
                    () -> assertEquals(new Charge.Exhausted(1, LoopBackBudget.OUTLINE_LOOP_BACK_BUDGET), second));
        }

        @Test
        @DisplayName("Should grant a second outline loop-back under a raised budget")
        void shouldGrantUnderRaisedBudget() {
            var once = granted(LoopBackBudget.outlineLoopBack(plan));

            assertEquals(2, granted(LoopBackBudget.outlineLoopBack(once, 2)).outlineLoopBacks());
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
                    () -> assertEquals(1, ci.fixUsed()),
                    () -> assertEquals(1, ci.retryUsed()),
                    () -> assertEquals(1, other.policyCounters().get("build-failure").fixUsed()),
                    () -> assertEquals(new Charge.Exhausted(1, 1), LoopBackBudget.policyLoopBack(other, "ci-failure", 1)),
                    () -> assertEquals(new Charge.Exhausted(1, 1), LoopBackBudget.policyRetry(other, "ci-failure", 1)));
        }

        @Test
        @DisplayName("Should keep the counters when the engine re-enters a unit")
        void shouldSurviveUnitReEntry() {
            var fixtures = new EngineFixtures();
            var engine = fixtures.engine(callingPlan("execute.commit", """
                    state execute.commit
                        phase execute
                        call sub.commit
                            done -> execute.commit-again
                            stepped_out -> plan.return

                    state execute.commit-again
                        phase execute
                        call sub.commit
                            done -> completed
                            stepped_out -> plan.return
                    """), parse("sub.pmwf", SUB_COMMIT));
            var charged = granted(LoopBackBudget.policyLoopBack(planStatus(EXECUTE, "execute.commit"),
                    "review-findings", 3));

            var ended = engine.advance(charged);

            assertAll(
                    () -> assertEquals("completed", ended.status().state()),
                    () -> assertEquals(2, fixtures.mark.calls()),
                    () -> assertEquals(charged.policyCounters(), ended.status().policyCounters()),
                    () -> assertEquals(1, ended.status().loopBackIteration()));
        }
    }

    @Nested
    @DisplayName("Refusals")
    class Refusals {

        @Test
        @DisplayName("Should refuse another scope than a plan, an unknown class and a negative budget")
        void shouldRefuseInvalidCharges() {
            var epic = plan.toBuilder().scopeType(JournalScopeType.EPIC).scopeId("engine-epic")
                    .phase("epic").build();

            assertAll(
                    () -> assertThrows(IllegalArgumentException.class, () -> LoopBackBudget.outlineLoopBack(epic)),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> LoopBackBudget.policyLoopBack(plan, "unknown-class", 1)),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> LoopBackBudget.policyRetry(plan, "ci-failure", -1)),
                    () -> assertThrows(NullPointerException.class, () -> new Charge.Granted(null)),
                    () -> assertEquals(Map.of(), plan.policyCounters()));
        }

        @Test
        @DisplayName("Should refuse the retired policy class spend for a loop-back, a retry and the counters")
        void shouldRefuseSpend() {
            var loopBack = assertThrows(IllegalArgumentException.class,
                    () -> LoopBackBudget.policyLoopBack(plan, "spend", 1));
            var retry = assertThrows(IllegalArgumentException.class,
                    () -> LoopBackBudget.policyRetry(plan, "spend", 1));
            var builder = plan.toBuilder()
                    .policyCounters(Map.of("spend", new WorkflowStatus.PolicyCounter(0, 0, null, null)));

            assertAll(
                    () -> assertTrue(loopBack.getMessage().contains("no remediation policy class"),
                            loopBack.getMessage()),
                    () -> assertTrue(retry.getMessage().contains("no remediation policy class"), retry.getMessage()),
                    () -> assertThrows(IllegalArgumentException.class, builder::build));
        }
    }
}
