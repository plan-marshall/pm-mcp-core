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

import java.util.LinkedHashMap;
import java.util.Objects;

import de.planmarshall.core.store.JournalScopeType;
import de.planmarshall.core.store.WorkflowStatus;
import de.planmarshall.core.store.WorkflowStatus.PolicyCounter;

/**
 * The accounting of loop-backs and remediation budgets in the status of a plan (PM-WF-7: every budget is persisted and
 * never reset; a spent budget ends in the outcome {@value #EXHAUSTED}, never in another loop-back).
 * <p>
 * Three counts are kept, each only ever raised:
 * <ul>
 * <li>{@code loop_back_iteration}, advanced by every loop-back begun: a loop-back of a remediation policy's
 * {@code fix} and a {@code loop_back_outline}. It labels the iterations and bounds nothing.</li>
 * <li>{@code outline_loop_backs}, the {@code loop_back_outline} transitions taken, bounded by the outline loop-back
 * budget: the compiled {@value #OUTLINE_LOOP_BACK_BUDGET}, or the value a raise waiver composed into the plan
 * instance.</li>
 * <li>{@code policy_counters}, per remediation policy class the retries and fixes charged, bounded by the budgets the
 * plan instance binds for that class. Re-entering the policy's unit reads the same counter again.</li>
 * </ul>
 * A charge returns the status with the count raised ({@link Charge.Granted}), or, when the budget is spent, the
 * outcome {@value #EXHAUSTED} with the status unchanged ({@link Charge.Exhausted}). The accounting is kept for plan
 * scopes only, as the status store admits.
 *
 * @since 0.1
 */
public final class LoopBackBudget {

    /** The compiled budget of {@code loop_back_outline} transitions per plan. */
    public static final long OUTLINE_LOOP_BACK_BUDGET = 1;

    /** The outcome a spent budget yields instead of the loop-back. */
    public static final String EXHAUSTED = "exhausted";

    private LoopBackBudget() {
    }

    /**
     * What a charge against a budget yields.
     *
     * @since 0.1
     */
    public sealed interface Charge permits Charge.Granted, Charge.Exhausted {

        /**
         * The budget had room: the charge is counted.
         *
         * @param status the status with the count raised
         * @since 0.1
         */
        record Granted(WorkflowStatus status) implements Charge {

            /** @throws NullPointerException if the status is {@code null} */
            public Granted {
                Objects.requireNonNull(status, "status");
            }
        }

        /**
         * The budget is spent: the outcome {@value LoopBackBudget#EXHAUSTED} is taken instead, and nothing is counted.
         *
         * @param used   the charges counted against the budget
         * @param budget the budget
         * @since 0.1
         */
        record Exhausted(long used, long budget) implements Charge {

            /** @return the outcome a spent budget yields, {@value LoopBackBudget#EXHAUSTED} */
            public String outcome() {
                return EXHAUSTED;
            }
        }
    }

    /**
     * Charges a {@code loop_back_outline} against the compiled outline loop-back budget.
     *
     * @param status the status of a plan
     * @return the granted loop-back, or the exhaustion
     * @throws IllegalArgumentException if the status is not that of a plan
     */
    public static Charge outlineLoopBack(WorkflowStatus status) {
        return outlineLoopBack(status, OUTLINE_LOOP_BACK_BUDGET);
    }

    /**
     * Charges a {@code loop_back_outline} against an outline loop-back budget. A granted loop-back raises
     * {@code outline_loop_backs} and {@code loop_back_iteration} by one.
     *
     * @param status the status of a plan
     * @param budget the effective budget: the compiled one, or the raised one of the plan instance
     * @return the granted loop-back, or the exhaustion
     * @throws IllegalArgumentException if the status is not that of a plan, or the budget is negative
     */
    public static Charge outlineLoopBack(WorkflowStatus status, long budget) {
        requirePlan(status);
        requireBudget(budget);
        if (status.outlineLoopBacks() >= budget) {
            return new Charge.Exhausted(status.outlineLoopBacks(), budget);
        }
        return new Charge.Granted(status.toBuilder()
                .outlineLoopBacks(status.outlineLoopBacks() + 1)
                .loopBackIteration(status.loopBackIteration() + 1)
                .build());
    }

