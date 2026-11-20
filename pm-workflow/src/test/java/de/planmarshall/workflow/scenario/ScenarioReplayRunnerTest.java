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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.planmarshall.core.queue.QueueTaskRecord;
import de.planmarshall.core.queue.QueueTaskRecord.Attempt;
import de.planmarshall.core.queue.QueueTaskRecord.AttemptEnd;
import de.planmarshall.core.queue.QueueTaskRecord.LostReason;
import de.planmarshall.core.queue.QueueTaskRecord.Outcome;
import de.planmarshall.core.queue.QueueTaskRecord.Status;
import de.planmarshall.core.spi.QueueTaskCreator;
import de.planmarshall.core.store.JournalScopeType;
import de.planmarshall.core.store.WorkflowStatusStore;
import de.planmarshall.workflow.consult.ConsultationService;
import de.planmarshall.workflow.consult.SubmitValidation;
import de.planmarshall.workflow.scenario.ScenarioReplayRunner.ScenarioFailure;
import de.planmarshall.workflow.task.WaitAnswer;
import de.planmarshall.workflow.task.WaitRequest;

/**
 * The runner of {@code pm-scenario} (PM-TEST-4): a script replays in process against the real engine over the fixture
 * unit, with the store in a temporary directory and a scripted worker in place of a model; every deviation from the
 * script fails at the step's line with the expected and the actual value, and the stored status bytes are kept after
 * every step.
 */
@EnableGeneratorController
@DisplayName("Scenario replay runner")
class ScenarioReplayRunnerTest {

    private static final String PLAN_ID = "scenario-plan";

    /** A script over the whole fixture unit that uses every supported directive; its steps are lines 3 to 13. */
    private static final String EVERY_DIRECTIVE = """
            scenario every-directive
            worker reviewer submitted
            start plan scenario-plan
            assert_state execute.build                                 # prepare ran, the build job was started
            complete_job b-0000000000000001 {"outcome":"succeeded"}
            assert_state execute.review                                # the task of the reviewer is queued
            assert_task_outcome reviewer submitted                     # the worker settles it first
            assert_state execute.ask
            answer_question Q-001 web {"branch_key":"approve"}
            assert_state execute.confirm
            answer_question Q-002 web {"branch_key":"answered"}
            assert_state completed
            assert_outcome merged
            """;

    @TempDir
    Path directory;

    private ScenarioReplayRunner runner() {
        return new ScenarioReplayRunner(ScenarioFixtures.planUnit(), directory);
    }

    /** @return the failure of replaying the text */
    private ScenarioFailure failure(String text) {
        return failure(text, directory);
    }

    /** @return the failure of replaying the text in the directory */
    private ScenarioFailure failure(String text, Path replayDirectory) {
        var runner = new ScenarioReplayRunner(ScenarioFixtures.planUnit(), replayDirectory);
        var script = ScenarioScript.read(text);
        return assertThrows(ScenarioFailure.class, () -> runner.run(script),
                "the script's deviation is raised as a failure");
    }

    /** @return a script that starts the plan, lets the reviewer deliver the outcome and runs the given lines */
    private static String reviewed(String reviewerOutcome, String lines) {
        return """
                scenario reviewed
                worker reviewer %s
                start plan scenario-plan
                complete_job b-0000000000000001 {"outcome":"succeeded"}
                %s
                """.formatted(reviewerOutcome, lines);
    }

    static Stream<Arguments> wrongExpectations() {
        return Stream.of(
                Arguments.of("""
                        scenario wrong-state
                        start plan scenario-plan
                        assert_state execute.review
                        """, 3, "assert_state expected 'execute.review' but the scope is in 'execute.build'"),
                Arguments.of("""
                        scenario not-ended
                        start plan scenario-plan
                        assert_outcome merged
                        """, 3,
                        "assert_outcome expected 'merged' but the scope has not ended; it is in 'execute.build'"),
                Arguments.of(reviewed("lost", "assert_outcome merged"), 5,
                        "assert_outcome expected 'merged' but the scope ended with 'handed_back'"),
                Arguments.of(reviewed("submitted", "assert_task_outcome reviewer lost"), 5,
                        "assert_task_outcome expected 'lost' for role reviewer but its last task ended 'submitted'"),
                Arguments.of(reviewed("submitted", "assert_task_outcome planner submitted"), 5,
                        "assert_task_outcome expected 'submitted' for role planner but no task of the role was"));
    }

    static Stream<Arguments> unacceptedEvents() {
        return Stream.of(
                Arguments.of("""
                        scenario answer-at-job
                        start plan scenario-plan
                        answer_question Q-001 web {"branch_key":"approve"}
                        """, "the event was not accepted: the state 'execute.build' waits for"),
                Arguments.of("""
                        scenario other-job
                        start plan scenario-plan
                        complete_job b-00000000000000ff {"outcome":"succeeded"}
                        """, "the event was not accepted: the state 'execute.build' waits for"),
                Arguments.of("""
                        scenario before-start
                        worker reviewer submitted
                        complete_job b-0000000000000001 {"outcome":"succeeded"}
                        """, "the scope has not been started"));
    }

    static Stream<Arguments> settledOutcomes() {
        return Stream.of(
                Arguments.of("submitted", "execute.ask"),
                Arguments.of("infeasible", "handed_back"),
                Arguments.of("lost", "handed_back"));
    }

    @Nested
    @DisplayName("Replay")
    class Replay {

        @Test
        @DisplayName("Should replay every directive kind against the real engine to the unit's terminal")
        void shouldReplayToTerminal() {
            var runner = runner();

            runner.run(ScenarioScript.read(EVERY_DIRECTIVE));

            var stored = new WorkflowStatusStore(ScenarioFixtures.scope(directory, JournalScopeType.PLAN, PLAN_ID));
            var fixtures = runner.fixtures();
            assertAll(
                    () -> assertEquals("completed", stored.read().orElseThrow().state(),
                            "the replay reached the unit's terminal state"),
                    () -> assertEquals(List.of(3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13),
                            runner.snapshots().stream().map(ScenarioReplayRunner.Snapshot::line).toList(),
                            "one snapshot was kept for every step line"),
                    () -> assertArrayEquals(Files.readAllBytes(stored.store()), runner.snapshots().getLast().status(),
                            "the last snapshot holds the stored status bytes"),
                    () -> assertEquals(1, fixtures.jobRequests.getValues().size(),
                            "the replay started the build job once"),
                    () -> assertEquals(List.of(new QueueTaskCreator.CreateRequest(PLAN_ID, "execute.review", "change",
                                    "reviewer")), fixtures.queueTasks.requests(),
                            "the replay created the queue task once"));
        }

