/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.engine;

import java.io.Serial;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import de.planmarshall.core.service.InternalFaultException;
import de.planmarshall.core.service.InternalFaultException.Reason;
import de.planmarshall.core.spi.AwaitedEvent;
import de.planmarshall.core.spi.Primitive;
import de.planmarshall.core.spi.PrimitiveOutcome;
import de.planmarshall.core.spi.PrimitiveRegistry;
import de.planmarshall.core.spi.PrimitiveResult;
import de.planmarshall.core.spi.WorkflowClock;
import de.planmarshall.core.spi.WorkflowExecutionContext;
import de.planmarshall.core.store.JournalScopeType;
import de.planmarshall.core.store.RetryCounters;
import de.planmarshall.core.store.ScopeTransaction;
import de.planmarshall.core.store.WorkflowStatus;
import de.planmarshall.core.store.WorkflowStatusStore;
import de.planmarshall.workflow.ast.CallFrame;
import de.planmarshall.workflow.ast.OutcomeTransition;
import de.planmarshall.workflow.ast.StateBehavior;
import de.planmarshall.workflow.ast.StateNode;
import de.planmarshall.workflow.ast.WorkflowDefinition;
import de.planmarshall.workflow.verify.VerificationResult;
import de.planmarshall.workflow.verify.WorkflowCatalog;
import de.planmarshall.workflow.verify.WorkflowValidator;

/**
 * The deterministic interpreter of one workflow unit (PM-IMPL-5; PM-WF-1: only verified units run; PM-WF-16: sub-workflow
 * calls with a persisted call stack; PM-ARCH-4: every run state lives in the status store).
 * <p>
 * The engine accepts a unit only when {@link WorkflowValidator} reports no violation for it and for every sub-workflow
 * it calls, directly or through another sub-workflow ({@link UnverifiedUnitException}). That is what makes an outcome
 * without a transition impossible: a verified unit routes every outcome of every primitive it names, so an outcome
 * that finds no transition at run time is an {@link InternalFaultException}, never a fallback.
 * <p>
 * One advance runs from the current state and chains in one transaction:
 * <ul>
 * <li>an {@code auto} state runs its primitive through the {@link PrimitiveRegistry} and takes the transition of the
 * returned outcome;</li>
 * <li>a {@code call} state pushes a frame (the calling unit and state, the caller's phase, the sub-workflow and its
 * bound arguments, {@code pushed_at} from the {@link WorkflowClock}) and enters the sub-workflow's start state; a state
 * that declares {@code phase inherit} keeps the caller's phase;</li>
 * <li>a terminal state of a sub-workflow pops exactly one frame and takes the calling state's transition of the
 * returned outcome; of an outcome routed twice, the one whose result guard holds over the frame's result record, the
 * facts the sub-workflow's primitives returned in this advance;</li>
 * <li>a terminal state of the top-level unit ends the unit ({@link EngineStep.Ended});</li>
 * <li>every other state (a link, job, composite job, task or question state) rests ({@link EngineStep.Resting}).</li>
 * </ul>
 * The advance returns the new status with {@code updated_at} from the clock; the caller writes it in the transaction
 * of the advance ({@link #advance(WorkflowStatus, WorkflowStatusStore, ScopeTransaction)}). Leaving a state clears the
 * job and task bindings of the state. The engine counts no retry itself: the retrying primitives count through
 * {@link RetryCounters}, and the engine drops the retry counts of every state of a sub-workflow when it pops or unwinds
 * the frame of that sub-workflow, in the same status write; entering and leaving states within a frame keeps them.
 * <p>
 * A condition overlay stops the scope where it is: {@link #raise(WorkflowStatus, String, Map)} sets it without changing
 * state, phase or call stack, an advance of a scope that carries one returns it unchanged, and
 * {@link #unwindToFallback(WorkflowStatus, String)} takes the fallback of a spent condition budget, unwinding every frame
 * in one transaction without taking a caller's {@code stepped_out} transition.
 *
 * @since 0.1
 */
public final class WorkflowEngine {

    /** The outcome a sub-workflow returns when it is left early. */
    public static final String STEPPED_OUT = "stepped_out";

    /** The transitions one advance may chain at most; a verified unit never comes near it. */
    static final int MAX_CHAIN = 1_000;

