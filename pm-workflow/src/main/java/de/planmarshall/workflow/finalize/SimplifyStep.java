/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.finalize;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import de.planmarshall.workflow.finalize.FinalizeStepDescriptor.InputScope;
import de.planmarshall.workflow.finalize.FinalizeStepDescriptor.Lane;
import de.planmarshall.workflow.finalize.FinalizeStepDescriptor.LaneClass;
import de.planmarshall.workflow.finalize.ProposalRecord.Disposition;

/**
 * The {@code default:finalize-step-simplify} step and the two tasks of its firing (Phase Workflows § Finalize Step
 * Ordering Bands): the read-only review task {@code review-simplify} that submits proposals with the clone candidates as
 * evidence, and the writer task {@code simplify-apply} that applies the accepted subset in the plan worktree.
 * <p>
 * The review task carries no write tool and never takes the plan's writer slot, so the step itself declares
 * {@code mutatesSource == false} and files no finding: a clone candidate is evidence for the restraint ladder, never a
 * finding by itself. The apply task's change routes through {@code finalize.settle-commit} like every other change.
 *
 * @since 0.1
 */
public final class SimplifyStep {

    /** The step id. */
    public static final String NAME = "default:finalize-step-simplify";

    /** The role of the review task, read-only. */
    public static final String REVIEW_ROLE = "review-simplify";

    /** The vocabulary of the review task. */
    public static final String SKILL = "skill://pm/finalize/simplify";

    /** The role of the apply task, the writer. */
    public static final String APPLY_ROLE = "implement";

    /** The state a change of the apply task routes through. */
    public static final String SETTLE_STATE = "finalize.settle-commit";

    /** The fact that carries the clone candidates to the review task. */
    public static final String CLONE_CANDIDATES = "clone_candidates";

    /** The fact that carries the recorded proposals to the apply task. */
    public static final String PROPOSALS = "proposals";

    private SimplifyStep() {
    }

    /**
     * @return the descriptor of the step, read-only at order 5
     */
    public static FinalizeStepDescriptor descriptor() {
        return new FinalizeStepDescriptor(NAME, FinalizeStepDescriptor.SIMPLIFY_ORDER, true, Set.of(),
                "Reviews the touched code for simplifications and proposes them, read-only",
                false, true, Optional.of(InputScope.PLAN_FOOTPRINT), List.of(),
                false, false,
                List.of("worktree", "outline", "intent"),
                List.of(),
                List.of("work_performed", CLONE_CANDIDATES, PROPOSALS),
                Optional.of(new Lane(LaneClass.PRUNABLE, Optional.empty(), Optional.of("land_lane"), "M")),
                List.of(), List.of(), Optional.of(REVIEW_ROLE), List.of(),
                Optional.empty(), List.of());
    }

    /**
     * @return the review task, which carries no write tool and never takes the plan's writer slot
     */
    public static ReviewTask reviewTask() {
        return new ReviewTask(REVIEW_ROLE, SKILL, false, false);
    }

    /**
     * @param candidates the clone candidates the server computed before the task
     * @return the facts of the review task
     */
    public static Map<String, Object> reviewFacts(List<CloneCandidate> candidates) {
        Objects.requireNonNull(candidates, "candidates");
        var facts = new LinkedHashMap<String, Object>();
        facts.put(CLONE_CANDIDATES, List.copyOf(candidates));
        return Map.copyOf(facts);
    }

    /**
     * Records a submission of {@code submit:simplify} as proposal records, each open.
     *
     * @param proposals the submitted proposals
     * @param firstNumber the next free proposal number of the plan
     * @return the records, in submission order
     * @throws IllegalArgumentException if the first number is below 1
     */
    public static List<ProposalRecord> record(List<SimplifyProposal> proposals, int firstNumber) {
        Objects.requireNonNull(proposals, "proposals");
        if (firstNumber < 1) {
            throw new IllegalArgumentException("the first proposal number is at least 1: " + firstNumber);
        }
        var records = new ArrayList<ProposalRecord>();
        for (var index = 0; index < proposals.size(); index++) {
            records.add(new ProposalRecord("SP-" + (firstNumber + index), proposals.get(index), Disposition.OPEN));
        }
        return List.copyOf(records);
    }

    /**
     * @param records the open proposal records
     * @return the facts of the apply task
     */
    public static Map<String, Object> applyFacts(List<ProposalRecord> records) {
        Objects.requireNonNull(records, "records");
        var facts = new LinkedHashMap<String, Object>();
        facts.put(PROPOSALS, List.copyOf(records));
        return Map.copyOf(facts);
    }

    /**
     * Applies the accepted subset: every accepted proposal becomes {@code accepted}, every other open proposal
     * {@code declined}.
     *
     * @param records  the proposal records of the firing
     * @param accepted the proposal ids the task accepted, possibly empty
     * @return the records after the apply and whether the step routes through {@value #SETTLE_STATE}
     * @throws IllegalArgumentException if an accepted id is not an open proposal of the firing
     */
    public static ApplyResult apply(List<ProposalRecord> records, List<String> accepted) {
        Objects.requireNonNull(records, "records");
        Objects.requireNonNull(accepted, "accepted");
        for (var id : accepted) {
            var known = records.stream().anyMatch(record -> record.proposalId().equals(id)
                    && record.disposition() == Disposition.OPEN);
            if (!known) {
                throw new IllegalArgumentException("the accepted id '%s' is no open proposal of the firing"
                        .formatted(id));
            }
        }
        var updated = records.stream().map(record -> {
            if (record.disposition() != Disposition.OPEN) {
                return record;
            }
            return record.with(accepted.contains(record.proposalId()) ? Disposition.ACCEPTED : Disposition.DECLINED);
        }).toList();
        return new ApplyResult(updated, !accepted.isEmpty());
    }

    /**
     * The review task of the step.
     *
     * @param role            the role
     * @param skill           the vocabulary
     * @param carriesWriteTool whether the task may write
     * @param takesWriterSlot whether the task takes the plan's writer slot
     * @since 0.1
     */
    public record ReviewTask(String role, String skill, boolean carriesWriteTool, boolean takesWriterSlot) {

        /**
         * @throws NullPointerException if a value is {@code null}
         */
        public ReviewTask {
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(skill, "skill");
        }
    }

    /**
     * The result of applying a submission of {@code apply}.
     *
     * @param records the records after the apply
     * @param applied whether at least one proposal was accepted, which routes the change through
     *                {@value SimplifyStep#SETTLE_STATE}
     * @since 0.1
     */
    public record ApplyResult(List<ProposalRecord> records, boolean applied) {

        /**
         * @throws NullPointerException if the records are {@code null}
         */
        public ApplyResult {
            records = List.copyOf(Objects.requireNonNull(records, "records"));
        }

        /** @return the state the applied change routes through */
        public String settleState() {
            return SETTLE_STATE;
        }
    }
}
