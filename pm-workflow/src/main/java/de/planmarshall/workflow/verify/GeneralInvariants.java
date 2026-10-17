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

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import de.planmarshall.core.spi.OutcomeClass;
import de.planmarshall.workflow.ast.OutcomeTransition;
import de.planmarshall.workflow.ast.StateBehavior;
import de.planmarshall.workflow.ast.StateNode;
import de.planmarshall.workflow.verify.CompiledInvariants.Check;
import de.planmarshall.workflow.verify.CompiledInvariants.Context;

/**
 * The general invariants every project unit satisfies, {@code INV-GEN-1} to {@code INV-GEN-5} (PM-IMPL-5).
 * <ul>
 * <li>{@link InvariantId#INV_GEN_1}: every violation of the static verifier (Rules 1-12) is reported, except the Rule 9
 * part of the plan unit, which is {@link InvariantId#INV_FIN_3}; the unit's scope and start state equal those of the
 * default unit, and so do its terminal states, each with its outcome and class. That no transition targets
 * {@code plan.aborted} is Rule 3's part of the verifier's result.</li>
 * <li>{@link InvariantId#INV_GEN_2}, tree part: a terminal with the outcome {@code waived} exists only in a remediation
 * unit, and a state that routes the outcome {@code waived} or {@code all_waived} of its own primitive runs a primitive
 * that declares at least one waivable gate. The rest is the named hook of the invariant.</li>
 * <li>{@link InvariantId#INV_GEN_3}: no state is named after a condition kind or a reserved state identifier
 * ({@code <phase>.blocked}, {@code <phase>.footprint-violation}, {@code <phase>.config-drift}), and no transition
 * targets one.</li>
 * <li>{@link InvariantId#INV_GEN_4}, tree part: every state that lies on every path of the default unit into its
 * success terminals is a state of the unit and lies on every path of the unit into its success terminals. The rest is
 * the named hook of the invariant.</li>
 * <li>{@link InvariantId#INV_GEN_5}: the unit is not {@code worker-task}, defines no state of it and calls it nowhere;
 * it has no resting {@code link} state and no question state with declared links; every {@code job llm} state routes
 * {@code lost} to no terminal of class {@code success} (except {@code adjudicate.vote}, whose {@code lost} ends
 * {@code adjudicate.escalated}), and to the same state as the default unit where that declares the route.</li>
 * </ul>
 *
 * @since 0.1
 */
final class GeneralInvariants {

    /** The task protocol unit, compiled only; no unit defines, calls or replaces it. */
    static final String WORKER_TASK = "worker-task";

    /** The outcome of a primitive or a sub-workflow that admits a failure through a consumed waiver. */
    static final String WAIVED = "waived";

    /** The outcome of a gather job whose every failure is covered by a consumed waiver. */
    static final String ALL_WAIVED = "all_waived";

    /** The vote state of the adjudication unit, the one task state whose {@code lost} ends in a success terminal. */
    static final String ADJUDICATE_VOTE = "adjudicate.vote";

    /** The terminal the {@code lost} of {@link #ADJUDICATE_VOTE} ends in. */
    static final String ADJUDICATE_ESCALATED = "adjudicate.escalated";

    private static final Set<String> WAIVER_OUTCOMES = Set.of(WAIVED, ALL_WAIVED);

    private GeneralInvariants() {
    }

    static Map<InvariantId, Check> checks() {
        return Map.of(
                InvariantId.INV_GEN_1, GeneralInvariants::rulesScopeAndTerminals,
                InvariantId.INV_GEN_2, GeneralInvariants::waiverDataFlow,
                InvariantId.INV_GEN_3, GeneralInvariants::conditionOverlays,
                InvariantId.INV_GEN_4, GeneralInvariants::noGuaranteeWeakening,
                InvariantId.INV_GEN_5, GeneralInvariants::modelWorkOnlyAsTasks);
    }

    private static void rulesScopeAndTerminals(Context context) {
        var id = InvariantId.INV_GEN_1;
        CompiledInvariants.ruleViolations(context.unit(), context.rules()).stream()
                .filter(violation -> violation.id() == id).forEach(context.found()::add);
        var unit = context.unit();
        var reference = context.reference();
        if (unit.scope() != reference.scope()) {
            context.reportUnit(id, "the scope %s differs from the scope %s of the default unit".formatted(unit.scope(),
                    reference.scope()));
        }
        if (!unit.startState().equals(reference.startState())) {
            context.reportUnit(id, "the start state %s differs from the start state %s of the default unit"
                    .formatted(describeStart(unit.startState()), describeStart(reference.startState())));
        }
        for (var terminal : reference.states().values()) {
            if (Context.isTerminal(terminal) && context.state(terminal.id()).filter(Context::isTerminal).isEmpty()) {
                context.reportUnit(id, "the terminal state '%s' of the default unit is missing".formatted(terminal.id()));
            }
        }
        for (var state : context.states()) {
            if (!Context.isTerminal(state)) {
                continue;
            }
            var counterpart = context.referenceState(state.id()).filter(Context::isTerminal);
            if (counterpart.isEmpty()) {
                context.reportState(id, state, "the terminal state '%s' is not a terminal state of the default unit"
                        .formatted(state.id()));
            } else if (!counterpart.get().terminalOutcome().equals(state.terminalOutcome())) {
                context.reportState(id, state, "the terminal state '%s' reports %s, the default unit %s"
                        .formatted(state.id(), describe(state), describe(counterpart.get())));
            }
        }
    }