    private static final String INHERIT = "inherit";

    /** The phase of the status store for each phase of the DSL, as the state catalogue maps them. */
    private static final Map<String, String> PHASES = Map.ofEntries(
            Map.entry("init", "phase-1-init"),
            Map.entry("refine", "phase-2-refine"),
            Map.entry("outline", "phase-3-outline"),
            Map.entry("plan", "phase-4-plan"),
            Map.entry("execute", "phase-5-execute"),
            Map.entry("finalize", "phase-6-finalize"),
            Map.entry("completed", "completed"),
            Map.entry("aborted", "aborted"),
            Map.entry("orchestrate", "orchestrator"),
            Map.entry("workspace", "workspace"));

    private final WorkflowDefinition unit;
    private final Map<String, WorkflowDefinition> units;
    private final PrimitiveRegistry registry;
    private final ParameterBinder binder;
    private final WorkflowClock clock;

    /**
     * Binds the parameter record of a primitive the engine runs: the parameters the engine resolves from state.
     *
     * @since 0.1
     */
    @FunctionalInterface
    public interface ParameterBinder {

        /**
         * @param primitive the primitive the current state runs
         * @param status    the status of the scope at the state
         * @return the parameter record, an instance of {@link Primitive#parameterType()}
         */
        Record bind(Primitive<?, ?> primitive, WorkflowStatus status);
    }

    /**
     * Creates the engine of a unit after verifying it and every sub-workflow it calls.
     *
     * @param unit      the top-level unit
     * @param catalog   the sub-workflows the units may call
     * @param validator the verifier, built over the same primitives and catalog
     * @param registry  the primitives the units name
     * @param binder    the binder of the parameters of a primitive
     * @param clock     the clock of {@code updated_at}, {@code pushed_at} and {@code raised_at}
     * @throws NullPointerException    if an argument is {@code null}
     * @throws UnverifiedUnitException if the unit, or a sub-workflow it calls, violates a rule of the verifier, or a
     *                                 called sub-workflow is not in the catalog
     */
    public WorkflowEngine(WorkflowDefinition unit, WorkflowCatalog catalog, WorkflowValidator validator,
            PrimitiveRegistry registry, ParameterBinder binder, WorkflowClock clock) {
        this.unit = Objects.requireNonNull(unit, "unit");
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(validator, "validator");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.binder = Objects.requireNonNull(binder, "binder");
        this.clock = Objects.requireNonNull(clock, "clock");
        var verified = new LinkedHashMap<String, WorkflowDefinition>();
        verifyWithCallees(unit, catalog, validator, verified);
        this.units = Collections.unmodifiableMap(verified);
    }

    /** @return the top-level unit the engine runs */
    public WorkflowDefinition unit() {
        return unit;
    }

    /**
     * Starts a scope at the unit's start state and chains from there.
     *
     * @param scopeType the type of the scope
     * @param scopeId   the plan or epic id; {@code null} for the workspace scope
     * @return the step the start ends in, with the first status of the scope
     * @throws IllegalArgumentException if the scope does not fit the status store, or the start state inherits its
     *                                  phase outside the workspace scope
     */
    public EngineStep start(JournalScopeType scopeType, String scopeId) {
        var startState = stateOf(unit, unit.startState());
        String phase;
        if (INHERIT.equals(startState.phase())) {
            if (scopeType != JournalScopeType.WORKSPACE) {
                throw new IllegalArgumentException("the start state '%s' inherits a phase the %s scope does not have"
                        .formatted(startState.id(), scopeType.wireName()));
            }
            phase = "workspace";
        } else {
            phase = phaseOf(startState.phase());
        }
        var now = clock.now();
        var status = WorkflowStatus.builder()
                .scopeType(scopeType)
                .scopeId(scopeId)
                .phase(phase)
                .state(startState.id())
                .callStack(List.of())
                .policyCounters(Map.of())
                .retryCounters(RetryCounters.empty())
                .activeJobs(Map.of())
                .activeTasks(Map.of())
                .updatedAt(now)
                .retained(Map.of())
                .build();
        return new Run(status).chain(startState.id());
    }

