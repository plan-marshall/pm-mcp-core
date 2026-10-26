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

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import de.planmarshall.core.spi.AwaitedEvent;
import de.planmarshall.core.spi.CycleMeasure;
import de.planmarshall.core.spi.JobStarter;
import de.planmarshall.core.spi.OutcomeClass;
import de.planmarshall.core.spi.PendingEntryReader;
import de.planmarshall.core.spi.Primitive;
import de.planmarshall.core.spi.PrimitiveOutcome;
import de.planmarshall.core.spi.PrimitiveRegistry;
import de.planmarshall.core.spi.PrimitiveResult;
import de.planmarshall.core.spi.QuestionRecordReader;
import de.planmarshall.core.spi.QueueTaskCreator;
import de.planmarshall.core.spi.WorkflowClock;
import de.planmarshall.core.spi.WorkflowExecutionContext;
import de.planmarshall.core.store.EntryId;
import de.planmarshall.core.store.JournalScopeType;
import de.planmarshall.core.store.RetryCounters;
import de.planmarshall.core.store.WorkflowStatus;
import de.planmarshall.workflow.ast.UnitOrigin;
import de.planmarshall.workflow.ast.WorkflowDefinition;
import de.planmarshall.workflow.dsl.ParseResult;
import de.planmarshall.workflow.dsl.WorkflowParser;
import de.planmarshall.workflow.verify.EntityKind;
import de.planmarshall.workflow.verify.EntityKindRegistry;
import de.planmarshall.workflow.verify.QuestionClassRegistry;
import de.planmarshall.workflow.verify.SchemaRegistry;
import de.planmarshall.workflow.verify.WorkflowCatalog;
import de.planmarshall.workflow.verify.WorkflowValidator;

/**
 * The fixtures of the engine tests: scripted primitives, a manual clock, recording doubles of the engine ports, and the
 * assembly of an engine over units parsed from DSL text written in the tests. No fixture is copied from a unit of the
 * product.
 * <p>
 * One instance serves one test: its primitives keep the outcomes a test scripted and count their calls.
 */
final class EngineFixtures {

    /** The instant the manual clock starts at. */
    static final Instant T0 = Instant.parse("2026-10-12T10:00:00Z");

    /** The plan every fixture status belongs to. */
    static final String PLAN_ID = "engine-plan";

    /** The phase of the status store the {@code execute} states of the fixtures carry. */
    static final String EXECUTE = "phase-5-execute";

    /**
     * A sub-workflow with a result record: {@code test.mark} returns its facts; {@code done} ends it with
     * {@code done}, {@code failed} with the server step-out.
     */
    static final String SUB_COMMIT = """
            ---
            name: sub.commit
            description: A unit written for the tests of pm-workflow.
            type: subworkflow
            icon: workflow.default
            entry: main
            exit: done
            modification_date: 2026-01-15
            version: 0.1.0
            author: plan-marshall
            ---
            workflow sub.commit version 1 format 1
            scope subworkflow
            result schema[commit-result]
            entry main -> sub.work

            state sub.work
                phase inherit
                auto test.mark
                    done -> sub.done
                    failed -> sub.stepped-out

            state sub.done terminal done success
                phase inherit

            state sub.stepped-out terminal stepped_out stepped_out
                phase inherit
            """;

    /** The transitions of every fate of {@code test.fate} but {@code succeeded} and {@code killed}. */
    static final String OTHER_FATES = """
                    failed -> handed_back
                    failed_unparsed -> handed_back
                    refused -> handed_back
                    cancelled -> handed_back
                    timed_out -> handed_back
                    indeterminate -> handed_back
            """;

