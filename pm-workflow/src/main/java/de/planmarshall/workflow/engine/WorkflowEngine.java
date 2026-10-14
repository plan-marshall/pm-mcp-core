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
import de.planmarshall.core.spi.JobStarter;
import de.planmarshall.core.spi.Primitive;
import de.planmarshall.core.spi.PrimitiveOutcome;
import de.planmarshall.core.spi.PrimitiveRegistry;
import de.planmarshall.core.spi.PrimitiveResult;
import de.planmarshall.core.spi.QuestionRecordReader;
import de.planmarshall.core.spi.QuestionRecordReader.QuestionRead;
import de.planmarshall.core.spi.QueueTaskCreator;
import de.planmarshall.core.spi.WorkflowClock;
import de.planmarshall.core.spi.WorkflowExecutionContext;
import de.planmarshall.core.store.JournalScopeType;
import de.planmarshall.core.store.RetryCounters;
import de.planmarshall.core.store.ScopeTransaction;
import de.planmarshall.core.store.WorkflowStatus;
import de.planmarshall.core.store.WorkflowStatus.ActiveJob;
import de.planmarshall.core.store.WorkflowStatus.JobEntry;
import de.planmarshall.core.store.WorkflowStatus.TaskEntry;
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
 * Entering a resting state parks it in the same status write: a {@code job} state binds the job a job-starting outcome
 * just reported, or starts its job through the {@link JobStarter} and binds that ({@code active_job},
 * {@code active_jobs}); a refused start is routed as the state's {@code refused} outcome. A {@code composite_job} state
 * binds itself; a task state creates its queue task through the {@link QueueTaskCreator} ({@code active_tasks}). A
 * resting state whose binding is in the store only waits: an advance after a restart never starts its job again. The
 * wait of the state ({@link RestingWait}) is derived from the store, and only the {@link EngineEvent} it waits for
 * resumes it ({@link #advance(WorkflowStatus, EngineEvent)}): a settled job routes its fate, settled tasks the evaluated
 * outcome, a time event re-runs the composite job's primitive, and a question resolution routes the outcome the record
 * read through the {@link QuestionRecordReader} reports. Waiting on a job is an engine event, never a link.
 * <p>
 * The advance returns the new status with {@code updated_at} from the clock; the caller writes it in the transaction
 * of the advance ({@link #advance(WorkflowStatus, WorkflowStatusStore, ScopeTransaction)}). Leaving a state clears the
 * job and task bindings of the state, and the engine holds no state outside the status. The engine counts no retry
 * itself: the retrying primitives count through
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

    /**
     * The phase of the status store for each phase of the DSL, as the state catalogue maps them; package-private for
     * the parity test that holds its values to {@code WorkflowStatus.PHASES}.
     */
    static final Map<String, String> PHASES = Map.ofEntries(
            Map.entry("init", "phase-1-init"),
            Map.entry("refine", "phase-2-refine"),
            Map.entry("outline", "phase-3-outline"),
            Map.entry("plan", "phase-4-plan"),
            Map.entry("execute", "phase-5-execute"),
            Map.entry("finalize", "phase-6-finalize"),
            Map.entry("completed", "completed"),
            Map.entry("aborted", "aborted"),
            Map.entry("epic", "epic"),
            Map.entry("workspace", "workspace"));

    private final WorkflowDefinition unit;
    private final Map<String, WorkflowDefinition> units;
    private final PrimitiveRegistry registry;
    private final ParameterBinder binder;
    private final JobStarter jobs;
    private final QueueTaskCreator tasks;
    private final QuestionRecordReader questions;
    private final WorkflowClock clock;

    /**
     * The ports through which the engine causes and observes what lies outside the status store.
     *
     * @param jobs      starts the job of a job state
     * @param tasks     creates the queue task of a task state
     * @param questions reads the question record a question state waits on
     * @param clock     the clock of {@code updated_at}, {@code pushed_at}, {@code raised_at} and a job's start
     * @since 0.1
     */
    public record Ports(JobStarter jobs, QueueTaskCreator tasks, QuestionRecordReader questions, WorkflowClock clock) {

        /** @throws NullPointerException if a port is {@code null} */
        public Ports {
            Objects.requireNonNull(jobs, "jobs");
            Objects.requireNonNull(tasks, "tasks");
            Objects.requireNonNull(questions, "questions");
            Objects.requireNonNull(clock, "clock");
        }
    }

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
     * @param ports     the ports of jobs, queue tasks, question records and time
     * @throws NullPointerException    if an argument is {@code null}
     * @throws UnverifiedUnitException if the unit, or a sub-workflow it calls, violates a rule of the verifier, or a
     *                                 called sub-workflow is not in the catalog
     */
    public WorkflowEngine(WorkflowDefinition unit, WorkflowCatalog catalog, WorkflowValidator validator,
            PrimitiveRegistry registry, ParameterBinder binder, Ports ports) {
        this.unit = Objects.requireNonNull(unit, "unit");
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(validator, "validator");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.binder = Objects.requireNonNull(binder, "binder");
        Objects.requireNonNull(ports, "ports");
        this.jobs = ports.jobs();
        this.tasks = ports.tasks();
        this.questions = ports.questions();
        this.clock = ports.clock();
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
     * Advances a scope from its current state. A scope that ended, that carries a condition, or that rests in a parked
     * state or a link or question state is returned unchanged; a job, composite job or task state the store binds
     * nothing to is parked now; from any other state the engine chains until the scope rests or ends.
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
            case StateBehavior.JobStep _, StateBehavior.ModelJob _ ->
                parked(status) ? resting(status, node) : new Run(status).chain(node.id());
            case StateBehavior.InteractiveLinks _, StateBehavior.QuestionWait _ -> resting(status, node);
        };
    }

    /**
     * Resumes a resting state by the event it waits for: the state routes the event's outcome, the binding of the state
     * is cleared in the transition that leaves it, and the engine chains on. A job state routes the fate of its settled
     * job, a task state the outcome its tasks were evaluated to, a composite job state the outcome its primitive returns
     * when it runs again on the event, and a question state the resolution the question record reports.
     *
     * @param status the status of the scope as the store holds it
     * @param event  the event
     * @return the step the advance ends in; the raised condition {@code precondition_unmet} when the question record
     *         of a resolution cannot be read; the status unchanged when the scope carries a condition
     * @throws IllegalArgumentException if the event is not the one the current state waits for, or the question record
     *                                  is still open or resolved with another outcome; the status stays as it is
     * @throws InternalFaultException   see {@link #advance(WorkflowStatus)}
     */
    public EngineStep advance(WorkflowStatus status, EngineEvent event) {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(event, "event");
        if (status.condition().isPresent()) {
            return EngineStep.ConditionRaised.of(status);
        }
        var node = stateOf(unitOf(CallStack.fromStore(status.callStack())), status.state());
        var wait = RestingWait.of(status, awaitedEventOf(node.behavior()));
        if (!wait.accepts(event)) {
            throw new IllegalArgumentException("the state '%s' waits for %s and does not accept %s"
                    .formatted(node.id(), wait, event));
        }
        var run = new Run(status);
        return switch (node.behavior()) {
            case StateBehavior.JobStep job when job.composite() ->
                run.chain(run.runPrimitive(node, job.primitiveId(), job.transitions()));
            case StateBehavior.JobStep job when event instanceof EngineEvent.JobSettled(_, var fate) ->
                run.chain(transitionOf(node, job.transitions(), fate, Map.of()).targetStateId());
            case StateBehavior.ModelJob modelJob when event instanceof EngineEvent.TasksSettled(_, var outcome) ->
                run.chain(transitionOf(node, modelJob.transitions(), outcome, Map.of()).targetStateId());
            case StateBehavior.QuestionWait question when event instanceof EngineEvent.QuestionResolved resolved ->
                resolve(run, status, node, question, resolved);
            default -> throw new IllegalStateException("the state '%s' accepted %s, which it cannot route"
                    .formatted(node.id(), event));
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
        return persisted(status, advance(status), store, transaction);
    }

    /**
     * Resumes a resting state by an event and adds the new status to the transaction of the advance.
     *
     * @param status      the status of the scope as the store holds it
     * @param event       the event
     * @param store       the status store of the scope
     * @param transaction the open transaction of the scope
     * @return the step the advance ends in
     * @throws IllegalArgumentException see {@link #advance(WorkflowStatus, EngineEvent)}
     */
    public EngineStep advance(WorkflowStatus status, EngineEvent event, WorkflowStatusStore store,
            ScopeTransaction transaction) {
        return persisted(status, advance(status, event), store, transaction);
    }

    /**
     * @param status the status of a scope
     * @return what the current state waits for; empty when it is no resting state
     */
    public Optional<RestingWait> restingWait(WorkflowStatus status) {
        Objects.requireNonNull(status, "status");
        var node = stateOf(unitOf(CallStack.fromStore(status.callStack())), status.state());
        return switch (node.behavior()) {
            case StateBehavior.InteractiveLinks _, StateBehavior.JobStep _, StateBehavior.ModelJob _,
                StateBehavior.QuestionWait _ -> Optional.of(RestingWait.of(status, awaitedEventOf(node.behavior())));
            case StateBehavior.AutoStep _, StateBehavior.SubWorkflowCall _, StateBehavior.TerminalState _ ->
                Optional.empty();
        };
    }

    private static EngineStep persisted(WorkflowStatus before, EngineStep step, WorkflowStatusStore store,
            ScopeTransaction transaction) {
        Objects.requireNonNull(store, "store");
        Objects.requireNonNull(transaction, "transaction");
        if (step.status() != before) {
            store.write(transaction, step.status());
        }
        return step;
    }

    private static boolean parked(WorkflowStatus status) {
        return status.activeJobBinding().filter(binding -> binding.stateId().equals(status.state())).isPresent();
    }

    private EngineStep resolve(Run run, WorkflowStatus status, StateNode node, StateBehavior.QuestionWait question,
            EngineEvent.QuestionResolved resolved) {
        var read = questions.read(new QuestionRecordReader.ReadRequest(Optional.ofNullable(status.scopeId()),
                resolved.questionId()));
        return switch (read) {
            case QuestionRead.Resolved(var outcome) when outcome.equals(resolved.outcome()) ->
                run.chain(transitionOf(node, question.transitions(), outcome, Map.of()).targetStateId());
            case QuestionRead.Resolved(var outcome) -> throw new IllegalArgumentException(
                    "the question %s resolved with '%s', not with '%s'".formatted(resolved.questionId(), outcome,
                            resolved.outcome()));
            case QuestionRead.Open _ -> throw new IllegalArgumentException(
                    "the question %s is still open".formatted(resolved.questionId()));
            case QuestionRead.Unreadable(var reason) -> {
                var facts = new LinkedHashMap<String, String>();
                facts.put("question_id", resolved.questionId());
                facts.put("reason", reason);
                yield raise(status, "precondition_unmet", facts);
            }
        };
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

        /** The job a job-starting outcome reported, which the job state the transition enters binds. */
        private String spawnedJobId;

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
                var spawned = spawnedJobId;
                spawnedJobId = null;
                switch (node.behavior()) {
                    case StateBehavior.AutoStep auto -> stateId = runPrimitive(node, auto.primitiveId(),
                            auto.transitions());
                    case StateBehavior.SubWorkflowCall call -> stateId = call(node, call);
                    case StateBehavior.TerminalState _ -> {
                        if (stack.isEmpty()) {
                            return ended(finish(), node);
                        }
                        stateId = returnFromFrame(node.terminalOutcome().orElseThrow(() -> fault(
                                Reason.UNRESOLVED_NAME, "the terminal state '%s' declares no outcome", node.id()))
                                .id());
                    }
                    case StateBehavior.JobStep job -> {
                        var refused = parkJob(node, job, spawned);
                        if (refused.isEmpty()) {
                            return resting(finish(), node);
                        }
                        stateId = refused.get();
                    }
                    case StateBehavior.ModelJob modelJob -> {
                        parkTasks(node, modelJob);
                        return resting(finish(), node);
                    }
                    case StateBehavior.InteractiveLinks _, StateBehavior.QuestionWait _ -> {
                        return resting(finish(), node);
                    }
                }
            }
        }

        /**
         * Binds a job state to its job: the composite job state to itself, the job state to the job a job-starting
         * outcome just reported or, without one, to the job the starter starts now.
         *
         * @return the target of the {@code refused} transition when the starter refused the start; empty when the state
         *         is parked
         */
        private Optional<String> parkJob(StateNode node, StateBehavior.JobStep job, String spawned) {
            if (job.composite()) {
                status = status.toBuilder().activeJob(new ActiveJob(node.id(), node.id())).build();
                return Optional.empty();
            }
            var jobId = spawned;
            if (jobId == null) {
                var started = jobs.start(new JobStarter.StartRequest(Optional.ofNullable(status.scopeId()), node.id(),
                        job.primitiveId()));
                if (!(started instanceof JobStarter.StartResult.Started(var startedId))) {
                    return Optional.of(transitionOf(node, job.transitions(), "refused", Map.of()).targetStateId());
                }
                jobId = startedId;
            }
            var kind = new JobStarter.StartResult.Started(jobId).kind().wireName();
            status = status.toBuilder()
                    .activeJob(new ActiveJob(node.id(), jobId))
                    .activeJobs(Map.of(job.primitiveId(), new JobEntry(jobId, kind, "queued", clock.now())))
                    .build();
            return Optional.empty();
        }

        /**
         * Creates the queue task of a task state for the one entity of its kind and binds the state to itself.
         *
         * @throws IllegalStateException for a fan-out state ({@code each}, {@code role each}), whose entity list the
         *                               package that computes it parks
         */
        private void parkTasks(StateNode node, StateBehavior.ModelJob modelJob) {
            if (modelJob.fanOutPrimitiveId().isPresent() || modelJob.roleName().isEmpty()) {
                throw new IllegalStateException("the fan-out task state '%s' is not parked by this engine"
                        .formatted(node.id()));
            }
            var role = modelJob.roleName().get();
            var entity = modelJob.entityKind();
            var created = tasks.create(new QueueTaskCreator.CreateRequest(Optional.ofNullable(status.scopeId()),
                    node.id(), entity, role));
            status = status.toBuilder()
                    .activeJob(new ActiveJob(node.id(), node.id()))
                    .activeTasks(Map.of(entity, new TaskEntry(created.queueTaskId(), role, "queued", null, null)))
                    .build();
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

        /**
         * Runs the primitive of an {@code auto} state, or of a composite job state its event resumes, and selects the
         * transition of the returned outcome.
         *
         * @return the target of that transition
         */
        String runPrimitive(StateNode node, String primitiveId, List<OutcomeTransition> transitions) {
            var primitive = primitiveOf(primitiveId);
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
            spawnedJobId = result.spawnedJobId().orElse(null);
            var outcome = result.outcome().wireName();
            return transitionOf(node, transitions, outcome, Map.of()).targetStateId();
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
