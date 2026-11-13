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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import lombok.Builder;
import lombok.experimental.UtilityClass;

/**
 * The deterministic packing of the ordered plan tasks into envelopes ({@code EnvelopePacker}).
 * <p>
 * The packer runs Next-Fit in task order: it closes the open envelope when the next task targets another module, is
 * served by another role, or would push the envelope past the budget; a task whose predicted cost alone exceeds the
 * budget sits alone. The execution profile is no sharing criterion. An envelope whose summed predicted cost stays
 * below {@link #MIN_JOB_TOKENS} is then merged into its adjacent envelope of the same module and role, the preceding
 * one first, else the following one; without such a neighbour it stays alone and reports
 * {@link PackedEnvelope#belowMinimum()}. Every envelope is a contiguous slice of the input, so the dependency order
 * of the input is kept, and the envelopes are named {@code envelope-1}, {@code envelope-2}, … in order.
 * <p>
 * The packer never derives a task's cost; it only sums the stamped {@link PlannedTaskSummary#predictedCostTokens()}.
 * <p>
 * Specification: {@code doc/specification/model-work/06-envelopes-questions-deadlines.adoc}, § Deterministic Envelope
 * Packing Algorithm, and § Cost Sizing, items 4 to 6.
 */
@UtilityClass
public class EnvelopePacker {

    /** The minimum predicted work in tokens that justifies an envelope task of its own. */
    public static final long MIN_JOB_TOKENS = 10_000;

    private static final String ENVELOPE_ID_PREFIX = "envelope-";

    /**
     * A plan task as the packer sees it.
     *
     * @param taskId              the task's id, for example {@code TASK-001}
     * @param moduleId            the module the task targets
     * @param role                the role the execute unit names for the task
     * @param skillUris           the skills of the task
     * @param costSize            the task's cost size
     * @param predictedCostTokens the task's stamped predicted tokens
     * @param dependsOnTaskIds    the ids of the tasks the task depends on
     */
    @Builder
    public record PlannedTaskSummary(String taskId, String moduleId, String role, Set<String> skillUris,
    CostSize costSize, long predictedCostTokens, List<String> dependsOnTaskIds) {

        /**
         * Copies the collections.
         *
         * @param taskId              the task's id
         * @param moduleId            the module the task targets
         * @param role                the role the execute unit names for the task
         * @param skillUris           the skills of the task
         * @param costSize            the task's cost size
         * @param predictedCostTokens the task's stamped predicted tokens
         * @param dependsOnTaskIds    the ids of the tasks the task depends on
         */
        public PlannedTaskSummary {
            Objects.requireNonNull(taskId, "taskId");
            Objects.requireNonNull(moduleId, "moduleId");
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(costSize, "costSize");
            skillUris = Set.copyOf(skillUris);
            dependsOnTaskIds = List.copyOf(dependsOnTaskIds);
        }
    }

    /**
     * A sealed envelope.
     *
     * @param envelopeId               the envelope's id, {@code envelope-N}
     * @param moduleId                 the module all its tasks target
     * @param role                     the role that serves all its tasks
     * @param taskIds                  the ids of its tasks, in task order
     * @param unionSkillUris           the distinct skills of its tasks, sorted
     * @param totalPredictedCostTokens the sum of its tasks' predicted tokens
     */
    @Builder
    public record PackedEnvelope(String envelopeId, String moduleId, String role, List<String> taskIds,
    List<String> unionSkillUris, long totalPredictedCostTokens) {

        /**
         * Copies the lists.
         *
         * @param envelopeId               the envelope's id
         * @param moduleId                 the module all its tasks target
         * @param role                     the role that serves all its tasks
         * @param taskIds                  the ids of its tasks, in task order
         * @param unionSkillUris           the distinct skills of its tasks, sorted
         * @param totalPredictedCostTokens the sum of its tasks' predicted tokens
         */
        public PackedEnvelope {
            Objects.requireNonNull(envelopeId, "envelopeId");
            Objects.requireNonNull(moduleId, "moduleId");
            Objects.requireNonNull(role, "role");
            taskIds = List.copyOf(taskIds);
            unionSkillUris = List.copyOf(unionSkillUris);
        }

        /**
         * Tells whether the envelope stays below the minimum task work, which happens only for an envelope that had
         * no adjacent envelope of its module and role to merge into; the plan lists it as {@code below_minimum_task}.
         *
         * @return {@code true} if {@link #totalPredictedCostTokens()} is below {@link #MIN_JOB_TOKENS}
         */
        public boolean belowMinimum() {
            return totalPredictedCostTokens < MIN_JOB_TOKENS;
        }
    }