    /**
     * The states of a plan that rests in every kind of resting state in turn: the job a job-starting outcome reports,
     * a task state of the entity kind {@code change}, a question state of the class {@code clarification}, and a
     * composite job state that waits for time.
     */
    static final String WAIT_STATES = """
            state execute.enqueue
                phase execute
                auto test.enqueue
                    started -> execute.run
                    skipped -> execute.review

            state execute.run
                phase execute
                job test.fate
                    succeeded -> execute.review
                    killed -> handed_back
            %s
            state execute.review
                phase execute
                job llm change role reviewer
                    approved -> execute.ask
                    lost -> handed_back

            state execute.ask
                phase execute
                question clarification
                    answered -> execute.backoff

            state execute.backoff
                phase execute
                composite_job test.backoff
                    succeeded -> completed
                    failed -> handed_back
                    cancelled -> handed_back
            """.formatted(OTHER_FATES);

    /** The two plan terminals the fixture plan units end in. */
    static final String PLAN_TERMINALS = """

            state completed terminal merged success
                phase completed

            state handed_back terminal handed_back failure
                phase completed
            """;

    /**
     * The fallback state of a fixture plan unit that calls a sub-workflow. Rule 6 admits only the phase's fallback
     * state, {@code plan.return} or {@code finalize.hand-back}, as the target of a plan's {@code stepped_out} route, so
     * every calling fixture plan routes its step-out here, and the fallback hands the plan back: whatever
     * {@code test.step} returns, it enters {@code handed_back}, the terminal the step-out entered before the rule was
     * enforced. It is not part of {@link #PLAN_TERMINALS}, because a plan that calls nothing would not reach it.
     */
    static final String PLAN_RETURN = """

            state plan.return
                phase execute
                auto test.step
                    done -> handed_back
                    failed -> handed_back
            """;

    /** The entity kind of the fixtures' task states: no entity unit, return evaluation {@code test.evaluate-change}. */
    static final String CHANGE_KIND = "change";

    /**
     * The entity kind of an architecture enrichment task, one task per module: no entity unit, return evaluation
     * {@code test.evaluate-change}. It is not among {@link EntityKindRegistry#defaults()} (ADR 009), so the fixtures
     * register it as they register {@link #CHANGE_KIND}.
     */
    static final String ENRICH_KIND = "enrich-module";

    /** The phase of the status store of the workspace scope. */
    static final String WORKSPACE_PHASE = "workspace";

    /** The state of the workspace fixture unit that hosts no task. */
    static final String WORKSPACE_IDLE = "workspace.idle";

    /** The task state of the workspace fixture unit that fans the {@link #ENRICH_KIND} tasks out, one per module. */
    static final String WORKSPACE_ENRICH = "workspace.enrich";

    /** The task state of the workspace fixture unit that hosts the one lesson review task. */
    static final String WORKSPACE_REVIEW = "workspace.review";

    /**
     * A workspace unit whose task states host the entities a workspace scope addresses besides its bootstraps: the
     * fan-out of {@link #ENRICH_KIND} tasks, keyed in {@code active_tasks} by their entity {@code enrich-<module_id>},
     * and the lesson review ({@code job llm lessons-review role lessons}, the default kind {@code lessons-review}),
     * which names no scope entity and is addressed by its {@code queue_task_id}. Its states inherit the phase of the
     * workspace scope, as the units the workspace scope hosts do.
     */
    static final String WORKSPACE_UNIT = """
            ---
            name: workspace
            description: A unit written for the tests of pm-workflow.
            type: system
            icon: workflow.default
            modification_date: 2026-01-15
            version: 0.1.0
            author: plan-marshall
            ---
            workflow workspace version 1 format 1
            scope workspace
            start workspace.idle

            state workspace.idle
                phase inherit
                auto test.step
                    done -> workspace.enrich
                    failed -> workspace.review

            state workspace.enrich
                phase inherit
                job llm enrich-module role architect each test.list-modules
                    approved -> workspace.idle
                    lost -> workspace.idle

            state workspace.review
                phase inherit
                job llm lessons-review role lessons
                    reviewed -> workspace.idle
                    infeasible -> workspace.idle
                    lost -> workspace.idle
            """;

