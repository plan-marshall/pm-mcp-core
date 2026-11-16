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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
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
import de.planmarshall.core.queue.QueueTaskRecord.AttemptEnd;
import de.planmarshall.core.queue.QueueTaskRecord.Outcome;
import de.planmarshall.core.queue.QueueTaskRecord.Status;
import de.planmarshall.core.spi.QueueTaskCreator;
import de.planmarshall.core.store.JournalScopeType;
import de.planmarshall.core.store.WorkflowStatusStore;
import de.planmarshall.workflow.consult.ConsultationService;
import de.planmarshall.workflow.consult.SubmitValidation;
import de.planmarshall.workflow.scenario.ScenarioReplayRunner.ScenarioFailure;

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
        return assertThrows(ScenarioFailure.class, () -> runner.run(script));
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
                    () -> assertEquals("completed", stored.read().orElseThrow().state()),
                    () -> assertEquals(List.of(3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13),
                            runner.snapshots().stream().map(ScenarioReplayRunner.Snapshot::line).toList()),
                    () -> assertArrayEquals(Files.readAllBytes(stored.store()), runner.snapshots().getLast().status()),
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
                    () -> assertEquals(3, startedTwice.line()),
                    () -> assertTrue(startedTwice.getMessage().contains("started already"), startedTwice.getMessage()),
                    () -> assertThrows(IllegalStateException.class, () -> runner.run(script)));
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
                    () -> assertEquals(line, failure.line()),
                    () -> assertTrue(failure.getMessage().startsWith("line %d: %s".formatted(line, message)),
                            failure.getMessage()));
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

            var failure = assertThrows(ScenarioFailure.class, () -> runner.run(script));

            assertAll(
                    () -> assertEquals(3, failure.line()),
                    () -> assertTrue(failure.getMessage().contains(message), failure.getMessage()),
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

            assertEquals(4, runner.snapshots().size());
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
                    + "the script declares no worker of the role", failure.getMessage());
        }

        @Test
        @DisplayName("Should fail a task outcome the fixture evaluation does not evaluate, naming the role")
        void shouldFailAskedTask() {
            var failure = failure(reviewed("asked", "assert_task_outcome reviewer asked"));

            assertAll(
                    () -> assertEquals(5, failure.line()),
                    () -> assertTrue(failure.getMessage().contains("the worker of role reviewer"),
                            failure.getMessage()));
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
                            surplus.getMessage()),
                    () -> assertEquals("line 3: the worker of role planner has task outcomes no task took: "
                            + "[submitted]", unused.getMessage()));
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
                    () -> assertEquals("completed", stored.read().orElseThrow().state()),
                    () -> assertEquals(List.of(2, 3, 4, 5, 6),
                            runner.snapshots().stream().map(ScenarioReplayRunner.Snapshot::line).toList()),
                    () -> assertArrayEquals(Files.readAllBytes(stored.store()), runner.snapshots().getLast().status()));
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
                    () -> assertEquals("completed", stored.read().orElseThrow().state()),
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

            var failure = assertThrows(ScenarioFailure.class, () -> runner.run(script));

            assertAll(
                    () -> assertEquals(3, failure.line()),
                    () -> assertTrue(failure.getMessage().contains("offers no link '" + unknown + "'"),
                            failure.getMessage()));
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

            var failure = assertThrows(ScenarioFailure.class, () -> runner.run(script));

            assertAll(
                    () -> assertEquals(3, failure.line()),
                    () -> assertTrue(failure.getMessage().contains("free parameter 'note'"),
                            failure.getMessage()));
        }

        @Test
        @DisplayName("Should replay an operator script twice to the same bytes after every step")
        void shouldReplayAnOperatorScriptToTheSameBytes() throws Exception {
            var secondDirectory = Generators.letterStrings(4, 10).next();
            var first = operatorRunner(directory);
            var second = operatorRunner(Files.createDirectory(directory.resolve(secondDirectory)));

            first.run(ScenarioScript.read(OPERATOR_SCRIPT));
            second.run(ScenarioScript.read(OPERATOR_SCRIPT));

            assertEquals(first.snapshots(), second.snapshots());
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
                    () -> assertTrue(started.length > 0),
                    () -> assertArrayEquals(started, asserted, "assert_state writes nothing"),
                    () -> assertFalse(Arrays.equals(asserted, settledJob), "complete_job writes the next state"),
                    () -> assertTrue(new String(settledJob, StandardCharsets.UTF_8).contains("execute.review")));
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
                    () -> assertEquals("completed", stored.read().orElseThrow().state()),
                    () -> assertEquals(1, task.attempts().size()),
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
                    () -> assertEquals(5, failure.line()),
                    () -> assertEquals("line 5: the call was refused 'link_not_available'", failure.getMessage()));
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
                    () -> assertEquals(7, failure.line()),
                    () -> assertEquals("line 7: assert_refused expected 'stale_entity' but the call was refused "
                            + "'link_not_available'", failure.getMessage()));
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
                    () -> assertEquals(5, failure.line()),
                    () -> assertEquals("line 5: assert_worker_state expected 'acknowledged' for role reviewer but its "
                            + "task qt-0000000000000001 is 'offered'", failure.getMessage()));
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
                    () -> assertEquals(7, failure.line()),
                    () -> assertEquals("line 7: worker_wait of role reviewer expected an offer but the protocol "
                            + "answered 'no_task', an empty window", failure.getMessage()));
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

            var failure = assertThrows(ScenarioFailure.class, () -> runner.run(script));

            assertAll(
                    () -> assertEquals(11, failure.line()),
                    () -> assertTrue(failure.getMessage().startsWith(
                                    "line 11: the submit was refused: the submit omits the delivered consultation_id "),
                            failure.getMessage()));
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