    /** Names an optional start state for a message: quoted, or {@code none} for a unit that binds entries instead. */
    private static String describeStart(Optional<String> startState) {
        return startState.map("'%s'"::formatted).orElse("none");
    }

    private static String describe(StateNode terminal) {
        return terminal.terminalOutcome().map(outcome -> "'%s' of class %s".formatted(outcome.id(),
                outcome.outcomeClass())).orElse("no outcome");
    }

    private static void waiverDataFlow(Context context) {
        var id = InvariantId.INV_GEN_2;
        var remediationUnit = context.unit().id().startsWith(CompiledInvariants.REMEDIATION_PREFIX);
        for (var state : context.states()) {
            var waivedTerminal = Context.isTerminal(state)
                    && state.terminalOutcome().map(outcome -> WAIVED.equals(outcome.id())).orElse(false);
            if (waivedTerminal && !remediationUnit) {
                context.reportState(id, state, "the terminal '%s' ends a waived path outside a remediation unit"
                        .formatted(state.id()));
            }
            var routesWaiver = Context.primitiveId(state).isPresent() && Context.transitions(state).stream()
                    .anyMatch(transition -> WAIVER_OUTCOMES.contains(transition.outcomeName()));
            if (routesWaiver) {
                context.primitive(state).filter(primitive -> primitive.waivableGates().isEmpty())
                        .ifPresent(primitive -> context.reportState(id, state,
                                "the state '%s' admits a failure as waived, but its primitive '%s' declares no waivable gate"
                                        .formatted(state.id(), primitive.id())));
            }
        }
    }

    private static void conditionOverlays(Context context) {
        var id = InvariantId.INV_GEN_3;
        for (var state : context.states()) {
            if (WorkflowValidator.isReservedTarget(state.id())) {
                context.reportState(id, state, "the state '%s' is named after a condition or a reserved state"
                        .formatted(state.id()));
            }
            for (var transition : Context.transitions(state)) {
                if (WorkflowValidator.isReservedTarget(transition.targetStateId())) {
                    context.reportTransition(id, state, transition,
                            "the transition '%s' targets the condition or reserved state '%s'"
                                    .formatted(transition.outcomeName(), transition.targetStateId()));
                }
            }
        }
    }

    private static void noGuaranteeWeakening(Context context) {
        context.requireDominatorsOf(InvariantId.INV_GEN_4,
                state -> Context.isTerminalOfClass(state, OutcomeClass.SUCCESS),
                state -> !Context.isTerminal(state));
    }

    private static void modelWorkOnlyAsTasks(Context context) {
        var id = InvariantId.INV_GEN_5;
        var unit = context.unit();
        if (WORKER_TASK.equals(unit.id())) {
            context.reportUnit(id, "the unit replaces the compiled unit '%s'".formatted(WORKER_TASK));
        }
        for (var state : context.states()) {
            if (state.id().startsWith(WORKER_TASK + ".")) {
                context.reportState(id, state, "the state '%s' defines a state of '%s'".formatted(state.id(),
                        WORKER_TASK));
            }
            Context.callee(state).filter(WORKER_TASK::equals).ifPresent(callee -> context.reportState(id, state,
                    "the state '%s' calls '%s'".formatted(state.id(), WORKER_TASK)));
            switch (state.behavior()) {
                case StateBehavior.InteractiveLinks _ -> context.reportState(id, state,
                        "the state '%s' is a resting link state".formatted(state.id()));
                case StateBehavior.QuestionWait question when !question.links().isEmpty() -> context.reportState(id,
                        state, "the question state '%s' declares links".formatted(state.id()));
                case StateBehavior.ModelJob _ -> conservativeLost(context, state);
                default -> {
                    // no model work of its own
                }
            }
        }
    }

    private static void conservativeLost(Context context, StateNode state) {
        for (var transition : Context.transitionsOn(state, CompiledInvariants.LOST)) {
            checkLostTransition(context, state, transition);
        }
    }

    /**
     * Checks one {@code lost} transition of a model-job state: the vote of the adjudication routes it to the
     * escalation, no other state ends it in a success terminal, and every other state routes it as the default unit
     * does.
     */
    private static void checkLostTransition(Context context, StateNode state, OutcomeTransition transition) {
        var id = InvariantId.INV_GEN_5;
        var target = transition.targetStateId();
        if (ADJUDICATE_VOTE.equals(state.id())) {
            if (!ADJUDICATE_ESCALATED.equals(target)) {
                context.reportTransition(id, state, transition, "the outcome 'lost' of '%s' routes to '%s', not to '%s'"
                        .formatted(state.id(), target, ADJUDICATE_ESCALATED));
            }
            return;
        }
        var successTerminal = context.state(target)
                .filter(targetState -> Context.isTerminalOfClass(targetState, OutcomeClass.SUCCESS)).isPresent();
        if (successTerminal) {
            context.reportTransition(id, state, transition,
                    "the outcome 'lost' of '%s' ends in the success terminal '%s'".formatted(state.id(), target));
            return;
        }
        context.referenceState(state.id())
                .flatMap(counterpart -> Context.transitionsOn(counterpart, CompiledInvariants.LOST).stream()
                        .findFirst())
                .filter(conservative -> !conservative.targetStateId().equals(target))
                .ifPresent(conservative -> context.reportTransition(id, state, transition,
                        "the outcome 'lost' of '%s' routes to '%s', the default unit's conservative route to '%s'"
                                .formatted(state.id(), target, conservative.targetStateId())));
    }
}