    /** An {@code auto} primitive: {@code done}, {@code failed}. */
    final Scripted<StepOutcome> step = new Scripted<>("test.step", StepOutcome.class, AwaitedEvent.NONE);

    /** A second {@code auto} primitive, which contributes the facts of a result record: {@code done}, {@code failed}. */
    final Scripted<StepOutcome> mark = new Scripted<>("test.mark", StepOutcome.class, AwaitedEvent.NONE);

    /** A link primitive of a session-driven state: {@code done}, {@code failed}. */
    final Scripted<StepOutcome> proceed = new Scripted<>("test.proceed", StepOutcome.class, AwaitedEvent.NONE);

    /** A {@code job} primitive: {@code succeeded}, {@code failed}, {@code cancelled}. */
    final Scripted<JobOutcome> build = new Scripted<>("test.build", JobOutcome.class, AwaitedEvent.JOB_SETTLED);

    /** A {@code composite_job} primitive that waits for time: {@code succeeded}, {@code failed}, {@code cancelled}. */
    final Scripted<JobOutcome> backoff = new Scripted<>("test.backoff", JobOutcome.class, AwaitedEvent.TIME);

    /** An {@code auto} primitive with an attempt cap: {@code again}, and {@code spent} once the cap is reached. */
    final Scripted<CapOutcome> cap = new Scripted<>("test.cap", CapOutcome.class, AwaitedEvent.NONE);

    /** The {@code auto} primitive of {@code epic.next-work}: {@code abort_landed}. */
    final Scripted<LandedOutcome> nextWork = new Scripted<>("test.next-work", LandedOutcome.class, AwaitedEvent.NONE);

    /** A {@code job} primitive whose outcomes are every fate of a job record. */
    final Scripted<FateOutcome> job = new Scripted<>("test.fate", FateOutcome.class, AwaitedEvent.JOB_SETTLED);

    /** An {@code auto} primitive that enqueues a job: {@code started} reports it, {@code skipped} enqueues none. */
    final Scripted<EnqueueOutcome> enqueue = new Scripted<>("test.enqueue", EnqueueOutcome.class, AwaitedEvent.NONE);

    /** The return-evaluation primitive of the entity kind {@code change}: {@code approved}. */
    final Scripted<ApprovedOutcome> evaluateChange = new Scripted<>("test.evaluate-change", ApprovedOutcome.class,
            AwaitedEvent.NONE);

    /** The clock of the engine. */
    final ManualClock clock = new ManualClock(T0);

    /** The job port of the engine. */
    final RecordingJobStarter jobStarter = new RecordingJobStarter();

    /** The queue task port of the engine. */
    final RecordingQueueTaskCreator taskCreator = new RecordingQueueTaskCreator();

    /** The question port of the engine. */
    final FixedQuestionRecordReader questionReader = new FixedQuestionRecordReader();

    /** The pending entry port of the address resolver. */
    final FixedPendingEntryReader entryReader = new FixedPendingEntryReader();

    /** @return the ports of the engine: the recording doubles and the manual clock of this fixture */
    WorkflowEngine.Ports ports() {
        return new WorkflowEngine.Ports(jobStarter, taskCreator, questionReader, clock);
    }

    /** @return the address resolver over this fixture's pending entry port and {@link #entityKinds()} */
    EntityAddressResolver resolver() {
        return new EntityAddressResolver(entryReader, entityKinds());
    }

    /** @return the registry of every primitive of this fixture */
    PrimitiveRegistry registry() {
        return new PrimitiveRegistry(List.of(step, mark, proceed, build, backoff, cap, nextWork, job, enqueue,
                evaluateChange));
    }

    /**
     * Parses a unit written in a test; it must follow the grammar.
     *
     * @param name   the name its source locations carry
     * @param source the text of the unit
     * @return the tree of the unit
     */
    static WorkflowDefinition parse(String name, String source) {
        var result = WorkflowParser.parse(name, source, UnitOrigin.DEFAULT);
        return assertInstanceOf(ParseResult.Parsed.class, result, () -> name + " was refused: " + result)
                .definition();
    }

