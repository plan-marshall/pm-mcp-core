/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.scenario;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import de.planmarshall.core.service.LockTransaction;
import de.planmarshall.core.spi.AwaitedEvent;
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
import de.planmarshall.core.store.JournalScope;
import de.planmarshall.core.store.JournalScopeType;
import de.planmarshall.core.store.LockKey;
import de.planmarshall.core.store.ScopeTransaction;
import de.planmarshall.workflow.ast.UnitOrigin;
import de.planmarshall.workflow.ast.WorkflowDefinition;
import de.planmarshall.workflow.dsl.ParseResult;
import de.planmarshall.workflow.dsl.WorkflowParser;
import de.planmarshall.workflow.engine.EngineEvent;
import de.planmarshall.workflow.engine.WorkflowEngine;
import de.planmarshall.workflow.verify.EntityKind;
import de.planmarshall.workflow.verify.EntityKindRegistry;
import de.planmarshall.workflow.verify.QuestionClassRegistry;
import de.planmarshall.workflow.verify.SchemaRegistry;
import de.planmarshall.workflow.verify.WorkflowCatalog;
import de.planmarshall.workflow.verify.WorkflowValidator;

/**
 * The fixtures of the replay harness {@code pm-scenario} (PM-TEST-4), written for the scenario tests and copied from
 * no unit of the product: a fixture plan unit as DSL text, the primitives it names, a manual clock, deterministic
 * doubles of the engine ports, and the assembly of a {@link WorkflowEngine} with the store of a scope in a directory
 * the test gives.
 * <p>
 * Everything a replay writes is fixed by the script: the clock starts at {@link #T0} and moves by {@link #STEP} per
 * step, job ids and queue task ids come from a counter, and every primitive returns one fixed outcome. Two replays of
 * one script with fresh fixtures therefore write the same bytes. One instance serves one replay.
 */
final class ScenarioFixtures {

    /** The instant the manual clock starts at. */
    static final Instant T0 = Instant.parse("2026-10-13T08:00:00Z");

    /** How far the manual clock moves per step of a replay. */
    static final Duration STEP = Duration.ofSeconds(1);

    /** The project the scopes of a replay belong to. */
    static final String PROJECT = "scenario-project";

    /** The entity kind of the fixture unit's task state: no entity unit, return evaluation {@code scenario.evaluate}. */
    static final String CHANGE_KIND = "change";

    /**
     * The fixture plan unit: an {@code auto} state, a job state, a task state of the role {@code reviewer}, a question
     * state of the class {@code review}, a question state of the class {@code clarification}, and the two terminals.
     * The job is started through the job port, so its id is {@code b-0000000000000001} in every replay.
     */
    static final String PLAN_UNIT = """
            workflow scenario.plan version 1 format 1
            scope plan
            start execute.prepare

            state execute.prepare
                phase execute
                auto scenario.prepare
                    done -> execute.build
                    failed -> handed_back

            state execute.build
                phase execute
                job scenario.build
                    succeeded -> execute.review
                    failed -> handed_back
                    failed_unparsed -> handed_back
                    refused -> handed_back
                    cancelled -> handed_back
                    timed_out -> handed_back
                    killed -> handed_back
                    indeterminate -> handed_back

            state execute.review
                phase execute
                job llm change role reviewer
                    approved -> execute.ask
                    rejected -> handed_back
                    lost -> handed_back

            state execute.ask
                phase execute
                question review
                    approve -> execute.confirm
                    revise -> handed_back
                    returned -> handed_back

            state execute.confirm
                phase execute
                question clarification
                    answered -> completed
                    assumed -> completed

            state completed terminal merged success
                phase completed

            state handed_back terminal handed_back failure
                phase completed
            """;

    /**
     * The outcome a question of a class resolves with when its deadline fires: the class fallback of the fixture
     * unit's question classes. The question records and their deadlines are not built yet, so the fixture states the
     * fallback the deadline would take.
     */
    static final Map<String, String> DEADLINE_OUTCOMES = Map.of("review", "returned", "clarification", "assumed");

    /** The clock of the engine and of the transactions. */
    final ManualClock clock = new ManualClock(T0);

    /** The job port of the engine. */
    final SequenceJobStarter jobStarter = new SequenceJobStarter();

    /** The queue task port of the engine. */
    final RecordingQueueTaskCreator taskCreator = new RecordingQueueTaskCreator();

