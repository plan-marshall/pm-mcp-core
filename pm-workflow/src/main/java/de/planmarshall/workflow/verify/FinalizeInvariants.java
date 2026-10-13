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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import de.planmarshall.workflow.ast.CallValue;
import de.planmarshall.workflow.ast.StateBehavior;
import de.planmarshall.workflow.ast.StateNode;
import de.planmarshall.workflow.ast.StepCompletionStatus;
import de.planmarshall.workflow.ast.WorkflowDefinition;
import de.planmarshall.workflow.finalize.FinalizeStepDescriptor;
import de.planmarshall.workflow.finalize.FinalizeStepDescriptor.LaneClass;
import de.planmarshall.workflow.verify.CompiledInvariants.Check;
import de.planmarshall.workflow.verify.CompiledInvariants.Context;

/**
 * The invariants of the finalize phase (Phase 6) of the plan unit, {@code INV-FIN-1} to {@code INV-FIN-6}
 * (PM-IMPL-5).
 * <ul>
 * <li>{@link InvariantId#INV_FIN_1}: every step of lane class {@code core} or {@code derived-state} of the default
 * roster is completed by some {@code completes} clause of the unit. Its order and descriptor are those of the roster,
 * which a unit cannot change.</li>
 * <li>{@link InvariantId#INV_FIN_2}: every violation of the finalize pipeline validator over the project's roster,
 * located at the first state with a transition that completes the step, or at the unit header.</li>
 * <li>{@link InvariantId#INV_FIN_3}: every Rule 9 violation of the static verifier (completion once per firing).</li>
 * <li>{@link InvariantId#INV_FIN_4}: no state of the plan unit runs a push primitive ({@code git.execute-push},
 * {@code git.push-with-lease}) itself; every {@code git.commit_and_push} call but {@code finalize.push-barrier} binds
 * {@code push_mode} as the default unit does at the same state, and a call the default unit does not have either lies
 * after {@code finalize.create-pr} or binds a {@code push_mode} that a call of the default unit before the pull
 * request binds; {@code finalize.create-pr} is a state of the unit and lies on every path into
 * {@code finalize.merge-gate} where it does so in the default unit.</li>
 * <li>{@link InvariantId#INV_FIN_5}: {@code finalize.merge-gate} is a state of the unit and lies on every path into a
 * state that routes the outcome {@code merged} (other than {@code finalize.archive-plan}); its outcome
 * {@code spend_overrun}, routed where the default unit routes it, enters a call of {@code remediation.spend}.</li>
 * <li>{@link InvariantId#INV_FIN_6}: every transition on {@code merged} (other than that of
 * {@code finalize.archive-plan}) completes {@code default:branch-cleanup} as {@code done}; every {@code call} state of
 * Phases 5 and 6 routes {@code stepped_out} to {@code finalize.hand-back}, the only fallback after Phase 4; the unit
 * completes {@code default:emit-landing} where the default unit does; and only {@code finalize.archive-plan} enters
 * the delivery terminals {@code completed}, {@code handed_back} and {@code returned}.</li>
 * </ul>
 *
 * @since 0.1
 */
final class FinalizeInvariants {

    /** The push barrier, the only push before the pull request. */
    static final String PUSH_BARRIER = "finalize.push-barrier";

    /** The creation of the pull request. */
    static final String CREATE_PR = "finalize.create-pr";

    /** The pre-merge barrier at order 70. */
    static final String MERGE_GATE = "finalize.merge-gate";

    /** The last step, the only path into the delivery terminals. */
    static final String ARCHIVE_PLAN = "finalize.archive-plan";

    /** The step the merge completes. */
    static final String BRANCH_CLEANUP = "default:branch-cleanup";

    /** The landing step that runs for every plan. */
    static final String EMIT_LANDING = "default:emit-landing";

    /** The degenerate remediation unit of a spend overrun. */
    static final String SPEND_REMEDIATION = "remediation.spend";

    /** The outcome of the merge. */
    static final String MERGED = "merged";

    /** The outcome of the pre-merge barrier on a spend overrun. */
    static final String SPEND_OVERRUN = "spend_overrun";

    /** The parameter of {@code git.commit_and_push} that decides whether it pushes. */
    static final String PUSH_MODE = "push_mode";

    /** The delivery terminals of a plan. */
    static final Set<String> DELIVERY_TERMINALS = Set.of("completed", "handed_back", "returned");

    /** The push primitives of {@code git.commit_and_push}, which only that unit runs. */
    static final Set<String> PUSH_PRIMITIVES = Set.of("git.execute-push", "git.push-with-lease");

    private static final Set<LaneClass> MANDATORY_LANE_CLASSES = Set.of(LaneClass.CORE, LaneClass.DERIVED_STATE);

    private FinalizeInvariants() {
    }