    /**
     * Advances a scope from its current state. A scope that rests, that ended, or that carries a condition is
     * returned unchanged; from any other state the engine chains until the scope rests or ends.
     *
     * @param status the status of the scope as the store holds it
     * @return the step the advance ends in
     * @throws InternalFaultException if the status names a state or sub-workflow the verified units do not hold, a
     *                                primitive returns an outcome its state does not route, or the chain does not end
     */
    public EngineStep advance(WorkflowStatus status) {
        Objects.requireNonNull(status, "status");
        if (status.condition().isPresent()) {
            return EngineStep.ConditionRaised.of(status);
        }
        var stack = CallStack.fromStore(status.callStack());
        var node = stateOf(unitOf(stack), status.state());
        return switch (node.behavior()) {
            case StateBehavior.AutoStep _, StateBehavior.SubWorkflowCall _ -> new Run(status).chain(node.id());
            case StateBehavior.TerminalState _ -> stack.isEmpty() ? ended(status, node) : new Run(status).chain(node.id());
            case StateBehavior.InteractiveLinks _, StateBehavior.JobStep _, StateBehavior.ModelJob _,
                StateBehavior.QuestionWait _ -> resting(status, node);
        };
    }

    /**
     * Advances a scope and adds the new status to the transaction of the advance, so the state change and the other
     * writes of the transaction are one journaled write. A status the advance did not change is not written again.
     *
     * @param status      the status of the scope as the store holds it
     * @param store       the status store of the scope
     * @param transaction the open transaction of the scope
     * @return the step the advance ends in
     * @throws InternalFaultException see {@link #advance(WorkflowStatus)}
     */
    public EngineStep advance(WorkflowStatus status, WorkflowStatusStore store, ScopeTransaction transaction) {
        Objects.requireNonNull(store, "store");
        Objects.requireNonNull(transaction, "transaction");
        var step = advance(status);
        if (step.status() != status) {
            store.write(transaction, step.status());
        }
        return step;
    }

    /**
     * Leaves the current sub-workflow through its step-out: exactly one frame unwinds, and the calling state routes the
     * outcome {@value #STEPPED_OUT}. This is the implicit {@code step_out} of a resting link state of a session-driven
     * sub-workflow.
     *
     * @param status the status of a scope inside a sub-workflow
     * @return the step the caller's routing of the step-out ends in
     * @throws IllegalStateException if the scope is in no sub-workflow, carries a condition, or does not rest on a link
     *                               state
     */
    public EngineStep stepOut(WorkflowStatus status) {
        Objects.requireNonNull(status, "status");
        if (status.condition().isPresent()) {
            throw new IllegalStateException("the state '%s' carries a condition; it cannot be stepped out of"
                    .formatted(status.state()));
        }
        var stack = CallStack.fromStore(status.callStack());
        if (stack.isEmpty()) {
            throw new IllegalStateException("the scope is in no sub-workflow; there is no frame to step out of");
        }
        var node = stateOf(unitOf(stack), status.state());
        if (!(node.behavior() instanceof StateBehavior.InteractiveLinks)) {
            throw new IllegalStateException("only a resting link state offers the step-out, not '%s'"
                    .formatted(node.id()));
        }
        var run = new Run(status);
        return run.chain(run.returnFromFrame(STEPPED_OUT));
    }

    /**
     * Raises a condition on the current state. State, phase and call stack stay as they are, so a condition raised
     * inside a sub-workflow leaves the scope inside it.
     *
     * @param status the status of the scope, without a condition
     * @param kind   the condition kind, one of the closed kinds except {@code integrity_breach}, whose breached store
     *               the digest check names
     * @param facts  the facts of the condition, by name in their order
     * @return the step with the condition set
     * @throws IllegalStateException    if the status carries a condition already
     * @throws IllegalArgumentException if the kind is {@code integrity_breach} or no condition kind
     */
    public EngineStep.ConditionRaised raise(WorkflowStatus status, String kind, Map<String, String> facts) {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(facts, "facts");
        return EngineStep.ConditionRaised.raise(status, kind, facts, clock.now());
    }