        @Test
        @DisplayName("Should refuse a second start and a second replay")
        void shouldRefuseRepetition() {
            var startedTwice = failure("""
                    scenario twice
                    start plan scenario-plan
                    start plan scenario-plan
                    """);
            var runner = runner();
            var script = ScenarioScript.read(EVERY_DIRECTIVE);
            runner.run(script);

            assertAll(
                    () -> assertEquals(3, startedTwice.line(), "the second start failed at its own line"),
                    () -> assertTrue(startedTwice.getMessage().contains("started already"),
                            "the failure says the scope started already"),
                    () -> assertThrows(IllegalStateException.class, () -> runner.run(script),
                            "a second replay of the same runner is refused"));
        }
    }

    @Nested
    @DisplayName("Assertion directives")
    class Assertions {

        @ParameterizedTest(name = "line {1}")
        @MethodSource("de.planmarshall.workflow.scenario.ScenarioReplayRunnerTest#wrongExpectations")
        @DisplayName("Should fail an assertion at its line with the expected and the actual value")
        void shouldFailWrongExpectation(String text, int line, String message) {
            var failure = failure(text);

            assertAll(
                    () -> assertEquals(line, failure.line(), "the failure carries the assertion's line"),
                    () -> assertTrue(failure.getMessage().startsWith("line %d: %s".formatted(line, message)),
                            "the failure states the line and both values"));
        }
    }

    @Nested
    @DisplayName("Events")
    class Events {

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.workflow.scenario.ScenarioReplayRunnerTest#unacceptedEvents")
        @DisplayName("Should fail an event the resting state does not wait for, never skip it")
        void shouldFailUnacceptedEvent(String text, String message) {
            var runner = runner();
            var script = ScenarioScript.read(text);

            var failure = assertThrows(ScenarioFailure.class, () -> runner.run(script),
                    "an event the resting state rejects is a failure");

            assertAll(
                    () -> assertEquals(3, failure.line(), "the failure names the event's line"),
                    () -> assertTrue(failure.getMessage().contains(message),
                            "the failure names the rejected event"),
                    () -> assertTrue(runner.snapshots().stream().noneMatch(snapshot -> snapshot.line() == 3),
                            "the failed step leaves no snapshot"));
        }
    }

    @Nested
    @DisplayName("Scripted worker")
    class Worker {

        @ParameterizedTest(name = "{0} -> {1}")
        @MethodSource("de.planmarshall.workflow.scenario.ScenarioReplayRunnerTest#settledOutcomes")
        @DisplayName("Should settle the queued task with the declared outcome and route its evaluation")
        void shouldSettleDeclaredOutcome(String outcome, String nextState) {
            var runner = runner();

            runner.run(ScenarioScript.read(reviewed(outcome, """
                    assert_task_outcome reviewer %s
                    assert_state %s
                    """.formatted(outcome, nextState))));

            assertEquals(4, runner.snapshots().size(), "the replay kept one snapshot per step");
        }

        @Test
        @DisplayName("Should fail a task whose role has no declared worker, naming the role")
        void shouldFailTaskWithoutWorker() {
            var failure = failure("""
                    scenario no-worker
                    start plan scenario-plan
                    complete_job b-0000000000000001 {"outcome":"succeeded"}
                    assert_outcome merged
                    """);

            assertEquals("line 4: the task qt-0000000000000001 of role reviewer is queued in 'execute.review', but "
                    + "the script declares no worker of the role", failure.getMessage(),
                    "the refusal names the role without a worker");
        }

        @Test
        @DisplayName("Should fail a task outcome the fixture evaluation does not evaluate, naming the role")
        void shouldFailAskedTask() {
            var failure = failure(reviewed("asked", "assert_task_outcome reviewer asked"));

            assertAll(
                    () -> assertEquals(5, failure.line(), "the failure names the directive's line"),
                    () -> assertTrue(failure.getMessage().contains("the worker of role reviewer"),
                            "the failure names the reviewer's worker"));
        }

        @Test
        @DisplayName("Should fail a declared outcome no task took, at the declaration's line and naming the role")
        void shouldFailSurplusOutcome() throws Exception {
            var surplus = failure(reviewed("submitted lost", "assert_task_outcome reviewer submitted"));
            var unused = failure("""
                    scenario unused-role
                    worker reviewer submitted
                    worker planner submitted
                    start plan scenario-plan
                    complete_job b-0000000000000001 {"outcome":"succeeded"}
                    assert_task_outcome reviewer submitted
                    """, Files.createDirectory(directory.resolve(Generators.letterStrings(4, 10).next())));

            assertAll(
                    () -> assertEquals("line 2: the worker of role reviewer has task outcomes no task took: [lost]",
                            surplus.getMessage(), "the surplus outcome fails at the declaration line"),
                    () -> assertEquals("line 3: the worker of role planner has task outcomes no task took: "
                            + "[submitted]", unused.getMessage(),
                            "the unused role's outcome fails at its line"));
        }
    }

    @Nested
    @DisplayName("Operator acts")
    class OperatorActs {

        private static final String OPERATOR_SCRIPT = """
                scenario operator-act
                start plan scenario-operator
                assert_state execute.wait
                operator_do proceed web
                assert_state completed
                assert_outcome merged
                """;

        private ScenarioReplayRunner operatorRunner(Path directory) {
            return new ScenarioReplayRunner(ScenarioFixtures.operatorUnit(), directory);
        }