    /** The question port of the engine. */
    final ScriptedQuestionRecordReader questionReader = new ScriptedQuestionRecordReader();

    /**
     * Parses a unit; it must follow the grammar.
     *
     * @param name   the name its source locations carry
     * @param source the text of the unit
     * @return the tree of the unit
     * @throws IllegalArgumentException if the parser refuses the text, naming the diagnostic
     */
    static WorkflowDefinition parse(String name, String source) {
        return switch (WorkflowParser.parse(name, source, UnitOrigin.DEFAULT)) {
            case ParseResult.Parsed parsed -> parsed.definition();
            case ParseResult.Refused refused -> throw new IllegalArgumentException(
                    "%s was refused: %s".formatted(name, refused.diagnostic()));
        };
    }

    /** @return the fixture plan unit {@link #PLAN_UNIT} */
    static WorkflowDefinition planUnit() {
        return parse("scenario-plan.pmwf", PLAN_UNIT);
    }

    /**
     * The fixture return evaluation of a task: {@code submitted} evaluates to the success outcome {@code approved},
     * {@code infeasible} to {@code rejected}, and {@code lost} to {@code lost}. A task that asked is not evaluated:
     * the question of an asking task state arrives with the task protocol.
     *
     * @param taskOutcome the closed outcome of a task
     * @return the outcome the task state takes
     * @throws IllegalArgumentException if the task outcome is {@code asked} or not closed
     */
    static String evaluate(String taskOutcome) {
        return switch (taskOutcome) {
            case "submitted" -> EvaluateOutcome.APPROVED.wireName();
            case "infeasible" -> EvaluateOutcome.REJECTED.wireName();
            case "lost" -> "lost";
            case "asked" -> throw new IllegalArgumentException(
                    "the fixture return evaluation does not evaluate an asked task; asking task states arrive with "
                            + "task-queue-and-protocol");
            default -> throw new IllegalArgumentException("'%s' is no outcome of a task".formatted(taskOutcome));
        };
    }

    /** @return the registry of the primitives the fixture unit names */
    static PrimitiveRegistry registry() {
        return new PrimitiveRegistry(List.of(
                new Fixed<>("scenario.prepare", StepOutcome.DONE, AwaitedEvent.NONE),
                new Fixed<>("scenario.build", FateOutcome.SUCCEEDED, AwaitedEvent.JOB_SETTLED),
                new Fixed<>("scenario.evaluate", EvaluateOutcome.APPROVED, AwaitedEvent.NONE)));
    }

    /** @return the default entity kinds plus {@link #CHANGE_KIND}, which needs no entity unit */
    static EntityKindRegistry entityKinds() {
        return EntityKindRegistry.defaults()
                .with(new EntityKind(CHANGE_KIND, Optional.empty(), "scenario.evaluate"));
    }

    /**
     * Assembles the engine of a unit over the fixture primitives and this instance's ports; the engine verifies the
     * unit first.
     *
     * @param unit the top-level unit
     * @return the engine
     */
    WorkflowEngine engine(WorkflowDefinition unit) {
        var registry = registry();
        var validator = new WorkflowValidator(registry, List.of(), entityKinds(), QuestionClassRegistry.defaults(),
                SchemaRegistry.defaults(), WorkflowCatalog.of());
        var ports = new WorkflowEngine.Ports(jobStarter, taskCreator, questionReader, clock);
        return new WorkflowEngine(unit, WorkflowCatalog.of(), validator, registry,
                (primitive, status) -> new NoParams(), ports);
    }