    /**
     * Takes the fallback of a spent condition budget: every frame unwinds in one transaction, the retry counts of every
     * unwound sub-workflow are dropped, the condition is cleared, and the scope enters the fallback state of the
     * top-level unit directly; no caller's {@value #STEPPED_OUT} transition is taken, so no unit's own recovery state
     * catches it.
     *
     * @param status          the status of the scope
     * @param fallbackStateId the fallback state of the phase in the top-level unit, for example {@code plan.return}
     * @return the step the fallback ends in
     * @throws IllegalArgumentException if the top-level unit has no such state
     */
    public EngineStep unwindToFallback(WorkflowStatus status, String fallbackStateId) {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(fallbackStateId, "fallbackStateId");
        if (!unit.states().containsKey(fallbackStateId)) {
            throw new IllegalArgumentException("the unit '%s' has no fallback state '%s'"
                    .formatted(unit.id(), fallbackStateId));
        }
        var run = new Run(status.toBuilder().pendingCondition(null).build());
        run.unwindAll();
        return run.chain(fallbackStateId);
    }

    private EngineStep ended(WorkflowStatus status, StateNode terminal) {
        var outcome = terminal.terminalOutcome().orElseThrow(
                () -> fault(Reason.UNRESOLVED_NAME, "the terminal state '%s' declares no outcome", terminal.id()));
        return new EngineStep.Ended(status, outcome.id(), outcome.outcomeClass());
    }

    private EngineStep resting(WorkflowStatus status, StateNode node) {
        return new EngineStep.Resting(status, node.id(), awaitedEventOf(node.behavior()));
    }

    private AwaitedEvent awaitedEventOf(StateBehavior behavior) {
        return switch (behavior) {
            case StateBehavior.InteractiveLinks _ -> AwaitedEvent.MODEL_SUBMIT;
            case StateBehavior.JobStep job -> job.composite() ? primitiveOf(job.primitiveId()).awaitedEvent()
                    : AwaitedEvent.JOB_SETTLED;
            case StateBehavior.ModelJob _ -> AwaitedEvent.TASK_SETTLED;
            case StateBehavior.QuestionWait _ -> AwaitedEvent.QUESTION_ANSWER;
            case StateBehavior.AutoStep _, StateBehavior.SubWorkflowCall _, StateBehavior.TerminalState _ ->
                AwaitedEvent.NONE;
        };
    }

    private WorkflowDefinition unitOf(CallStack stack) {
        return stack.top().map(frame -> verifiedUnit(frame.subWorkflowId())).orElse(unit);
    }

    private WorkflowDefinition verifiedUnit(String unitId) {
        var found = units.get(unitId);
        if (found == null) {
            throw fault(Reason.UNRESOLVED_NAME, "the sub-workflow '%s' is not a verified unit of the engine", unitId);
        }
        return found;
    }

    private static StateNode stateOf(WorkflowDefinition definition, String stateId) {
        var node = definition.states().get(stateId);
        if (node == null) {
            throw fault(Reason.UNRESOLVED_NAME, "the unit '%s' has no state '%s'", definition.id(), stateId);
        }
        return node;
    }

    private Primitive<?, ?> primitiveOf(String primitiveId) {
        return registry.find(primitiveId).orElseThrow(
                () -> fault(Reason.UNRESOLVED_NAME, "the primitive '%s' is not registered", primitiveId));
    }

    private static String phaseOf(String dslPhase) {
        var phase = PHASES.get(dslPhase);
        if (phase == null) {
            throw fault(Reason.UNRESOLVED_NAME, "the phase '%s' has no value in the status store", dslPhase);
        }
        return phase;
    }

    private static InternalFaultException fault(Reason reason, String message, Object... names) {
        var details = new HashMap<String, Object>();
        for (var index = 0; index < names.length; index++) {
            details.put("name_" + index, String.valueOf(names[index]));
        }
        return new InternalFaultException(reason, message.formatted(names), details);
    }

    private static void verifyWithCallees(WorkflowDefinition definition, WorkflowCatalog catalog,
            WorkflowValidator validator, Map<String, WorkflowDefinition> verified) {
        if (verified.containsKey(definition.id())) {
            return;
        }
        var result = validator.verify(definition);
        if (!result.passed()) {
            throw new UnverifiedUnitException(definition.id(), result);
        }
        verified.put(definition.id(), definition);
        for (var state : definition.states().values()) {
            if (state.behavior() instanceof StateBehavior.SubWorkflowCall call) {
                var callee = catalog.find(call.subWorkflowId()).orElseThrow(() -> new UnverifiedUnitException(
                        call.subWorkflowId(), new VerificationResult(List.of())));
                verifyWithCallees(callee, catalog, validator, verified);
            }
        }
    }

