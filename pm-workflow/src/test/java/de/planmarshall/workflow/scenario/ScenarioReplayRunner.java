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
import java.io.Serial;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import de.planmarshall.core.spi.AwaitedEvent;
import de.planmarshall.core.store.JournalScope;
import de.planmarshall.core.store.WorkflowStatus;
import de.planmarshall.core.store.WorkflowStatusStore;
import de.planmarshall.workflow.ast.StateBehavior;
import de.planmarshall.workflow.ast.WorkflowDefinition;
import de.planmarshall.workflow.engine.EngineEvent;
import de.planmarshall.workflow.engine.EngineStep;
import de.planmarshall.workflow.engine.WorkflowEngine;

/**
 * The runner of the replay harness {@code pm-scenario} (PM-TEST-4): it replays a {@link ScenarioScript} in process
 * against the real {@link WorkflowEngine}, from an empty status store in a directory the test gives, without a live
 * model.
 * <p>
 * Each step of the script is one directive:
 * <ul>
 * <li>{@code start} starts the scope ({@link WorkflowEngine#start});</li>
 * <li>{@code complete_job} delivers {@link EngineEvent.JobSettled}, {@code answer_question} the answer's
 * {@link EngineEvent.QuestionResolved}, and {@code expire_question} the resolution with the deadline outcome of the
 * question's class ({@link ScenarioFixtures#DEADLINE_OUTCOMES});</li>
 * <li>{@code assert_state} compares the state of the stored status, {@code assert_outcome} the outcome the run ended
 * with, and {@code assert_task_outcome} the outcome of the last task the worker of a role settled.</li>
 * </ul>
 * A task state the scope rests in is settled by the {@link ScriptedFixtureWorker} of its role before the next step
 * that is not an {@code assert_state}, so a script can assert the task state first and then what the task ended with.
 * Every new status is written through a {@link de.planmarshall.core.store.ScopeTransaction} to the store, and the
 * runner keeps the bytes of {@code mcp/status.json} after every step ({@link #snapshots()}), also when the replay
 * fails, so two replays can be compared step by step.
 * <p>
 * Every deviation is a {@link ScenarioFailure} naming the step's line: a wrong expectation (with the expected and the
 * actual value), an event the resting state does not wait for, a task without a declared worker outcome, and a declared
 * outcome no task took. Nothing is skipped silently. One runner replays one script once.
 */
final class ScenarioReplayRunner {

    private final ScenarioFixtures fixtures = new ScenarioFixtures();
    private final WorkflowEngine engine;
    private final Path directory;
    private final List<Snapshot> snapshots = new ArrayList<>();
    private final Map<String, ScriptedFixtureWorker> workers = new LinkedHashMap<>();
    private JournalScope scope;
    private WorkflowStatusStore store;
    private WorkflowStatus status;
    private EngineStep.Ended ended;
    private boolean replayed;

    /**
     * @param unit      the plan unit the scenario runs; verified when the engine is assembled
     * @param directory the empty directory the stores of the replay live in
     */
    ScenarioReplayRunner(WorkflowDefinition unit, Path directory) {
        this.engine = fixtures.engine(Objects.requireNonNull(unit, "unit"));
        this.directory = Objects.requireNonNull(directory, "directory");
    }

    /**
     * The stored status after one step.
     *
     * @param line   the line of the step
     * @param status the bytes of the status store after the step; empty before the scope was started
     */
    record Snapshot(int line, byte[] status) {

        @Override
        public boolean equals(Object other) {
            return other instanceof Snapshot(int otherLine, byte[] otherStatus) && line == otherLine
                    && Arrays.equals(status, otherStatus);
        }

        @Override
        public int hashCode() {
            return line * 31 + Arrays.hashCode(status);
        }

        @Override
        public String toString() {
            return "Snapshot[line=%d, %d bytes]".formatted(line, status.length);
        }
    }

    /** A step of a scenario did not go as the script says. */
    static final class ScenarioFailure extends AssertionError {

