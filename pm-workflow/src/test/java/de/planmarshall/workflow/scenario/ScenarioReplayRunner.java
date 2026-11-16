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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

import de.planmarshall.core.queue.QueueTaskRecord;
import de.planmarshall.core.queue.QueueTaskRecord.AttemptEnd;
import de.planmarshall.core.spi.AwaitedEvent;
import de.planmarshall.core.store.JournalScope;
import de.planmarshall.core.store.JournalScopeType;
import de.planmarshall.core.store.WorkflowStatus;
import de.planmarshall.core.store.WorkflowStatusStore;
import de.planmarshall.workflow.ast.WorkflowDefinition;
import de.planmarshall.workflow.consult.ConsultationOutcome;
import de.planmarshall.workflow.consult.ConsultationRequest;
import de.planmarshall.workflow.consult.SubmitValidation;
import de.planmarshall.workflow.engine.EngineEvent;
import de.planmarshall.workflow.engine.EngineStep;
import de.planmarshall.workflow.engine.WorkflowEngine;
import de.planmarshall.workflow.task.CallResult;
import de.planmarshall.workflow.task.TaskCall;
import de.planmarshall.workflow.task.WaitAnswer;

/**
 * The runner of the replay harness {@code pm-scenario} (PM-TEST-4): it replays a {@link ScenarioScript} in process
 * against the real {@link WorkflowEngine}, from an empty status store in a directory the test gives, without a live
 * model.
 * <p>
 * Each step of the script is one directive:
 * <ul>
 * <li>{@code start} starts the scope ({@link WorkflowEngine#start});</li>
 * <li>{@code complete_job} delivers {@link EngineEvent.JobSettled}, {@code answer_question} the answer's
 * {@link EngineEvent.QuestionResolved}, and {@code operator_do} the {@link EngineEvent.OperatorAct} of an operator
 * link of an operator wait;</li>
 * <li>{@code worker_wait} starts the generation of a protocol worker and delivers its wait to the real
 * {@link de.planmarshall.workflow.task.TaskProtocol}, {@code worker_do} its acknowledgement, submit, infeasible
 * report or consultation, and {@code assert_worker_state} and {@code assert_refused} the state of its task and the
 * refusal of its last call;</li>
 * <li>{@code kill_worker}, {@code end_turn}, {@code kill_relay} and {@code stall_worker} apply the fact the supervisor
 * reports for the loss of a protocol worker at the instant it is detected, and fence its generation, and
 * {@code restart_runtime} rebuilds the engine, the task protocol and the stores' objects over the replay's directory;
 * </li>
 * <li>{@code assert_state} compares the state of the stored status, {@code assert_outcome} the outcome the run ended
 * with, and {@code assert_task_outcome} the outcome of the last task the worker of a role settled.</li>
 * </ul>
 * A task state the scope rests in and whose role has no protocol worker is settled by the
 * {@link ScriptedFixtureWorker} of its role before the next step that is not an {@code assert_state},
 * {@code assert_worker_state} or {@code assert_refused}, so a script can assert the task state first and then what the
 * task ended with. A protocol worker's task is settled by the protocol, never by
 * the harness. Every new status is written through a {@link de.planmarshall.core.store.ScopeTransaction} to the store,
 * the queue of the replay keeps its own records, and the runner keeps the bytes of the status store and of the queue
 * after every step ({@link #snapshots()}), also when the replay fails, so two replays can be compared step by step.
 * <p>
 * Every deviation is a {@link ScenarioFailure} naming the step's line: a wrong expectation (with the expected and the
 * actual value), an event the resting state does not wait for, a task without a declared worker outcome, a declared
 * outcome no task took, and a call of the task protocol the script expected accepted. Nothing is skipped silently. One
 * runner replays one script once.
 */
// Every datum in this class is a contract: the failure-message templates the tests assert exactly.
final class ScenarioReplayRunner {

