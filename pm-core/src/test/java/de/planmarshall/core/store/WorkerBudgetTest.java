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

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.store.BuildTimings.Baseline;
import de.planmarshall.core.store.BuildTimings.KeyTimings;
import de.planmarshall.core.store.BuildTimings.Learned;
import de.planmarshall.core.store.JobRecord.BudgetDerivation;

/**
 * The per-role learned worker budget (Timeouts &amp; Budgets Specification, Job Budgets; Model Work § Worker Budget):
 * the first match of escalation, operator, learned and floor, never below the role's floor. The floor, the budgets, the
 * learned values and their observation counts are generated on either side of the floor; the compiled minimum of the
 * floor and the factor 1.25 of the specification are the boundary values kept.
 */
@EnableGeneratorController
@DisplayName("Worker budget")
class WorkerBudgetTest {

    private static KeyTimings learned(long seconds, int observations) {
        return new KeyTimings(new Baseline(seconds, observations), Baseline.empty(), Baseline.empty(), null, null);
    }

    /** @return a role floor at or above the compiled minimum */
    private static int floor() {
        return Generators.integers(WorkerBudget.COMPILED_FLOOR_SECONDS, 600).next();
    }

    private static int observations() {
        return Generators.integers(1, 50).next();
    }

    /** @return the learned value scaled as the specification scales it, {@code floor(1.25 * L)} */
    private static long scaled(long learned) {
        return learned * 5 / 4;
    }

    @Test
    @DisplayName("allocates the role floor when the key has no learned value and no operator budget")
    void floorDefault() {
        var floor = floor();

        var budget = WorkerBudget.allocate(floor, null, null, null);

        assertAll(
                () -> assertEquals(floor, budget.seconds(), "the budget should be the role floor"),
                () -> assertEquals(BudgetDerivation.Kind.FLOOR_DEFAULT, budget.derivation().kind(),
                        "the derivation should be the floor default"),
                () -> assertNull(budget.derivation().observations(), "a floor default has no observations"));
    }

    @Test
    @DisplayName("allocates max(F, floor(1.25 * L)) from the learned value with its observation count")
    void learned() {
        var floor = floor();
        var observations = observations();
        var below = Generators.longs(1, floor * 4L / 5).next();

        var budget = WorkerBudget.allocate(floor, null, null, learned(below, observations));

        assertAll(
                () -> assertEquals(floor, budget.seconds(), "a small learned value should give the floor"),
                () -> assertEquals(BudgetDerivation.Kind.LEARNED, budget.derivation().kind(),
                        "the derivation should be learned"),
                () -> assertEquals((long) observations, budget.derivation().observations(),
                        "the observation count should be carried"));
    }

    @Test
    @DisplayName("a learned value above the floor is scaled by 1.25")
    void learnedAboveTheFloor() {
        var floor = floor();
        var above = Generators.longs(floor, 10_000).next();

        var budget = WorkerBudget.allocate(floor, null, null, learned(above, observations()));

        assertAll(
                () -> assertEquals(scaled(above), budget.seconds(), "the learned value should be scaled by 1.25"),
                () -> assertEquals(BudgetDerivation.Kind.LEARNED, budget.derivation().kind(),
                        "the derivation should be learned"));
    }

    @Test
    @DisplayName("an operator budget wins over the learned value and is clamped up to the floor")
    void operator() {
        var floor = floor();
        var operatorAbove = Generators.longs(floor, 100_000).next();
        var operatorBelow = Generators.longs(0, floor - 1L).next();

        var above = WorkerBudget.allocate(floor, null, operatorAbove,
                learned(Generators.longs(floor, 10_000).next(), observations()));
        var below = WorkerBudget.allocate(floor, null, operatorBelow, null);

        assertAll(
                () -> assertEquals(operatorAbove, above.seconds(), "the operator budget should win"),
                () -> assertEquals(BudgetDerivation.Kind.OPERATOR, above.derivation().kind(),
                        "the derivation should be the operator"),
                () -> assertEquals(floor, below.seconds(), "a low operator budget should be clamped"),
                () -> assertEquals(BudgetDerivation.Kind.OPERATOR, below.derivation().kind(),
                        "the clamped derivation should be operator"));
    }

    @Test
    @DisplayName("an escalation budget wins over every other source")
    void escalated() {
        var floor = floor();
        var escalation = Generators.longs(floor, 10_000).next();

        var budget = WorkerBudget.allocate(floor, escalation, Generators.longs(floor, 10_000).next(),
                learned(Generators.longs(floor, 10_000).next(), observations()));

        assertAll(
                () -> assertEquals(escalation, budget.seconds(), "the escalation budget should win"),
                () -> assertEquals(BudgetDerivation.Kind.ESCALATED, budget.derivation().kind(),
                        "the derivation should be escalated"));
    }

    @Test
    @DisplayName("a role floor below the compiled minimum is refused (the compiled minimum is the boundary)")
    void floorMinimum() {
        var derivation = new BudgetDerivation(BudgetDerivation.Kind.FLOOR_DEFAULT, null);
        var belowMinimum = WorkerBudget.COMPILED_FLOOR_SECONDS - 1;

        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> WorkerBudget.allocate(belowMinimum, null, null, null),
                        "an allocation below the minimum is refused"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new WorkerBudget(belowMinimum, derivation, belowMinimum),
                        "a budget below the minimum floor is refused"));
    }

    @Test
    @DisplayName("takes the poisoned baseline only while the clean one has no observation, then the cold one")
    void baselinePrecedence() {
        var floor = floor();
        var cleanSeconds = Generators.longs(floor, 10_000).next();
        var cleanObservations = observations();
        var poisonedSeconds = Generators.longs(floor, 10_000).next();
        var poisonedObservations = observations();
        var coldSeconds = Generators.longs(floor, 10_000).next();
        var coldObservations = observations();
        var clean = new Baseline(cleanSeconds, cleanObservations);
        var poisoned = new Baseline(poisonedSeconds, poisonedObservations);
        var cold = new Baseline(coldSeconds, coldObservations);

        assertAll(
                () -> assertEquals(new Learned(cleanSeconds, cleanObservations),
                        new KeyTimings(clean, poisoned, cold, null, null).learned(),
                        "the clean baseline should be taken first"),
                () -> assertEquals(new Learned(poisonedSeconds, poisonedObservations),
                        new KeyTimings(Baseline.empty(), poisoned, cold, null, null).learned(),
                        "the poisoned baseline should be taken next"),
                () -> assertEquals(new Learned(coldSeconds, coldObservations),
                        new KeyTimings(Baseline.empty(), Baseline.empty(), cold, null, null).learned(),
                        "the cold baseline should be taken last"),
                () -> assertEquals(new Learned(null, 0),
                        new KeyTimings(Baseline.empty(), Baseline.empty(), Baseline.empty(), null, null).learned(),
                        "no baseline should give no learned value"),
                () -> assertEquals(scaled(poisonedSeconds), WorkerBudget.allocate(floor, null, null,
                        new KeyTimings(Baseline.empty(), poisoned, cold, null, null)).seconds(),
                        "the poisoned baseline should be allocated"),
                () -> assertEquals(scaled(coldSeconds), WorkerBudget.allocate(floor, null, null,
                        new KeyTimings(Baseline.empty(), Baseline.empty(), cold, null, null)).seconds(),
                        "the cold baseline should be allocated"));
    }
}