        @Test
        @DisplayName("Should take the operator act of an operator wait and reach the state its link leads to")
        void shouldTakeTheOperatorAct() {
            var runner = operatorRunner(directory);

            runner.run(ScenarioScript.read(OPERATOR_SCRIPT));

            var stored = new WorkflowStatusStore(ScenarioFixtures.scope(directory, JournalScopeType.PLAN,
                    "scenario-operator"));
            assertAll(
                    () -> assertEquals("completed", stored.read().orElseThrow().state(),
                            "the operator act reached the merged terminal"),
                    () -> assertEquals(List.of(2, 3, 4, 5, 6),
                            runner.snapshots().stream().map(ScenarioReplayRunner.Snapshot::line).toList(),
                            "the operator steps kept their line order"),
                    () -> assertArrayEquals(Files.readAllBytes(stored.store()), runner.snapshots().getLast().status(),
                            "the status bytes match the last snapshot"));
        }

        @Test
        @DisplayName("Should give a link of the operator wait the parameters the script names")
        void shouldGiveTheLinkItsParameters() {
            var note = Generators.letterStrings(1, 8).next();
            var runner = operatorRunner(directory);

            runner.run(ScenarioScript.read("""
                    scenario operator-parameters
                    start plan scenario-operator
                    operator_do choose cli {"note":"%s"}
                    assert_state completed
                    """.formatted(note)));

            var stored = new WorkflowStatusStore(ScenarioFixtures.scope(directory, JournalScopeType.PLAN,
                    "scenario-operator"));
            assertAll(
                    () -> assertEquals("completed", stored.read().orElseThrow().state(),
                            "the act reached the operator's terminal state"),
                    () -> assertEquals(List.of(note), runner.fixtures().note.notes(),
                            "the primitive received the parameter the script named"));
        }

        @Test
        @DisplayName("Should refuse an act the resting operator wait does not offer, at the step's line")
        void shouldFailAnActTheStateDoesNotOffer() {
            var unknown = "z" + Generators.letterStrings(4, 10).next();
            var runner = operatorRunner(directory);
            var script = ScenarioScript.read("""
                    scenario operator-unknown-link
                    start plan scenario-operator
                    operator_do %s web
                    """.formatted(unknown));

            var failure = assertThrows(ScenarioFailure.class, () -> runner.run(script),
                    "an act the resting wait does not offer is refused");

            assertAll(
                    () -> assertEquals(3, failure.line(), "the failure names the act's line"),
                    () -> assertTrue(failure.getMessage().contains("offers no link '" + unknown + "'"),
                            "the failure names the link not offered"));
        }

        @Test
        @DisplayName("Should refuse a link whose declared free parameter the act omits, at the step's line")
        void shouldFailAnActWithoutItsFreeParameter() {
            var otherName = Generators.letterStrings(5, 10).next();
            var otherValue = Generators.letterStrings(1, 8).next();
            var runner = operatorRunner(directory);
            var script = ScenarioScript.read("""
                    scenario operator-missing-parameter
                    start plan scenario-operator
                    operator_do choose cli {"%s":"%s"}
                    """.formatted(otherName, otherValue));

            var failure = assertThrows(ScenarioFailure.class, () -> runner.run(script),
                    "an act that omits the free parameter is refused");

            assertAll(
                    () -> assertEquals(3, failure.line(), "the failure carries the act's line"),
                    () -> assertTrue(failure.getMessage().contains("free parameter 'note'"),
                            "the failure names the free parameter note"));
        }

        @Test
        @DisplayName("Should replay an operator script twice to the same bytes after every step")
        void shouldReplayAnOperatorScriptToTheSameBytes() throws Exception {
            var secondDirectory = Generators.letterStrings(4, 10).next();
            var first = operatorRunner(directory);
            var second = operatorRunner(Files.createDirectory(directory.resolve(secondDirectory)));

            first.run(ScenarioScript.read(OPERATOR_SCRIPT));
            second.run(ScenarioScript.read(OPERATOR_SCRIPT));

            assertEquals(first.snapshots(), second.snapshots(), "both runs replay to the same snapshots");
        }
    }

    @Nested
    @DisplayName("Stored bytes")
    class StoredBytes {
        @Test
        @DisplayName("Should keep the stored status bytes after every step, unchanged by an assertion")
        void shouldKeepBytesAfterEveryStep() {
            var runner = runner();

            runner.run(ScenarioScript.read(EVERY_DIRECTIVE));

            var snapshots = runner.snapshots();
            var started = snapshots.get(0).status();
            var asserted = snapshots.get(1).status();
            var settledJob = snapshots.get(2).status();
            assertAll(
                    () -> assertTrue(started.length > 0, "the first step stored non-empty status bytes"),
                    () -> assertArrayEquals(started, asserted, "assert_state writes nothing"),
                    () -> assertFalse(Arrays.equals(asserted, settledJob), "complete_job writes the next state"),
                    () -> assertTrue(new String(settledJob, StandardCharsets.UTF_8).contains("execute.review"),
                            "the settled job stores the review state"));
        }
    }

    @Nested
    @DisplayName("Protocol worker")
    class ProtocolWorker {

        /**
         * The protocol worker's script: the task is offered to its generation, acknowledged, submitted, and the closed
         * outcome is read from the queue record.
         */
        private static final String SCRIPT = """
                scenario protocol-worker
                start plan scenario-plan
                complete_job b-0000000000000001 {"outcome":"succeeded"}
                assert_state execute.review
                worker_wait reviewer
                assert_worker_state reviewer offered
                worker_do ack {}
                assert_worker_state reviewer acknowledged
                worker_do reviewer submit:change {}
                assert_worker_state reviewer closed
                assert_task_outcome reviewer submitted
                assert_state execute.ask
                answer_question Q-001 web {"branch_key":"approve"}
                assert_state execute.confirm
                answer_question Q-002 web {"branch_key":"answered"}
                assert_state completed
                assert_outcome merged
                """;