        @Serial
        private static final long serialVersionUID = 1L;

        private final int line;

        ScenarioFailure(int line, String message) {
            super("line %d: %s".formatted(line, message));
            this.line = line;
        }

        /** @return the line of the step that failed */
        int line() {
            return line;
        }
    }

    /**
     * Replays the script.
     *
     * @param script the script
     * @throws ScenarioFailure       if a step does not go as the script says
     * @throws IllegalStateException if the runner replayed already
     */
    void run(ScenarioScript script) {
        Objects.requireNonNull(script, "script");
        if (replayed) {
            throw new IllegalStateException("a runner replays one script once");
        }
        replayed = true;
        script.workers().forEach(worker -> workers.put(worker.role(), new ScriptedFixtureWorker(worker)));
        for (var directive : script.directives()) {
            if (!(directive instanceof ScenarioDirective.AssertState)) {
                settleRestingTasks(directive.line());
            }
            execute(directive);
            snapshots.add(new Snapshot(directive.line(), storedBytes()));
        }
        for (var declaration : script.workers()) {
            var left = workers.get(declaration.role()).remaining();
            if (!left.isEmpty()) {
                var message = "the worker of role %s has task outcomes no task took: %s";
                throw new ScenarioFailure(declaration.line(), message.formatted(declaration.role(), left));
            }
        }
    }

    /** @return the stored status after every step replayed so far, in order */
    List<Snapshot> snapshots() {
        return List.copyOf(snapshots);
    }

    /** @return the fixtures of the replay, with the ports the engine used */
    ScenarioFixtures fixtures() {
        return fixtures;
    }

    private void execute(ScenarioDirective directive) {
        switch (directive) {
            case ScenarioDirective.Start start -> start(start);
            case ScenarioDirective.CompleteJob job -> advance(job.line(),
                    new EngineEvent.JobSettled(job.jobId(), job.fate()));
            case ScenarioDirective.AnswerQuestion answer -> {
                fixtures.questionReader.resolve(answer.questionId(), answer.outcome());
                advance(answer.line(), new EngineEvent.QuestionResolved(answer.questionId(), answer.outcome()));
            }
            case ScenarioDirective.ExpireQuestion expire -> expire(expire);
            case ScenarioDirective.AssertState assertion -> {
                var actual = storedStatus(assertion.line()).state();
                if (!assertion.stateId().equals(actual)) {
                    throw new ScenarioFailure(assertion.line(), "assert_state expected '%s' but the scope is in '%s'"
                            .formatted(assertion.stateId(), actual));
                }
            }
            case ScenarioDirective.AssertOutcome assertion -> assertOutcome(assertion);
            case ScenarioDirective.AssertTaskOutcome assertion -> assertTaskOutcome(assertion);
        }
    }

    private void assertOutcome(ScenarioDirective.AssertOutcome assertion) {
        if (ended == null) {
            var message = "assert_outcome expected '%s' but the scope has not ended; it is in '%s'";
            throw new ScenarioFailure(assertion.line(), message.formatted(assertion.outcome(),
                    storedStatus(assertion.line()).state()));
        }
        if (!assertion.outcome().equals(ended.outcomeId())) {
            var message = "assert_outcome expected '%s' but the scope ended with '%s'";
            throw new ScenarioFailure(assertion.line(), message.formatted(assertion.outcome(), ended.outcomeId()));
        }
    }

    private void assertTaskOutcome(ScenarioDirective.AssertTaskOutcome assertion) {
        var worker = workers.get(assertion.role());
        var actual = worker == null ? null : worker.lastOutcome().orElse(null);
        if (actual == null) {
            var message = "assert_task_outcome expected '%s' for role %s but no task of the role was settled";
            throw new ScenarioFailure(assertion.line(), message.formatted(assertion.outcome(), assertion.role()));
        }
        if (!assertion.outcome().equals(actual)) {
            var message = "assert_task_outcome expected '%s' for role %s but its last task ended '%s'";
            throw new ScenarioFailure(assertion.line(), message.formatted(assertion.outcome(), assertion.role(),
                    actual));
        }
    }

