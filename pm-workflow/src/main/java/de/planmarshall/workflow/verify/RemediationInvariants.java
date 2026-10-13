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

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import de.planmarshall.core.spi.AwaitedEvent;
import de.planmarshall.core.spi.OutcomeClass;
import de.planmarshall.workflow.ast.CallValue;
import de.planmarshall.workflow.ast.OutcomeTransition;
import de.planmarshall.workflow.ast.ParamDecl;
import de.planmarshall.workflow.ast.ParamType;
import de.planmarshall.workflow.ast.ResultGuard;
import de.planmarshall.workflow.ast.SourceLocation;
import de.planmarshall.workflow.ast.StateBehavior;
import de.planmarshall.workflow.ast.StateNode;
import de.planmarshall.workflow.ast.WorkflowDefinition;
import de.planmarshall.workflow.verify.CompiledInvariants.Check;
import de.planmarshall.workflow.verify.CompiledInvariants.Context;

/**
 * The invariants of the remediation units and their call sites, {@code INV-REM-1} to {@code INV-REM-7} (PM-IMPL-5,
 * PM-WF-16). The states of a remediation unit are named {@code remediation-<class>.<state>}; the checks find them by
 * their last segment ({@code gather}, {@code budget-check}, {@code decide}, ...).
 * <ul>
 * <li>{@link InvariantId#INV_REM_1}: the unit declares {@code retry_budget} and {@code fix_budget} as
 * {@code int[0..5]}, {@code waiver_wait_seconds} as {@code int[0..86400]} and {@code actions} as a
 * {@code list<enum[...]>} of the default unit's actions, a subset of {@code retry}, {@code fix} and {@code loop_back}
 * ({@code remediation.spend}: {@code waiver_wait_seconds} only), and no other parameter; its terminals are among
 * {@code resolved} ({@code success}), {@code waived} ({@code success}, only for a class with a {@code met}-waivable
 * gate), {@code exhausted} ({@code failure}) and {@code fix_requested} ({@code unsettled}), each reachable.</li>
 * <li>{@link InvariantId#INV_REM_2}: {@code budget-check} is the only state that enters {@code decide};
 * {@code choose} and {@code apply-fix} route {@code lost} and {@code infeasible} to {@code conservative}, which is an
 * {@code auto} state entering only {@code rerun} or the {@code exhausted} terminal; {@code screen} routes {@code lost}
 * to one of its continuations; {@code triage} routes {@code lost} and {@code infeasible} to {@code budget-check}. The
 * offered remedies and the budgets left are computed by {@code remediation.offer-actions} at runtime.</li>
 * <li>{@link InvariantId#INV_REM_3}: the {@code waived} terminal is entered only on {@code all_waived} of
 * {@code gather} or on {@code waived} of {@code waiver-wait}; {@code waiver-wait} is a {@code composite_job}, the only
 * state whose primitive awaits {@code OPERATOR_WAIVER}, and is entered only on {@code exhausted_wait} of
 * {@code budget-check}, on {@code request_waiver} of {@code choose}, or from itself.</li>
 * <li>{@link InvariantId#INV_REM_4}: a call of {@code git.commit_and_push} in the unit routes {@code stepped_out} and a
 * {@code done} without a commit ({@code guard not result.commit}, or an unguarded {@code done}) to
 * {@code budget-check}. The charging in the transaction of the fix task's submit is the primitive's.</li>
 * <li>{@link InvariantId#INV_REM_5}: every call of a remediation unit in the plan unit routes {@code stepped_out} to the
 * phase's fallback ({@code plan.return} in Phases 1-4, {@code finalize.hand-back} afterwards) and {@code exhausted} to
 * it as well, or, from Phase 5 outside the {@code land} lane and from Phase 6 before the merge, and for every unit but
 * {@code remediation.spend}, to a call of the re-planning unit {@code replan}; routes {@code waived} and
 * {@code fix_requested} as the default unit does at the same call; and binds {@code retry_budget} and
 * {@code fix_budget} at most 5 and {@code waiver_wait_seconds} at most 86400.</li>
 * <li>{@link InvariantId#INV_REM_6}, tree part: no unit but a remediation unit runs a primitive of the
 * {@code remediation.} namespace, and a remediation unit runs none that its default unit does not run. The rest is the
 * named hook of the invariant.</li>
 * <li>{@link InvariantId#INV_REM_7}: every unit but {@code remediation.spend} declares {@code gather} (a
 * {@code composite_job}, or an {@code auto} state that no cycle returns to), {@code budget-check}, {@code decide}
 * ({@code auto remediation.offer-actions}, routing {@code choice} to {@code choose}), {@code choose} (a
 * {@code job llm remediation-decision} state) and {@code conservative}, plus {@code rerun} where its actions include
 * {@code retry}, {@code fix-requested} where they include {@code loop_back}, {@code apply-fix} where the default unit
 * has it, and {@code waiver-wait} for a class with a waivable gate; only {@code decide}, {@code choose} and (for
 * {@code rerun}) {@code conservative} enter a remedy state.</li>
 * </ul>
 *
 * @since 0.1
 */
