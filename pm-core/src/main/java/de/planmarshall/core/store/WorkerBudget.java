/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.store;

import java.util.Objects;

import de.planmarshall.core.store.BuildTimings.KeyTimings;
import de.planmarshall.core.store.JobRecord.BudgetDerivation;

/**
 * The budget of one worker, allocated from the key {@code llm:<role>} (Timeouts &amp; Budgets Specification, Job
 * Budgets; Model Work § Worker Budget).
 * <p>
 * The allocation is the specification's first match over four sources:
 * <ol>
 *   <li>an escalation budget {@code E} is set for the key: {@code t = E} ({@code escalated});</li>
 *   <li>an operator budget {@code O} is enrolled for the key: {@code t = max(F, O)} ({@code operator});</li>
 *   <li>the key has a learned value {@code L}: {@code t = max(F, floor(1.25 * L))} ({@code learned}, with its
 *       observation count);</li>
 *   <li>otherwise {@code t = F} ({@code floor_default}).</li>
 * </ol>
 * The floor {@code F} is the role artifact's {@code budget_floor_seconds}, whose compiled minimum is
 * {@value #COMPILED_FLOOR_SECONDS}; a budget never falls below it, whatever derivation selected it. The escalation cap
 * bounds only the escalation step, never an operator budget.
 * <p>
 * The budget of a worker runs from its spawn to its exit for a fresh worker, and per task from the offer to the
 * attempt's end for a warm worker; the time a worker waits for a model slot is outside it.
 *
 * @param seconds          the allocated budget in seconds
 * @param derivation       how the budget was derived, as the job record records it
 * @param roleFloorSeconds the floor of the role
 * @since 0.1
 */
public record WorkerBudget(long seconds, BudgetDerivation derivation, int roleFloorSeconds) {

    /** The compiled minimum of a role's {@code budget_floor_seconds}. */
    public static final int COMPILED_FLOOR_SECONDS = 60;

    /** The compiled default of the escalation cap {@code C}. */
    public static final long DEFAULT_ESCALATION_CAP_SECONDS = 1800;

    /**
     * @throws NullPointerException     if the derivation is {@code null}
     * @throws IllegalArgumentException if the budget is below the role floor, or the floor below its compiled minimum
     */
    public WorkerBudget {
        Objects.requireNonNull(derivation, "derivation");
        if (roleFloorSeconds < COMPILED_FLOOR_SECONDS) {
            throw new IllegalArgumentException(
                    "A role floor is at least %d seconds: %d".formatted(COMPILED_FLOOR_SECONDS, roleFloorSeconds));
        }
        if (seconds < roleFloorSeconds) {
            throw new IllegalArgumentException(
                    "A worker budget is never below the role floor: %d below %d".formatted(seconds, roleFloorSeconds));
        }
    }

    /**
     * @param roleFloorSeconds the role artifact's {@code budget_floor_seconds}
     * @param escalationSeconds the one-shot escalation of the key; {@code null} when none is owed
     * @param operatorBudget   the operator's budget for the key; {@code null} when none is enrolled
     * @param timings          the learned timings of the key; {@code null} when the key has none
     * @return the budget of a worker of the role
     */
    public static WorkerBudget allocate(int roleFloorSeconds, Long escalationSeconds, Long operatorBudget,
            KeyTimings timings) {
        if (roleFloorSeconds < COMPILED_FLOOR_SECONDS) {
            throw new IllegalArgumentException(
                    "A role floor is at least %d seconds: %d".formatted(COMPILED_FLOOR_SECONDS, roleFloorSeconds));
        }
        var floor = roleFloorSeconds;
        if (escalationSeconds != null) {
            return new WorkerBudget(Math.max(floor, escalationSeconds),
                    new BudgetDerivation(BudgetDerivation.Kind.ESCALATED, null), floor);
        }
        if (operatorBudget != null) {
            return new WorkerBudget(Math.max(floor, operatorBudget),
                    new BudgetDerivation(BudgetDerivation.Kind.OPERATOR, null), floor);
        }
        if (timings != null) {
            var learned = timings.learned();
            if (learned.seconds() != null) {
                return new WorkerBudget(Math.max(floor, (long) Math.floor(1.25 * learned.seconds())),
                        new BudgetDerivation(BudgetDerivation.Kind.LEARNED, (long) learned.observations()), floor);
            }
        }
        return new WorkerBudget(floor, new BudgetDerivation(BudgetDerivation.Kind.FLOOR_DEFAULT, null), floor);
    }
}