    private void start(ScenarioDirective.Start start) {
        if (status != null) {
            throw new ScenarioFailure(start.line(), "the scope was started already");
        }
        scope = ScenarioFixtures.scope(directory, start.scope(), start.scopeId());
        store = new WorkflowStatusStore(scope);
        try {
            Files.createDirectories(store.store().getParent());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        fixtures.clock.tick();
        EngineStep step;
        try {
            step = engine.start(start.scope(), start.scopeId());
        } catch (IllegalArgumentException e) {
            throw new ScenarioFailure(start.line(), "start refused: " + e.getMessage());
        }
        var transaction = fixtures.transaction(scope);
        store.write(transaction, step.status());
        transaction.commit();
        apply(step);
    }

    private void expire(ScenarioDirective.ExpireQuestion expire) {
        var current = storedStatus(expire.line());
        var node = engine.unit().states().get(current.state());
        if (!(node.behavior() instanceof StateBehavior.QuestionWait question)) {
            throw new ScenarioFailure(expire.line(), "expire_question %s, but the scope is in '%s', no question state"
                    .formatted(expire.questionId(), current.state()));
        }
        var outcome = ScenarioFixtures.DEADLINE_OUTCOMES.get(question.questionClass());
        if (outcome == null) {
            throw new ScenarioFailure(expire.line(), "the fixtures state no deadline outcome of the question class "
                    + question.questionClass());
        }
        fixtures.questionReader.resolve(expire.questionId(), outcome);
        advance(expire.line(), new EngineEvent.QuestionResolved(expire.questionId(), outcome));
    }

    /** Lets the worker of each role settle the task state the scope rests in, if it rests in one. */
    private void settleRestingTasks(int line) {
        if (status == null || ended != null) {
            return;
        }
        var wait = engine.restingWait(status);
        if (wait.isEmpty() || wait.get().awaitedEvent() != AwaitedEvent.TASK_SETTLED) {
            return;
        }
        if (status.activeTasks().size() != 1) {
            throw new ScenarioFailure(line, "the task state '%s' holds %d tasks; the harness settles one"
                    .formatted(status.state(), status.activeTasks().size()));
        }
        var entry = status.activeTasks().entrySet().iterator().next();
        var task = entry.getValue();
        var worker = workers.get(task.role());
        if (worker == null) {
            var message = "the task %s of role %s is queued in '%s', but the script declares no worker of the role";
            throw new ScenarioFailure(line, message.formatted(task.queueTaskId(), task.role(), status.state()));
        }
        EngineEvent.TasksSettled settlement;
        try {
            settlement = worker.settle(entry.getKey(), task);
        } catch (IllegalStateException e) {
            throw new ScenarioFailure(line, e.getMessage());
        }
        advance(line, settlement);
    }

    private void advance(int line, EngineEvent event) {
        if (status == null) {
            throw new ScenarioFailure(line, "the scope has not been started");
        }
        fixtures.clock.tick();
        var transaction = fixtures.transaction(scope);
        EngineStep step;
        try {
            step = engine.advance(status, event, store, transaction);
        } catch (IllegalArgumentException e) {
            throw new ScenarioFailure(line, "the event was not accepted: " + e.getMessage());
        }
        if (step.status() != status) {
            transaction.commit();
        }
        apply(step);
    }

    private void apply(EngineStep step) {
        status = step.status();
        ended = step instanceof EngineStep.Ended end ? end : null;
    }

    private WorkflowStatus storedStatus(int line) {
        if (store == null) {
            throw new ScenarioFailure(line, "the scope has not been started");
        }
        return store.read().orElseThrow(() -> new ScenarioFailure(line, "the status store is empty"));
    }

    private byte[] storedBytes() {
        if (store == null || !Files.exists(store.store())) {
            return new byte[0];
        }
        try {
            return Files.readAllBytes(store.store());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
