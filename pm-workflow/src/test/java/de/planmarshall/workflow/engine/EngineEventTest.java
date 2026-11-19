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

import static de.planmarshall.workflow.engine.EngineFixtures.OTHER_FATES;
import static de.planmarshall.workflow.engine.EngineFixtures.PLAN_ID;
import static de.planmarshall.workflow.engine.EngineFixtures.T0;
import static de.planmarshall.workflow.engine.EngineFixtures.WAIT_STATES;
import static de.planmarshall.workflow.engine.EngineFixtures.plan;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Map;
import java.util.stream.Stream;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.planmarshall.core.spi.AwaitedEvent;
import de.planmarshall.core.spi.JobStarter;
import de.planmarshall.core.spi.QuestionRecordReader.QuestionRead;
import de.planmarshall.core.store.JournalScopeType;
import de.planmarshall.core.store.WorkflowStatus;
import de.planmarshall.workflow.engine.EngineFixtures.EnqueueOutcome;
import de.planmarshall.workflow.engine.EngineFixtures.JobOutcome;

/**
 * Wait events (PM-WF-2, PM-ARCH-4): every kind of resting state (a job, a task state, a question state, a composite job
 * that waits for time) parks in the status write that enters it, resumes only by its own event, and refuses every
 * other event without a change; a job recorded {@code killed} settles with {@code killed} and is never started again.
 */
@EnableGeneratorController
@DisplayName("Engine events")
class EngineEventTest {

    private static final String SPAWNED = "g-00000000000000a1";

    private final EngineFixtures fixtures = new EngineFixtures();

    private final WorkflowEngine engine = fixtures.engine(plan("execute.enqueue", WAIT_STATES));

    /** @return the status resting on the job {@code test.enqueue} reported */
    private WorkflowStatus atJob() {
        fixtures.enqueue.thenSpawning(EnqueueOutcome.STARTED, SPAWNED);
        return engine.start(JournalScopeType.PLAN, PLAN_ID).status();
    }

    /** @return the status resting on the task state, entered without a job */
    private WorkflowStatus atReview() {
        fixtures.enqueue.then(EnqueueOutcome.SKIPPED);
        return engine.start(JournalScopeType.PLAN, PLAN_ID).status();
    }

    private WorkflowStatus atQuestion() {
        return engine.advance(atReview(), new EngineEvent.TasksSettled(Map.of("change", "submitted"), Map.of(), "approved"))
                .status();
    }

    private WorkflowStatus atBackoff() {
        fixtures.answerQuestion(new QuestionRead.Resolved("answered"));
        return engine.advance(atQuestion(), new EngineEvent.QuestionResolved("Q-001", "answered")).status();
    }

    static Stream<EngineEvent> foreignEventsOfJob() {
        return Stream.of(new EngineEvent.JobSettled("b-00000000000000ff", "succeeded"),
                new EngineEvent.TasksSettled(Map.of("change", "submitted"), Map.of(), "approved"),
                new EngineEvent.QuestionResolved("Q-001", "answered"),
                new EngineEvent.TimeElapsed(T0));
    }

    @Nested
    @DisplayName("Job")
    class Job {

        @Test
        @DisplayName("Should bind the job a job-starting outcome reports, without starting another")
        void shouldBindSpawnedJob() {
            var tick = Generators.integers(1, 120).next();
            fixtures.clock.tick(Duration.ofSeconds(tick));

            var status = atJob();

            assertAll(
                    () -> assertEquals("execute.run", status.state(), "the job state is execute.run"),
                    () -> assertEquals(new WorkflowStatus.ActiveJob("execute.run", SPAWNED),
                            status.activeJobBinding().orElseThrow(), "the spawned job is bound to the state"),
                    () -> assertEquals(Map.of("test.fate", new WorkflowStatus.JobEntry(SPAWNED, "git", "queued",
                            T0.plusSeconds(tick))), status.activeJobs(), "the job entry is recorded as queued"),
                    () -> assertTrue(fixtures.jobRequests.getValues().isEmpty(),
                            "no job is started for a recorded state"));
        }

        @Test
        @DisplayName("Should start the job of a job state entered without one, and route a refused start")
        void shouldStartOrRouteRefusal() {
            var startEngine = fixtures.engine(plan("execute.build", """
                    state execute.build
                        job test.fate
                            succeeded -> completed
                            killed -> handed_back
                    %s""".formatted(OTHER_FATES)));

            var started = startEngine.start(JournalScopeType.PLAN, PLAN_ID).status();
            fixtures.refusing = true;
            var refused = startEngine.start(JournalScopeType.PLAN, PLAN_ID);

            assertAll(
                    () -> assertEquals("b-0000000000000001", started.activeJobBinding().orElseThrow().jobId(),
                            "the new job id is bound to the start"),
                    () -> assertEquals(new JobStarter.StartRequest(PLAN_ID, "execute.build", "test.fate"),
                            fixtures.jobRequests.getValues().getFirst(),
                            "the start request names the job state"),
                    () -> assertEquals("handed_back", assertInstanceOf(EngineStep.Ended.class, refused,
                                    "the refusal ends the unit").status().state(),
                            "a refused start is handed back"));
        }