        /** The script whose first call is refused and asserted, and whose late call of the closed task is asserted. */
        private static final String REFUSED = """
                scenario refused
                start plan scenario-plan
                complete_job b-0000000000000001 {"outcome":"succeeded"}
                worker_wait reviewer
                worker_do reviewer submit:change {}
                assert_refused link_not_available
                worker_do reviewer ack {}
                worker_do reviewer submit:change {}
                worker_do reviewer ack {}
                assert_refused stale_entity
                assert_worker_state reviewer closed
                assert_task_outcome reviewer submitted
                assert_state execute.ask
                """;

        @Test
        @DisplayName("Should wait, acknowledge, submit and read the closed outcome from the queue record")
        void shouldRunTheProtocolWorker() {
            var runner = runner();

            runner.run(ScenarioScript.read(SCRIPT));

            var stored = new WorkflowStatusStore(ScenarioFixtures.scope(directory, JournalScopeType.PLAN, PLAN_ID));
            var task = runner.fixtures().queue.read("qt-0000000000000001").orElseThrow();
            assertAll(
                    () -> assertEquals("completed", stored.read().orElseThrow().state(),
                            "the approved branch completed"),
                    () -> assertEquals(1, task.attempts().size(), "the task was leased once"),
                    () -> assertEquals(Status.CLOSED, task.status(), "the task record is closed"),
                    () -> assertEquals(Outcome.SUBMITTED, task.outcome(), "the closed outcome is submitted"),
                    () -> assertEquals(AttemptEnd.CLOSED, task.currentAttempt().orElseThrow().end(),
                            "the attempt ended closed"),
                    () -> assertEquals("l-0000000000000001",
                            task.currentAttempt().orElseThrow().generationId(),
                            "the task was leased to the worker's generation"));
        }

        @Test
        @DisplayName("Should fail a call the script expected accepted, at the step's line")
        void shouldFailACallTheScriptExpectedAccepted() {
            var failure = failure("""
                    scenario no-ack
                    start plan scenario-plan
                    complete_job b-0000000000000001 {"outcome":"succeeded"}
                    worker_wait reviewer
                    worker_do reviewer submit:change {}
                    """);

            assertAll(
                    () -> assertEquals(5, failure.line(), "the failure names the submit's line"),
                    () -> assertEquals("line 5: the call was refused 'link_not_available'", failure.getMessage(),
                            "the refusal names the wire reason"));
        }

        @Test
        @DisplayName("Should assert the refusal of the last call, of an offer and of a closed task")
        void shouldAssertRefusalsAndStates() {
            var runner = runner();

            runner.run(ScenarioScript.read(REFUSED));

            var stored = new WorkflowStatusStore(ScenarioFixtures.scope(directory, JournalScopeType.PLAN, PLAN_ID));
            assertEquals("execute.ask", stored.read().orElseThrow().state(), "the submit took the approved branch");
        }

        @Test
        @DisplayName("Should fail assert_refused at its line when the call was refused with another reason")
        void shouldFailAnAssertRefusedOnAnotherReason() {
            var failure = failure("""
                    scenario wrong-refusal
                    start plan scenario-plan
                    complete_job b-0000000000000001 {"outcome":"succeeded"}
                    worker_wait reviewer
                    worker_do reviewer ack {}
                    worker_do reviewer ack {}
                    assert_refused stale_entity
                    """);

            assertAll(
                    () -> assertEquals(7, failure.line(), "the failure names the assertion's line"),
                    () -> assertEquals("line 7: assert_refused expected 'stale_entity' but the call was refused "
                            + "'link_not_available'", failure.getMessage(), "the refusal names both reasons"));
        }

        @Test
        @DisplayName("Should fail assert_worker_state at its line when the task rests in another state")
        void shouldFailAnAssertWorkerStateOnAnotherState() {
            var failure = failure("""
                    scenario wrong-state
                    start plan scenario-plan
                    complete_job b-0000000000000001 {"outcome":"succeeded"}
                    worker_wait reviewer
                    assert_worker_state reviewer acknowledged
                    """);

            assertAll(
                    () -> assertEquals(5, failure.line(), "the failure names the assertion's line"),
                    () -> assertEquals("line 5: assert_worker_state expected 'acknowledged' for role reviewer but its "
                            + "task qt-0000000000000001 is 'offered'", failure.getMessage(),
                            "the message names both states"));
        }

        @Test
        @DisplayName("Should fail a wait the protocol answers with no task, naming the answer")
        void shouldFailAWaitWithoutAnOffer() {
            var failure = failure("""
                    scenario no-offer
                    start plan scenario-plan
                    complete_job b-0000000000000001 {"outcome":"succeeded"}
                    worker_wait reviewer
                    worker_do reviewer ack {}
                    worker_do reviewer submit:change {}
                    worker_wait reviewer
                    """);

            assertAll(
                    () -> assertEquals(7, failure.line(), "the failure names the wait's line"),
                    () -> assertEquals("line 7: worker_wait of role reviewer expected an offer but the protocol "
                            + "answered 'no_task', an empty window", failure.getMessage(),
                            "the answer names the reason"));
        }

        @Test
        @DisplayName("Should close the task infeasible and route the rejected branch")
        void shouldCloseTheTaskInfeasible() {
            var runner = runner();

            runner.run(ScenarioScript.read("""
                    scenario infeasible
                    start plan scenario-plan
                    complete_job b-0000000000000001 {"outcome":"succeeded"}
                    worker_wait reviewer
                    worker_do ack {}
                    worker_do reviewer infeasible
                    assert_task_outcome reviewer infeasible
                    assert_state handed_back
                    assert_outcome handed_back
                    """));

            var stored = new WorkflowStatusStore(ScenarioFixtures.scope(directory, JournalScopeType.PLAN, PLAN_ID));
            assertAll(
                    () -> assertEquals("handed_back", stored.read().orElseThrow().state(),
                            "the rejected branch handed the plan back"),
                    () -> assertEquals(Status.CLOSED,
                            runner.fixtures().queue.read("qt-0000000000000001").orElseThrow().status(),
                            "the task record is closed"));
        }
    }

    @Nested
    @DisplayName("Worker faults")
    class WorkerFaults {