    /**
     * Charges the loop-back of a remediation policy's {@code fix} against the class's fix budget. A granted loop-back
     * raises the class's {@code fix_used} and {@code loop_back_iteration} by one.
     *
     * @param status      the status of a plan
     * @param policyClass the remediation policy class, for example {@code ci-failure}
     * @param fixBudget   the fix budget the plan instance binds for the class
     * @return the granted loop-back, or the exhaustion
     * @throws IllegalArgumentException if the status is not that of a plan, the class is not a remediation policy
     *                                  class, or the budget is negative
     */
    public static Charge policyLoopBack(WorkflowStatus status, String policyClass, long fixBudget) {
        requirePlan(status);
        requireBudget(fixBudget);
        var counter = counterOf(status, policyClass);
        if (counter.fixUsed() >= fixBudget) {
            return new Charge.Exhausted(counter.fixUsed(), fixBudget);
        }
        var charged = new PolicyCounter(counter.retryUsed(), counter.fixUsed() + 1, counter.renewedFrom(),
                counter.lastRetry());
        return new Charge.Granted(status.toBuilder()
                .policyCounters(withCounter(status, policyClass, charged))
                .loopBackIteration(status.loopBackIteration() + 1)
                .build());
    }

    /**
     * Charges a retry of a remediation policy against the class's retry budget. A retry is no loop-back: it raises the
     * class's {@code retry_used} and leaves {@code loop_back_iteration} as it is.
     *
     * @param status      the status of a plan
     * @param policyClass the remediation policy class, for example {@code review-findings}
     * @param retryBudget the retry budget the plan instance binds for the class
     * @return the granted retry, or the exhaustion
     * @throws IllegalArgumentException if the status is not that of a plan, the class is not a remediation policy
     *                                  class, or the budget is negative
     */
    public static Charge policyRetry(WorkflowStatus status, String policyClass, long retryBudget) {
        requirePlan(status);
        requireBudget(retryBudget);
        var counter = counterOf(status, policyClass);
        if (counter.retryUsed() >= retryBudget) {
            return new Charge.Exhausted(counter.retryUsed(), retryBudget);
        }
        var charged = new PolicyCounter(counter.retryUsed() + 1, counter.fixUsed(), counter.renewedFrom(),
                counter.lastRetry());
        return new Charge.Granted(status.toBuilder()
                .policyCounters(withCounter(status, policyClass, charged))
                .build());
    }

    private static PolicyCounter counterOf(WorkflowStatus status, String policyClass) {
        Objects.requireNonNull(policyClass, "policyClass");
        if (!WorkflowStatus.POLICY_CLASSES.contains(policyClass)) {
            throw new IllegalArgumentException("'%s' is no remediation policy class".formatted(policyClass));
        }
        var counter = status.policyCounters().get(policyClass);
        return counter != null ? counter : new PolicyCounter(0, 0, null, null);
    }

    private static LinkedHashMap<String, PolicyCounter> withCounter(WorkflowStatus status, String policyClass,
            PolicyCounter counter) {
        var counters = new LinkedHashMap<>(status.policyCounters());
        counters.put(policyClass, counter);
        return counters;
    }

    private static void requirePlan(WorkflowStatus status) {
        Objects.requireNonNull(status, "status");
        if (status.scopeType() != JournalScopeType.PLAN) {
            throw new IllegalArgumentException("loop-backs and remediation budgets are kept for plan scopes only, not "
                    + "for the %s scope".formatted(status.scopeType().wireName()));
        }
    }

    private static void requireBudget(long budget) {
        if (budget < 0) {
            throw new IllegalArgumentException("a budget is at least 0: " + budget);
        }
    }
}