final class RemediationInvariants {

    /** The degenerate remediation unit of a spend overrun, which has no action. */
    static final String SPEND_UNIT = "remediation.spend";

    /** The remediation units of the classes with a {@code met}-waivable gate. */
    static final Set<String> WAIVABLE_UNITS = Set.of("remediation.ci-failure", "remediation.review-findings",
            "remediation.analysis", "remediation.qgate-findings", SPEND_UNIT);

    /** The parameter of the retry budget. */
    static final String RETRY_BUDGET = "retry_budget";

    /** The parameter of the fix budget. */
    static final String FIX_BUDGET = "fix_budget";

    /** The parameter of the waiver wait. */
    static final String WAIVER_WAIT = "waiver_wait_seconds";

    /** The parameter of the bound actions. */
    static final String ACTIONS = "actions";

    /** The hard cap of the retry and the fix budget. */
    static final int BUDGET_CAP = 5;

    /** The hard cap of the waiver wait in seconds. */
    static final int WAIVER_WAIT_CAP = 86400;

    /** The closed action set of the remediation units. */
    static final Set<String> ACTION_SET = Set.of("retry", "fix", ExecuteInvariants.LOOP_BACK);

    /** The primitive of {@code decide}. */
    static final String OFFER_ACTIONS = "remediation.offer-actions";

    /** The entity kind of the decision task of {@code choose}. */
    static final String DECISION_KIND = "remediation-decision";

    /** The re-planning unit a ceiling hands over to. */
    static final String REPLAN_UNIT = "replan";

    private static final String GATHER = "gather";
    private static final String SCREEN = "screen";
    private static final String TRIAGE = "triage";
    private static final String BUDGET_CHECK = "budget-check";
    private static final String DECIDE = "decide";
    private static final String CHOOSE = "choose";
    private static final String CONSERVATIVE = "conservative";
    private static final String RERUN = "rerun";
    private static final String APPLY_FIX = "apply-fix";
    private static final String FIX_REQUESTED = "fix-requested";
    private static final String WAIVER_WAIT_STATE = "waiver-wait";

    private static final String INFEASIBLE = "infeasible";
    private static final String EXHAUSTED = "exhausted";
    private static final String FIX_REQUESTED_OUTCOME = "fix_requested";

    private static final ParamType BUDGET_TYPE = new ParamType.Int(Optional.of(0), Optional.of(BUDGET_CAP));
    private static final ParamType WAIT_TYPE = new ParamType.Int(Optional.of(0), Optional.of(WAIVER_WAIT_CAP));

    private static final Map<String, OutcomeClass> TERMINALS = Map.of("resolved", OutcomeClass.SUCCESS,
            GeneralInvariants.WAIVED, OutcomeClass.SUCCESS, EXHAUSTED, OutcomeClass.FAILURE, FIX_REQUESTED_OUTCOME,
            OutcomeClass.UNSETTLED);

    private RemediationInvariants() {
    }

