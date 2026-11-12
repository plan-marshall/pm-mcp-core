/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.envelope;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.planmarshall.workflow.envelope.EnvelopePacker.PackedEnvelope;
import de.planmarshall.workflow.envelope.EnvelopePacker.PlannedTaskSummary;

/**
 * The deterministic packing of the ordered plan tasks into envelopes: Next-Fit under the budget, the closing criteria,
 * the merge of an envelope below the minimum task work, and the shape of the sealed envelopes.
 *
 * @since 0.1
 */
@EnableGeneratorController
@DisplayName("EnvelopePacker")
class EnvelopePackerTest {

    private static final long MIN = EnvelopePacker.MIN_JOB_TOKENS;

    private int taskCounter;
    private long budget;
    private String module;
    private String role;

    @BeforeEach
    void setUp() {
        taskCounter = 0;
        budget = Generators.longs(100_000, 1_000_000).next();
        module = name();
        role = name();
    }

    private static String name() {
        return Generators.letterStrings(3, 12).next();
    }

    private static String otherThan(String value) {
        String other;
        do {
            other = name();
        } while (other.equals(value));
        return other;
    }

    private long regularCost() {
        return Generators.longs(MIN, budget / 4).next();
    }

    private static long smallCost() {
        return Generators.longs(1, MIN - 1).next();
    }

    private PlannedTaskSummary task(String moduleId, String roleName, long cost, Set<String> skills,
            List<String> dependsOn) {
        taskCounter++;
        return PlannedTaskSummary.builder()
                // the task id follows the plan's TASK-NNN format
                .taskId("TASK-%03d".formatted(taskCounter))
                .moduleId(moduleId)
                .role(roleName)
                .skillUris(skills)
                .costSize(Generators.enumValues(CostSize.class).next())
                .predictedCostTokens(cost)
                .dependsOnTaskIds(dependsOn)
                .build();
    }

    private PlannedTaskSummary task(String moduleId, String roleName, long cost) {
        return task(moduleId, roleName, cost, Set.of(name()), List.of());
    }

    private PlannedTaskSummary task(long cost) {
        return task(module, role, cost);
    }

    private List<PackedEnvelope> pack(PlannedTaskSummary... tasks) {
        return EnvelopePacker.packEnvelopes(List.of(tasks), budget);
    }

    private static List<String> ids(PlannedTaskSummary... tasks) {
        return Stream.of(tasks).map(PlannedTaskSummary::taskId).toList();
    }

    private static long sum(PlannedTaskSummary... tasks) {
        return Stream.of(tasks).mapToLong(PlannedTaskSummary::predictedCostTokens).sum();
    }

    @Nested
    @DisplayName("the Next-Fit run")
    class NextFit {

        @Test
        @DisplayName("packs an empty input into no envelope")
        void packsAnEmptyInputIntoNoEnvelope() {
            assertTrue(EnvelopePacker.packEnvelopes(List.of(), budget).isEmpty(),
                    "An empty input yields no envelope at all");
        }

        @Test
        @DisplayName("refuses a missing task list")
        void refusesAMissingTaskList() {
            assertThrows(NullPointerException.class, () -> EnvelopePacker.packEnvelopes(null, budget),
                    "A null task list is refused");
        }

        @Test
        @DisplayName("packs one task into one envelope")
        void packsOneTaskIntoOneEnvelope() {
            var only = task(regularCost());

            var envelopes = pack(only);

            assertEquals(1, envelopes.size(), "One task yields exactly one envelope");
            var envelope = envelopes.getFirst();
            assertAll("The envelope of the single task",
                    () -> assertEquals(ids(only), envelope.taskIds(), "The envelope holds the single task"),
                    () -> assertEquals(module, envelope.moduleId(), "The envelope carries the task's module"),
                    () -> assertEquals(role, envelope.role(), "The envelope carries the task's role"),
                    () -> assertEquals(only.predictedCostTokens(), envelope.totalPredictedCostTokens(),
                            "The total is the task's predicted cost"));
        }

        @Test
        @DisplayName("packs a run that stays under the budget into one envelope")
        void packsARunUnderTheBudgetIntoOneEnvelope() {
            var first = task(regularCost());
            var second = task(regularCost());
            var third = task(regularCost());

            var envelopes = pack(first, second, third);

            assertEquals(1, envelopes.size(), "A run within the budget shares one envelope");
            assertAll("The shared envelope",
                    () -> assertEquals(ids(first, second, third), envelopes.getFirst().taskIds(),
                            "The envelope holds the run in task order"),
                    () -> assertEquals(sum(first, second, third), envelopes.getFirst().totalPredictedCostTokens(),
                            "The total sums the tasks' predicted costs"));
        }