        /**
         * The queue task id of the fixture unit's one task state: a wire value of the replay, read from the fixture
         * creator's counter, which is the contract of every assertion on the record.
         */
        private static final String TASK_ID = "qt-0000000000000001";

        private ScenarioReplayRunner replayed(String text) {
            var runner = runner();
            runner.run(ScenarioScript.read(text));
            return runner;
        }

        private QueueTaskRecord task(ScenarioReplayRunner runner) {
            return runner.fixtures().queue.read(TASK_ID).orElseThrow();
        }

        private static List<Attempt> attempts(QueueTaskRecord task) {
            return task.attempts();
        }

        private static long closedAttempts(QueueTaskRecord task) {
            return attempts(task).stream().filter(attempt -> attempt.end() == AttemptEnd.CLOSED).count();
        }

        @Test
        @DisplayName("Should detect a worker lost between its offer and its acknowledgement at the acknowledgement deadline")
        void shouldDetectALossBeforeTheAcknowledgement() {
            var runner = replayed(WorkerFaultScenarios.KILL_BEFORE_ACK);
            var task = task(runner);
            var lost = attempts(task).getFirst();

            assertAll(
                    () -> assertEquals(Outcome.SUBMITTED, task.outcome(), "the replacing worker submitted once"),
                    () -> assertEquals(2, attempts(task).size(), "the task was offered to two generations"),
                    () -> assertEquals(AttemptEnd.WORKER_LOST, lost.end(), "the offer's acknowledgement was missed"),
                    () -> assertEquals(lost.offeredAt().plusSeconds(ScenarioFixtures.ACK_DEADLINE_SECONDS),
                            lost.endedAt(), "the loss is at the acknowledgement deadline, not before"),
                    () -> assertNotEquals(lost.generationId(), attempts(task).get(1).generationId(),
                            "the replacing worker is another generation"),
                    () -> assertEquals(1, closedAttempts(task), "exactly one attempt closed the task"),
                    () -> assertTrue(runner.fixtures().fences.isFenced(lost.generationId()),
                            "the lost generation is fenced"));
        }

        @Test
        @DisplayName("Should detect a worker lost between its acknowledgement and its submit at the silence limit")
        void shouldDetectALossAfterTheAcknowledgement() {
            var runner = replayed(WorkerFaultScenarios.KILL_AFTER_ACK);
            var task = task(runner);
            var lost = attempts(task).getFirst();

            assertAll(
                    () -> assertEquals(Outcome.SUBMITTED, task.outcome(), "the replacing worker submitted once"),
                    () -> assertEquals(2, attempts(task).size(), "the task was offered to two generations"),
                    () -> assertEquals(AttemptEnd.WORKER_LOST, lost.end(), "the acknowledged worker went silent"),
                    () -> assertEquals(lost.acknowledgedAt().plusSeconds(ScenarioFixtures.SILENCE_LIMIT_SECONDS),
                            lost.endedAt(), "the loss is detected at the silence limit, never before"),
                    () -> assertEquals(1, closedAttempts(task), "exactly one attempt closed the task"));
        }

        @Test
        @DisplayName("Should keep a worker that stalls less than its silence limit, with no re-offer spent")
        void shouldKeepAWorkerThatStallsLessThanTheLimit() {
            var runner = replayed(WorkerFaultScenarios.STALL_SHORT);
            var task = task(runner);
            var only = attempts(task).getFirst();

            assertAll(
                    () -> assertEquals(Outcome.SUBMITTED, task.outcome(), "the same worker submitted the task"),
                    () -> assertEquals(1, attempts(task).size(), "no re-offer was spent"),
                    () -> assertEquals(0, task.spentReOffers(), "the attempt did not count as a re-offer"),
                    () -> assertEquals(AttemptEnd.CLOSED, only.end(), "the attempt closed the task"),
                    () -> assertEquals(only.acknowledgedAt().plusSeconds(ScenarioFixtures.SILENCE_LIMIT_SECONDS / 3
                                    + ScenarioFixtures.STEP.getSeconds()),
                            only.endedAt(), "the submit came after the stall, by the same generation"));
        }

        @Test
        @DisplayName("Should replace a worker that stalls up to its silence limit, exactly at the limit")
        void shouldReplaceAWorkerThatStallsUpToTheLimit() {
            var runner = replayed(WorkerFaultScenarios.STALL_LONG);
            var task = task(runner);
            var lost = attempts(task).getFirst();

            assertAll(
                    () -> assertEquals(Outcome.SUBMITTED, task.outcome(), "the replacing worker submitted once"),
                    () -> assertEquals(2, attempts(task).size(), "the task was offered to two generations"),
                    () -> assertEquals(AttemptEnd.WORKER_LOST, lost.end(), "the stalled worker was lost"),
                    () -> assertEquals(lost.acknowledgedAt().plusSeconds(ScenarioFixtures.SILENCE_LIMIT_SECONDS),
                            lost.endedAt(), "the loss is detected at the silence limit, never before"),
                    () -> assertEquals(1, closedAttempts(task), "exactly one attempt closed the task"));
        }

        @Test
        @DisplayName("Should detect a worker that ends its turn without a submit as the exit after its first call")
        void shouldDetectAWorkerThatEndsItsTurn() {
            var runner = replayed(WorkerFaultScenarios.END_TURN);
            var task = task(runner);
            var lost = attempts(task).getFirst();

            assertAll(
                    () -> assertEquals(Outcome.SUBMITTED, task.outcome(), "the replacing worker submitted once"),
                    () -> assertEquals(2, attempts(task).size(), "the task was offered to two generations"),
                    () -> assertEquals(AttemptEnd.EXITED_WITHOUT_SUBMIT, lost.end(), "the worker exited without a submit"),
                    () -> assertEquals(lost.acknowledgedAt().plusSeconds(ScenarioFixtures.STEP.getSeconds()),
                            lost.endedAt(), "the exit is observed at once, at the step's instant"),
                    () -> assertEquals(1, closedAttempts(task), "exactly one attempt closed the task"));
        }