        @Test
        @DisplayName("Should resume by the settled job and clear its binding in the transition that leaves the state")
        void shouldResumeBySettledJob() {
            var resumed = engine.advance(atJob(), new EngineEvent.JobSettled(SPAWNED, "succeeded"));

            var status = resumed.status();
            assertAll(
                    () -> assertEquals("execute.review", status.state(), "the job settles into execute.review"),
                    () -> assertTrue(status.activeJobs().isEmpty(), "the settled state holds no active job"),
                    () -> assertEquals("execute.review", status.activeJobBinding().orElseThrow().jobId(),
                            "the state binds itself as the job"));
        }

        @Test
        @DisplayName("Should settle a job recorded killed with killed and never start it again")
        void shouldSettleKilledJob() {
            var atJob = atJob();

            var waited = engine.advance(atJob);
            var settled = engine.advance(atJob, new EngineEvent.JobSettled(SPAWNED, "killed"));

            assertAll(
                    () -> assertSame(atJob, waited.status(), "a recorded killed job is not resumed"),
                    () -> assertEquals("handed_back", assertInstanceOf(EngineStep.Ended.class, settled,
                                    "the killed job ends the unit").status().state(),
                            "a killed job is handed back"),
                    () -> assertTrue(fixtures.jobRequests.getValues().isEmpty(),
                            "no job is started for a recorded state"));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.workflow.engine.EngineEventTest#foreignEventsOfJob")
        @DisplayName("Should refuse every event but the settlement of its own job")
        void shouldRefuseForeignEvent(EngineEvent event) {
            var atJob = atJob();

            assertThrows(IllegalArgumentException.class, () -> engine.advance(atJob, event),
                    "a foreign event is refused");
        }
    }

    @Nested
    @DisplayName("Tasks")
    class Tasks {

        @Test
        @DisplayName("Should create the queue task of a task state and bind the state to itself")
        void shouldParkTaskState() {
            var status = atReview();

            assertAll(
                    () -> assertEquals("execute.review", status.state(), "the task state is execute.review"),
                    () -> assertEquals(Map.of("change", new WorkflowStatus.TaskEntry("qt-0000000000000001",
                                    "reviewer", "queued", null, null)), status.activeTasks(),
                            "one queue task is recorded"),
                    () -> assertEquals(1, fixtures.taskRequests.getValues().size(),
                            "one queue task is created for the state"),
                    () -> assertEquals(AwaitedEvent.TASK_SETTLED,
                            engine.restingWait(status).orElseThrow().awaitedEvent(), "the state awaits the task"));
        }

        @Test
        @DisplayName("Should resume by the settlement of exactly its tasks, routing the evaluated outcome")
        void shouldResumeBySettledTasks() {
            var atReview = atReview();

            var asked = engine.advance(atReview, new EngineEvent.TasksSettled(Map.of("change", "submitted"), Map.of(), "approved"));
            var lost = engine.advance(atReview, new EngineEvent.TasksSettled(Map.of("change", "lost"), Map.of(), "lost"));
            var otherEntity = new EngineEvent.TasksSettled(Map.of("other", "submitted"), Map.of(), "approved");
            var elapsed = new EngineEvent.TimeElapsed(T0);

            assertAll(
                    () -> assertEquals("execute.ask", asked.status().state(), "the approved task reaches execute.ask"),
                    () -> assertTrue(asked.status().activeTasks().isEmpty(), "the settled tasks are cleared"),
                    () -> assertEquals("handed_back", lost.status().state(), "a lost task is handed back"),
                    () -> assertThrows(IllegalArgumentException.class, () -> engine.advance(atReview, otherEntity),
                            "another entity's task is refused"),
                    () -> assertThrows(IllegalArgumentException.class, () -> engine.advance(atReview, elapsed),
                            "an elapsed time event is refused"));
        }

        @Test
        @DisplayName("Should refuse to park a fan-out task state, whose entities this engine does not compute")
        void shouldRefuseFanOut() {
            var fanOut = fixtures.engine(plan("execute.review", """
                    state execute.review
                        job llm change role reviewer each test.step
                            approved -> completed
                            lost -> handed_back
                    """));

            assertThrows(IllegalStateException.class, () -> fanOut.start(JournalScopeType.PLAN, PLAN_ID),
                    "a fan-out task state is refused");
        }
    }

    @Nested
    @DisplayName("Question")
    class Question {

        @Test
        @DisplayName("Should resume by the resolution the question record reports")
        void shouldResumeByResolution() {
            var status = atBackoff();

            assertAll(
                    () -> assertEquals("execute.backoff", status.state(), "the question resumes into execute.backoff"),
                    () -> assertEquals(new WorkflowStatus.ActiveJob("execute.backoff", "execute.backoff"),
                            status.activeJobBinding().orElseThrow(), "the backoff job stays bound"));
        }