    private static <P extends Record, R extends Enum<R> & PrimitiveOutcome> PrimitiveResult<R> execute(
            Primitive<P, R> primitive, WorkflowExecutionContext context, Record parameters) {
        var parameterType = primitive.parameterType();
        if (!parameterType.isInstance(parameters)) {
            throw new IllegalStateException("the binder bound %s for the primitive '%s', which takes %s"
                    .formatted(parameters.getClass().getName(), primitive.id(), parameterType.getName()));
        }
        return primitive.execute(context, parameterType.cast(parameters));
    }

    /**
     * One advance: the status it changes, the call stack, and per frame the result record its sub-workflow's
     * primitives returned in this advance.
     */
    private final class Run {

        private WorkflowStatus status;
        private CallStack stack;
        private final Deque<Map<String, Object>> results = new ArrayDeque<>();
        private int transitions;

        Run(WorkflowStatus status) {
            this.status = status;
            this.stack = CallStack.fromStore(status.callStack());
            for (var index = 0; index < stack.depth(); index++) {
                results.push(new LinkedHashMap<>());
            }
        }

        EngineStep chain(String firstStateId) {
            var stateId = firstStateId;
            while (true) {
                if (++transitions > MAX_CHAIN) {
                    throw fault(Reason.UNBOUNDED_CHAIN, "the advance chained more than %d transitions at '%s'",
                            MAX_CHAIN, stateId);
                }
                var node = stateOf(unitOf(stack), stateId);
                enter(node);
                switch (node.behavior()) {
                    case StateBehavior.AutoStep auto -> stateId = runAuto(node, auto);
                    case StateBehavior.SubWorkflowCall call -> stateId = call(node, call);
                    case StateBehavior.TerminalState _ -> {
                        if (stack.isEmpty()) {
                            return ended(finish(), node);
                        }
                        stateId = returnFromFrame(node.terminalOutcome().orElseThrow(() -> fault(
                                Reason.UNRESOLVED_NAME, "the terminal state '%s' declares no outcome", node.id()))
                                .id());
                    }
                    case StateBehavior.InteractiveLinks _, StateBehavior.JobStep _, StateBehavior.ModelJob _,
                        StateBehavior.QuestionWait _ -> {
                        return resting(finish(), node);
                    }
                }
            }
        }

        /** Makes the state current: its phase applies, and the bindings of the state left are cleared. */
        private void enter(StateNode node) {
            var phase = INHERIT.equals(node.phase()) ? status.phase() : phaseOf(node.phase());
            status = status.toBuilder()
                    .state(node.id())
                    .phase(phase)
                    .callStack(stack.toStore())
                    .activeJob(null)
                    .activeJobs(Map.of())
                    .activeTasks(Map.of())
                    .build();
        }

        private String runAuto(StateNode node, StateBehavior.AutoStep auto) {
            var primitive = primitiveOf(auto.primitiveId());
            var parameters = Objects.requireNonNull(binder.bind(primitive, status), "parameters");
            var scopeId = Optional.ofNullable(status.scopeId());
            WorkflowExecutionContext context = () -> scopeId;
            PrimitiveResult<?> result = execute(primitive, context, parameters);
            if (!primitive.outcomeType().isInstance(result.outcome())) {
                throw fault(Reason.UNDECLARED_OUTCOME, "the primitive '%s' of the state '%s' returned %s",
                        primitive.id(), node.id(), result.outcome());
            }
            if (!results.isEmpty()) {
                results.peek().putAll(result.updatedFacts());
            }
            var outcome = result.outcome().wireName();
            return transitionOf(node, auto.transitions(), outcome, Map.of()).targetStateId();
        }