    /**
     * Packs the ordered plan tasks into envelopes.
     *
     * @param orderedTasks         the plan tasks in topological order over their dependencies, ties broken by task id
     * @param envelopeBudgetTokens the per-envelope budget in tokens
     * @return the envelopes in order, each a contiguous slice of the input; empty for an empty input
     */
    public static List<PackedEnvelope> packEnvelopes(List<PlannedTaskSummary> orderedTasks,
            long envelopeBudgetTokens) {
        Objects.requireNonNull(orderedTasks, "orderedTasks");
        List<List<PlannedTaskSummary>> groups = new ArrayList<>();
        List<PlannedTaskSummary> current = new ArrayList<>();
        long currentTotal = 0;
        for (PlannedTaskSummary task : orderedTasks) {
            boolean fits = !current.isEmpty()
                    && canShareEnvelope(current.getFirst(), task)
                    && currentTotal + task.predictedCostTokens() <= envelopeBudgetTokens;
            if (!fits && !current.isEmpty()) {
                groups.add(current);
                current = new ArrayList<>();
                currentTotal = 0;
            }
            current.add(task);
            currentTotal += task.predictedCostTokens();
        }
        if (!current.isEmpty()) {
            groups.add(current);
        }

        mergeBelowMinimum(groups);

        List<PackedEnvelope> envelopes = new ArrayList<>();
        for (List<PlannedTaskSummary> group : groups) {
            envelopes.add(sealEnvelope(envelopes.size() + 1, group));
        }
        return List.copyOf(envelopes);
    }

    private static boolean canShareEnvelope(PlannedTaskSummary first, PlannedTaskSummary candidate) {
        return first.moduleId().equals(candidate.moduleId()) && first.role().equals(candidate.role());
    }

    /**
     * Merges every group below the minimum task work into its adjacent compatible group, the preceding one first.
     * Adjacent compatible groups were split by the budget alone, so every merge yields a group over the budget; the
     * minimum task work takes precedence by design. Contiguity is kept.
     * <p>
     * The groups are examined in order. After a merge the index stays where it is, so the group that now sits there
     * is examined next: the following group after a merge into the preceding one, the merged group itself after a
     * merge into the following one.
     */
    private static void mergeBelowMinimum(List<List<PlannedTaskSummary>> groups) {
        int index = 0;
        while (index < groups.size()) {
            int target = mergeTarget(groups, index);
            if (target < 0) {
                index++;
            } else {
                mergeInto(groups, index, target);
            }
        }
    }

    /**
     * Finds the group the group at {@code index} merges into.
     *
     * @param groups the groups in order
     * @param index  the index of the examined group
     * @return the index of the adjacent compatible group, the preceding one first, else the following one; {@code -1}
     *         when the examined group reaches {@link #MIN_JOB_TOKENS} or has no compatible neighbour
     */
    private static int mergeTarget(List<List<PlannedTaskSummary>> groups, int index) {
        List<PlannedTaskSummary> small = groups.get(index);
        if (total(small) >= MIN_JOB_TOKENS) {
            return -1;
        }
        if (index > 0 && canShareEnvelope(groups.get(index - 1).getFirst(), small.getFirst())) {
            return index - 1;
        }
        if (index + 1 < groups.size() && canShareEnvelope(groups.get(index + 1).getFirst(), small.getFirst())) {
            return index + 1;
        }
        return -1;
    }

    /**
     * Moves the group at {@code index} into its adjacent target group and removes it: to the end of a preceding
     * target, to the front of a following one, so contiguity is kept.
     *
     * @param groups the groups in order
     * @param index  the index of the group to merge
     * @param target the index of the adjacent group it merges into, {@code index - 1} or {@code index + 1}
     */
    private static void mergeInto(List<List<PlannedTaskSummary>> groups, int index, int target) {
        List<PlannedTaskSummary> small = groups.get(index);
        if (target < index) {
            groups.get(target).addAll(small);
        } else {
            groups.get(target).addAll(0, small);
        }
        groups.remove(index);
    }

    private static long total(List<PlannedTaskSummary> group) {
        return group.stream().mapToLong(PlannedTaskSummary::predictedCostTokens).sum();
    }

    private static PackedEnvelope sealEnvelope(int index, List<PlannedTaskSummary> group) {
        var head = group.getFirst();
        return PackedEnvelope.builder()
                .envelopeId(ENVELOPE_ID_PREFIX + index)
                .moduleId(head.moduleId())
                .role(head.role())
                .taskIds(group.stream().map(PlannedTaskSummary::taskId).toList())
                .unionSkillUris(group.stream()
                        .flatMap(task -> task.skillUris().stream())
                        .distinct()
                        .sorted()
                        .toList())
                .totalPredictedCostTokens(total(group))
                .build();
    }
}
