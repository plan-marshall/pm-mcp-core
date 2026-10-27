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

import de.planmarshall.core.spi.QueueTaskCreator;
import de.planmarshall.core.store.JournalScopeType;
import de.planmarshall.core.store.WorkflowStatusStore;
import de.planmarshall.workflow.scenario.ScenarioReplayRunner.ScenarioFailure;

/**
 * The runner of {@code pm-scenario} (PM-TEST-4): a script replays in process against the real engine over the fixture
 * unit, with the store in a temporary directory and a scripted worker in place of a model; every deviation from the
 * script fails at the step's line with the expected and the actual value, and the stored status bytes are kept after
 * every step.
 */
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
        var runner = runner();
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
                    () -> assertEquals(1, fixtures.jobStarter.requests.size()),
                    () -> assertEquals(List.of(new QueueTaskCreator.CreateRequest(Optional.of(PLAN_ID),
                            "execute.review", "change", "reviewer")), fixtures.taskCreator.requests));
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
        void shouldFailSurplusOutcome() {
            var surplus = failure(reviewed("submitted lost", "assert_task_outcome reviewer submitted"));
            var unused = failure("""
                    scenario unused-role
                    worker reviewer submitted
                    worker planner submitted
                    start plan scenario-plan
                    complete_job b-0000000000000001 {"outcome":"succeeded"}
                    assert_task_outcome reviewer submitted
                    """);

            assertAll(
                    () -> assertEquals("line 2: the worker of role reviewer has task outcomes no task took: [lost]",
                            surplus.getMessage()),
                    () -> assertEquals("line 3: the worker of role planner has task outcomes no task took: "
                            + "[submitted]", unused.getMessage()));
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
}
