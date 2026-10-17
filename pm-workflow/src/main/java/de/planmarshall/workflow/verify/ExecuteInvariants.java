/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.verify;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import de.planmarshall.workflow.ast.CallValue;
import de.planmarshall.workflow.ast.StateBehavior;
import de.planmarshall.workflow.ast.StateNode;
import de.planmarshall.workflow.verify.CompiledInvariants.Check;
import de.planmarshall.workflow.verify.CompiledInvariants.Context;

/**
 * The invariants of the execute phase (Phase 5) of the plan unit and its {@code land} lane, {@code INV-EXEC-1} to
 * {@code INV-EXEC-3} (PM-IMPL-5).
 * <ul>
 * <li>{@link InvariantId#INV_EXEC_1}: a worktree initialization ({@code execute.worktree-init},
 * {@code land.worktree-init}, or {@code finalize.adopt-pr} of an {@code adopt} plan) lies on every path into a task
 * state, a job state and a {@code git.commit_and_push} call of Phase 5, and on every path into a call that binds the
 * action {@code loop_back}, unless the default unit reaches the same state without one; and every transition on
 * {@code loop_back_outline} targets a state of phase {@code outline}.</li>
 * <li>{@link InvariantId#INV_EXEC_2}: no state of the plan unit runs the commit primitive {@code git.stage-and-commit}
 * itself (every commit goes through {@code call git.commit_and_push}); {@code execute.deliverable-build} is a state of
 * the unit, and it and {@code execute.verify-sweep} lie on every path into {@code execute.exit-barrier},
 * {@code land.verify-sweep} on every path into {@code land.exit-barrier}, where the default unit has that dominance;
 * and every transition from a state of an earlier phase into a state of phase {@code finalize} leaves an exit
 * barrier or a post-run-only route ({@code plan.return}, {@code outline.empty-harvest}), is the {@code adopt} lane's
 * {@code init.ready -> finalize.adopt-pr}, enters a fallback state, or is a transition of the default unit. The task
 * footprint check of a {@code submit} runs in the task's return evaluation, which Rule 1 binds.</li>
 * <li>{@link InvariantId#INV_EXEC_3}: the envelope task states are the {@code job llm} states of Phase 5 with a
 * transition into {@code execute.evaluate-envelope-return}; there is at most one, it fans out to no list and no roles,
 * and every transition of it enters {@code execute.evaluate-envelope-return}; and the unit has no {@code job llm} state
 * of Phase 5 that the default unit does not have.</li>
 * </ul>
 *
 * @since 0.1
 */
final class ExecuteInvariants {

    /** The worktree initialization of the execute phase. */
    static final String WORKTREE_INIT = "execute.worktree-init";

    /** The worktree initialization of the {@code land} lane. */
    static final String LAND_WORKTREE_INIT = "land.worktree-init";

    /** The adoption of an existing pull request, which creates the worktree of an {@code adopt} plan. */
    static final String ADOPT_PR = "finalize.adopt-pr";

    /** The build after every deliverable. */
    static final String DELIVERABLE_BUILD = "execute.deliverable-build";

    /** The verify sweep of the execute phase. */
    static final String VERIFY_SWEEP = "execute.verify-sweep";

    /** The exit barrier of the execute phase. */
    static final String EXIT_BARRIER = "execute.exit-barrier";

    /** The verify sweep of the {@code land} lane. */
    static final String LAND_VERIFY_SWEEP = "land.verify-sweep";

    /** The exit barrier of the {@code land} lane. */
    static final String LAND_EXIT_BARRIER = "land.exit-barrier";

    /** The one state through which the plan reacts to envelope work. */
    static final String EVALUATE_ENVELOPE_RETURN = "execute.evaluate-envelope-return";

    /** The last state of the init phase, from which the {@code adopt} lane enters Phase 6. */
    static final String INIT_READY = InitInvariants.READY;

    /** The empty harvest of the outline phase, a post-run-only route. */
    static final String EMPTY_HARVEST = "outline.empty-harvest";