    static Map<InvariantId, Check> checks() {
        return Map.of(
                InvariantId.INV_REM_1, RemediationInvariants::contract,
                InvariantId.INV_REM_2, RemediationInvariants::noDeadEnd,
                InvariantId.INV_REM_3, RemediationInvariants::waivedPath,
                InvariantId.INV_REM_4, RemediationInvariants::nestedCommits,
                InvariantId.INV_REM_5, RemediationInvariants::callers,
                InvariantId.INV_REM_6, RemediationInvariants::accounting,
                InvariantId.INV_REM_7, RemediationInvariants::fullTemplate);
    }

    // ----- INV-REM-1

    private static void contract(Context context) {
        var unit = context.unit();
        var expected = expectedParams(unit);
        for (var param : unit.params()) {
            checkParam(context, expected, param);
        }
        reportMissingParams(context, expected.keySet());
        var reachable = context.reachable();
        for (var state : context.states()) {
            if (Context.isTerminal(state)) {
                checkTerminal(context, state, reachable);
            }
        }
    }

    /**
     * @return the parameters of the contract but {@code actions}, by name, with their declared types
     */
    private static Map<String, ParamType> expectedParams(WorkflowDefinition unit) {
        var expected = new LinkedHashMap<String, ParamType>();
        expected.put(WAIVER_WAIT, WAIT_TYPE);
        if (!SPEND_UNIT.equals(unit.id())) {
            expected.put(RETRY_BUDGET, BUDGET_TYPE);
            expected.put(FIX_BUDGET, BUDGET_TYPE);
        }
        return expected;
    }

    /**
     * Checks one declared parameter against the contract: {@code actions} against the action set, every other one
     * against its expected type.
     */
    private static void checkParam(Context context, Map<String, ParamType> expected, ParamDecl param) {
        var id = InvariantId.INV_REM_1;
        var unitId = context.unit().id();
        if (ACTIONS.equals(param.name()) && !SPEND_UNIT.equals(unitId)) {
            checkActions(context, param.type(), param.location());
            return;
        }
        var type = expected.get(param.name());
        if (type == null) {
            context.report(id, unitId, param.location(), "the parameter '%s' is not part of the contract"
                    .formatted(param.name()));
        } else if (!type.equals(param.type())) {
            context.report(id, unitId, param.location(), "the parameter '%s' is not declared as %s"
                    .formatted(param.name(), describe(type)));
        }
    }

    /**
     * Reports every parameter of the contract the unit does not declare, {@code actions} last.
     */
    private static void reportMissingParams(Context context, Set<String> expectedNames) {
        var id = InvariantId.INV_REM_1;
        var unit = context.unit();
        var declared = new LinkedHashSet<String>();
        unit.params().forEach(param -> declared.add(param.name()));
        expectedNames.stream().filter(name -> !declared.contains(name)).forEach(name -> context.reportUnit(id,
                "the unit does not declare the parameter '%s'".formatted(name)));
        if (!SPEND_UNIT.equals(unit.id()) && !declared.contains(ACTIONS)) {
            context.reportUnit(id, "the unit does not declare the parameter '%s'".formatted(ACTIONS));
        }
    }

    /**
     * Checks one terminal: its outcome is one of the contract's with its class, {@code waived} only for a class with
     * a waivable gate, and it is reachable.
     */
    private static void checkTerminal(Context context, StateNode state, Set<String> reachable) {
        var id = InvariantId.INV_REM_1;
        var unitId = context.unit().id();
        var outcome = state.terminalOutcome();
        var expectedClass = outcome.map(terminal -> TERMINALS.get(terminal.id()));
        if (expectedClass.isEmpty() || expectedClass.get() != outcome.get().outcomeClass()) {
            context.reportState(id, state, "the terminal '%s' is none of resolved, waived, exhausted and"
                    .formatted(state.id()) + " fix_requested with its class");
        } else if (GeneralInvariants.WAIVED.equals(outcome.get().id()) && !WAIVABLE_UNITS.contains(unitId)) {
            context.reportState(id, state, "the class of '%s' has no waivable gate, so it ends nothing waived"
                    .formatted(unitId));
        }
        if (!reachable.contains(state.id())) {
            context.reportState(id, state, "the terminal '%s' is not reachable".formatted(state.id()));
        }
    }