    /**
     * Parses a plan unit with the start state given, the states given and {@link #PLAN_TERMINALS}.
     *
     * @param start  the start state
     * @param states the states before the terminals
     * @return the tree of the unit
     */
    static WorkflowDefinition plan(String start, String states) {
        return parse("plan.pmwf", """
                ---
                name: plan
                description: A unit written for the tests of pm-workflow.
                type: workflow
                icon: workflow.default
                modification_date: 2026-01-15
                version: 0.1.0
                author: plan-marshall
                ---
                workflow plan version 1 format 1
                scope plan
                start %s

                %s%s""".formatted(start, states, PLAN_TERMINALS));
    }

    /**
     * Parses a plan unit that calls a sub-workflow: {@link #plan(String, String)} with the fallback state
     * {@link #PLAN_RETURN} its {@code stepped_out} routes enter.
     *
     * @param start  the start state
     * @param states the states before the fallback and the terminals
     * @return the tree of the unit
     */
    static WorkflowDefinition callingPlan(String start, String states) {
        return plan(start, states + PLAN_RETURN);
    }

    /** The entry the fixture sub-workflows bind, which a frame of {@link #frame(String, String, String)} records. */
    static final String MAIN_ENTRY = "main";

    /**
     * @param callerWorkflowId the calling unit
     * @param callerStateId    the calling state
     * @param subworkflowId    the called sub-workflow
     * @return a frame of the store through the entry {@link #MAIN_ENTRY} without bound arguments, pushed at
     *         {@link #T0} in {@link #EXECUTE}
     */
    static WorkflowStatus.CallFrame frame(String callerWorkflowId, String callerStateId, String subworkflowId) {
        return frame(callerWorkflowId, callerStateId, subworkflowId, MAIN_ENTRY);
    }

    /**
     * @param callerWorkflowId the calling unit
     * @param callerStateId    the calling state
     * @param subworkflowId    the called sub-workflow
     * @param entry            the entry of the sub-workflow the call went through
     * @return a frame of the store without bound arguments, pushed at {@link #T0} in {@link #EXECUTE}
     */
    static WorkflowStatus.CallFrame frame(String callerWorkflowId, String callerStateId, String subworkflowId,
            String entry) {
        return new WorkflowStatus.CallFrame(callerWorkflowId, callerStateId, EXECUTE, subworkflowId, entry, Map.of(),
                T0);
    }

    /**
     * @param callees the sub-workflows the units may call
     * @return the verifier over this fixture's primitives and the callees
     */
    WorkflowValidator validator(WorkflowDefinition... callees) {
        return new WorkflowValidator(registry(), List.of(), entityKinds(), QuestionClassRegistry.defaults(),
                SchemaRegistry.defaults(), WorkflowCatalog.of(callees));
    }

    /**
     * The default entity kinds carry no test identifier, so the fixtures register their own kinds, as the verifier
     * tests do. {@link #CHANGE_KIND} and {@link #ENRICH_KIND} have no entity unit: no fixture state asks, and the
     * engine runs no entity unit, so the catalog of the fixtures need not hold one.
     *
     * @return the default entity kinds plus {@link #CHANGE_KIND} and {@link #ENRICH_KIND}
     */
    static EntityKindRegistry entityKinds() {
        return EntityKindRegistry.defaults()
                .with(new EntityKind(CHANGE_KIND, Optional.empty(), "test.evaluate-change"))
                .with(new EntityKind(ENRICH_KIND, Optional.empty(), "test.evaluate-change"));
    }

    /**
     * Assembles an engine whose binder binds the empty parameter record of every fixture primitive.
     *
     * @param unit    the top-level unit
     * @param callees the sub-workflows the units may call
     * @return the engine
     */
    WorkflowEngine engine(WorkflowDefinition unit, WorkflowDefinition... callees) {
        return new WorkflowEngine(unit, WorkflowCatalog.of(callees), validator(callees), registry(),
                (primitive, status) -> new NoParams(), ports());
    }