    /** The primitive of Stage 4 of {@code git.commit_and_push}, which only that unit runs. */
    static final String COMMIT_PRIMITIVE = "git.stage-and-commit";

    /** The remediation action whose fix runs as Phase 5 remediation tasks. */
    static final String LOOP_BACK = "loop_back";

    /** The outcome that loops back to the outline. */
    static final String LOOP_BACK_OUTLINE = "loop_back_outline";

    private static final Set<String> WORKTREE_INITS = Set.of(WORKTREE_INIT, LAND_WORKTREE_INIT, ADOPT_PR);

    private static final Set<String> PHASE_SIX_SOURCES = Set.of(EXIT_BARRIER, LAND_EXIT_BARRIER,
            CompiledInvariants.PLAN_RETURN, EMPTY_HARVEST);

    private static final Set<String> FALLBACKS = Set.of(CompiledInvariants.PLAN_RETURN, CompiledInvariants.HAND_BACK,
            CompiledInvariants.PLAN_ABORTED);

    private static final Set<String> AFTER_PHASE_FIVE = Set.of(CompiledInvariants.PHASE_FINALIZE, "completed",
            "aborted");

    private static final String PHASE_OUTLINE = "outline";

    private ExecuteInvariants() {
    }

    static Map<InvariantId, Check> checks() {
        return Map.of(
                InvariantId.INV_EXEC_1, ExecuteInvariants::worktree,
                InvariantId.INV_EXEC_2, ExecuteInvariants::footprintAndExit,
                InvariantId.INV_EXEC_3, ExecuteInvariants::serialEnvelopes);
    }

    private static void worktree(Context context) {
        var id = InvariantId.INV_EXEC_1;
        var unguarded = Context.avoiding(context.graph(), context.unit().startingStates(), WORKTREE_INITS);
        var referenceUnguarded = Context.avoiding(context.referenceGraph(), context.reference().startingStates(),
                WORKTREE_INITS);
        for (var state : context.states()) {
            if (WORKTREE_INITS.contains(state.id()) || !unguarded.contains(state.id())
                    || referenceUnguarded.contains(state.id())) {
                continue;
            }
            if (isPhaseFive(state) && needsWorktree(state)) {
                context.reportState(id, state, "a path reaches '%s' without passing a worktree initialization %s"
                        .formatted(state.id(), Context.sorted(WORKTREE_INITS)));
            } else if (bindsLoopBack(state)) {
                context.reportState(id, state, "a path reaches '%s', which binds '%s', without passing a worktree"
                        .formatted(state.id(), LOOP_BACK) + " initialization");
            }
        }
        for (var edge : Context.edges(context.unit())) {
            if (!LOOP_BACK_OUTLINE.equals(edge.transition().outcomeName())) {
                continue;
            }
            var intoOutline = context.state(edge.transition().targetStateId())
                    .filter(target -> PHASE_OUTLINE.equals(target.phase())).isPresent();
            if (!intoOutline) {
                context.reportTransition(id, edge.source(), edge.transition(),
                        "the transition '%s' targets '%s', which is not a state of the outline phase"
                                .formatted(LOOP_BACK_OUTLINE, edge.transition().targetStateId()));
            }
        }
    }

    private static boolean isPhaseFive(StateNode state) {
        return CompiledInvariants.PHASE_EXECUTE.contains(state.phase());
    }

    private static boolean needsWorktree(StateNode state) {
        return switch (state.behavior()) {
            case StateBehavior.ModelJob _, StateBehavior.JobStep _ -> true;
            case StateBehavior.SubWorkflowCall call -> CompiledInvariants.GIT_UNIT.equals(call.subWorkflowId());
            case StateBehavior.InteractiveLinks _, StateBehavior.AutoStep _, StateBehavior.QuestionWait _,
                StateBehavior.TerminalState _ -> false;
        };
    }

    private static boolean bindsLoopBack(StateNode state) {
        return state.behavior() instanceof StateBehavior.SubWorkflowCall call && call.paramBindings().values()
                .stream().anyMatch(value -> value instanceof CallValue.EnumList list
                && list.values().contains(LOOP_BACK));
    }