        @Test
        @DisplayName("Should refuse a resolution the record does not confirm: still open or another outcome")
        void shouldRefuseUnconfirmedResolution() {
            var atQuestion = atQuestion();
            var event = new EngineEvent.QuestionResolved("Q-001", "answered");

            fixtures.answerQuestion(new QuestionRead.Open());
            var open = assertThrows(IllegalArgumentException.class, () -> engine.advance(atQuestion, event),
                    "an open question is refused");
            var declined = "not-" + Generators.letterStrings(1, 8).next();
            fixtures.answerQuestion(new QuestionRead.Resolved(declined));
            var other = assertThrows(IllegalArgumentException.class, () -> engine.advance(atQuestion, event),
                    "another resolution is refused");

            assertAll(
                    () -> assertTrue(open.getMessage().contains("still open"), "the refusal names the open record"),
                    () -> assertTrue(other.getMessage().contains(declined), "the refusal names the other outcome"));
        }

        @Test
        @DisplayName("Should raise precondition_unmet when the question record cannot be read, never take it for open")
        void shouldRaiseOnUnreadableRecord() {
            var atQuestion = atQuestion();
            var reason = Generators.letterStrings(1, 20).next();
            fixtures.answerQuestion(new QuestionRead.Unreadable(reason));

            var raised = engine.advance(atQuestion, new EngineEvent.QuestionResolved("Q-001", "answered"));

            var condition = assertInstanceOf(EngineStep.ConditionRaised.class, raised,
                    "the advance raises a condition");
            assertAll(
                    () -> assertEquals("precondition_unmet", condition.kind(), "the condition is precondition_unmet"),
                    () -> assertEquals("{\"question_id\":\"Q-001\",\"reason\":\"" + reason + "\"}",
                            condition.facts(), "the facts name the question and reason"),
                    () -> assertEquals("execute.ask", condition.status().state(), "the state stays at execute.ask"),
                    () -> assertSame(condition.status(), engine.advance(condition.status(),
                                    new EngineEvent.QuestionResolved("Q-001", "answered")).status(),
                            "the unreadable record raises again"));
        }
    }

    @Nested
    @DisplayName("Time")
    class Time {

        @Test
        @DisplayName("Should resume a composite job that waits for time by running its primitive again")
        void shouldResumeByElapsedTime() {
            var atBackoff = atBackoff();
            fixtures.backoff.then(JobOutcome.SUCCEEDED);

            var elapsed = Generators.integers(1, 120).next();
            var ended = engine.advance(atBackoff, new EngineEvent.TimeElapsed(T0.plusSeconds(elapsed)));
            var resolved = new EngineEvent.QuestionResolved("Q-001", "answered");

            assertAll(
                    () -> assertEquals(AwaitedEvent.TIME, engine.restingWait(atBackoff).orElseThrow().awaitedEvent(),
                            "the backoff state waits for time"),
                    () -> assertEquals("completed", assertInstanceOf(EngineStep.Ended.class, ended,
                                    "the elapsed time ends the unit").status().state(),
                            "the waited job completes the unit"),
                    () -> assertEquals(1, fixtures.backoff.calls(), "the backoff primitive ran once"),
                    () -> assertThrows(IllegalArgumentException.class, () -> engine.advance(atBackoff, resolved),
                            "a question event is refused in a time wait"));
        }

        @Test
        @DisplayName("Should report no wait for a state that does not rest")
        void shouldReportNoWaitOutsideRestingState() {
            var elapsed = new EngineEvent.TimeElapsed(T0);
            var ended = engine.advance(atBackoff(), elapsed).status();

            assertAll(
                    () -> assertTrue(engine.restingWait(ended).isEmpty(), "an ended status reports no wait"),
                    () -> assertThrows(IllegalArgumentException.class, () -> engine.advance(ended, elapsed),
                            "an ended status refuses further events"));
        }
    }

    @Nested
    @DisplayName("Event values")
    class EventValues {

        @Test
        @DisplayName("Should refuse malformed events")
        void shouldRefuseMalformedEvents() {
            var noTasks = Map.<String, String>of();
            var unknownTaskOutcome = Map.of("change", "done");
            var submitted = Map.of("change", "submitted");

            assertAll(
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new EngineEvent.JobSettled("x-0000000000000001", "succeeded"),
                            "a malformed job id is refused"),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new EngineEvent.JobSettled(SPAWNED, "vanished"),
                            "an unknown job outcome is refused"),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new EngineEvent.TasksSettled(noTasks, Map.of(), "approved"),
                            "an empty task map is refused"),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new EngineEvent.TasksSettled(unknownTaskOutcome, Map.of(), "approved"),
                            "an unknown task outcome is refused"),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new EngineEvent.TasksSettled(submitted, Map.of(), " "),
                            "a blank task outcome is refused"),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new EngineEvent.QuestionResolved("Q-1", "answered"),
                            "a malformed question id is refused"),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new EngineEvent.QuestionResolved("Q-001", ""),
                            "an empty question answer is refused"),
                    () -> assertThrows(NullPointerException.class, () -> new EngineEvent.TimeElapsed(null),
                            "a null event time is refused"));
        }
    }
}