    /**
     * A status of the fixture plan at a state, with no frame.
     *
     * @param phase the phase of the status store
     * @param state the current state
     * @return the status
     */
    static WorkflowStatus planStatus(String phase, String state) {
        return WorkflowStatus.builder()
                .scopeType(JournalScopeType.PLAN)
                .scopeId(PLAN_ID)
                .phase(phase)
                .state(state)
                .callStack(List.of())
                .policyCounters(Map.of())
                .waiverRequests(List.of())
                .retryCounters(RetryCounters.empty())
                .activeJobs(Map.of())
                .activeTasks(Map.of())
                .updatedAt(T0)
                .retained(Map.of())
                .build();
    }

    /** @return the tree of {@link #WORKSPACE_UNIT} */
    static WorkflowDefinition workspaceUnit() {
        return parse("workspace.pmwf", WORKSPACE_UNIT);
    }

    /**
     * A status of the workspace scope at a state of {@link #WORKSPACE_UNIT}, with no frame.
     *
     * @param state the current state
     * @param tasks the active tasks of the state by the key {@code active_tasks} holds them under
     * @return the status
     */
    static WorkflowStatus workspaceStatus(String state, Map<String, WorkflowStatus.TaskEntry> tasks) {
        return planStatus(WORKSPACE_PHASE, state).toBuilder()
                .scopeType(JournalScopeType.WORKSPACE)
                .scopeId(null)
                .activeTasks(tasks)
                .build();
    }

    /**
     * @param queueTaskId the queue task
     * @param role        the role of the task
     * @return the entry of a queued task, not yet leased
     */
    static WorkflowStatus.TaskEntry queuedTask(String queueTaskId, String role) {
        return new WorkflowStatus.TaskEntry(queueTaskId, role, "queued", null, null);
    }

    /** The empty parameter record of every fixture primitive. */
    record NoParams() {
    }

    /** The outcomes of {@code test.step}, {@code test.mark} and {@code test.proceed}. */
    enum StepOutcome implements PrimitiveOutcome {
        DONE("done", OutcomeClass.SUCCESS), FAILED("failed", OutcomeClass.FAILURE);

        private final String wireName;
        private final OutcomeClass outcomeClass;

        StepOutcome(String wireName, OutcomeClass outcomeClass) {
            this.wireName = wireName;
            this.outcomeClass = outcomeClass;
        }

        @Override
        public String wireName() {
            return wireName;
        }

        @Override
        public OutcomeClass outcomeClass() {
            return outcomeClass;
        }
    }

    /** The outcomes of a job, {@code cancelled} included as on every job primitive. */
    enum JobOutcome implements PrimitiveOutcome {
        SUCCEEDED("succeeded", OutcomeClass.SUCCESS), FAILED("failed", OutcomeClass.FAILURE),
        CANCELLED("cancelled", OutcomeClass.INDETERMINATE);

        private final String wireName;
        private final OutcomeClass outcomeClass;

        JobOutcome(String wireName, OutcomeClass outcomeClass) {
            this.wireName = wireName;
            this.outcomeClass = outcomeClass;
        }

        @Override
        public String wireName() {
            return wireName;
        }

        @Override
        public OutcomeClass outcomeClass() {
            return outcomeClass;
        }
    }

    /** The outcomes of {@code test.cap}: {@code spent} is its exhaustion outcome. */
    enum CapOutcome implements PrimitiveOutcome {
        AGAIN("again", OutcomeClass.SUCCESS), SPENT("spent", OutcomeClass.FAILURE);

        private final String wireName;
        private final OutcomeClass outcomeClass;

        CapOutcome(String wireName, OutcomeClass outcomeClass) {
            this.wireName = wireName;
            this.outcomeClass = outcomeClass;
        }

        @Override
        public String wireName() {
            return wireName;
        }

        @Override
        public OutcomeClass outcomeClass() {
            return outcomeClass;
        }
    }