    private static void checkActions(Context context, ParamType type, SourceLocation location) {
        var id = InvariantId.INV_REM_1;
        var actions = actionsOf(type);
        if (actions.isEmpty() || !ACTION_SET.containsAll(actions.get())) {
            context.report(id, context.unit().id(), location, "the parameter '%s' is not a list of the actions %s"
                    .formatted(ACTIONS, Context.sorted(ACTION_SET)));
            return;
        }
        var referenceActions = actionsOf(context.reference());
        if (referenceActions.isPresent() && !referenceActions.get().equals(actions.get())) {
            context.report(id, context.unit().id(), location, "the actions %s differ from the default unit's %s"
                    .formatted(Context.sorted(actions.get()), Context.sorted(referenceActions.get())));
        }
    }

    private static Optional<Set<String>> actionsOf(ParamType type) {
        if (type instanceof ParamType.ListOf(ParamType.EnumOf(var values))) {
            return Optional.of(Set.copyOf(values));
        }
        return Optional.empty();
    }

    /**
     * @param definition a remediation unit
     * @return the values of its {@code actions} parameter; empty when it declares none
     */
    static Optional<Set<String>> actionsOf(WorkflowDefinition definition) {
        return definition.params().stream().filter(param -> ACTIONS.equals(param.name())).findFirst()
                .flatMap(param -> actionsOf(param.type()));
    }

    private static String describe(ParamType type) {
        return type instanceof ParamType.Int(var min, var max) && min.isPresent() && max.isPresent()
                ? "int[%d..%d]".formatted(min.get(), max.get()) : type.toString();
    }

    // ----- INV-REM-2

    private static void noDeadEnd(Context context) {
        var id = InvariantId.INV_REM_2;
        context.stateWithSuffix(DECIDE).ifPresent(decide -> context.graph().predecessors(decide.id()).stream()
                .filter(source -> !hasSuffix(source, BUDGET_CHECK))
                .forEach(source -> context.reportState(id, decide, "'%s' is entered from '%s', not only from '%s'"
                        .formatted(decide.id(), source, BUDGET_CHECK))));
        for (var suffix : List.of(CHOOSE, APPLY_FIX)) {
            context.stateWithSuffix(suffix).ifPresent(state -> requireRoutes(context, id, state,
                    List.of(CompiledInvariants.LOST, INFEASIBLE), CONSERVATIVE));
        }
        context.stateWithSuffix(TRIAGE).ifPresent(state -> requireRoutes(context, id, state,
                List.of(CompiledInvariants.LOST, INFEASIBLE), BUDGET_CHECK));
        context.stateWithSuffix(CONSERVATIVE).ifPresent(conservative -> {
            if (!(conservative.behavior() instanceof StateBehavior.AutoStep)) {
                context.reportState(id, conservative, "'%s' is not an auto state".formatted(conservative.id()));
            }
            for (var transition : Context.transitions(conservative)) {
                var target = context.state(transition.targetStateId());
                var admitted = hasSuffix(transition.targetStateId(), RERUN) || target
                        .filter(state -> state.terminalOutcome().filter(outcome -> EXHAUSTED.equals(outcome.id()))
                                .isPresent()).isPresent();
                if (!admitted) {
                    context.reportTransition(id, conservative, transition, "'%s' routes '%s' to '%s', neither the"
                            .formatted(conservative.id(), transition.outcomeName(), transition.targetStateId())
                            + " single retry nor the exhausted terminal");
                }
            }
        });
        context.stateWithSuffix(SCREEN).ifPresent(screen -> {
            var lost = Context.transitionsOn(screen, CompiledInvariants.LOST);
            var continuations = Context.transitions(screen).stream()
                    .filter(transition -> !CompiledInvariants.LOST.equals(transition.outcomeName()))
                    .map(OutcomeTransition::targetStateId).toList();
            if (lost.isEmpty()) {
                context.reportState(id, screen, "'%s' routes no 'lost'".formatted(screen.id()));
            }
            lost.stream().filter(transition -> !continuations.contains(transition.targetStateId()))
                    .forEach(transition -> context.reportTransition(id, screen, transition,
                            "'%s' routes 'lost' to '%s', not to its continuation".formatted(screen.id(),
                                    transition.targetStateId())));
        });
    }