    private final ScenarioFixtures fixtures;
    private final WorkflowDefinition unit;
    private final Path directory;
    private final List<Snapshot> snapshots = new ArrayList<>();
    private final Map<String, ScriptedFixtureWorker> workers = new LinkedHashMap<>();
    private final Map<String, ScriptedProtocolWorker> protocolWorkers = new LinkedHashMap<>();
    private WorkflowEngine engine;
    private JournalScope scope;
    private JournalScopeType scopeType;
    private String scopeId;
    private WorkflowStatusStore store;
    private WorkflowStatus status;
    private EngineStep.Ended ended;
    private ScriptedProtocolWorker currentWorker;
    private CallResult lastWorkerCall;
    private boolean replayed;

    /**
     * @param unit      the plan unit the scenario runs; verified when the engine is assembled
     * @param directory the empty directory the stores of the replay live in
     */
    ScenarioReplayRunner(WorkflowDefinition unit, Path directory) {
        this.directory = Objects.requireNonNull(directory, "directory");
        this.unit = Objects.requireNonNull(unit, "unit");
        this.fixtures = new ScenarioFixtures(directory);
        this.engine = fixtures.engine(this.unit);
    }

    /**
     * The stores of the replay after one step.
     *
     * @param line   the line of the step
     * @param status the bytes of the status store after the step; empty before the scope was started
     * @param queue  the bytes of the queue files after the step, by file name; empty before the first record
     */
    record Snapshot(int line, byte[] status, Map<String, byte[]> queue) {

        /** @throws NullPointerException if a component is {@code null} */
        Snapshot {
            Objects.requireNonNull(status, "status");
            queue = Map.copyOf(queue);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Snapshot snapshot && line == snapshot.line
                    && Arrays.equals(status, snapshot.status) && equalQueue(queue, snapshot.queue);
        }