    /** The fates of a job record, the outcomes of {@code test.fate}. */
    enum FateOutcome implements PrimitiveOutcome {
        SUCCEEDED("succeeded", OutcomeClass.SUCCESS), FAILED("failed", OutcomeClass.FAILURE),
        FAILED_UNPARSED("failed_unparsed", OutcomeClass.FAILURE), REFUSED("refused", OutcomeClass.FAILURE),
        CANCELLED("cancelled", OutcomeClass.INDETERMINATE), TIMED_OUT("timed_out", OutcomeClass.FAILURE),
        KILLED("killed", OutcomeClass.INDETERMINATE), INDETERMINATE("indeterminate", OutcomeClass.INDETERMINATE);

        private final String wireName;
        private final OutcomeClass outcomeClass;

        FateOutcome(String wireName, OutcomeClass outcomeClass) {
            this.wireName = wireName;
            this.outcomeClass = outcomeClass;
        }

        @Override
        public String wireName() {
            return wireName;
        }

        @Override
        public OutcomeClass outcomeClass() {
            return outcomeClass;
        }
    }

    /** The outcomes of {@code test.enqueue}: {@code started} reports the job it started. */
    enum EnqueueOutcome implements PrimitiveOutcome {
        STARTED("started", OutcomeClass.SUCCESS), SKIPPED("skipped", OutcomeClass.SKIP);

        private final String wireName;
        private final OutcomeClass outcomeClass;

        EnqueueOutcome(String wireName, OutcomeClass outcomeClass) {
            this.wireName = wireName;
            this.outcomeClass = outcomeClass;
        }

        @Override
        public String wireName() {
            return wireName;
        }

        @Override
        public OutcomeClass outcomeClass() {
            return outcomeClass;
        }

        @Override
        public boolean startsJob() {
            return this == STARTED;
        }
    }

    /** The one outcome of the return evaluation of {@code change}. */
    enum ApprovedOutcome implements PrimitiveOutcome {
        APPROVED;

        @Override
        public String wireName() {
            return "approved";
        }

        @Override
        public OutcomeClass outcomeClass() {
            return OutcomeClass.SUCCESS;
        }
    }

    /** The one outcome of {@code test.next-work}. */
    enum LandedOutcome implements PrimitiveOutcome {
        ABORT_LANDED;

        @Override
        public String wireName() {
            return "abort_landed";
        }

        @Override
        public OutcomeClass outcomeClass() {
            return OutcomeClass.SUCCESS;
        }
    }

    /**
     * A primitive that returns the outcomes a test scripted, in order, and then its first outcome; it counts its calls.
     * {@code test.cap} declares an attempt cap with the exhaustion outcome {@code spent}.
     *
     * @param <R> the outcome enum
     */
    static final class Scripted<R extends Enum<R> & PrimitiveOutcome> implements Primitive<NoParams, R> {

        private final String id;
        private final Class<R> outcomeType;
        private final AwaitedEvent awaitedEvent;
        private final Deque<PrimitiveResult<R>> script = new ArrayDeque<>();
        private final List<Optional<String>> scopes = new ArrayList<>();

        Scripted(String id, Class<R> outcomeType, AwaitedEvent awaitedEvent) {
            this.id = id;
            this.outcomeType = outcomeType;
            this.awaitedEvent = awaitedEvent;
        }

        /**
         * @param outcome the outcome of the next call
         * @return this primitive
         */
        Scripted<R> then(R outcome) {
            return then(outcome, Map.of());
        }

        /**
         * @param outcome the outcome of the next call
         * @param facts   the facts the next call returns
         * @return this primitive
         */
        Scripted<R> then(R outcome, Map<String, Object> facts) {
            script.add(new PrimitiveResult<>(outcome, facts, Optional.empty()));
            return this;
        }

        /**
         * @param outcome the outcome of the next call, which reports the job it started
         * @param jobId   the job the next call reports
         * @return this primitive
         */
        Scripted<R> thenSpawning(R outcome, String jobId) {
            script.add(new PrimitiveResult<>(outcome, Map.of(), Optional.of(jobId)));
            return this;
        }

