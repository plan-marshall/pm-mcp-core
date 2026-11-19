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
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.random.RandomGenerator;
import java.util.Optional;
import java.util.Set;

import de.planmarshall.core.service.InternalFaultException;
import de.planmarshall.core.service.InternalFaultException.Reason;
import de.planmarshall.core.spi.AwaitedEvent;
import de.planmarshall.core.spi.JobStarter;
import de.planmarshall.core.spi.Primitive;
import de.planmarshall.core.spi.PrimitiveRegistry;
import de.planmarshall.core.spi.PrimitiveResult;
import de.planmarshall.core.spi.QuestionRecordReader;
import de.planmarshall.core.spi.QuestionRecordReader.QuestionRead;
import de.planmarshall.core.spi.QueueTaskCreator;
import de.planmarshall.core.spi.WorkflowClock;
import de.planmarshall.core.spi.WorkflowExecutionContext;
import de.planmarshall.core.store.JournalScopeType;
import de.planmarshall.core.store.PlanHistory;
import de.planmarshall.core.store.PlanHistoryLine;
import de.planmarshall.core.store.RetryCounters;
import de.planmarshall.core.store.ScopeTransaction;
import de.planmarshall.core.store.WorkflowStatus;
import de.planmarshall.core.store.WorkflowStatus.ActiveJob;
import de.planmarshall.core.store.WorkflowStatus.JobEntry;
import de.planmarshall.core.store.WorkflowStatus.TaskEntry;
import de.planmarshall.core.store.WorkflowStatusStore;
import de.planmarshall.workflow.ast.CallFrame;
import de.planmarshall.workflow.ast.LinkAction;
import de.planmarshall.workflow.ast.LinkSpec;
import de.planmarshall.workflow.ast.OutcomeTransition;
import de.planmarshall.workflow.ast.ParamSpec;
import de.planmarshall.workflow.ast.Phase;
import de.planmarshall.workflow.ast.StateBehavior;
import de.planmarshall.workflow.ast.StateNode;
import de.planmarshall.workflow.ast.TerminalOutcome;
import de.planmarshall.workflow.ast.WorkflowDefinition;
import de.planmarshall.workflow.plan.CompositionEvent;
import de.planmarshall.workflow.plan.CompositionResult;
import de.planmarshall.workflow.plan.InstanceComposition;
import de.planmarshall.workflow.plan.InstancePosition;
import de.planmarshall.workflow.plan.InstanceUnits;
import de.planmarshall.workflow.plan.PlanInstanceComposer;
import de.planmarshall.workflow.verify.VerificationResult;
import de.planmarshall.workflow.verify.WorkflowCatalog;
import de.planmarshall.workflow.verify.WorkflowValidator;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

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
 * <li>a {@code call} state pushes a frame (the calling unit and state, the sub-workflow, the entry
 * the call goes through, the bound arguments, {@code pushed_at} from the {@link WorkflowClock}) and enters the
 * sub-workflow at the state that entry binds, the entry the call names or the callee's sole entry when the call names
 * none; the frame and its entry are written with the status in the transaction of the call; the phase of the scope is
 * the phase sub-workflow on the stack, which the callee shares;</li>
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

    /** The condition a refused composition of a server event raises on the composing transition. */
    public static final String PRECONDITION_UNMET = "precondition_unmet";

    /** The transitions one advance may chain at most; a verified unit never comes near it. */
    static final int MAX_CHAIN = 1_000;

    /** The phase of a scope whose unit carries no phase sub-workflow. */
    private static final String WORKSPACE_PHASE = "workspace";

    private static final String MACHINE_PHASE = "machine";

    private static final String EPIC_PHASE = "epic";

    /** The phase a plan scope opens at, before its first state is entered. */
    private static final String FIRST_PHASE = "phase-1-init";

    /**
     * The phase of every end but an abort: the delivery terminals {@code completed}, {@code handed_back} and
     * {@code returned} take it whatever their outcome class; the outcome tells them apart.
     */
    private static final String COMPLETED_PHASE = "completed";

    /** The phase of an end whose terminal outcome id is {@value #ABORTED_OUTCOME}, as of {@code plan.aborted}. */
    private static final String ABORTED_PHASE = "aborted";

    /** The terminal outcome id of an operator-initiated end, the only end that takes {@value #ABORTED_PHASE}. */
    private static final String ABORTED_OUTCOME = "aborted";

    /** The states the plan unit declares itself that name no phase: they carry the phase the plan called last. */
    private static final Set<String> PLAN_UNIT_OWN_STATES = Set.of("plan.return", "plan.aborted");

    private final WorkflowDefinition unit;
    private final Map<String, WorkflowDefinition> units;
    private final PrimitiveRegistry registry;
    private final ParameterBinder binder;
    private final JobStarter jobs;
    private final QueueTaskCreator tasks;
    private final QuestionRecordReader questions;
    private final WorkflowClock clock;
    private final RandomGenerator ids;
    private final @Nullable InputBindingContext bindingContext;
    private final @Nullable InstanceComposition composition;
    private final @Nullable InstanceUnits versionUnits;

    /**
     * The ports through which the engine causes and observes what lies outside the status store.
     *
     * @param jobs        starts the job of a job state
     * @param tasks       creates the queue task of a task state
     * @param questions   reads the question record a question state waits on
     * @param clock       the clock of {@code updated_at}, {@code pushed_at}, {@code raised_at} and a job's start
     * @param composition composes and records the plan instance versions; {@code null} for a scope that composes none
     * @param units       reads the units of the version in force; {@code null} runs the verified unit the engine was
     *                    built with
     * @since 0.1
     */
    @Builder
    public record Ports(JobStarter jobs, QueueTaskCreator tasks, QuestionRecordReader questions, WorkflowClock clock,
    RandomGenerator ids,
    @Nullable InputBindingContext bindingContext,
    @Nullable InstanceComposition composition,
    @Nullable InstanceUnits units) {

        /** @throws NullPointerException if a port other than the two instance ports is {@code null} */
        public Ports {
            Objects.requireNonNull(jobs, "jobs");
            Objects.requireNonNull(tasks, "tasks");
            Objects.requireNonNull(questions, "questions");
            Objects.requireNonNull(clock, "clock");
            Objects.requireNonNull(ids, "ids");
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

        /**
         * Binds the parameters of a primitive an operator act runs, with the operator's submission for the link's free
         * parameters. A binder that reads the submission overrides it; the default ignores it and binds from the status
         * alone, as every non-operator caller does.
         *
         * @param primitive  the primitive the current state runs
         * @param status     the status of the scope at the state
         * @param parameters the operator's submission for the link's free parameters, by name; empty for a link without
         *                   one
         * @return the parameter record, an instance of {@link Primitive#parameterType()}
         */
        default Record bind(Primitive<?, ?> primitive, WorkflowStatus status, Map<String, String> parameters) {
            if (!parameters.isEmpty()) {
                throw new UnsupportedOperationException(
                        "the binder of '%s' accepts no operator link parameters; install a parameter-aware binder"
                                .formatted(primitive.id()));
            }
            return bind(primitive, status);
        }
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
        this.ids = ports.ids();
        this.bindingContext = ports.bindingContext();
        this.composition = ports.composition();
        this.versionUnits = ports.units();
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
     * @param scopeId   the plan or epic id; {@code null} for the workspace and the machine scope
     * @return the step the start ends in, with the first status of the scope
     * @throws IllegalArgumentException if the unit declares no start state (a unit with entries is called, never
     *                                  started), or the scope does not fit the status store
     */
    public EngineStep start(JournalScopeType scopeType, @Nullable String scopeId) {
        var status = initialStatus(scopeType, scopeId);
        return new Run(status).chain(startStateOf(unit).id());
    }

    /**
     * Starts a scope at the unit's start state, composing version 1 of a plan instance in the same creation
     * transaction, and chains from there.
     * <p>
     * {@code start} is the creation's composition event (Workflow DSL § Versions &amp; Composition Events): the version
     * is composed before the first state is entered, so it is in force from the plan's first state, and a refused
     * creation composition creates no plan.
     *
     * @param scopeType   the type of the scope
     * @param scopeId     the plan or epic id; {@code null} for the workspace and the machine scope
     * @param store       the status store of the scope
     * @param transaction the transaction that creates the scope
     * @param history     the plan's history
     * @return the step the start ends in, with the first status of the scope
     * @throws IllegalArgumentException if the unit declares no start state, or the scope does not fit the status store
     * @throws IllegalStateException    if the creation composition of {@code start} is refused
     */
    public EngineStep start(JournalScopeType scopeType, @Nullable String scopeId, WorkflowStatusStore store,
            ScopeTransaction transaction, PlanHistory history) {
        Objects.requireNonNull(store, "store");
        Objects.requireNonNull(transaction, "transaction");
        Objects.requireNonNull(history, "history");
        var before = initialStatus(scopeType, scopeId);
        var run = new Run(before, history, transaction);
        run.composeStart();
        return persisted(before, run.chain(startStateOf(run.unitOf(CallStack.empty())).id()), store, transaction);
    }

    private WorkflowStatus initialStatus(JournalScopeType scopeType, @Nullable String scopeId) {
        var startState = startStateOf(unit);
        var now = clock.now();
        return WorkflowStatus.builder()
                .scopeType(scopeType)
                .scopeId(scopeId)
                .phase(initialPhase(scopeType))
                .state(startState.id())
                .callStack(List.of())
                .policyCounters(Map.of())
                .waiverRequests(List.of())
                .retryCounters(RetryCounters.empty())
                .activeJobs(Map.of())
                .activeTasks(Map.of())
                .updatedAt(now)
                .retained(Map.of())
                .build();
    }

    private static StateNode startStateOf(WorkflowDefinition definition) {
        var startStateId = definition.startState();
        if (startStateId == null) {
            throw new IllegalArgumentException(
                    "the unit '%s' declares no start state; a unit with entries is called, never started"
                            .formatted(definition.id()));
        }
        return stateOf(definition, startStateId);
    }

    /**
     * Composes an operator or configuration event ({@code config_adopted}, {@code waiver}, {@code override}) at the
     * scope's position and puts its version in force from the next transition: the state does not move, and a version
     * is never in force mid-state (Workflow DSL § Versions &amp; Composition Events, item 1).
     * <p>
     * The status and the version's history line are written in {@code transaction}; a refused version leaves the
     * version in force and is returned as the result, its attempt logged in the same transaction. The version's files
     * are staged in that transaction, so the transition that runs the new version is a later advance, after this call
     * committed; an advance inside this transaction reads the version through the composition port's units.
     *
     * @param status      the status of the scope
     * @param event       the operator or configuration event
     * @param store       the status store of the scope
     * @param transaction the transaction of the advance
     * @param history     the plan's history
     * @return the status after the composition, with the version in force, and the composition's result
     * @throws IllegalArgumentException if the event is a server event, or the scope is no plan scope
     * @throws IllegalStateException    if the engine carries no instance composition
     */
    public OperatorComposition composeOperatorEvent(WorkflowStatus status, CompositionEvent event,
            WorkflowStatusStore store, ScopeTransaction transaction, PlanHistory history) {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(store, "store");
        Objects.requireNonNull(transaction, "transaction");
        Objects.requireNonNull(history, "history");
        if (event.trigger() != CompositionEvent.Trigger.OPERATOR) {
            throw new IllegalArgumentException("the event '%s' is no operator or configuration event"
                    .formatted(event.wireName()));
        }
        if (status.scopeType() != JournalScopeType.PLAN) {
            throw new IllegalArgumentException("the %s scope composes no plan instance"
                    .formatted(status.scopeType().wireName()));
        }
        var run = new Run(status, history, transaction);
        var result = run.composeOperator(event);
        var composed = run.flush();
        store.write(transaction, composed);
        return new OperatorComposition(composed, result);
    }

    /**
     * The outcome of composing an operator or configuration event: the status with the version in force, and the
     * composition's result, the refusal of a version the position rule or a check refused included.
     *
     * @param status the status after the composition
     * @param result the composition's result
     * @since 0.1
     */
    public record OperatorComposition(WorkflowStatus status, CompositionResult result) {

        /**
         * @throws NullPointerException if a component is {@code null}
         */
        public OperatorComposition {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(result, "result");
        }
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
        return advance(status, new Run(status));
    }

    private EngineStep advance(WorkflowStatus status, Run run) {
        Objects.requireNonNull(status, "status");
        if (status.condition().isPresent()) {
            return EngineStep.ConditionRaised.of(status);
        }
        var stack = CallStack.fromStore(status.callStack());
        var node = stateOf(run.unitOf(stack), status.state());
        return switch (node.behavior()) {
            case StateBehavior.AutoStep _, StateBehavior.SubWorkflowCall _ -> run.chain(node.id());
            case StateBehavior.TerminalState _ -> stack.isEmpty() ? ended(status, node) : run.chain(node.id());
            case StateBehavior.JobStep _, StateBehavior.ModelJob _ ->
                parked(status) ? resting(status, node) : run.chain(node.id());
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
        return advance(status, event, new Run(status));
    }

    private EngineStep advance(WorkflowStatus status, EngineEvent event, Run run) {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(event, "event");
        if (status.condition().isPresent()) {
            return EngineStep.ConditionRaised.of(status);
        }
        var node = stateOf(run.unitOf(CallStack.fromStore(status.callStack())), status.state());
        var wait = RestingWait.of(status, awaitedEventOf(node.behavior()));
        if (!wait.accepts(event)) {
            throw new IllegalArgumentException("the state '%s' waits for %s and does not accept %s"
                    .formatted(node.id(), wait, event));
        }
        return switch (node.behavior()) {
            case StateBehavior.JobStep job when job.composite() ->
                run.chain(run.runPrimitive(node, job.primitiveId(), job.transitions()));
            case StateBehavior.JobStep job when event instanceof EngineEvent.JobSettled(_, var fate) ->
                run.chain(run.leftTo(node, transitionOf(node, job.transitions(), fate, Map.of()).targetStateId(),
                        fate));
            case StateBehavior.ModelJob modelJob when event instanceof EngineEvent.TasksSettled(_, _, var outcome) ->
                run.chain(run.leftTo(node, transitionOf(node, modelJob.transitions(), outcome, Map.of())
                        .targetStateId(), outcome));
            case StateBehavior.QuestionWait question when event instanceof EngineEvent.QuestionResolved resolved ->
                resolve(run, status, node, question, resolved);
            case StateBehavior.InteractiveLinks links when event instanceof EngineEvent.OperatorAct act ->
                run.chain(run.runPrimitive(node, primitiveOf(linkOf(links, act)),
                        linkOf(links, act).transitions(), act.parameters()));
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
        return persisted(status, advance(status, new Run(status, null, transaction)), store, transaction);
    }

    /**
     * Advances a scope, adds the new status to the transaction of the advance, and appends the history lines the
     * advance produced to the plan's history in the same transaction, so the state change and its history are one
     * journaled write.
     *
     * @param status          the status of the scope as the store holds it
     * @param store           the status store of the scope
     * @param transaction     the open transaction of the scope
     * @param history         the plan's history
     * @return the step the advance ends in
     * @throws InternalFaultException see {@link #advance(WorkflowStatus)}
     */
    public EngineStep advance(WorkflowStatus status, WorkflowStatusStore store, ScopeTransaction transaction,
            PlanHistory history) {
        Objects.requireNonNull(history, "history");
        return persisted(status, advance(status, new Run(status, history, transaction)), store, transaction);
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
        return persisted(status, advance(status, event, new Run(status, null, transaction)), store, transaction);
    }

    /**
     * Resumes a resting state by an event, adds the new status to the transaction of the advance, and appends the
     * history lines the advance produced to the plan's history in the same transaction.
     *
     * @param status          the status of the scope as the store holds it
     * @param event           the event
     * @param store           the status store of the scope
     * @param transaction     the open transaction of the scope
     * @param history         the plan's history
     * @return the step the advance ends in
     * @throws IllegalArgumentException see {@link #advance(WorkflowStatus, EngineEvent)}
     */
    public EngineStep advance(WorkflowStatus status, EngineEvent event, WorkflowStatusStore store,
            ScopeTransaction transaction, PlanHistory history) {
        Objects.requireNonNull(history, "history");
        return persisted(status, advance(status, event, new Run(status, history, transaction)), store, transaction);
    }

    /**
     * @param status the status of a scope
     * @return what the current state waits for; empty when it is no resting state
     */
    public Optional<RestingWait> restingWait(WorkflowStatus status) {
        Objects.requireNonNull(status, "status");
        var run = new Run(status);
        var node = stateOf(run.unitOf(CallStack.fromStore(status.callStack())), status.state());
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
        var read = questions.read(new QuestionRecordReader.ReadRequest(status.scopeId(), resolved.questionId()));
        return switch (read) {
            case QuestionRead.Resolved(var outcome) when outcome.equals(resolved.outcome()) ->
                run.chain(run.leftTo(node, transitionOf(node, question.transitions(), outcome, Map.of())
                        .targetStateId(), outcome));
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
     * outcome {@value #STEPPED_OUT}. This is the implicit {@code step_out} of a resting link state of an operator wait
     * in a sub-workflow.
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
        var run = new Run(status);
        var node = stateOf(run.unitOf(stack), status.state());
        if (!(node.behavior() instanceof StateBehavior.InteractiveLinks)) {
            throw new IllegalStateException("only a resting link state offers the step-out, not '%s'"
                    .formatted(node.id()));
        }
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
        var outcome = terminalOutcomeOf(terminal);
        var phase = ABORTED_OUTCOME.equals(outcome.id()) ? ABORTED_PHASE : COMPLETED_PHASE;
        var endedStatus = phase.equals(status.phase()) ? status : status.toBuilder().phase(phase).build();
        return new EngineStep.Ended(endedStatus, outcome.id(), outcome.outcomeClass());
    }

    /** The outcome a terminal state reports; a terminal state without one is a fault of the unit. */
    private static TerminalOutcome terminalOutcomeOf(StateNode terminal) {
        var outcome = terminal.terminalOutcome();
        if (outcome == null) {
            throw fault(Reason.UNRESOLVED_NAME, "the terminal state '%s' declares no outcome", terminal.id());
        }
        return outcome;
    }

    private EngineStep resting(WorkflowStatus status, StateNode node) {
        return new EngineStep.Resting(status, node.id(), awaitedEventOf(node.behavior()));
    }

    private AwaitedEvent awaitedEventOf(StateBehavior behavior) {
        return switch (behavior) {
            case StateBehavior.InteractiveLinks _ -> AwaitedEvent.OPERATOR_ACT;
            case StateBehavior.JobStep job -> job.composite() ? primitiveOf(job.primitiveId()).awaitedEvent()
                    : AwaitedEvent.JOB_SETTLED;
            case StateBehavior.ModelJob _ -> AwaitedEvent.TASK_SETTLED;
            case StateBehavior.QuestionWait _ -> AwaitedEvent.QUESTION_ANSWER;
            case StateBehavior.AutoStep _, StateBehavior.SubWorkflowCall _, StateBehavior.TerminalState _ ->
                AwaitedEvent.NONE;
        };
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

    /** The link of an operator wait the operator's act names, with its submitted parameters checked against its free parameters. */
    private static LinkSpec linkOf(StateBehavior.InteractiveLinks links, EngineEvent.OperatorAct act) {
        var link = links.links().stream().filter(candidate -> candidate.idPattern().equals(act.linkId())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "the operator wait '%s' offers no link '%s'".formatted(act.stateId(), act.linkId())));
        if (link.freeParams().isEmpty() && !act.parameters().isEmpty()) {
            throw new IllegalArgumentException("the link '%s' declares no parameters, but the act submits %s"
                    .formatted(link.idPattern(), act.parameters().keySet()));
        }
        if (!link.freeParams().isEmpty() && act.parameters().isEmpty()) {
            throw new IllegalArgumentException("the link '%s' requires its free parameters %s".formatted(
                    link.idPattern(), link.freeParams().stream().map(ParamSpec::name).toList()));
        }
        return link;
    }

    /** The primitive a link runs; {@code do <primitive_id>} is the only link action. */
    private static String primitiveOf(LinkSpec link) {
        return ((LinkAction.ExecutePrimitive) link.action()).primitiveId();
    }

    /**
     * The phase a scope opens at, before its first state is entered: the scope's own phase for the scope types that
     * carry one, and the first plan phase otherwise. {@code enter} derives the phase of the state actually reached.
     */
    private static String initialPhase(JournalScopeType scopeType) {
        return switch (scopeType) {
            case WORKSPACE -> WORKSPACE_PHASE;
            case MACHINE -> MACHINE_PHASE;
            case EPIC -> EPIC_PHASE;
            case PLAN -> FIRST_PHASE;
            case LESSONS -> throw new IllegalArgumentException("the lesson store has no status document");
        };
    }

    /**
     * The phase of the state about to be current (PM-WF-16): the phase sub-workflow on the call stack, else the phase
     * unit the current unit itself is, else the phase the plan unit's call state {@code phases.<name>} names, else the
     * scope's own phase. No unit and no state declares a phase of its own.
     *
     * @param currentUnit the unit the state belongs to
     * @param stateId     the identifier of the state
     * @param stack       the call stack at the state
     * @param previous    the phase the scope carried before, the last fallback of a plan scope
     */
    private static String phaseOf(WorkflowDefinition currentUnit, String stateId, CallStack stack, String previous) {
        for (var frame : stack.unwound()) {
            var phase = Phase.ofUnit(frame.subWorkflowId());
            if (phase.isPresent()) {
                return phase.get().wireName();
            }
        }
        var unitPhase = Phase.ofUnit(currentUnit.id());
        if (unitPhase.isPresent()) {
            return unitPhase.get().wireName();
        }
        var callStatePhase = Phase.ofCallState(stateId);
        if (callStatePhase.isPresent()) {
            return callStatePhase.get().wireName();
        }
        var prefixed = phaseOfStatePrefix(stateId);
        if (prefixed.isPresent()) {
            return prefixed.get().wireName();
        }
        return previous;
    }

    /**
     * The phase a flat plan unit's state names by the first segment of its identifier ({@code execute.commit}), which
     * the engine's fixtures and a pre-split unit carry where a composed unit has the call state {@code phases.execute}.
     * A composed unit derives its phase from the call stack, so this is the last fallback before the previous phase. A
     * state of the plan unit itself that names no phase ({@code plan.return}, {@code plan.aborted}) keeps the phase the
     * plan called last, so those two are not read as the phase {@code plan}.
     */
    private static Optional<Phase> phaseOfStatePrefix(String stateId) {
        var dot = stateId.indexOf('.');
        if (dot < 0) {
            return Optional.empty();
        }
        var segment = stateId.substring(0, dot);
        if (PLAN_UNIT_OWN_STATES.contains(stateId)) {
            return Optional.empty();
        }
        return Arrays.stream(Phase.values())
                .filter(phase -> phase.name().toLowerCase(Locale.ROOT).equals(segment)).findFirst();
    }

    private static String phaseOf(WorkflowDefinition currentUnit, String stateId, CallStack stack,
            WorkflowStatus status) {
        var scopePhase = switch (status.scopeType()) {
            case WORKSPACE -> WORKSPACE_PHASE;
            case MACHINE -> MACHINE_PHASE;
            case EPIC -> EPIC_PHASE;
            case PLAN -> status.phase();
            case LESSONS -> throw new IllegalArgumentException("the lesson store has no status document");
        };
        return phaseOf(currentUnit, stateId, stack, scopePhase);
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

    private static <P extends Record> PrimitiveResult<?> execute(
            Primitive<P, ?> primitive, WorkflowExecutionContext context, Record parameters) {
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

        /** The plan's history the advance appends to, and the transaction that writes it; {@code null} for none. */
        private final @Nullable PlanHistory history;

        private final @Nullable ScopeTransaction transaction;

        /** The instance version in force from here on; the status's, or the version a composition just took force. */
        private int instanceVersion;

        /** The units of the version in force, read once per advance by unit id. */
        private final Map<String, WorkflowDefinition> unitsInForce = new HashMap<>();

        /** The units of a version this advance composed, readable before its files are committed; {@code null} for none. */
        private @Nullable InstanceUnits justComposed;

        /** The history lines of this advance, in the order the advance produced them. */
        private final List<Pending> lines = new ArrayList<>();

        /** The job a job-starting outcome reported, which the job state the transition enters binds. */
        private @Nullable String spawnedJobId;

        Run(WorkflowStatus status) {
            this(status, null, null);
        }

        /** The correlation id of this advance: one per Run, shared by every frame it pushes. */
        private final de.planmarshall.core.id.CorrelationId correlationId;

        Run(WorkflowStatus status, @Nullable PlanHistory history, @Nullable ScopeTransaction transaction) {
            this.status = status;
            this.stack = CallStack.fromStore(status.callStack());
            this.history = history;
            this.transaction = transaction;
            this.instanceVersion = status.instanceVersionInForce().orElse(1);
            this.correlationId = de.planmarshall.core.id.CorrelationId.random(ids);
            for (var index = 0; index < stack.depth(); index++) {
                results.push(new LinkedHashMap<>());
            }
        }

        /**
         * The unit a state belongs to: the version in force's unit where the version is readable, else the verified
         * unit the engine was built with.
         *
         * @param stack the call stack at the state
         * @return the unit that declares the state
         */
        private WorkflowDefinition unitOf(CallStack stack) {
            return unitById(stack.top().map(frame -> frame.subWorkflowId()).orElse(unit.id()));
        }

        /**
         * @param unitId the id of a unit
         * @return the unit as the version in force carries it, else the verified unit the engine was built with
         */
        private WorkflowDefinition unitById(String unitId) {
            return unitsInForce.computeIfAbsent(unitId, this::loadUnit);
        }

        /**
         * Reads one unit of the version in force: its own plan unit, the composed file of a phase sub-workflow or bound
         * axis variant, or the stored text of a unit the header binds by digest. A version this advance composed is
         * read through the units the composition answered for it, because its files are staged and not yet committed.
         *
         * @param unitId the id of the unit
         * @return the unit as the version carries it
         * @throws InternalFaultException if the version carries no such unit
         */
        private WorkflowDefinition loadUnit(String unitId) {
            var version = status.instanceVersionInForce().orElse(null);
            var units = justComposed != null ? justComposed : versionUnits;
            if (version == null || units == null) {
                return verifiedUnit(unitId);
            }
            var plan = units.planUnit(version).orElseThrow(() -> fault(Reason.UNRESOLVED_NAME,
                    "the version %d in force carries no plan unit", version));
            if (unitId.equals(plan.id())) {
                return plan;
            }
            return units.composedUnit(version, unitId)
                    .or(() -> units.subUnit(version, unitId))
                    .orElseThrow(() -> fault(Reason.UNRESOLVED_NAME,
                            "the version %d in force carries no unit '%s'", version, unitId));
        }


        /**
         * @return where the scope stands: the unit and state it is in, and the calling unit and state of every frame
         */
        InstancePosition position() {
            return InstancePosition.of(unitOf(stack).id(), status.state(),
                    stack.frames().stream()
                            .map(frame -> new InstancePosition.UnitState(frame.callerWorkflowId(),
                                    frame.callerStateId()))
                            .toList());
        }

        /** Records a history line of the advance; a run without a history records nothing. */
        private void record(PlanHistoryLine.Event event, String node, @Nullable String outcome) {
            if (history != null) {
                lines.add(Pending.builder()
                        .event(event)
                        .node(node)
                        .callPath(stack.toStore().stream()
                                .map(WorkflowStatus.CallFrame::subworkflowId).toList())
                        .phase(status.phase())
                        .outcome(outcome)
                        .instanceVersion(instanceVersion)
                        .build());
            }
        }

        /** Records that the state was left, with the outcome its transition was chosen by. */
        void leave(StateNode node, String outcome) {
            record(PlanHistoryLine.Event.LEFT, node.id(), outcome);
        }

        /** Records the state as left and returns the target its transition was chosen for. */
        String leftTo(StateNode node, String target, String outcome) {
            leave(node, outcome);
            return target;
        }

        /** Writes the lines of the advance into the transaction, numbered after the history in force. */
        private void recordHistory() {
            if (history == null || lines.isEmpty()) {
                return;
            }
            var seq = history.nextSeq();
            var numbered = new ArrayList<PlanHistoryLine>(lines.size());
            for (var line : lines) {
                numbered.add(line.line(seq++, clock.now()));
            }
            history.append(transaction, numbered);
        }

        /**
         * A line of the advance before it is numbered; the instance version in force is the one the line was produced
         * under, so a line before a composition keeps the version it belongs to.
         */
        @Builder
        private record Pending(PlanHistoryLine.Event event, String node, List<String> callPath, String phase,
        @Nullable String outcome, int instanceVersion) {

            PlanHistoryLine line(long seq, Instant at) {
                return PlanHistoryLine.of(seq, event, node, callPath, phase, outcome, instanceVersion, at);
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
                var refusedComposition = enter(node);
                if (refusedComposition != null) {
                    return EngineStep.ConditionRaised.raise(finish(), PRECONDITION_UNMET, refusedComposition,
                            clock.now());
                }
                node = stateOf(unitOf(stack), stateId);
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
                        stateId = returnFromFrame(terminalOutcomeOf(node).id());
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
        private Optional<String> parkJob(StateNode node, StateBehavior.JobStep job, @Nullable String spawned) {
            if (job.composite()) {
                status = status.toBuilder().activeJob(new ActiveJob(node.id(), node.id())).build();
                return Optional.empty();
            }
            var jobId = spawned;
            if (jobId == null) {
                var started = jobs.start(new JobStarter.StartRequest(status.scopeId(), node.id(), job.primitiveId()));
                if (!(started instanceof JobStarter.StartResult.Started(var startedId))) {
                    leave(node, "refused");
                    return Optional.of(transitionOf(node, job.transitions(), "refused", Map.of()).targetStateId());
                }
                jobId = startedId;
            }
            var kind = new JobStarter.StartResult.Started(jobId).kind().wireName();
            status = status.toBuilder()
                    .activeJob(new ActiveJob(node.id(), jobId))
                    .activeJobs(Map.of(job.primitiveId(), JobEntry.builder()
                            .jobId(jobId)
                            .kind(kind)
                            .status("queued")
                            .startedAt(clock.now())
                            .build()))
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
            var role = modelJob.roleName();
            if (modelJob.fanOutPrimitiveId() != null || role == null) {
                throw new IllegalStateException("the fan-out task state '%s' is not parked by this engine"
                        .formatted(node.id()));
            }
            var entity = modelJob.entityKind();
            var created = tasks.create(QueueTaskCreator.CreateRequest.builder()
                    .scopeId(status.scopeId())
                    .stateId(node.id())
                    .entityKey(entity)
                    .role(role)
                    .build());
            status = status.toBuilder()
                    .activeJob(new ActiveJob(node.id(), node.id()))
                    .activeTasks(Map.of(entity, TaskEntry.builder()
                            .queueTaskId(created.queueTaskId())
                            .role(role)
                            .state("queued")
                            .outcome(null)
                            .reason(null)
                            .build()))
                    .build();
        }

        /** Makes the state current: its phase applies, and the bindings of the state left are cleared. */
        private @Nullable Map<String, String> enter(StateNode node) {
            var phase = phaseOf(unitOf(stack), node.id(), stack, status);
            status = status.toBuilder()
                    .state(node.id())
                    .phase(phase)
                    .callStack(stack.toStore())
                    .activeJob(null)
                    .activeJobs(Map.of())
                    .activeTasks(Map.of())
                    .build();
            record(PlanHistoryLine.Event.ENTERED, node.id(), null);
            return composeAtEntry(node);
        }

        /**
         * Composes the version of the server event whose target state the state is, in the transaction that enters it,
         * so the version is in force for the rest of this chain (Workflow DSL § Versions &amp; Composition Events).
         *
         * @return the facts of a refused composition, which the signal raises as {@code precondition_unmet}; {@code
         *         null} when the state composes no event or the version composed
         */
        private @Nullable Map<String, String> composeAtEntry(StateNode node) {
            if (composition == null || status.scopeType() != JournalScopeType.PLAN) {
                return null;
            }
            var event = composition.serverEventAt(node.id()).orElse(null);
            if (event == null || event.trigger() != CompositionEvent.Trigger.SERVER) {
                return null;
            }
            if (transaction == null || history == null) {
                return Map.of("reason", PRECONDITION_UNMET, "detail",
                        ("the event '%s' composes only in an advance that carries the transaction and the plan's "
                                + "history").formatted(event.wireName()));
            }
            if (justComposed != null) {
                return Map.of("reason", PRECONDITION_UNMET, "detail",
                        "the version composed earlier in this advance must be committed before '%s' composes another"
                                .formatted(event.wireName()));
            }
            return composeAndTakeForce(event);
        }

        /**
         * Composes the event at the position and puts a composed version in force; a refused version leaves the version
         * in force and is reported as the condition facts.
         *
         * @param event the composition event
         * @return the facts of the refusal, {@code null} when a version took force
         */
        private @Nullable Map<String, String> composeAndTakeForce(CompositionEvent event) {
            if (composition == null) {
                throw new IllegalStateException("no plan instance composition is wired");
            }
            if (status.scopeType() != JournalScopeType.PLAN) {
                throw new IllegalStateException(
                        "the %s scope composes no plan instance".formatted(status.scopeType().wireName()));
            }
            var composing = transaction;
            if (composing == null) {
                throw new IllegalStateException(
                        "the event '%s' composes in the transaction of an advance".formatted(event.wireName()));
            }
            if (history == null) {
                throw new IllegalStateException("the event '%s' composes in an advance that carries the plan's history"
                        .formatted(event.wireName()));
            }
            var result = composition.compose(event, position(), status.instanceVersionInForce().orElse(0));
            return switch (result) {
                case CompositionResult.Composed composed -> {
                    takeForce(composing, composed);
                    yield null;
                }
                case CompositionResult.Refused refused -> {
                    composition.recordOutcome(composing, refused);
                    yield refusalFacts(event, refused);
                }
            };
        }

        /**
         * Puts a composed version in force from here on: records it and its history line in the transaction, switches
         * the units to the version's composed files, and leaves every counter and every history line as they are.
         *
         * @param composing the transaction of the advance
         * @param composed  the composed version
         */
        private void takeForce(ScopeTransaction composing, CompositionResult.Composed composed) {
            var version = composed.instance().version();
            instanceVersion = version;
            composition.recordOutcome(composing, composed);
            justComposed = composition.unitsOf(composed);
            unitsInForce.clear();
            status = status.toBuilder().instanceVersion(version).build();
            record(PlanHistoryLine.Event.INSTANCE, PlanInstanceComposer.PLAN_UNIT, null);
        }

        private static Map<String, String> refusalFacts(CompositionEvent event, CompositionResult.Refused refused) {
            var facts = new LinkedHashMap<String, String>();
            facts.put("event", event.wireName());
            facts.put("reason", refused.refusal().wireName());
            if (!refused.violations().isEmpty()) {
                facts.put("violation", refused.violations().getFirst());
            }
            return facts;
        }

        /**
         * Composes version 1 of a plan in its creation transaction: {@code start} is the creation's event, so a refused
         * creation composition creates no plan.
         */
        void composeStart() {
            if (composition == null || status.scopeType() != JournalScopeType.PLAN) {
                return;
            }
            if (composeAndTakeForce(CompositionEvent.START) != null) {
                throw new IllegalStateException("the creation composition of 'start' was refused");
            }
        }

        /**
         * Composes an operator or configuration event at the current position and puts its version in force from the
         * next transition: the state does not move.
         *
         * @param event the operator or configuration event
         * @return the composition's result
         */
        CompositionResult composeOperator(CompositionEvent event) {
            if (composition == null) {
                throw new IllegalStateException("no plan instance composition is wired");
            }
            var composing = transaction;
            if (composing == null) {
                throw new IllegalStateException(
                        "the event '%s' composes in a transaction".formatted(event.wireName()));
            }
            var result = composition.compose(event, position(), status.instanceVersionInForce().orElse(0));
            switch (result) {
                case CompositionResult.Composed composed -> takeForce(composing, composed);
                case CompositionResult.Refused refused -> composition.recordOutcome(composing, refused);
            }
            return result;
        }

        /** Writes the status and the pending history lines of an operator composition. */
        WorkflowStatus flush() {
            status = status.toBuilder().updatedAt(clock.now()).build();
            recordHistory();
            return status;
        }

        /**
         * Runs the primitive of an {@code auto} state, or of a composite job state its event resumes, and selects the
         * transition of the returned outcome.
         *
         * @return the target of that transition
         */
        String runPrimitive(StateNode node, String primitiveId, List<OutcomeTransition> transitions) {
            return runPrimitive(node, primitiveId, transitions, Map.of());
        }

        /**
         * Runs the primitive of an {@code auto} state, or of a composite job state its event resumes, and selects the
         * transition of the returned outcome.
         *
         * @param operatorParameters the operator's submission for an operator link's free parameters; empty otherwise
         * @return the target of that transition
         */
        String runPrimitive(StateNode node, String primitiveId, List<OutcomeTransition> transitions,
                Map<String, String> operatorParameters) {
            var primitive = primitiveOf(primitiveId);
            var parameters = Objects.requireNonNull(binder.bind(primitive, status, operatorParameters), "parameters");
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
            spawnedJobId = result.spawnedJobId();
            var outcome = result.outcome().wireName();
            leave(node, outcome);
            return transitionOf(node, transitions, outcome, Map.of()).targetStateId();
        }

        private String call(StateNode node, StateBehavior.SubWorkflowCall call) {
            var callee = unitById(call.subWorkflowId());
            var entry = entryOf(callee, call);
            var frame = CallFrame.builder()
                    .callerWorkflowId(unitOf(stack).id())
                    .callerStateId(node.id())
                    .subWorkflowId(callee.id())
                    .entry(entry)
                    .boundArgs(call.paramBindings())
                    .commandId(de.planmarshall.core.id.CommandId.random(ids))
                    .correlationId(correlationId)
                    .pushedAt(clock.now().toString())
                    .build();
            record(PlanHistoryLine.Event.CALLED, callee.id(), null);
            stack = stack.push(frame);
            results.push(new LinkedHashMap<>());
            return callee.entryBindings().get(entry).stateId();
        }

        /**
         * The entry a call goes through, which its frame records and whose bound state the call enters: the entry the
         * call names, or the callee's sole entry when the call names none.
         */
        private String entryOf(WorkflowDefinition callee, StateBehavior.SubWorkflowCall call) {
            var bindings = callee.entryBindings();
            var entry = call.entry();
            if (entry != null) {
                if (!bindings.containsKey(entry)) {
                    throw fault(Reason.UNRESOLVED_NAME, "the sub-workflow '%s' binds no entry '%s'", callee.id(),
                            entry);
                }
                return entry;
            }
            if (bindings.size() != 1) {
                throw fault(Reason.UNRESOLVED_NAME, "the call of '%s' names no entry, and the callee binds %s entries",
                        callee.id(), bindings.size());
            }
            return bindings.keySet().iterator().next();
        }

        /**
         * Pops the top frame, drops the retry counts of the left sub-workflow, restores the caller's phase and selects
         * the caller's transition of the returned outcome.
         *
         * @return the target of that transition, in the calling unit
         */
        String returnFromFrame(String outcome) {
            var callee = unitOf(stack);
            leave(stateOf(callee, status.state()), outcome);
            var popped = stack.pop();
            var result = results.pop();
            stack = popped.remaining();
            var frame = popped.frame();
            var caller = stateOf(unitOf(stack), frame.callerStateId());
            status = status.toBuilder()
                    .phase(phaseOf(unitOf(stack), caller.id(), stack, status))
                    .callStack(stack.toStore())
                    .retryCounters(clearedFor(status.retryCounters(), callee))
                    .build();
            record(PlanHistoryLine.Event.RETURNED, callee.id(), outcome);
            if (!(caller.behavior() instanceof StateBehavior.SubWorkflowCall call)) {
                throw fault(Reason.UNRESOLVED_NAME, "the calling state '%s' of the frame is no call state",
                        caller.id());
            }
            leave(caller, outcome);
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
            recordHistory();
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
                .filter(transition -> resultGuardHolds(transition, result))
                .toList();
        if (matching.size() != 1) {
            throw fault(Reason.UNDECLARED_OUTCOME, "the state '%s' has %d transitions for the outcome '%s', not one",
                    node.id(), matching.size(), outcome);
        }
        return matching.getFirst();
    }

    /** Whether a transition has no result guard, or its result guard holds over the result record. */
    private static boolean resultGuardHolds(OutcomeTransition transition, Map<String, Object> result) {
        var guard = transition.resultGuard();
        return guard == null || GuardEvaluator.holds(guard, result);
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
        private final transient @Nullable VerificationResult verification;

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