    private static void requireRoutes(Context context, InvariantId id, StateNode state, List<String> outcomes,
            String targetSuffix) {
        if (Context.transitionsOn(state, CompiledInvariants.LOST).isEmpty()) {
            context.reportState(id, state, "'%s' routes no 'lost'".formatted(state.id()));
        }
        for (var outcome : outcomes) {
            Context.transitionsOn(state, outcome).stream()
                    .filter(transition -> !hasSuffix(transition.targetStateId(), targetSuffix))
                    .forEach(transition -> context.reportTransition(id, state, transition,
                            "'%s' routes '%s' to '%s', not to '%s'".formatted(state.id(), outcome,
                                    transition.targetStateId(), targetSuffix)));
        }
    }

    // ----- INV-REM-3

    private static void waivedPath(Context context) {
        for (var edge : Context.edges(context.unit())) {
            checkWaivedEntry(context, edge.source(), edge.transition());
            checkWaiverWaitEntry(context, edge.source(), edge.transition());
        }
        for (var state : context.states()) {
            checkWaiverWait(context, state);
        }
    }

    /**
     * A transition enters the {@code waived} terminal only on {@code all_waived} of {@code gather} or on
     * {@code waived} of {@code waiver-wait}.
     */
    private static void checkWaivedEntry(Context context, StateNode source, OutcomeTransition transition) {
        var entersWaived = context.state(transition.targetStateId())
                .flatMap(StateNode::terminalOutcome)
                .filter(outcome -> GeneralInvariants.WAIVED.equals(outcome.id())).isPresent();
        var admitted = hasSuffix(source.id(), GATHER) && GeneralInvariants.ALL_WAIVED.equals(transition.outcomeName())
                || hasSuffix(source.id(), WAIVER_WAIT_STATE)
                && GeneralInvariants.WAIVED.equals(transition.outcomeName());
        if (entersWaived && !admitted) {
            context.reportTransition(InvariantId.INV_REM_3, source, transition,
                    "'%s' enters the waived terminal on '%s'".formatted(source.id(), transition.outcomeName()));
        }
    }

    /**
     * A transition enters {@code waiver-wait} only from itself, on {@code exhausted_wait} of {@code budget-check} or
     * on {@code request_waiver} of {@code choose}.
     */
    private static void checkWaiverWaitEntry(Context context, StateNode source, OutcomeTransition transition) {
        var entersWait = hasSuffix(transition.targetStateId(), WAIVER_WAIT_STATE);
        var admittedEntry = hasSuffix(source.id(), WAIVER_WAIT_STATE)
                || hasSuffix(source.id(), BUDGET_CHECK) && "exhausted_wait".equals(transition.outcomeName())
                || hasSuffix(source.id(), CHOOSE) && "request_waiver".equals(transition.outcomeName());
        if (entersWait && !admittedEntry) {
            context.reportTransition(InvariantId.INV_REM_3, source, transition,
                    "'%s' enters the waiver wait on '%s'".formatted(source.id(), transition.outcomeName()));
        }
    }

    /**
     * Only {@code waiver-wait} awaits an operator waiver, and it is a {@code composite_job}.
     */
    private static void checkWaiverWait(Context context, StateNode state) {
        var id = InvariantId.INV_REM_3;
        var awaitsWaiver = context.primitive(state)
                .filter(primitive -> primitive.awaitedEvent() == AwaitedEvent.OPERATOR_WAIVER).isPresent();
        var isWait = hasSuffix(state.id(), WAIVER_WAIT_STATE);
        if (awaitsWaiver && !isWait) {
            context.reportState(id, state, "'%s' awaits an operator waiver but is not the waiver wait"
                    .formatted(state.id()));
        }
        if (isWait && !(state.behavior() instanceof StateBehavior.JobStep job && job.composite())) {
            context.reportState(id, state, "the waiver wait '%s' is not a composite_job".formatted(state.id()));
        }
    }