        @Test
        @DisplayName("closes the envelope when the next task would exceed the budget")
        void closesOnTheBudget() {
            var full = task(budget);
            var next = task(regularCost());

            var envelopes = pack(full, next);

            assertAll("The envelopes split by the budget",
                    () -> assertEquals(2, envelopes.size(), "The budget closes the first envelope"),
                    () -> assertEquals(ids(full), envelopes.get(0).taskIds(), "The full envelope holds one task"),
                    () -> assertEquals(ids(next), envelopes.get(1).taskIds(), "The next task opens an envelope"));
        }

        @Test
        @DisplayName("closes the envelope when the next task targets another module")
        void closesOnAModuleChange() {
            var first = task(regularCost());
            var other = task(otherThan(module), role, regularCost());

            var envelopes = pack(first, other);

            assertAll("The envelopes split by the module",
                    () -> assertEquals(2, envelopes.size(), "A module change closes the envelope"),
                    () -> assertEquals(module, envelopes.get(0).moduleId(), "The first envelope keeps its module"),
                    () -> assertEquals(other.moduleId(), envelopes.get(1).moduleId(),
                            "The second envelope carries the new module"));
        }

        @Test
        @DisplayName("closes the envelope when the next task is served by another role")
        void closesOnARoleChange() {
            var first = task(regularCost());
            var other = task(module, otherThan(role), regularCost());

            var envelopes = pack(first, other);

            assertAll("The envelopes split by the role",
                    () -> assertEquals(2, envelopes.size(), "A role change closes the envelope"),
                    () -> assertEquals(role, envelopes.get(0).role(), "The first envelope keeps its role"),
                    () -> assertEquals(other.role(), envelopes.get(1).role(),
                            "The second envelope carries the new role"));
        }

        @Test
        @DisplayName("seats a task over the budget alone")
        void seatsAnOverBudgetTaskAlone() {
            var before = task(regularCost());
            var huge = task(budget + Generators.longs(1, budget).next());
            var after = task(regularCost());

            var envelopes = pack(before, huge, after);

            assertAll("The over-budget task sits alone",
                    () -> assertEquals(3, envelopes.size(), "Each side of the huge task is split off"),
                    () -> assertEquals(ids(huge), envelopes.get(1).taskIds(), "The huge task has its own envelope"),
                    () -> assertTrue(envelopes.get(1).totalPredictedCostTokens() > budget,
                            "The lone envelope exceeds the budget"),
                    () -> assertEquals(huge.predictedCostTokens(), envelopes.get(1).totalPredictedCostTokens(),
                            "The lone envelope totals the huge task"));
        }

        @Test
        @DisplayName("shares an envelope between tasks that differ in all but module and role")
        void doesNotUseTheProfileAsASharingCriterion() {
            var first = task(module, role, regularCost(), Set.of(name()), List.of());
            var second = task(module, role, regularCost(), Set.of(name()), List.of(first.taskId()));

            var envelopes = pack(first, second);

            assertEquals(1, envelopes.size(), "Only module and role decide the sharing");
            assertEquals(ids(first, second), envelopes.getFirst().taskIds(), "Both tasks share the envelope");
        }
    }

    @Nested
    @DisplayName("the merge below the minimum task work")
    class MergeBelowMinimum {

        @Test
        @DisplayName("merges a small envelope into the preceding compatible one")
        void mergesIntoThePreceding() {
            var full = task(budget);
            var small = task(smallCost());

            var envelopes = pack(full, small);

            assertEquals(1, envelopes.size(), "The small envelope joins the preceding one");
            var merged = envelopes.getFirst();
            assertAll("The merged envelope",
                    () -> assertEquals(ids(full, small), merged.taskIds(), "The small task follows in order"),
                    () -> assertEquals(sum(full, small), merged.totalPredictedCostTokens(),
                            "The merged total exceeds the budget"),
                    () -> assertFalse(merged.belowMinimum(), "The merged envelope is not below minimum"));
        }

        @Test
        @DisplayName("merges a small envelope into the following one without a preceding one")
        void mergesIntoTheFollowing() {
            var small = task(smallCost());
            var full = task(budget);

            var envelopes = pack(small, full);

            assertEquals(1, envelopes.size(), "The small envelope joins the following one");
            var merged = envelopes.getFirst();
            assertAll("The merged envelope",
                    () -> assertEquals(ids(small, full), merged.taskIds(), "The small task leads in order"),
                    () -> assertEquals(sum(small, full), merged.totalPredictedCostTokens(),
                            "The merged total sums both tasks"),
                    () -> assertFalse(merged.belowMinimum(), "The merged envelope is not below minimum"));
        }