        private String call(StateNode node, StateBehavior.SubWorkflowCall call) {
            var callee = verifiedUnit(call.subWorkflowId());
            var frame = new CallFrame(unitOf(stack).id(), node.id(), status.phase(), callee.id(),
                    call.paramBindings(), clock.now().toString());
            stack = stack.push(frame);
            results.push(new LinkedHashMap<>());
            return callee.startState();
        }

        /**
         * Pops the top frame, drops the retry counts of the left sub-workflow, restores the caller's phase and selects
         * the caller's transition of the returned outcome.
         *
         * @return the target of that transition, in the calling unit
         */
        String returnFromFrame(String outcome) {
            var callee = unitOf(stack);
            var popped = stack.pop();
            var result = results.pop();
            stack = popped.remaining();
            var frame = popped.frame();
            status = status.toBuilder()
                    .phase(frame.callerPhase())
                    .callStack(stack.toStore())
                    .retryCounters(clearedFor(status.retryCounters(), callee))
                    .build();
            var caller = stateOf(unitOf(stack), frame.callerStateId());
            if (!(caller.behavior() instanceof StateBehavior.SubWorkflowCall call)) {
                throw fault(Reason.UNRESOLVED_NAME, "the calling state '%s' of the frame is no call state",
                        caller.id());
            }
            return transitionOf(caller, call.transitions(), outcome, result).targetStateId();
        }

        /** Unwinds every frame and drops the retry counts of every unwound sub-workflow. */
        void unwindAll() {
            var retryCounters = status.retryCounters();
            var remaining = stack;
            for (var _ : stack.unwound()) {
                retryCounters = clearedFor(retryCounters, unitOf(remaining));
                remaining = remaining.pop().remaining();
            }
            stack = CallStack.empty();
            results.clear();
            status = status.toBuilder().callStack(List.of()).retryCounters(retryCounters).build();
        }

        private WorkflowStatus finish() {
            status = status.toBuilder().callStack(stack.toStore()).updatedAt(clock.now()).build();
            return status;
        }
    }

    private static RetryCounters clearedFor(RetryCounters counters, WorkflowDefinition subWorkflow) {
        var cleared = counters;
        for (var stateId : subWorkflow.states().keySet()) {
            cleared = cleared.clearedFor(stateId);
        }
        return cleared;
    }

    /**
     * Selects the one transition of an outcome: of the transitions routing it, the one without a result guard or whose
     * result guard holds over the result record.
     */
    private static OutcomeTransition transitionOf(StateNode node, List<OutcomeTransition> transitions, String outcome,
            Map<String, Object> result) {
        var matching = transitions.stream()
                .filter(transition -> transition.outcomeName().equals(outcome))
                .filter(transition -> transition.resultGuard().map(guard -> GuardEvaluator.holds(guard, result))
                        .orElse(true))
                .toList();
        if (matching.size() != 1) {
            throw fault(Reason.UNDECLARED_OUTCOME, "the state '%s' has %d transitions for the outcome '%s', not one",
                    node.id(), matching.size(), outcome);
        }
        return matching.getFirst();
    }

    /**
     * The refusal of a unit that the verifier does not pass: the engine runs no unit with a violation, and no unit that
     * calls a sub-workflow with one or a sub-workflow the catalog does not hold.
     *
     * @since 0.1
     */
    public static final class UnverifiedUnitException extends IllegalArgumentException {

        @Serial
        private static final long serialVersionUID = 1L;

        /** The refused unit. */
        private final String unitId;

        /** What the verifier found; not part of the serialized form. */
        private final transient VerificationResult verification;

        /**
         * @param unitId       the refused unit
         * @param verification what the verifier found; empty violations for a called unit the catalog does not hold
         */
        UnverifiedUnitException(String unitId, VerificationResult verification) {
            super(verification.passed()
                    ? "the sub-workflow '%s' is not in the catalog".formatted(unitId)
                    : "the unit '%s' violates %d rules of the verifier, the first: %s".formatted(unitId,
                    verification.violations().size(), verification.violations().getFirst()));
            this.unitId = unitId;
            this.verification = verification;
        }

        /** @return the refused unit */
        public String unitId() {
            return unitId;
        }

        /** @return what the verifier found; empty after deserialization or for a unit the catalog does not hold */
        public VerificationResult verification() {
            return verification == null ? new VerificationResult(List.of()) : verification;
        }
    }
}