    // ----- INV-REM-4

    private static void nestedCommits(Context context) {
        var id = InvariantId.INV_REM_4;
        for (var state : context.states()) {
            if (Context.callee(state).filter(CompiledInvariants.GIT_UNIT::equals).isEmpty()) {
                continue;
            }
            for (var transition : Context.transitions(state)) {
                var commitLess = CompiledInvariants.STEPPED_OUT.equals(transition.outcomeName())
                        || SubWorkflowInvariants.DONE.equals(transition.outcomeName())
                        && transition.resultGuard().map(ResultGuard::negated).orElse(true);
                if (commitLess && !hasSuffix(transition.targetStateId(), BUDGET_CHECK)) {
                    context.reportTransition(id, state, transition,
                            "the nested commit '%s' routes '%s' without a commit to '%s', not to '%s'".formatted(
                                    state.id(), transition.outcomeName(), transition.targetStateId(), BUDGET_CHECK));
                }
            }
        }
    }

    // ----- INV-REM-5

    private static void callers(Context context) {
        var afterMerge = afterMerge(context);
        for (var state : context.states()) {
            if (state.behavior() instanceof StateBehavior.SubWorkflowCall call
                    && call.subWorkflowId().startsWith(CompiledInvariants.REMEDIATION_PREFIX)) {
                checkRemediationCall(context, state, call, afterMerge);
            }
        }
    }

    /**
     * Checks one call of a remediation unit: the routing of each of its outcomes and the bound budgets.
     */
    private static void checkRemediationCall(Context context, StateNode state, StateBehavior.SubWorkflowCall call,
            Set<String> afterMerge) {
        var early = CompiledInvariants.PHASES_BEFORE_EXECUTE.contains(state.phase());
        var fallback = early ? CompiledInvariants.PLAN_RETURN : CompiledInvariants.HAND_BACK;
        var replanAdmitted = replanAdmitted(state, call, afterMerge);
        for (var transition : Context.transitions(state)) {
            checkCallerRoute(context, state, transition, fallback, replanAdmitted);
        }
        checkBudgetBindings(context, InvariantId.INV_REM_5, state, call);
    }

    /**
     * @return {@code true} when the call may route {@code exhausted} to the re-planning unit: from Phase 5 outside
     *         the {@code land} lane and from Phase 6 before the merge, for every unit but {@code remediation.spend}
     */
    private static boolean replanAdmitted(StateNode state, StateBehavior.SubWorkflowCall call,
            Set<String> afterMerge) {
        return !SPEND_UNIT.equals(call.subWorkflowId()) && (
                CompiledInvariants.PHASE_EXECUTE.contains(state.phase()) && !state.id().startsWith("land.")
                        || CompiledInvariants.PHASE_FINALIZE.equals(state.phase())
                        && !afterMerge.contains(state.id()));
    }

    /**
     * Checks the route of one outcome of a remediation call; {@code resolved} continues the caller's flow.
     */
    private static void checkCallerRoute(Context context, StateNode state, OutcomeTransition transition,
            String fallback, boolean replanAdmitted) {
        switch (transition.outcomeName()) {
            case CompiledInvariants.STEPPED_OUT -> requireFallback(context, state, transition, fallback);
            case EXHAUSTED -> requireFallbackOrReplan(context, state, transition, fallback, replanAdmitted);
            case GeneralInvariants.WAIVED, FIX_REQUESTED_OUTCOME -> requireReferenceRoute(context,
                    InvariantId.INV_REM_5, state, transition);
            default -> {
                // resolved continues the caller's flow
            }
        }
    }