        @Test
        @DisplayName("prefers the preceding envelope when both neighbours are compatible")
        void prefersThePreceding() {
            var before = task(budget);
            var small = task(smallCost());
            var after = task(budget);

            var envelopes = pack(before, small, after);

            assertAll("The small envelope joins the preceding one",
                    () -> assertEquals(2, envelopes.size(), "One merge leaves two envelopes"),
                    () -> assertEquals(ids(before, small), envelopes.get(0).taskIds(),
                            "The small task joined the preceding one"),
                    () -> assertEquals(ids(after), envelopes.get(1).taskIds(),
                            "The following envelope stays as it was"));
        }

        @Test
        @DisplayName("leaves a small envelope alone without a compatible neighbour")
        void leavesItAloneWithoutACompatibleNeighbour() {
            var otherModule = otherThan(module);
            var before = task(otherModule, role, regularCost());
            var small = task(smallCost());
            var after = task(otherModule, role, regularCost());

            var envelopes = pack(before, small, after);

            assertEquals(3, envelopes.size(), "Without a compatible neighbour nothing merges");
            var alone = envelopes.get(1);
            assertAll("The small envelope stays alone",
                    () -> assertEquals(ids(small), alone.taskIds(), "The small task keeps its own envelope"),
                    () -> assertTrue(alone.belowMinimum(), "The lone small envelope is below minimum"),
                    () -> assertFalse(envelopes.get(0).belowMinimum(), "A regular envelope is not below minimum"));
        }
    }

    @Nested
    @DisplayName("the sealed envelopes")
    class SealedEnvelopes {

        @Test
        @DisplayName("keep every task contiguous and in task order")
        void keepTheTasksContiguousAndInOrder() {
            var otherModule = otherThan(module);
            var first = task(regularCost());
            var second = task(regularCost());
            var third = task(otherModule, role, regularCost());
            var fourth = task(otherModule, role, regularCost());
            var fifth = task(regularCost());

            var envelopes = pack(first, second, third, fourth, fifth);

            assertAll("The envelopes are contiguous slices",
                    () -> assertEquals(ids(first, second), envelopes.get(0).taskIds(), "The first slice is kept"),
                    () -> assertEquals(ids(third, fourth), envelopes.get(1).taskIds(), "The second slice is kept"),
                    () -> assertEquals(ids(fifth), envelopes.get(2).taskIds(), "The third slice is kept"),
                    () -> assertEquals(ids(first, second, third, fourth, fifth),
                            envelopes.stream().flatMap(envelope -> envelope.taskIds().stream()).toList(),
                            "The concatenation keeps the input order"));
        }

        @Test
        @DisplayName("are named envelope-1, envelope-2, ... in order, contract ids")
        void areNamedSequentially() {
            var first = task(regularCost());
            var second = task(otherThan(module), role, regularCost());
            var third = task(module, otherThan(role), regularCost());

            var envelopes = pack(first, second, third);

            assertEquals(List.of("envelope-1", "envelope-2", "envelope-3"),
                    envelopes.stream().map(PackedEnvelope::envelopeId).toList(),
                    "The envelope ids count up from one in order");
        }

        @Test
        @DisplayName("carry the distinct skills of their tasks, sorted")
        void carryTheDistinctSortedSkills() {
            var shared = name();
            var first = task(module, role, regularCost(), Set.of(shared, otherThan(shared)), List.of());
            var second = task(module, role, regularCost(), Set.of(shared, otherThan(shared)), List.of());
            var expected = new TreeSet<String>();
            expected.addAll(first.skillUris());
            expected.addAll(second.skillUris());

            var envelopes = pack(first, second);

            assertEquals(List.copyOf(expected), envelopes.getFirst().unionSkillUris(),
                    "The skill union is distinct and sorted");
        }

        @Test
        @DisplayName("report below minimum only under the minimum task work, contract boundary")
        void reportBelowMinimumAtTheBoundary() {
            assertAll("The minimum task work boundary",
                    () -> assertFalse(envelopeOf(MIN).belowMinimum(), "An envelope at the minimum is not below"),
                    () -> assertTrue(envelopeOf(MIN - 1).belowMinimum(), "An envelope under the minimum is below"));
        }

        private PackedEnvelope envelopeOf(long total) {
            return PackedEnvelope.builder()
                    .envelopeId("envelope-1")
                    .moduleId(module)
                    .role(role)
                    .taskIds(List.of())
                    .unionSkillUris(List.of())
                    .totalPredictedCostTokens(total)
                    .build();
        }
    }
}