        @Test
        @DisplayName("Should detect a relay killed under a held wait a silence limit after the relay's death")
        void shouldDetectARelayKilledUnderAHeldWait() {
            var runner = replayed(WorkerFaultScenarios.KILL_RELAY);
            var task = task(runner);
            var lost = attempts(task).getFirst();

            assertAll(
                    () -> assertEquals(Outcome.SUBMITTED, task.outcome(), "the replacing worker submitted once"),
                    () -> assertEquals(2, attempts(task).size(), "the task was offered to two generations"),
                    () -> assertEquals(AttemptEnd.WORKER_LOST, lost.end(), "the worker whose relay died was lost"),
                    () -> assertEquals(lost.acknowledgedAt().plusSeconds(WorkerFaultScenarios.HELD_WAIT_SECONDS
                                    + ScenarioFixtures.STEP.getSeconds()
                                    + ScenarioFixtures.SILENCE_LIMIT_SECONDS),
                            lost.endedAt(), "the silence counts from the relay's death, at the limit"),
                    () -> assertEquals(1, closedAttempts(task), "exactly one attempt closed the task"));
        }

        @Test
        @DisplayName("Should close a task lost with attempts_spent once its re-offers are spent")
        void shouldCloseTheTaskWhenTheBudgetIsSpent() {
            var runner = replayed(WorkerFaultScenarios.BUDGET_EXHAUSTED);
            var task = task(runner);

            assertAll(
                    () -> assertEquals(Outcome.LOST, task.outcome(), "the task closed lost"),
                    () -> assertEquals(LostReason.ATTEMPTS_SPENT, task.reason(), "the re-offers are spent"),
                    () -> assertEquals(ScenarioFixtures.RE_OFFER_BUDGET + 1, attempts(task).size(),
                            "every lost attempt was offered again"),
                    () -> assertEquals(ScenarioFixtures.RE_OFFER_BUDGET + 1, task.spentReOffers(),
                            "every loss spent one re-offer"),
                    () -> assertEquals(0, closedAttempts(task), "no attempt closed the task"));
        }

        @Test
        @DisplayName("Should release the lease of a lost worker at the fence, before a successor takes the task")
        void shouldReleaseTheLeaseAtTheFence() {
            var runner = runner();
            runner.run(ScenarioScript.read("""
                    scenario lost-and-released
                    start plan scenario-plan
                    complete_job b-0000000000000001 {"outcome":"succeeded"}
                    worker_wait reviewer
                    worker_do reviewer ack {}
                    kill_worker reviewer
                    """));
            var task = task(runner);

            assertAll(
                    () -> assertEquals(Status.QUEUED, task.status(), "the task waits for a successor"),
                    () -> assertEquals(Optional.of("fenced"), runner.workerStateOf("reviewer"),
                            "the fence's release reads fenced"),
                    () -> assertNull(task.lease(), "the lease was released at the fence"),
                    () -> assertEquals(AttemptEnd.WORKER_LOST, task.currentAttempt().orElseThrow().end(),
                            "the attempt ended with the loss, not with the fence"),
                    () -> assertEquals(1, task.spentReOffers(), "the loss spent one re-offer"),
                    () -> assertTrue(runner.fixtures().fences.isFenced(
                                    task.currentAttempt().orElseThrow().generationId()),
                            "the lost generation is fenced"));
        }

        @Test
        @DisplayName("Should rebuild the runtime over the same stores and keep a lease, a fence and the queue record")
        void shouldSurviveARuntimeRestartWithALeasedTask() {
            var runner = replayed(WorkerFaultScenarios.RESTART_WITH_A_LEASE);
            var task = task(runner);

            assertAll(
                    () -> assertEquals(Outcome.SUBMITTED, task.outcome(), "the one submit closed the task"),
                    () -> assertEquals(1, closedAttempts(task), "the task was closed once"),
                    () -> assertEquals(1, attempts(task).stream()
                                    .map(Attempt::generationId).distinct().count(),
                            "the same generation kept the lease"),
                    () -> assertEquals(AttemptEnd.WAITED_AGAIN, attempts(task).getFirst().end(),
                            "the restarted wait ended the attempt waited_again"),
                    () -> assertTrue(runner.fixtures().fences.fenced().isEmpty(),
                            "an orderly restart writes no fence"));
        }

        @Test
        @DisplayName("Should answer a second generation no_task while the lease of the restarted one is held")
        void shouldAnswerASecondGenerationNoTask() {
            var runner = replayed(WorkerFaultScenarios.RESTART_HELD_BY_ONE_GENERATION);
            var successor = "l-%016x".formatted(2);
            var other = runner.fixtures().protocol().wait(new WaitRequest(successor, "reviewer",
                            ScenarioFixtures.DIGEST, ScenarioFixtures.DIGEST, ScenarioFixtures.RE_OFFER_BUDGET),
                    runner.fixtures().clock.now().plus(ScenarioFixtures.STEP));

            assertAll(
                    () -> assertEquals(Status.OFFERED, task(runner).status(),
                            "the held task is offered to its generation"),
                    () -> assertEquals("l-0000000000000001",
                            task(runner).lease() == null ? null : task(runner).lease().generationId(),
                            "the lease survived the restart"),
                    () -> assertEquals(new WaitAnswer.WaitAgain(WaitAnswer.WaitAgainReason.NO_TASK), other,
                            "a second generation is answered no_task"));
        }

        @Test
        @DisplayName("Should let the successor take the released task after a restart that follows a loss")
        void shouldLetTheSuccessorTakeTheTaskAfterARestart() {
            var runner = replayed(WorkerFaultScenarios.RESTART_AFTER_A_LOSS);
            var task = task(runner);
            var lost = attempts(task).getFirst();

            assertAll(
                    () -> assertEquals(Outcome.SUBMITTED, task.outcome(), "the successor submitted the task once"),
                    () -> assertEquals(2, attempts(task).size(), "the task was offered to two generations"),
                    () -> assertEquals(AttemptEnd.WORKER_LOST, lost.end(), "the first worker was lost"),
                    () -> assertNotEquals(lost.generationId(), attempts(task).get(1).generationId(),
                            "the successor is another generation"),
                    () -> assertTrue(runner.fixtures().fences.isFenced(lost.generationId()),
                            "the fence survived the restart"),
                    () -> assertEquals(1, closedAttempts(task), "exactly one attempt closed the task"));
        }

