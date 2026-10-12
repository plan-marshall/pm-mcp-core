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
import java.util.List;
import java.util.Map;
import java.util.Optional;

import de.planmarshall.core.spi.AwaitedEvent;
import de.planmarshall.core.spi.CycleMeasure;
import de.planmarshall.core.spi.JobStarter;
import de.planmarshall.core.spi.OutcomeClass;
import de.planmarshall.core.spi.Primitive;
import de.planmarshall.core.spi.PrimitiveOutcome;
import de.planmarshall.core.spi.PrimitiveRegistry;
import de.planmarshall.core.spi.PrimitiveResult;
import de.planmarshall.core.spi.QuestionRecordReader;
import de.planmarshall.core.spi.QueueTaskCreator;
import de.planmarshall.core.spi.WorkflowClock;
import de.planmarshall.core.spi.WorkflowExecutionContext;
import de.planmarshall.core.store.JournalScopeType;
import de.planmarshall.core.store.RetryCounters;
import de.planmarshall.core.store.WorkflowStatus;
import de.planmarshall.workflow.ast.UnitOrigin;
import de.planmarshall.workflow.ast.WorkflowDefinition;
import de.planmarshall.workflow.dsl.ParseResult;
import de.planmarshall.workflow.dsl.WorkflowParser;
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
            workflow sub.commit version 1 format 1
            scope subworkflow
            result schema[commit-result]
            start sub.work

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

    /** The two plan terminals the fixture plan units end in. */
    static final String PLAN_TERMINALS = """

            state completed terminal merged success
                phase completed

            state handed_back terminal handed_back failure
                phase completed
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

    /** The clock of the engine. */
    final ManualClock clock = new ManualClock(T0);

    /** @return the registry of every primitive of this fixture */
    PrimitiveRegistry registry() {
        return new PrimitiveRegistry(List.of(step, mark, proceed, build, backoff, cap, nextWork));
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
                workflow plan version 1 format 1
                scope plan
                start %s

                %s%s""".formatted(start, states, PLAN_TERMINALS));
    }

    /**
     * @param callerWorkflowId the calling unit
     * @param callerStateId    the calling state
     * @param subworkflowId    the called sub-workflow
     * @return a frame of the store without bound arguments, pushed at {@link #T0} in {@link #EXECUTE}
     */
    static WorkflowStatus.CallFrame frame(String callerWorkflowId, String callerStateId, String subworkflowId) {
        return new WorkflowStatus.CallFrame(callerWorkflowId, callerStateId, EXECUTE, subworkflowId, Map.of(), T0);
    }

    /**
     * @param callees the sub-workflows the units may call
     * @return the verifier over this fixture's primitives and the callees
     */
    WorkflowValidator validator(WorkflowDefinition... callees) {
        return new WorkflowValidator(registry(), List.of(), EntityKindRegistry.defaults(),
                QuestionClassRegistry.defaults(), SchemaRegistry.defaults(), WorkflowCatalog.of(callees));
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
                (primitive, status) -> new NoParams(), clock);
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
                .retryCounters(RetryCounters.empty())
                .activeJobs(Map.of())
                .activeTasks(Map.of())
                .updatedAt(T0)
                .retained(Map.of())
                .build();
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

    /** A job starter that starts a fixed job for every request and records the requests. */
    static final class RecordingJobStarter implements JobStarter {

        final List<StartRequest> requests = new ArrayList<>();

        @Override
        public StartResult start(StartRequest request) {
            requests.add(request);
            return new StartResult.Started("b-%016x".formatted(requests.size()));
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
}