    /**
     * Creates the journal scope of a scope in a directory, with the directories its journal and status store need.
     *
     * @param directory the directory the stores of the replay live in
     * @param type      the scope type
     * @param scopeId   the scope id, {@code null} for the workspace
     * @return the journal scope
     * @throws IllegalArgumentException if the type is the lesson store
     * @throws UncheckedIOException     if a directory cannot be created
     */
    static JournalScope scope(Path directory, JournalScopeType type, String scopeId) {
        var scope = switch (type) {
            case PLAN -> JournalScope.plan(directory, PROJECT, scopeId);
            case EPIC -> JournalScope.epic(directory, PROJECT, scopeId);
            case WORKSPACE -> JournalScope.workspace(directory, PROJECT);
            case LESSONS -> throw new IllegalArgumentException("a scenario does not start the lesson store");
        };
        try {
            Files.createDirectories(scope.journalFile().getParent());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return scope;
    }

    /**
     * Opens a transaction of a scope at the clock's current instant, under a lock that holds every key: the replay is
     * the only writer of its scope.
     *
     * @param scope the scope
     * @return the open transaction
     */
    ScopeTransaction transaction(JournalScope scope) {
        return ScopeTransaction.open(new HoldingEverything(), scope, event -> {
            // a status write owes no audit record
        }, Clock.fixed(clock.now(), ZoneOffset.UTC));
    }

    /** The empty parameter record of every fixture primitive. */
    record NoParams() {
    }

    /** The outcomes of {@code scenario.prepare}. */
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

    /** The fates of a job record as {@link EngineEvent.JobSettled} states them: the outcomes of {@code scenario.build}. */
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

    /** The outcomes of the return evaluation {@code scenario.evaluate} of {@link #CHANGE_KIND}. */
    enum EvaluateOutcome implements PrimitiveOutcome {
        APPROVED("approved", OutcomeClass.SUCCESS), REJECTED("rejected", OutcomeClass.FAILURE);

        private final String wireName;
        private final OutcomeClass outcomeClass;

        EvaluateOutcome(String wireName, OutcomeClass outcomeClass) {
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

    /**
     * A primitive that returns one fixed outcome on every call.
     *
     * @param <R> the outcome enum
     */
    static final class Fixed<R extends Enum<R> & PrimitiveOutcome> implements Primitive<NoParams, R> {

        private final String id;
        private final R outcome;
        private final AwaitedEvent awaitedEvent;

        Fixed(String id, R outcome, AwaitedEvent awaitedEvent) {
            this.id = id;
            this.outcome = outcome;
            this.awaitedEvent = awaitedEvent;
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
            return outcome.getDeclaringClass();
        }

        @Override
        public PrimitiveResult<R> execute(WorkflowExecutionContext context, NoParams parameters) {
            return new PrimitiveResult<>(outcome, Map.of(), Optional.empty());
        }

        @Override
        public AwaitedEvent awaitedEvent() {
            return awaitedEvent;
        }
    }

    /** A clock the replay moves by {@link ScenarioFixtures#STEP} per step. */
    static final class ManualClock implements WorkflowClock {

        private Instant now;

        ManualClock(Instant start) {
            now = start;
        }

        /** Moves the clock by one {@link ScenarioFixtures#STEP}. */
        void tick() {
            now = now.plus(STEP);
        }

        @Override
        public Instant now() {
            return now;
        }
    }

    /** A job starter that starts the build jobs {@code b-0000000000000001}, {@code b-0000000000000002}, … in order. */
    static final class SequenceJobStarter implements JobStarter {

        final List<StartRequest> requests = new ArrayList<>();

        @Override
        public StartResult start(StartRequest request) {
            requests.add(request);
            return new StartResult.Started("b-%016x".formatted(requests.size()));
        }
    }

    /** A task creator that creates the queue tasks {@code qt-0000000000000001}, … in order, and records them. */
    static final class RecordingQueueTaskCreator implements QueueTaskCreator {

        final List<CreateRequest> requests = new ArrayList<>();

        @Override
        public Created create(CreateRequest request) {
            requests.add(request);
            return new Created("qt-%016x".formatted(requests.size()));
        }
    }

    /** A question reader that reports the resolution the replay set for a question, and every other one open. */
    static final class ScriptedQuestionRecordReader implements QuestionRecordReader {

        private final Map<String, String> resolutions = new HashMap<>();

        /**
         * @param questionId the question
         * @param outcome    the outcome it is resolved with from now on
         */
        void resolve(String questionId, String outcome) {
            resolutions.put(questionId, outcome);
        }

        @Override
        public QuestionRead read(ReadRequest request) {
            var outcome = resolutions.get(request.questionId());
            return outcome == null ? new QuestionRead.Open() : new QuestionRead.Resolved(outcome);
        }
    }

    /** A lock transaction that holds every key: a replay is the only writer of its scope. */
    private static final class HoldingEverything implements LockTransaction {

        @Override
        public void acquire(LockKey key) {
            // every key is held already
        }

        @Override
        public boolean holds(LockKey key) {
            return true;
        }

        @Override
        public void close() {
            // nothing to release
        }
    }
}