    /**
     * {@code stepped_out} routes to the phase's fallback.
     */
    private static void requireFallback(Context context, StateNode state, OutcomeTransition transition,
            String fallback) {
        var target = transition.targetStateId();
        if (!fallback.equals(target)) {
            context.reportTransition(InvariantId.INV_REM_5, state, transition,
                    "'%s' routes 'stepped_out' to '%s', not".formatted(state.id(), target)
                            + " to the fallback '%s'".formatted(fallback));
        }
    }

    /**
     * {@code exhausted} routes to the phase's fallback or, where admitted, to a call of the re-planning unit.
     */
    private static void requireFallbackOrReplan(Context context, StateNode state, OutcomeTransition transition,
            String fallback, boolean replanAdmitted) {
        var target = transition.targetStateId();
        var toReplan = replanAdmitted && context.state(target).flatMap(Context::callee)
                .filter(REPLAN_UNIT::equals).isPresent();
        if (!fallback.equals(target) && !toReplan) {
            context.reportTransition(InvariantId.INV_REM_5, state, transition,
                    "'%s' routes 'exhausted' to '%s', not to".formatted(state.id(), target)
                            + " the fallback '%s'%s".formatted(fallback,
                                    replanAdmitted ? " or a re-planning call" : ""));
        }
    }

    private static Set<String> afterMerge(Context context) {
        var mergeTargets = Context.edges(context.unit()).stream()
                .filter(edge -> FinalizeInvariants.MERGED.equals(edge.transition().outcomeName()))
                .map(edge -> edge.transition().targetStateId())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return context.graph().forwardClosure(mergeTargets);
    }

    private static void requireReferenceRoute(Context context, InvariantId id, StateNode state,
            OutcomeTransition transition) {
        var counterpart = context.referenceState(state.id())
                .filter(reference -> Context.callee(reference).equals(Context.callee(state)));
        if (counterpart.isEmpty()) {
            return;
        }
        var routes = Context.transitionsOn(counterpart.get(), transition.outcomeName()).stream()
                .map(RemediationInvariants::route).toList();
        if (!routes.contains(route(transition))) {
            context.reportTransition(id, state, transition, "'%s' routes '%s' as %s, the default unit as %s"
                    .formatted(state.id(), transition.outcomeName(), route(transition), routes));
        }
    }

    private static String route(OutcomeTransition transition) {
        return transition.targetStateId() + transition.completions().stream()
                .map(completion -> " completes %s as %s".formatted(completion.stepId(), completion.status()))
                .reduce("", String::concat);
    }

    private static void checkBudgetBindings(Context context, InvariantId id, StateNode state,
            StateBehavior.SubWorkflowCall call) {
        for (var entry : Map.of(RETRY_BUDGET, BUDGET_CAP, FIX_BUDGET, BUDGET_CAP, WAIVER_WAIT, WAIVER_WAIT_CAP)
                .entrySet()) {
            if (call.paramBindings().get(entry.getKey()) instanceof CallValue.IntValue(var value)
                    && (value < 0 || value > entry.getValue())) {
                context.reportState(id, state, "'%s' binds %s=%d above the hard cap %d".formatted(state.id(),
                        entry.getKey(), value, entry.getValue()));
            }
        }
    }

    // ----- INV-REM-6

    private static void accounting(Context context) {
        var id = InvariantId.INV_REM_6;
        var remediationUnit = context.unit().id().startsWith(CompiledInvariants.REMEDIATION_PREFIX);
        var known = new LinkedHashSet<String>();
        context.reference().states().values()
                .forEach(state -> Context.primitiveId(state).ifPresent(known::add));
        for (var state : context.states()) {
            Context.primitiveId(state).filter(primitive -> primitive.startsWith(CompiledInvariants.REMEDIATION_PREFIX))
                    .filter(primitive -> !remediationUnit || !known.contains(primitive))
                    .ifPresent(primitive -> context.reportState(id, state, remediationUnit
                            ? "'%s' runs the remediation primitive '%s', which the default unit does not run"
                            .formatted(state.id(), primitive)
                            : "'%s' runs the remediation primitive '%s' outside a remediation unit"
                            .formatted(state.id(), primitive)));
        }
    }