        @Test
        @DisplayName("Should lose a worker whose stalls add up past its silence limit, at the limit")
        void shouldLoseAWorkerWhoseStallsAddUp() {
            var runner = replayed(WorkerFaultScenarios.STALL_CUMULATIVE);
            var task = task(runner);
            var lost = attempts(task).getFirst();

            assertAll(
                    () -> assertEquals(Outcome.SUBMITTED, task.outcome(), "the replacing worker submitted once"),
                    () -> assertEquals(2, attempts(task).size(), "the task was offered to two generations"),
                    () -> assertEquals(AttemptEnd.WORKER_LOST, lost.end(), "the silent worker was lost"),
                    () -> assertEquals(lost.acknowledgedAt().plusSeconds(ScenarioFixtures.SILENCE_LIMIT_SECONDS),
                            lost.endedAt(), "the silence of both stalls ends at the limit"),
                    () -> assertEquals(1, closedAttempts(task), "exactly one attempt closed the task"));
        }

        @Test
        @DisplayName("Should lose a worker after a restart that follows its acknowledgement")
        void shouldLoseAWorkerAfterARestart() {
            var runner = replayed(WorkerFaultScenarios.RESTART_THEN_LOST);
            var task = task(runner);
            var lost = attempts(task).getFirst();

            assertAll(
                    () -> assertEquals(AttemptEnd.WORKER_LOST, lost.end(), "the restarted worker was lost"),
                    () -> assertEquals(lost.acknowledgedAt().plusSeconds(ScenarioFixtures.SILENCE_LIMIT_SECONDS),
                            lost.endedAt(), "the restart kept the instant of the last call"),
                    () -> assertEquals(Status.QUEUED, task.status(), "the task waits for a successor"),
                    () -> assertNull(task.lease(), "the lease was released at the fence"));
        }

        @Test
        @DisplayName("Should refuse a relay killed on a task the worker has not acknowledged")
        void shouldRefuseARelayKilledOnAnOffer() {
            var runner = runner();
            var script = ScenarioScript.read("""
                    scenario relay-before-ack
                    start plan scenario-plan
                    complete_job b-0000000000000001 {"outcome":"succeeded"}
                    worker_wait reviewer
                    kill_relay reviewer
                    """);

            var failure = assertThrows(ScenarioFailure.class, () -> runner.run(script),
                    "a relay killed on an unacknowledged task is refused");

            assertAll(
                    () -> assertEquals(5, failure.line(), "the failure names the fault's line"),
                    () -> assertEquals("line 5: kill_relay acts on a task the worker of role reviewer has "
                            + "acknowledged, but its task qt-0000000000000001 is 'offered'", failure.getMessage(),
                            "the refusal names the role and the state"));
        }

        @Test
        @DisplayName("Should refuse a runtime restart of a scope that was never started")
        void shouldRefuseARestartBeforeTheStart() {
            var runner = runner();
            var script = ScenarioScript.read("""
                    scenario restart-before-start
                    restart_runtime
                    """);

            var failure = assertThrows(ScenarioFailure.class, () -> runner.run(script),
                    "a restart of a scope never started is refused");

            assertAll(
                    () -> assertEquals(2, failure.line(), "the failure names the restart's line"),
                    () -> assertEquals("line 2: the scope has not been started", failure.getMessage(),
                            "the refusal names the missing start"));
        }

        @Test
        @DisplayName("Should refuse a runtime restart of a scope that already ended")
        void shouldRefuseARestartAfterTheEnd() {
            var runner = runner();
            var script = ScenarioScript.read("""
                    scenario restart-after-the-end
                    worker reviewer submitted
                    start plan scenario-plan
                    complete_job b-0000000000000001 {"outcome":"succeeded"}
                    assert_task_outcome reviewer submitted
                    answer_question Q-001 web {"branch_key":"approve"}
                    answer_question Q-002 web {"branch_key":"answered"}
                    assert_outcome merged
                    restart_runtime
                    """);

            var failure = assertThrows(ScenarioFailure.class, () -> runner.run(script),
                    "a restart of an ended scope is refused");

            assertAll(
                    () -> assertEquals(9, failure.line(), "the failure names the restart's line"),
                    () -> assertEquals("line 9: restart_runtime has nothing to continue: the scope ended with 'merged'",
                            failure.getMessage(), "the refusal names the ended outcome"));
        }

        @Test
        @DisplayName("Should refuse a late call of a generation lost before a restart")
        void shouldRefuseALateCallAfterARestart() {
            var runner = replayed(WorkerFaultScenarios.RESTART_AFTER_A_LOSS_THEN_A_LATE_CALL);
            var task = task(runner);
            var lost = attempts(task).getFirst();

            assertAll(
                    () -> assertEquals(Outcome.SUBMITTED, task.outcome(), "the successor submitted the task once"),
                    () -> assertEquals(2, attempts(task).size(), "the task was offered to two generations"),
                    () -> assertEquals(AttemptEnd.WORKER_LOST, lost.end(), "the first worker was lost"),
                    () -> assertTrue(runner.fixtures().fences.isFenced(lost.generationId()),
                            "the fence survived the restart"),
                    () -> assertEquals(1, closedAttempts(task), "exactly one attempt closed the task"));
        }

        @Test
        @DisplayName("Should refuse a fault on a consultation task, naming the role and the kind")
        void shouldRefuseAFaultOnAConsultationTask() {
            var runner = new ScenarioReplayRunner(ScenarioFixtures.consultUnit(), directory);
            var script = ScenarioScript.read("""
                    scenario fault-on-a-consultation
                    start plan scenario-ask
                    worker_wait author
                    worker_do author ack {}
                    worker_do author consult:reviewer {"question":"is the change safe?"}
                    worker_wait reviewer
                    kill_worker reviewer
                    """);

            var failure = assertThrows(ScenarioFailure.class, () -> runner.run(script),
                    "a fault on a consultation task is refused");

            assertAll(
                    () -> assertEquals(7, failure.line(), "the failure names the fault's line"),
                    () -> assertTrue(failure.getMessage().contains("is a consultation"),
                            "the failure says the task is a consultation"),
                    () -> assertTrue(failure.getMessage().contains("role reviewer"),
                            "the failure names the reviewer role"));
        }