    static Map<InvariantId, Check> checks() {
        return Map.of(
                InvariantId.INV_FIN_1, FinalizeInvariants::mandatorySteps,
                InvariantId.INV_FIN_2, FinalizeInvariants::orderingBands,
                InvariantId.INV_FIN_3, FinalizeInvariants::completionOncePerFiring,
                InvariantId.INV_FIN_4, FinalizeInvariants::pushBarrier,
                InvariantId.INV_FIN_5, FinalizeInvariants::preMergeBarrier,
                InvariantId.INV_FIN_6, FinalizeInvariants::cleanupFallbackAndLanding);
    }

    private static void mandatorySteps(Context context) {
        var completed = completedSteps(context.unit());
        context.defaultRoster().stream()
                .filter(FinalizeInvariants::isMandatory)
                .map(FinalizeStepDescriptor::name)
                .filter(step -> !completed.contains(step))
                .forEach(step -> context.reportUnit(InvariantId.INV_FIN_1,
                        "the mandatory step '%s' is completed by no transition".formatted(step)));
    }

    private static boolean isMandatory(FinalizeStepDescriptor step) {
        return step.lane().map(lane -> MANDATORY_LANE_CLASSES.contains(lane.laneClass())).orElse(false);
    }

    private static Set<String> completedSteps(WorkflowDefinition definition) {
        var steps = new LinkedHashSet<String>();
        Context.edges(definition)
                .forEach(edge -> edge.transition().completions().forEach(completion -> steps.add(completion.stepId())));
        return steps;
    }

    /**
     * @param definition a unit
     * @param step       the identifier of a finalize step
     * @return the first state, in declaration order, with a transition that completes the step
     */
    static Optional<StateNode> firstCompletingState(WorkflowDefinition definition, String step) {
        return definition.states().values().stream()
                .filter(state -> Context.transitions(state).stream().anyMatch(transition -> transition.completions()
                        .stream().anyMatch(completion -> step.equals(completion.stepId()))))
                .findFirst();
    }

    private static void orderingBands(Context context) {
        for (var violation : context.pipeline()) {
            var message = "rule %d (%s) of the step '%s': %s".formatted(violation.rule().specificationRule(),
                    violation.rule(), violation.stepName(), violation.message());
            firstCompletingState(context.unit(), violation.stepName()).ifPresentOrElse(
                    state -> context.reportState(InvariantId.INV_FIN_2, state, message),
                    () -> context.reportUnit(InvariantId.INV_FIN_2, message));
        }
    }

    private static void completionOncePerFiring(Context context) {
        CompiledInvariants.ruleViolations(context.unit(), context.rules()).stream()
                .filter(violation -> violation.id() == InvariantId.INV_FIN_3).forEach(context.found()::add);
    }

    private static void pushBarrier(Context context) {
        var id = InvariantId.INV_FIN_4;
        reportSelfPushes(context);
        var prePullRequestModes = prePullRequestModes(context);
        for (var state : context.states()) {
            if (isCommitCall(state) && !PUSH_BARRIER.equals(state.id())) {
                checkCommitCall(context, state, prePullRequestModes);
            }
        }
        context.requirePresent(id, CREATE_PR);
        context.requireDominance(id, CREATE_PR, MERGE_GATE);
    }

    /**
     * Reports every state of the unit that runs a push primitive itself instead of calling the git unit.
     */
    private static void reportSelfPushes(Context context) {
        for (var state : context.states()) {
            if (Context.primitiveId(state).filter(PUSH_PRIMITIVES::contains).isPresent()) {
                context.reportState(InvariantId.INV_FIN_4, state,
                        "the state '%s' pushes itself instead of calling '%s'".formatted(state.id(),
                                CompiledInvariants.GIT_UNIT));
            }
        }
    }