    private static void footprintAndExit(Context context) {
        var id = InvariantId.INV_EXEC_2;
        for (var state : context.states()) {
            if (Context.primitiveId(state).filter(COMMIT_PRIMITIVE::equals).isPresent()) {
                context.reportState(id, state, "the state '%s' commits with '%s' instead of calling '%s'"
                        .formatted(state.id(), COMMIT_PRIMITIVE, CompiledInvariants.GIT_UNIT));
            }
        }
        context.requirePresent(id, DELIVERABLE_BUILD);
        context.requireDominance(id, DELIVERABLE_BUILD, EXIT_BARRIER);
        context.requireDominance(id, VERIFY_SWEEP, EXIT_BARRIER);
        context.requireDominance(id, LAND_VERIFY_SWEEP, LAND_EXIT_BARRIER);
        for (var edge : Context.edges(context.unit())) {
            var source = edge.source();
            var transition = edge.transition();
            var target = context.state(transition.targetStateId());
            var entersPhaseSix = !AFTER_PHASE_FIVE.contains(source.phase()) && target
                    .filter(state -> CompiledInvariants.PHASE_FINALIZE.equals(state.phase())).isPresent();
            if (!entersPhaseSix || PHASE_SIX_SOURCES.contains(source.id()) || FALLBACKS.contains(target.get().id())
                    || INIT_READY.equals(source.id()) && ADOPT_PR.equals(target.get().id())
                    || Context.hasTransition(context.reference(), source.id(), transition.outcomeName(),
                    target.get().id())) {
                continue;
            }
            context.reportTransition(id, source, transition,
                    "the transition '%s' -> '%s' enters Phase 6 without passing an exit barrier".formatted(
                            transition.outcomeName(), transition.targetStateId()));
        }
    }

    private static void serialEnvelopes(Context context) {
        var id = InvariantId.INV_EXEC_3;
        context.requirePresent(id, EVALUATE_ENVELOPE_RETURN);
        var envelopeStates = context.states().stream()
                .filter(state -> isPhaseFive(state) && state.behavior() instanceof StateBehavior.ModelJob)
                .filter(state -> Context.transitions(state).stream()
                        .anyMatch(transition -> EVALUATE_ENVELOPE_RETURN.equals(transition.targetStateId())))
                .toList();
        if (envelopeStates.size() > 1) {
            envelopeStates.stream().skip(1).forEach(state -> context.reportState(id, state,
                    "'%s' is a second envelope task state besides '%s'".formatted(state.id(),
                            envelopeStates.getFirst().id())));
        }
        for (var state : envelopeStates) {
            var modelJob = (StateBehavior.ModelJob) state.behavior();
            if (modelJob.fanOutPrimitiveId().isPresent() || modelJob.rolesPrimitiveId().isPresent()) {
                context.reportState(id, state, "the envelope task state '%s' runs several tasks at a time"
                        .formatted(state.id()));
            }
            Context.transitions(state).stream()
                    .filter(transition -> !EVALUATE_ENVELOPE_RETURN.equals(transition.targetStateId()))
                    .forEach(transition -> context.reportTransition(id, state, transition,
                            "the envelope task state '%s' routes '%s' to '%s', not to '%s'".formatted(state.id(),
                                    transition.outcomeName(), transition.targetStateId(), EVALUATE_ENVELOPE_RETURN)));
        }
        var knownTaskStates = new HashSet<String>();
        context.reference().states().values().stream()
                .filter(state -> isPhaseFive(state) && state.behavior() instanceof StateBehavior.ModelJob)
                .forEach(state -> knownTaskStates.add(state.id()));
        context.states().stream()
                .filter(state -> isPhaseFive(state) && state.behavior() instanceof StateBehavior.ModelJob)
                .filter(state -> !knownTaskStates.contains(state.id()))
                .forEach(state -> context.reportState(id, state,
                        "the task state '%s' of Phase 5 is not a task state of the default unit".formatted(state.id())));
    }
}