    // ----- INV-REM-7

    private static void fullTemplate(Context context) {
        var id = InvariantId.INV_REM_7;
        var unit = context.unit();
        if (SPEND_UNIT.equals(unit.id())) {
            return;
        }
        var required = new LinkedHashSet<>(List.of(GATHER, BUDGET_CHECK, DECIDE, CHOOSE, CONSERVATIVE));
        var actions = actionsOf(unit).orElse(Set.of());
        if (actions.contains("retry")) {
            required.add(RERUN);
        }
        if (actions.contains(ExecuteInvariants.LOOP_BACK)) {
            required.add(FIX_REQUESTED);
        }
        if (context.referenceStateWithSuffix(APPLY_FIX).isPresent()) {
            required.add(APPLY_FIX);
        }
        if (WAIVABLE_UNITS.contains(unit.id())) {
            required.add(WAIVER_WAIT_STATE);
        }
        required.stream().filter(suffix -> context.stateWithSuffix(suffix).isEmpty())
                .forEach(suffix -> context.reportUnit(id, "the template state '%s' is missing".formatted(suffix)));
        context.stateWithSuffix(GATHER).ifPresent(gather -> checkGather(context, id, gather));
        context.stateWithSuffix(DECIDE).ifPresent(decide -> {
            if (!(decide.behavior() instanceof StateBehavior.AutoStep auto && OFFER_ACTIONS.equals(auto.primitiveId()))) {
                context.reportState(id, decide, "'%s' is not 'auto %s'".formatted(decide.id(), OFFER_ACTIONS));
            }
            var choiceToChoose = Context.transitionsOn(decide, "choice").stream()
                    .anyMatch(transition -> hasSuffix(transition.targetStateId(), CHOOSE));
            if (!choiceToChoose) {
                context.reportState(id, decide, "'%s' does not route 'choice' to '%s'".formatted(decide.id(), CHOOSE));
            }
        });
        context.stateWithSuffix(CHOOSE).ifPresent(choose -> {
            if (!(choose.behavior() instanceof StateBehavior.ModelJob job && DECISION_KIND.equals(job.entityKind()))) {
                context.reportState(id, choose, "'%s' is not a 'job llm %s' state".formatted(choose.id(), DECISION_KIND));
            }
        });
        requireEntries(context, id, RERUN, Set.of(DECIDE, CHOOSE, CONSERVATIVE));
        requireEntries(context, id, APPLY_FIX, Set.of(DECIDE, CHOOSE));
        requireEntries(context, id, FIX_REQUESTED, Set.of(DECIDE, CHOOSE));
    }

    private static void checkGather(Context context, InvariantId id, StateNode gather) {
        switch (gather.behavior()) {
            case StateBehavior.JobStep job when job.composite() -> {
                // the evidence job
            }
            case StateBehavior.AutoStep _ -> {
                var onCycle = context.graph().forwardClosure(context.graph().successors(gather.id()))
                        .contains(gather.id());
                if (onCycle) {
                    context.reportState(id, gather, "the auto state '%s' lies on a cycle of the unit".formatted(
                            gather.id()));
                }
            }
            default -> context.reportState(id, gather, "'%s' is neither a composite_job nor an auto state"
                    .formatted(gather.id()));
        }
    }

    private static void requireEntries(Context context, InvariantId id, String remedy, Set<String> admitted) {
        for (var state : Context.statesWithSuffix(context.unit(), remedy)) {
            context.graph().predecessors(state.id()).stream()
                    .filter(source -> admitted.stream().noneMatch(suffix -> hasSuffix(source, suffix)))
                    .forEach(source -> context.reportState(id, state, "the remedy state '%s' is entered from '%s',"
                            .formatted(state.id(), source) + " past '%s'".formatted(DECIDE)));
        }
    }

    private static boolean hasSuffix(String stateId, String suffix) {
        return stateId.endsWith("." + suffix);
    }
}