        private static boolean equalQueue(Map<String, byte[]> left, Map<String, byte[]> right) {
            if (!left.keySet().equals(right.keySet())) {
                return false;
            }
            for (var name : left.keySet()) {
                if (!Arrays.equals(left.get(name), right.get(name))) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int hashCode() {
            var hash = line * 31 + Arrays.hashCode(status);
            for (var name : new TreeMap<>(queue).keySet()) {
                hash = hash * 31 + name.hashCode() * 31 + Arrays.hashCode(queue.get(name));
            }
            return hash;
        }

        @Override
        public String toString() {
            return "Snapshot[line=%d, %d bytes, %d queue files]".formatted(line, status.length, queue.size());
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
            if (directive instanceof ScenarioDirective.WorkerWait wait && !protocolWorkers.containsKey(wait.role())) {
                protocolWorkers.put(wait.role(),
                        new ScriptedProtocolWorker(wait.role(), fixtures.generation(), fixtures.protocol()));
            }
        }
        var directives = script.directives();
        for (var index = 0; index < directives.size(); index++) {
            var directive = directives.get(index);
            if (settles(directive)) {
                settleRestingTasks(directive.line());
            }
            var expectsRefusal = index + 1 < directives.size()
                    && directives.get(index + 1) instanceof ScenarioDirective.AssertRefused;
            execute(directive, expectsRefusal);
            snapshots.add(new Snapshot(directive.line(), storedBytes(), queueFiles()));
        }
        for (var declaration : script.workers()) {
            var left = workers.get(declaration.role()).remaining();
            if (!left.isEmpty()) {
                var message = "the worker of role %s has task outcomes no task took: %s";
                throw new ScenarioFailure(declaration.line(), message.formatted(declaration.role(), left));
            }
        }
    }

    /** @return the stored status and queue bytes after every step replayed so far, in order */
    List<Snapshot> snapshots() {
        return List.copyOf(snapshots);
    }

    /** @return the fixtures of the replay, with the ports the engine used */
    ScenarioFixtures fixtures() {
        return fixtures;
    }

    /**
     * The state of the task of one role's protocol worker, as {@code assert_worker_state} derives it; the seam a test
     * uses for a state a script cannot reach (a release the fence did not cause).
     *
     * @param role the role whose protocol worker's task is read
     * @return the state, or empty when the worker holds no task
     */
    Optional<String> workerStateOf(String role) {
        var worker = protocolWorkers.get(role);
        var taskId = worker == null ? null : worker.queueTaskId().orElse(null);
        if (taskId == null) {
            return Optional.empty();
        }
        return Optional.of(workerState(fixtures.queue.read(taskId).orElseThrow()));
    }

    /** @return whether the step settles the resting task before it runs */
    private static boolean settles(ScenarioDirective directive) {
        return !(directive instanceof ScenarioDirective.AssertState
                || directive instanceof ScenarioDirective.AssertWorkerState
                || directive instanceof ScenarioDirective.AssertRefused);
    }

    private void execute(ScenarioDirective directive, boolean expectsRefusal) {
        switch (directive) {
            case ScenarioDirective.Start start -> start(start);
            case ScenarioDirective.CompleteJob job -> advance(job.line(),
                    new EngineEvent.JobSettled(job.jobId(), job.fate()));
            case ScenarioDirective.AnswerQuestion answer -> {
                fixtures.questionReader.resolve(answer.questionId(), answer.outcome());
                advance(answer.line(), new EngineEvent.QuestionResolved(answer.questionId(), answer.outcome()));
            }
            case ScenarioDirective.AssertState assertion -> {
                var actual = storedStatus(assertion.line()).state();
                if (!assertion.stateId().equals(actual)) {
                    throw new ScenarioFailure(assertion.line(), "assert_state expected '%s' but the scope is in '%s'"
                            .formatted(assertion.stateId(), actual));
                }
            }
            case ScenarioDirective.AssertOutcome assertion -> assertOutcome(assertion);
            case ScenarioDirective.AssertTaskOutcome assertion -> assertTaskOutcome(assertion);
            case ScenarioDirective.OperatorDo act -> operatorAct(act);
            case ScenarioDirective.WorkerWait wait -> wait(wait);
            case ScenarioDirective.WorkerDo call -> workerDo(call, expectsRefusal);
            case ScenarioDirective.KillWorker fault -> killWorker(fault);
            case ScenarioDirective.EndTurn fault -> endTurn(fault);
            case ScenarioDirective.KillRelay fault -> killRelay(fault);
            case ScenarioDirective.StallWorker fault -> stallWorker(fault);
            case ScenarioDirective.RestartRuntime restart -> restartRuntime(restart);
            case ScenarioDirective.AssertWorkerState assertion -> assertWorkerState(assertion);
            case ScenarioDirective.AssertRefused assertion -> assertRefused(assertion);
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
        if (protocolWorkers.containsKey(assertion.role())) {
            assertTaskOutcomeFromQueue(assertion);
            return;
        }
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

    /** Reads the closed outcome of a protocol worker's task from its queue record. */
    private void assertTaskOutcomeFromQueue(ScenarioDirective.AssertTaskOutcome assertion) {
        var taskId = protocolWorkers.get(assertion.role()).queueTaskId().orElse(null);
        if (taskId == null) {
            var message = "assert_task_outcome expected '%s' for role %s but the worker of the role holds no task";
            throw new ScenarioFailure(assertion.line(), message.formatted(assertion.outcome(), assertion.role()));
        }
        var task = queueTask(assertion.line(), taskId);
        if (!task.isClosed()) {
            var message = "assert_task_outcome expected '%s' for role %s but its task %s is open '%s'";
            throw new ScenarioFailure(assertion.line(), message.formatted(assertion.outcome(), assertion.role(),
                    taskId, task.status().wireName()));
        }
        var actual = task.outcome() == null ? null : task.outcome().wireName();
        if (!assertion.outcome().equals(actual)) {
            var message = "assert_task_outcome expected '%s' for role %s but its last task ended '%s'";
            throw new ScenarioFailure(assertion.line(), message.formatted(assertion.outcome(), assertion.role(),
                    actual));
        }
    }

    /**
     * Takes an operator act at the resting operator wait: the operator follows one offered link of the state the scope
     * rests in.
     */
    private void operatorAct(ScenarioDirective.OperatorDo act) {
        if (status == null) {
            throw new ScenarioFailure(act.line(), "the scope has not been started");
        }
        advance(act.line(), new EngineEvent.OperatorAct(status.state(), act.link(), act.parameters()));
    }

    /** Starts the generation of a protocol worker: its wait is the shipped protocol's, and an offer is required. */
    private void wait(ScenarioDirective.WorkerWait step) {
        var worker = protocolWorkers.get(step.role());
        if (worker.replaced()) {
            worker = worker.successor(fixtures.generation());
            protocolWorkers.put(step.role(), worker);
        }
        currentWorker = worker;
        fixtures.clock.tick();
        var answer = worker.wait(fixtures.clock.now());
        if (answer instanceof WaitAnswer.Offered) {
            return;
        }
        var message = "worker_wait of role %s expected an offer but the protocol answered %s";
        throw new ScenarioFailure(step.line(), message.formatted(step.role(), describe(answer)));
    }

    private static String describe(WaitAnswer answer) {
        return switch (answer) {
            case WaitAnswer.WaitAgain again -> "'%s', an empty window".formatted(again.reason().wireName());
            case WaitAnswer.End end -> "the end of the worker '%s'".formatted(end.reason().wireName());
            case WaitAnswer.Answered(_, _) -> "the answer of a held task, which this harness does not deliver";
            case WaitAnswer.Offered(_) -> "an offer";
        };
    }

    /**
     * Runs a protocol worker's call. A refusal the script does not assert is a failure at the step's line: a step the
     * script expects accepted. A call that closes the task settles the task state of the engine.
     */
    private void workerDo(ScenarioDirective.WorkerDo call, boolean expectsRefusal) {
        var worker = target(call);
        fixtures.clock.tick();
        var at = fixtures.clock.now();
        CallResult result;
        switch (call.call().action()) {
            case ScenarioDirective.Action.Ack() -> {
                requireTask(call.line(), worker);
                result = worker.acknowledge(at);
            }
            case ScenarioDirective.Action.Infeasible() -> {
                requireTask(call.line(), worker);
                result = worker.infeasible(at);
            }
            case ScenarioDirective.Action.Submit submit when "answer".equals(submit.kind()) -> {
                answerConsultation(call, worker, at);
                lastWorkerCall = null;
                return;
            }
            case ScenarioDirective.Action.Submit submit -> result = submit(call, submit, worker, at);
            case ScenarioDirective.Action.Consult consult -> {
                openConsultation(call, consult, worker, at);
                lastWorkerCall = null;
                return;
            }
        }
        lastWorkerCall = result;
        if (result instanceof CallResult.Accepted accepted && accepted.task().isClosed()) {
            settle(call.line(), accepted.task());
        }
        if (result instanceof CallResult.Refused refused && !expectsRefusal) {
            var message = "the call was refused '%s'";
            throw new ScenarioFailure(call.line(), message.formatted(refused.refusal().wireName()));
        }
    }

    /**
     * @param call a worker's call
     * @return the worker the call targets: the named role's, or the script's most recent wait's
     */
    private ScriptedProtocolWorker target(ScenarioDirective.WorkerDo call) {
        var role = call.call().role();
        if (role == null) {
            if (currentWorker == null) {
                throw new ScenarioFailure(call.line(), "worker_do names no role, and no worker_wait precedes it");
            }
            return currentWorker;
        }
        var worker = protocolWorkers.get(role);
        if (worker == null) {
            throw new ScenarioFailure(call.line(), "the script declares no worker_wait of role " + role);
        }
        return worker;
    }

    /**
     * Submits the protocol worker's task: the shipped consultation service validates the consultation references the
     * call names, and the protocol closes the task once.
     */
    private CallResult submit(ScenarioDirective.WorkerDo call, ScenarioDirective.Action.Submit submit,
            ScriptedProtocolWorker worker, Instant at) {
        var taskId = requireTask(call.line(), worker);
        var task = queueTask(call.line(), taskId);
        if (!submit.kind().equals(task.entity())) {
            var message = "worker_do submit:%s names a kind, but the task %s works the entity %s";
            throw new ScenarioFailure(call.line(), message.formatted(submit.kind(), taskId, task.entity()));
        }
        var validation = fixtures.consultations.validateSubmit(taskId,
                consultationIds(call.call().arguments()));
        if (validation instanceof SubmitValidation.Refused refused) {
            throw new ScenarioFailure(call.line(), "the submit was refused: " + refused.detail());
        }
        return worker.submit(at);
    }

    /** Opens a consultation of another role through the shipped consultation service. */
    private void openConsultation(ScenarioDirective.WorkerDo call, ScenarioDirective.Action.Consult consult,
            ScriptedProtocolWorker worker, Instant at) {
        var taskId = requireTask(call.line(), worker);
        var request = ConsultationRequest.builder()
                .askingTaskId(taskId)
                .generationId(worker.generation())
                .role(consult.role())
                .roleClass(fixtures.roleClass(consult.role()))
                .formDowngraded(false)
                .declaredRoles(fixtures.consultRoles())
                .question(call.call().arguments().get("question"))
                .at(at)
                .build();
        switch (fixtures.consultations.open(request)) {
            case ConsultationOutcome.Opened(_, _) -> {
                // the consultation task is queued for the consulted role
            }
            case ConsultationOutcome.Refused refused -> throw new ScenarioFailure(call.line(),
                    "the consultation was refused '%s': %s".formatted(refused.refusal().wireName(), refused.detail()));
            case ConsultationOutcome.Answered(_, _) -> throw new ScenarioFailure(call.line(),
                    "opening a consultation does not answer it");
        }
    }

    /** Answers the consultation the protocol worker holds, through the shipped consultation service. */
    private void answerConsultation(ScenarioDirective.WorkerDo call, ScriptedProtocolWorker worker, Instant at) {
        var taskId = requireTask(call.line(), worker);
        switch (fixtures.consultations.answer(taskId, worker.generation(), call.call().arguments().get("answer"), at)) {
            case ConsultationOutcome.Answered(_, _) -> {
                // the answer is delivered to the asker's next wait
            }
            case ConsultationOutcome.Refused refused -> throw new ScenarioFailure(call.line(),
                    "the answer was refused '%s': %s".formatted(refused.refusal().wireName(), refused.detail()));
            case ConsultationOutcome.Opened(_, _) -> throw new ScenarioFailure(call.line(),
                    "answering a consultation does not open one");
        }
    }

    private static String requireTask(int line, ScriptedProtocolWorker worker) {
        return worker.queueTaskId().orElseThrow(() -> new ScenarioFailure(line,
                "the worker of role %s holds no leased task: no worker_wait of the role was offered one"
                        .formatted(worker.role())));
    }

    /** @return the consultation references the call's argument names; empty when it names none */
    private static List<String> consultationIds(Map<String, String> arguments) {
        var ids = arguments.get("consultation_ids");
        if (ids == null || ids.isBlank()) {
            return List.of();
        }
        return Arrays.stream(ids.split(",")).map(String::strip).filter(id -> !id.isEmpty()).toList();
    }

    /** Settles the task state of the engine with the task the protocol just closed. */
    private void settle(int line, QueueTaskRecord task) {
        var entities = status.activeTasks().entrySet().stream()
                .filter(entry -> entry.getValue().queueTaskId().equals(task.queueTaskId()))
                .map(Map.Entry::getKey)
                .toList();
        if (entities.size() != 1) {
            var message = "the closed task %s is no active task of the state '%s'";
            throw new ScenarioFailure(line, message.formatted(task.queueTaskId(), status.state()));
        }
        if (task.outcome() == null) {
            throw new ScenarioFailure(line, "the task %s closed without an outcome".formatted(task.queueTaskId()));
        }
        var taskOutcome = task.outcome().wireName();
        String evaluated;
        try {
            evaluated = ScenarioFixtures.evaluate(taskOutcome);
        } catch (IllegalArgumentException e) {
            throw new ScenarioFailure(line, "the worker of role %s: %s".formatted(task.role(), e.getMessage()));
        }
        advanceAt(line, new EngineEvent.TasksSettled(Map.of(entities.getFirst(), taskOutcome), evaluated));
    }

    /** Asserts the state of a protocol worker's task, read from its queue record. */
    private void assertWorkerState(ScenarioDirective.AssertWorkerState assertion) {
        var worker = protocolWorkers.get(assertion.role());
        var taskId = worker == null ? null : worker.queueTaskId().orElse(null);
        if (taskId == null) {
            var message = "assert_worker_state expected '%s' for role %s but the worker of the role holds no task";
            throw new ScenarioFailure(assertion.line(), message.formatted(assertion.state(), assertion.role()));
        }
        var actual = workerState(queueTask(assertion.line(), taskId));
        if (!assertion.state().equals(actual)) {
            var message = "assert_worker_state expected '%s' for role %s but its task %s is '%s'";
            throw new ScenarioFailure(assertion.line(),
                    message.formatted(assertion.state(), assertion.role(), taskId, actual));
        }
    }

    /** The state of the task of a protocol worker, derived from its queue record and the replay's fence store. */
    private String workerState(QueueTaskRecord task) {
        if (task.isClosed()) {
            return "closed";
        }
        return switch (task.status()) {
            case OFFERED -> "offered";
            case EXECUTING -> "acknowledged";
            case QUEUED -> fenced(task) ? "fenced" : "queued";
            default -> task.status().wireName();
        };
    }

    /**
     * @param task a queued task
     * @return whether the replay fenced the generation its last attempt belonged to, which released the lease
     */
    private boolean fenced(QueueTaskRecord task) {
        var attempts = task.attempts();
        return !attempts.isEmpty() && fixtures.fences.isFenced(attempts.getLast().generationId());
    }

    /** Asserts that the last {@code worker_do} was refused with the reason the step names. */
    private void assertRefused(ScenarioDirective.AssertRefused assertion) {
        if (lastWorkerCall == null) {
            var message = "assert_refused expected '%s' but the last worker_do made no call of the task protocol";
            throw new ScenarioFailure(assertion.line(), message.formatted(assertion.reason()));
        }
        if (lastWorkerCall instanceof CallResult.Accepted) {
            var message = "assert_refused expected '%s' but the last worker_do was accepted";
            throw new ScenarioFailure(assertion.line(), message.formatted(assertion.reason()));
        }
        var refused = (CallResult.Refused) lastWorkerCall;
        if (!assertion.reason().equals(refused.refusal().wireName())) {
            var message = "assert_refused expected '%s' but the call was refused '%s'";
            throw new ScenarioFailure(assertion.line(),
                    message.formatted(assertion.reason(), refused.refusal().wireName()));
        }
        lastWorkerCall = null;
    }

    /**
     * A worker lost between its offer and its acknowledgement, or between its acknowledgement and its submit: the
     * attempt ends with the fact the deadline of the open attempt reports, and the generation is fenced at that
     * deadline.
     */
    private void killWorker(ScenarioDirective.KillWorker fault) {
        var worker = workerOf(fault.line(), fault.role(), "kill_worker");
        lose(fault.line(), worker, AttemptEnd.WORKER_LOST, deadline(fault.line(), worker).instant());
    }

    /**
     * A worker that ends its turn without a submit: the supervisor observes the exit of the worker's process after its
     * first call, at once.
     */
    private void endTurn(ScenarioDirective.EndTurn fault) {
        requireAcknowledged(fault.line(), fault.role(), "end_turn");
        fixtures.clock.tick();
        lose(fault.line(), workerOf(fault.line(), fault.role(), "end_turn"),
                AttemptEnd.EXITED_WITHOUT_SUBMIT, fixtures.clock.now());
    }

    /**
     * A relay killed under the wait its acknowledged worker holds: the held wait dies with the relay, so the silence
     * limit counts again from the relay's death and the loss is detected at its end.
     */
    private void killRelay(ScenarioDirective.KillRelay fault) {
        requireAcknowledged(fault.line(), fault.role(), "kill_relay");
        fixtures.clock.tick();
        var at = fixtures.clock.now().plusSeconds(ScenarioFixtures.WORKER_PARAMETERS.silenceLimitSeconds());
        lose(fault.line(), workerOf(fault.line(), fault.role(), "kill_relay"), AttemptEnd.WORKER_LOST, at);
    }

    /**
     * A worker that makes no call for the given time: shorter than the limit that governs its open attempt it changes
     * nothing and the worker is not replaced; at or beyond the limit the attempt ends with the loss at that limit.
     * Stalls add up, as silence does: two stalls whose times sum past the limit have the worker lost.
     */
    private void stallWorker(ScenarioDirective.StallWorker fault) {
        var worker = workerOf(fault.line(), fault.role(), "stall_worker");
        var deadline = deadline(fault.line(), worker);
        var until = fixtures.clock.now().plusSeconds(fault.seconds());
        fixtures.clock.advanceTo(until);
        if (!until.isBefore(deadline.instant())) {
            lose(fault.line(), worker, AttemptEnd.WORKER_LOST, deadline.instant());
        }
    }

    /**
     * The runtime restart: the engine, the task protocol and the stores' objects are rebuilt over the same directory,
     * the status is re-read from its bytes, and every protocol worker is rebuilt over its own generation with the lease
     * the queue record still names. Nothing in memory survives; a lease, a fence and a queue record do.
     */
    private void restartRuntime(ScenarioDirective.RestartRuntime restart) {
        if (status == null) {
            throw new ScenarioFailure(restart.line(), "the scope has not been started");
        }
        if (ended != null) {
            var message = "restart_runtime has nothing to continue: the scope ended with '%s'";
            throw new ScenarioFailure(restart.line(), message.formatted(ended.outcomeId()));
        }
        var before = new LinkedHashMap<>(protocolWorkers);
        fixtures.restart();
        engine = fixtures.engine(unit);
        fixtures.queueTasks.scope(ScenarioFixtures.taskScope(scopeType, scopeId));
        store = new WorkflowStatusStore(scope);
        status = store.read().orElseThrow(() -> new ScenarioFailure(restart.line(), "the status store is empty"));
        protocolWorkers.clear();
        before.forEach((role, worker) -> {
            var restarted = new ScriptedProtocolWorker(role, worker.generation(), fixtures.protocol());
            restarted.replaced(worker.replaced());
            if (!worker.replaced()) {
                var leased = leasedTaskOf(worker.generation());
                if (leased != null) {
                    restarted.leased(leased);
                }
            }
            protocolWorkers.put(role, restarted);
        });
        currentWorker = currentWorker == null ? null : protocolWorkers.get(currentWorker.role());
        lastWorkerCall = null;
    }

    /** @return the task the queue's lease names for the generation; {@code null} when it holds none */
    private QueueTaskRecord leasedTaskOf(String generation) {
        return fixtures.queue.records().stream()
                .filter(task -> task.lease() != null && task.lease().generationId().equals(generation))
                .findFirst()
                .orElse(null);
    }

    /**
     * Ends the open attempt of a role's protocol worker with the fact of its loss, at the instant the loss is
     * detected, and fences the generation there: the task is queued again within its re-offer budget, or closed
     * {@code lost} with {@code attempts_spent} when the budget is spent.
     */
    private void lose(int line, ScriptedProtocolWorker worker, AttemptEnd end, Instant at) {
        var role = worker.role();
        var taskId = requireTask(line, worker);
        fixtures.clock.advanceTo(at);
        var reported = fixtures.protocol().report(new TaskCall(taskId, worker.generation()), end,
                ScenarioFixtures.RE_OFFER_BUDGET, at);
        if (reported instanceof CallResult.Refused refused) {
            var message = "the loss of the worker of role %s was refused '%s'";
            throw new ScenarioFailure(line, message.formatted(role, refused.refusal().wireName()));
        }
        fixtures.protocol().fence(worker.generation(), at);
        worker.replaced(true);
        lastWorkerCall = null;
        var task = ((CallResult.Accepted) reported).task();
        if (task.isClosed()) {
            settle(line, task);
        }
    }

    /** @return the role's protocol worker, or a failure naming the missing declaration */
    private ScriptedProtocolWorker workerOf(int line, String role, String directive) {
        var worker = protocolWorkers.get(role);
        if (worker == null) {
            throw new ScenarioFailure(line, "%s names the role %s, which the script never declares with worker_wait"
                    .formatted(directive, role));
        }
        return worker;
    }

    /** Refuses a fault the state of the role's task does not admit. */
    private void requireAcknowledged(int line, String role, String directive) {
        var worker = workerOf(line, role, directive);
        var taskId = requireTask(line, worker);
        var task = queueTask(line, taskId);
        if (task.status() != QueueTaskRecord.Status.EXECUTING) {
            var message = "%s acts on a task the worker of role %s has acknowledged, but its task %s is '%s'";
            throw new ScenarioFailure(line, message.formatted(directive, role, taskId, task.status().wireName()));
        }
    }

    /**
     * The deadline that governs the role's open attempt, from the worker's compiled limits read from
     * {@link ScenarioFixtures#WORKER_PARAMETERS}: the acknowledgement deadline of an unacknowledged offer, otherwise
     * the silence limit of the last call.
     *
     * @param line   the line of the fault
     * @param worker the role's protocol worker, whose open attempt a fault ends
     * @return the limit in seconds and the instant it falls at
     */
    private Deadline deadline(int line, ScriptedProtocolWorker worker) {
        var taskId = requireTask(line, worker);
        var task = queueTask(line, taskId);
        var parameters = ScenarioFixtures.WORKER_PARAMETERS;
        var ackDeadline = parameters.ackDeadlineSeconds();
        if (task.status() == QueueTaskRecord.Status.OFFERED && !parameters.ackInformational()
                && ackDeadline.isPresent()) {
            var offeredAt = worker.offeredAt().orElseThrow(() -> new ScenarioFailure(line,
                    "the worker of role %s holds no offer instant".formatted(worker.role())));
            return new Deadline(ackDeadline.getAsInt(), offeredAt.plusSeconds(ackDeadline.getAsInt()));
        }
        var lastCallAt = worker.lastCallAt().orElseThrow(() -> new ScenarioFailure(line,
                "the worker of role %s made no call".formatted(worker.role())));
        var limit = parameters.silenceLimitSeconds();
        return new Deadline(limit, lastCallAt.plusSeconds(limit));
    }

    /**
     * A deadline of the compiled worker values: the limit in seconds and the instant it falls at.
     *
     * @param seconds the limit in seconds
     * @param instant the instant the limit falls at
     */
    private record Deadline(int seconds, Instant instant) {
    }

    private void start(ScenarioDirective.Start start) {
        if (status != null) {
            throw new ScenarioFailure(start.line(), "the scope was started already");
        }
        scope = ScenarioFixtures.scope(directory, start.scope(), start.scopeId());
        scopeType = start.scope();
        scopeId = start.scopeId();
        store = new WorkflowStatusStore(scope);
        try {
            Files.createDirectories(store.store().getParent());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        fixtures.queueTasks.scope(ScenarioFixtures.taskScope(start.scope(), start.scopeId()));
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
        if (protocolWorkers.containsKey(task.role())) {
            // a protocol worker's task is settled by its worker_wait and worker_do, never by the harness
            return;
        }
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
        fixtures.clock.tick();
        advanceAt(line, event);
    }

    private void advanceAt(int line, EngineEvent event) {
        if (status == null) {
            throw new ScenarioFailure(line, "the scope has not been started");
        }
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

    private QueueTaskRecord queueTask(int line, String queueTaskId) {
        return fixtures.queue.read(queueTaskId).orElseThrow(() -> new ScenarioFailure(line,
                "the queue holds no task '%s'".formatted(queueTaskId)));
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

    /** @return the bytes of the queue files after the step, by file name; empty before the first record */
    private Map<String, byte[]> queueFiles() {
        var queueDirectory = fixtures.queue.directory();
        if (!Files.isDirectory(queueDirectory)) {
            return Map.of();
        }
        var files = new TreeMap<String, byte[]>();
        try (var entries = Files.list(queueDirectory)) {
            entries.filter(Files::isRegularFile).sorted().forEach(path -> {
                try {
                    files.put(path.getFileName().toString(), Files.readAllBytes(path));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return files;
    }
}
