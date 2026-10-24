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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.store.BuildTimings.Baseline;
import de.planmarshall.core.store.BuildTimings.KeyTimings;
import de.planmarshall.core.store.BuildTimings.Learned;
import de.planmarshall.core.store.JobRecord.BudgetDerivation;

/**
 * The per-role learned worker budget (Timeouts &amp; Budgets Specification, Job Budgets; Model Work § Worker Budget):
 * the first match of escalation, operator, learned and floor, never below the role's floor.
 */
@DisplayName("Worker budget")
class WorkerBudgetTest {

    private static final int FLOOR = 300;

    private static KeyTimings learned(long seconds, int observations) {
        return new KeyTimings(new Baseline(seconds, observations), Baseline.empty(), Baseline.empty(), null, null);
    }

    @Test
    @DisplayName("allocates the role floor when the key has no learned value and no operator budget")
    void floorDefault() {
        var budget = WorkerBudget.allocate(FLOOR, null, null, null);

        assertAll(
                () -> assertEquals(FLOOR, budget.seconds()),
                () -> assertEquals(BudgetDerivation.Kind.FLOOR_DEFAULT, budget.derivation().kind()),
                () -> assertNull(budget.derivation().observations()));
    }

    @Test
    @DisplayName("allocates max(F, floor(1.25 * L)) from the learned value with its observation count")
    void learned() {
        var budget = WorkerBudget.allocate(FLOOR, null, null, learned(200, 3));

        assertAll(
                () -> assertEquals(300, budget.seconds()),
                () -> assertEquals(BudgetDerivation.Kind.LEARNED, budget.derivation().kind()),
                () -> assertEquals(3L, budget.derivation().observations()));
    }

    @Test
    @DisplayName("a learned value above the floor is scaled by 1.25")
    void learnedAboveTheFloor() {
        var budget = WorkerBudget.allocate(FLOOR, null, null, learned(800, 5));

        assertAll(
                () -> assertEquals(1000, budget.seconds()),
                () -> assertEquals(BudgetDerivation.Kind.LEARNED, budget.derivation().kind()));
    }

    @Test
    @DisplayName("an operator budget wins over the learned value and is clamped up to the floor")
    void operator() {
        var above = WorkerBudget.allocate(FLOOR, null, 900L, learned(800, 5));
        var below = WorkerBudget.allocate(FLOOR, null, 50L, null);

        assertAll(
                () -> assertEquals(900, above.seconds()),
                () -> assertEquals(BudgetDerivation.Kind.OPERATOR, above.derivation().kind()),
                () -> assertEquals(FLOOR, below.seconds()),
                () -> assertEquals(BudgetDerivation.Kind.OPERATOR, below.derivation().kind()));
    }

    @Test
    @DisplayName("an escalation budget wins over every other source")
    void escalated() {
        var budget = WorkerBudget.allocate(FLOOR, 1200L, 900L, learned(800, 5));

        assertAll(
                () -> assertEquals(1200, budget.seconds()),
                () -> assertEquals(BudgetDerivation.Kind.ESCALATED, budget.derivation().kind()));
    }

    @Test
    @DisplayName("a role floor below the compiled minimum is refused")
    void floorMinimum() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> WorkerBudget.allocate(59, null, null, null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new WorkerBudget(59, new BudgetDerivation(BudgetDerivation.Kind.FLOOR_DEFAULT, null),
                                59)));
    }

    @Test
    @DisplayName("takes the poisoned baseline only while the clean one has no observation, then the cold one")
    void baselinePrecedence() {
        var clean = new Baseline(500L, 4);
        var poisoned = new Baseline(900L, 2);
        var cold = new Baseline(1200L, 3);

        assertAll(
                () -> assertEquals(new Learned(500L, 4),
                        new KeyTimings(clean, poisoned, cold, null, null).learned()),
                () -> assertEquals(new Learned(900L, 2),
                        new KeyTimings(Baseline.empty(), poisoned, cold, null, null).learned()),
                () -> assertEquals(new Learned(1200L, 3),
                        new KeyTimings(Baseline.empty(), Baseline.empty(), cold, null, null).learned()),
                () -> assertEquals(new Learned(null, 0),
                        new KeyTimings(Baseline.empty(), Baseline.empty(), Baseline.empty(), null, null).learned()),
                () -> assertEquals(1125, WorkerBudget.allocate(FLOOR, null, null,
                        new KeyTimings(Baseline.empty(), poisoned, cold, null, null)).seconds()),
                () -> assertEquals(1500, WorkerBudget.allocate(FLOOR, null, null,
                        new KeyTimings(Baseline.empty(), Baseline.empty(), cold, null, null)).seconds()));
    }
}