        @Override
        public boolean enqueuesJob() {
            return EnqueueOutcome.class.equals(outcomeType);
        }

        /** @return how often the primitive ran */
        int calls() {
            return scopes.size();
        }

        /** @return the scope of every call, in order */
        List<Optional<String>> scopes() {
            return scopes;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public Class<NoParams> parameterType() {
            return NoParams.class;
        }

        @Override
        public Class<R> outcomeType() {
            return outcomeType;
        }

        @Override
        public PrimitiveResult<R> execute(WorkflowExecutionContext context, NoParams parameters) {
            scopes.add(context.scopeId());
            var next = script.poll();
            return next != null ? next
                    : new PrimitiveResult<>(outcomeType.getEnumConstants()[0], Map.of(), Optional.empty());
        }

        @Override
        public AwaitedEvent awaitedEvent() {
            return awaitedEvent;
        }

        @Override
        public CycleMeasure cycleMeasure() {
            return CapOutcome.class.equals(outcomeType) ? CycleMeasure.ATTEMPT_CAP : CycleMeasure.NONE;
        }

        @Override
        public Optional<String> exhaustionOutcome() {
            return CapOutcome.class.equals(outcomeType) ? Optional.of(CapOutcome.SPENT.wireName())
                    : Optional.empty();
        }
    }

    /** A clock a test moves by hand. */
    static final class ManualClock implements WorkflowClock {

        private Instant now;

        ManualClock(Instant start) {
            now = start;
        }

        /**
         * @param step how far to move the clock
         * @return the new instant
         */
        Instant tick(Duration step) {
            now = now.plus(step);
            return now;
        }

        @Override
        public Instant now() {
            return now;
        }
    }

    /** A job starter that starts a numbered build job for every request, or refuses it, and records the requests. */
    static final class RecordingJobStarter implements JobStarter {

        final List<StartRequest> requests = new ArrayList<>();

        /** Whether the next starts are refused. */
        boolean refusing;

        @Override
        public StartResult start(StartRequest request) {
            requests.add(request);
            return refusing ? new StartResult.Refused("no build slot")
                    : new StartResult.Started("b-%016x".formatted(requests.size()));
        }
    }

    /** A task creator that creates a numbered task for every request and records the requests. */
    static final class RecordingQueueTaskCreator implements QueueTaskCreator {

        final List<CreateRequest> requests = new ArrayList<>();

        @Override
        public Created create(CreateRequest request) {
            requests.add(request);
            return new Created("qt-%016x".formatted(requests.size()));
        }
    }

    /** A question reader that answers every read with the read a test set. */
    static final class FixedQuestionRecordReader implements QuestionRecordReader {

        QuestionRead answer = new QuestionRead.Open();

        @Override
        public QuestionRead read(ReadRequest request) {
            return answer;
        }
    }

    /**
     * A pending entry reader that answers each {@code entry_id} with the read a test set for it, and every other one
     * {@link PendingEntryReader.EntryRead.Deleted}, as a store without that record does; it records every read.
     */
    static final class FixedPendingEntryReader implements PendingEntryReader {

        private final Map<EntryId, EntryRead> answers = new HashMap<>();
        private final List<EntryId> reads = new ArrayList<>();

        /**
         * @param entryId the entry, in the form of {@link EntryId}
         * @param read    the read of every later request for it
         * @return this reader
         */
        FixedPendingEntryReader answer(String entryId, EntryRead read) {
            answers.put(new EntryId(entryId), read);
            return this;
        }

        /** @return the typed {@code entry_id} of every read, in order */
        List<EntryId> reads() {
            return reads;
        }

        @Override
        public EntryRead read(ReadRequest request) {
            reads.add(request.entryId());
            return answers.getOrDefault(request.entryId(), new EntryRead.Deleted());
        }
    }
}