    /**
     * @return the push modes the calls of the git unit in the default unit bind before the pull request, the push
     *         barrier excluded, in declaration order
     */
    private static Set<Optional<CallValue>> prePullRequestModes(Context context) {
        return context.reference().states().values().stream()
                .filter(state -> isCommitCall(state) && !PUSH_BARRIER.equals(state.id())
                        && !context.referenceDominates(CREATE_PR, state.id()))
                .map(FinalizeInvariants::pushMode)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Checks one call of the git unit: a call the default unit has at the same state binds its push mode; a new
     * call lies after the pull request or binds a push mode the default unit binds before it.
     */
    private static void checkCommitCall(Context context, StateNode state,
            Set<Optional<CallValue>> prePullRequestModes) {
        var id = InvariantId.INV_FIN_4;
        var mode = pushMode(state);
        var counterpart = context.referenceState(state.id()).filter(FinalizeInvariants::isCommitCall);
        if (counterpart.isPresent()) {
            var defaultMode = pushMode(counterpart.get());
            if (!defaultMode.equals(mode)) {
                context.reportState(id, state, "the call of '%s' in '%s' binds %s %s, the default unit %s"
                        .formatted(CompiledInvariants.GIT_UNIT, state.id(), PUSH_MODE, describe(mode),
                                describe(defaultMode)));
            }
        } else if (!afterPullRequest(context, state) && !prePullRequestModes.contains(mode)) {
            context.reportState(id, state, "the new call of '%s' in '%s' may push before the pull request (%s %s)"
                    .formatted(CompiledInvariants.GIT_UNIT, state.id(), PUSH_MODE, describe(mode)));
        }
    }

    private static boolean afterPullRequest(Context context, StateNode state) {
        return context.state(CREATE_PR).isPresent() && context.dominates(CREATE_PR, state.id());
    }

    private static boolean isCommitCall(StateNode state) {
        return Context.callee(state).filter(CompiledInvariants.GIT_UNIT::equals).isPresent();
    }

    private static Optional<CallValue> pushMode(StateNode state) {
        return state.behavior() instanceof StateBehavior.SubWorkflowCall call
                ? Optional.ofNullable(call.paramBindings().get(PUSH_MODE)) : Optional.empty();
    }

    private static String describe(Optional<CallValue> value) {
        return value.map(bound -> switch (bound) {
            case CallValue.EnumValue(var text) -> text;
            case CallValue.IntValue(var number) -> Integer.toString(number);
            case CallValue.EnumList(var values) -> values.toString();
        }).orElse("unbound");
    }

    private static void preMergeBarrier(Context context) {
        var id = InvariantId.INV_FIN_5;
        if (!context.requirePresent(id, MERGE_GATE)) {
            return;
        }
        var gate = context.state(MERGE_GATE);
        for (var state : context.states()) {
            var merges = !ARCHIVE_PLAN.equals(state.id()) && !MERGE_GATE.equals(state.id())
                    && !Context.transitionsOn(state, MERGED).isEmpty();
            if (merges && (gate.isEmpty() || !context.dominates(MERGE_GATE, state.id()))) {
                context.reportState(id, state, "a path reaches the merge in '%s' without passing '%s'"
                        .formatted(state.id(), MERGE_GATE));
            }
        }
        gate.ifPresent(mergeGate -> {
            var overruns = Context.transitionsOn(mergeGate, SPEND_OVERRUN);
            var referenceOverruns = context.referenceState(MERGE_GATE)
                    .map(counterpart -> Context.transitionsOn(counterpart, SPEND_OVERRUN)).orElse(List.of());
            if (overruns.isEmpty() && !referenceOverruns.isEmpty()) {
                context.reportState(id, mergeGate, "'%s' routes no '%s'".formatted(MERGE_GATE, SPEND_OVERRUN));
            }
            for (var transition : overruns) {
                var callsSpend = context.state(transition.targetStateId()).flatMap(Context::callee)
                        .filter(SPEND_REMEDIATION::equals).isPresent();
                if (!callsSpend) {
                    context.reportTransition(id, mergeGate, transition,
                            "the outcome '%s' enters '%s', which does not call '%s'".formatted(SPEND_OVERRUN,
                                    transition.targetStateId(), SPEND_REMEDIATION));
                }
            }
        });
    }

    private static void cleanupFallbackAndLanding(Context context) {
        var id = InvariantId.INV_FIN_6;
        for (var edge : Context.edges(context.unit())) {
            var source = edge.source();
            var transition = edge.transition();
            if (MERGED.equals(transition.outcomeName()) && !ARCHIVE_PLAN.equals(source.id())
                    && transition.completions().stream().noneMatch(completion -> BRANCH_CLEANUP
                    .equals(completion.stepId()) && completion.status() == StepCompletionStatus.DONE)) {
                context.reportTransition(id, source, transition, "the outcome '%s' of '%s' does not complete '%s' as done"
                        .formatted(MERGED, source.id(), BRANCH_CLEANUP));
            }
            var lateCall = source.behavior() instanceof StateBehavior.SubWorkflowCall
                    && (CompiledInvariants.PHASE_EXECUTE.contains(source.phase())
                    || CompiledInvariants.PHASE_FINALIZE.equals(source.phase()));
            if (lateCall && CompiledInvariants.STEPPED_OUT.equals(transition.outcomeName())
                    && !CompiledInvariants.HAND_BACK.equals(transition.targetStateId())) {
                context.reportTransition(id, source, transition, "the call state '%s' routes '%s' to '%s', not to '%s'"
                        .formatted(source.id(), CompiledInvariants.STEPPED_OUT, transition.targetStateId(),
                                CompiledInvariants.HAND_BACK));
            }
            if (DELIVERY_TERMINALS.contains(transition.targetStateId()) && !ARCHIVE_PLAN.equals(source.id())) {
                context.reportTransition(id, source, transition, "'%s' enters the delivery terminal '%s'; only '%s' may"
                        .formatted(source.id(), transition.targetStateId(), ARCHIVE_PLAN));
            }
        }
        if (completedSteps(context.reference()).contains(EMIT_LANDING)
                && !completedSteps(context.unit()).contains(EMIT_LANDING)) {
            context.reportUnit(id, "the step '%s' is completed by no transition".formatted(EMIT_LANDING));
        }
    }
}