        @Test
        @DisplayName("Should measure the silence of a consulting worker from its consultation")
        void shouldCountAConsultationAsACall() {
            var runner = new ScenarioReplayRunner(ScenarioFixtures.consultUnit(), directory);
            runner.run(ScenarioScript.read("""
                    scenario loss-after-a-consultation
                    start plan scenario-ask
                    worker_wait author
                    worker_do author ack {}
                    worker_do author consult:reviewer {"question":"is the change safe?"}
                    kill_worker author
                    assert_worker_state author fenced
                    """));
            var lost = runner.fixtures().queue.records().stream()
                    .filter(task -> task.kind() == QueueTaskRecord.Kind.WORK)
                    .findFirst().orElseThrow().attempts().getFirst();

            assertEquals(lost.acknowledgedAt().plusSeconds(ScenarioFixtures.STEP.getSeconds()
                            + ScenarioFixtures.SILENCE_LIMIT_SECONDS),
                    lost.endedAt(), "the consultation is the worker's last call");
        }

        @Test
        @DisplayName("Should free a leased task on a pause at once without spending a re-offer")
        void shouldFreeTheLeaseOnAPauseAtOnce() {
            var runner = runner();
            runner.run(ScenarioScript.read("""
                    scenario pause-at-once
                    start plan scenario-plan
                    complete_job b-0000000000000001 {"outcome":"succeeded"}
                    worker_wait reviewer
                    worker_do reviewer ack {}
                    """));

            var ended = runner.fixtures().protocol().pausePlan(PLAN_ID,
                    runner.fixtures().clock.now().plus(ScenarioFixtures.STEP));
            var task = task(runner);

            assertAll(
                    () -> assertEquals(1, ended, "the pause ended the one open attempt"),
                    () -> assertEquals(Optional.of("queued"), runner.workerStateOf("reviewer"),
                            "the release the fence did not cause reads queued"),
                    () -> assertEquals(Status.QUEUED, task.status(), "the task waits for a successor"),
                    () -> assertNull(task.lease(), "the pause released the lease"),
                    () -> assertEquals(AttemptEnd.PAUSED, task.currentAttempt().orElseThrow().end(),
                            "the attempt ended paused"),
                    () -> assertEquals(0, task.spentReOffers(), "a pause at once spends no re-offer"));
        }
    }

    @Nested
    @DisplayName("Consultation")
    class Consultation {

        /** The asker consults the reviewer, the reviewer answers, and the asker's submit omits the reference. */
        private static final String SCRIPT = """
                scenario consultation
                start plan scenario-ask
                assert_state execute.review
                worker_wait author
                worker_do author ack {}
                worker_do author consult:reviewer {"question":"is the change safe?"}
                worker_wait reviewer
                worker_do reviewer ack {}
                worker_do reviewer submit:answer {"answer":"yes"}
                worker_wait author
                worker_do author submit:change {}
                """;

        private static final String ANSWERED = """
                scenario consultation-answered
                start plan scenario-ask
                worker_wait author
                worker_do author ack {}
                worker_do author consult:reviewer {"question":"is the change safe?"}
                worker_wait reviewer
                worker_do reviewer ack {}
                worker_do reviewer submit:answer {"answer":"yes"}
                worker_wait author
                """;

        private ScenarioReplayRunner consultRunner() {
            return new ScenarioReplayRunner(ScenarioFixtures.consultUnit(), directory);
        }

        @Test
        @DisplayName("Should refuse the submit that omits the delivered consultation reference")
        void shouldRefuseTheSubmitWithoutTheReference() {
            var runner = consultRunner();
            var script = ScenarioScript.read(SCRIPT);

            var failure = assertThrows(ScenarioFailure.class, () -> runner.run(script),
                    "the submit without its consultation id is refused");

            assertAll(
                    () -> assertEquals(11, failure.line(), "the failure names the submit's line"),
                    () -> assertTrue(failure.getMessage().startsWith(
                                    "line 11: the submit was refused: the submit omits the delivered consultation_id "),
                            "the refusal names the missing consultation_id"));
        }

        @Test
        @DisplayName("Should re-offer the task at the asker's next wait and accept the submit that names the reference")
        void shouldAcceptTheSubmitThatNamesTheReference() {
            var runner = consultRunner();

            runner.run(ScenarioScript.read(ANSWERED));

            var queue = runner.fixtures().queue;
            var asker = queue.records().stream()
                    .filter(task -> task.kind() == QueueTaskRecord.Kind.WORK).findFirst().orElseThrow();
            var consultation = asker.consultations().getFirst();
            var service = new ConsultationService(queue);
            assertAll(
                    () -> assertEquals(QueueTaskRecord.ConsultationStatus.ANSWERED, consultation.status(),
                            "the consultation is answered"),
                    () -> assertEquals(QueueTaskRecord.DeliveredTo.WAIT, consultation.deliveredTo(),
                            "the answer is delivered to the asker's wait"),
                    () -> assertEquals(Status.OFFERED, asker.status(),
                            "the asker's next wait re-offers the task"),
                    () -> assertEquals("l-0000000000000001",
                            asker.lease() == null ? null : asker.lease().generationId(),
                            "the task stays leased to the asking generation"),
                    () -> assertInstanceOf(SubmitValidation.Accepted.class,
                            service.validateSubmit(asker.queueTaskId(), List.of(consultation.consultationId())),
                            "the submit that names the reference is accepted"),
                    () -> assertInstanceOf(SubmitValidation.Refused.class,
                            service.validateSubmit(asker.queueTaskId(), List.of()),
                            "the submit that omits the reference is refused"));
        }
    }
}
