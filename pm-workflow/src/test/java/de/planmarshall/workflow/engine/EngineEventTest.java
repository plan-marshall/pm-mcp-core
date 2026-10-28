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
import java.util.Optional;
import java.util.stream.Stream;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

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
        return engine.advance(atReview(), new EngineEvent.TasksSettled(Map.of("change", "submitted"), "approved"))
                .status();
    }

    private WorkflowStatus atBackoff() {
        fixtures.questionReader.answer = new QuestionRead.Resolved("answered");
        return engine.advance(atQuestion(), new EngineEvent.QuestionResolved("Q-001", "answered")).status();
    }

    static Stream<EngineEvent> foreignEventsOfJob() {
        return Stream.of(new EngineEvent.JobSettled("b-00000000000000ff", "succeeded"),
                new EngineEvent.TasksSettled(Map.of("change", "submitted"), "approved"),
                new EngineEvent.QuestionResolved("Q-001", "answered"),
                new EngineEvent.TimeElapsed(T0));
    }

    @Nested
    @DisplayName("Job")
    class Job {

        @Test
        @DisplayName("Should bind the job a job-starting outcome reports, without starting another")
        void shouldBindSpawnedJob() {
            fixtures.clock.tick(Duration.ofSeconds(2));

            var status = atJob();

            assertAll(
                    () -> assertEquals("execute.run", status.state()),
                    () -> assertEquals(new WorkflowStatus.ActiveJob("execute.run", SPAWNED),
                            status.activeJobBinding().orElseThrow()),
                    () -> assertEquals(Map.of("test.fate", new WorkflowStatus.JobEntry(SPAWNED, "git", "queued",
                            T0.plusSeconds(2))), status.activeJobs()),
                    () -> assertTrue(fixtures.jobStarter.requests.isEmpty()));
        }

        @Test
        @DisplayName("Should start the job of a job state entered without one, and route a refused start")
        void shouldStartOrRouteRefusal() {
            var startEngine = fixtures.engine(plan("execute.build", """
                    state execute.build
                        phase execute
                        job test.fate
                            succeeded -> completed
                            killed -> handed_back
                    %s""".formatted(OTHER_FATES)));

            var started = startEngine.start(JournalScopeType.PLAN, PLAN_ID).status();
            fixtures.jobStarter.refusing = true;
            var refused = startEngine.start(JournalScopeType.PLAN, PLAN_ID);

            assertAll(
                    () -> assertEquals("b-0000000000000001", started.activeJobBinding().orElseThrow().jobId()),
                    () -> assertEquals(new JobStarter.StartRequest(Optional.of(PLAN_ID), "execute.build", "test.fate"),
                            fixtures.jobStarter.requests.getFirst()),
                    () -> assertEquals("handed_back", assertInstanceOf(EngineStep.Ended.class, refused)
                            .status().state()));
        }

        @Test
        @DisplayName("Should resume by the settled job and clear its binding in the transition that leaves the state")
        void shouldResumeBySettledJob() {
            var resumed = engine.advance(atJob(), new EngineEvent.JobSettled(SPAWNED, "succeeded"));

            var status = resumed.status();
            assertAll(
                    () -> assertEquals("execute.review", status.state()),
                    () -> assertTrue(status.activeJobs().isEmpty()),
                    () -> assertEquals("execute.review", status.activeJobBinding().orElseThrow().jobId()));
        }

        @Test
        @DisplayName("Should settle a job recorded killed with killed and never start it again")
        void shouldSettleKilledJob() {
            var atJob = atJob();

            var waited = engine.advance(atJob);
            var settled = engine.advance(atJob, new EngineEvent.JobSettled(SPAWNED, "killed"));

            assertAll(
                    () -> assertSame(atJob, waited.status()),
                    () -> assertEquals("handed_back", assertInstanceOf(EngineStep.Ended.class, settled)
                            .status().state()),
                    () -> assertTrue(fixtures.jobStarter.requests.isEmpty()));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.workflow.engine.EngineEventTest#foreignEventsOfJob")
        @DisplayName("Should refuse every event but the settlement of its own job")
        void shouldRefuseForeignEvent(EngineEvent event) {
            var atJob = atJob();

            assertThrows(IllegalArgumentException.class, () -> engine.advance(atJob, event));
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
                    () -> assertEquals("execute.review", status.state()),
                    () -> assertEquals(Map.of("change", new WorkflowStatus.TaskEntry("qt-0000000000000001",
                            "reviewer", "queued", null, null)), status.activeTasks()),
                    () -> assertEquals(1, fixtures.taskCreator.requests.size()),
                    () -> assertEquals(AwaitedEvent.TASK_SETTLED,
                            engine.restingWait(status).orElseThrow().awaitedEvent()));
        }

        @Test
        @DisplayName("Should resume by the settlement of exactly its tasks, routing the evaluated outcome")
        void shouldResumeBySettledTasks() {
            var atReview = atReview();

            var asked = engine.advance(atReview, new EngineEvent.TasksSettled(Map.of("change", "submitted"),
                    "approved"));
            var lost = engine.advance(atReview, new EngineEvent.TasksSettled(Map.of("change", "lost"), "lost"));
            var otherEntity = new EngineEvent.TasksSettled(Map.of("other", "submitted"), "approved");
            var elapsed = new EngineEvent.TimeElapsed(T0);

            assertAll(
                    () -> assertEquals("execute.ask", asked.status().state()),
                    () -> assertTrue(asked.status().activeTasks().isEmpty()),
                    () -> assertEquals("handed_back", lost.status().state()),
                    () -> assertThrows(IllegalArgumentException.class, () -> engine.advance(atReview, otherEntity)),
                    () -> assertThrows(IllegalArgumentException.class, () -> engine.advance(atReview, elapsed)));
        }

        @Test
        @DisplayName("Should refuse to park a fan-out task state, whose entities this engine does not compute")
        void shouldRefuseFanOut() {
            var fanOut = fixtures.engine(plan("execute.review", """
                    state execute.review
                        phase execute
                        job llm change role reviewer each test.step
                            approved -> completed
                            lost -> handed_back
                    """));

            assertThrows(IllegalStateException.class, () -> fanOut.start(JournalScopeType.PLAN, PLAN_ID));
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
                    () -> assertEquals("execute.backoff", status.state()),
                    () -> assertEquals(new WorkflowStatus.ActiveJob("execute.backoff", "execute.backoff"),
                            status.activeJobBinding().orElseThrow()));
        }

        @Test
        @DisplayName("Should refuse a resolution the record does not confirm: still open or another outcome")
        void shouldRefuseUnconfirmedResolution() {
            var atQuestion = atQuestion();
            var event = new EngineEvent.QuestionResolved("Q-001", "answered");

            fixtures.questionReader.answer = new QuestionRead.Open();
            var open = assertThrows(IllegalArgumentException.class, () -> engine.advance(atQuestion, event));
            fixtures.questionReader.answer = new QuestionRead.Resolved("declined");
            var other = assertThrows(IllegalArgumentException.class, () -> engine.advance(atQuestion, event));

            assertAll(
                    () -> assertTrue(open.getMessage().contains("still open")),
                    () -> assertTrue(other.getMessage().contains("declined")));
        }

        @Test
        @DisplayName("Should raise precondition_unmet when the question record cannot be read, never take it for open")
        void shouldRaiseOnUnreadableRecord() {
            var atQuestion = atQuestion();
            fixtures.questionReader.answer = new QuestionRead.Unreadable("record unparseable");

            var raised = engine.advance(atQuestion, new EngineEvent.QuestionResolved("Q-001", "answered"));

            var condition = assertInstanceOf(EngineStep.ConditionRaised.class, raised);
            assertAll(
                    () -> assertEquals("precondition_unmet", condition.kind()),
                    () -> assertEquals("{\"question_id\":\"Q-001\",\"reason\":\"record unparseable\"}",
                            condition.facts()),
                    () -> assertEquals("execute.ask", condition.status().state()),
                    () -> assertSame(condition.status(), engine.advance(condition.status(),
                            new EngineEvent.QuestionResolved("Q-001", "answered")).status()));
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

            var ended = engine.advance(atBackoff, new EngineEvent.TimeElapsed(T0.plusSeconds(30)));
            var resolved = new EngineEvent.QuestionResolved("Q-001", "answered");

            assertAll(
                    () -> assertEquals(AwaitedEvent.TIME, engine.restingWait(atBackoff).orElseThrow().awaitedEvent()),
                    () -> assertEquals("completed", assertInstanceOf(EngineStep.Ended.class, ended).status().state()),
                    () -> assertEquals(1, fixtures.backoff.calls()),
                    () -> assertThrows(IllegalArgumentException.class, () -> engine.advance(atBackoff, resolved)));
        }

        @Test
        @DisplayName("Should report no wait for a state that does not rest")
        void shouldReportNoWaitOutsideRestingState() {
            var elapsed = new EngineEvent.TimeElapsed(T0);
            var ended = engine.advance(atBackoff(), elapsed).status();

            assertAll(
                    () -> assertTrue(engine.restingWait(ended).isEmpty()),
                    () -> assertThrows(IllegalArgumentException.class, () -> engine.advance(ended, elapsed)));
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
                            () -> new EngineEvent.JobSettled("x-0000000000000001", "succeeded")),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new EngineEvent.JobSettled(SPAWNED, "vanished")),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new EngineEvent.TasksSettled(noTasks, "approved")),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new EngineEvent.TasksSettled(unknownTaskOutcome, "approved")),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new EngineEvent.TasksSettled(submitted, " ")),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new EngineEvent.QuestionResolved("Q-1", "answered")),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new EngineEvent.QuestionResolved("Q-001", "")),
                    () -> assertThrows(NullPointerException.class, () -> new EngineEvent.TimeElapsed(null)));
        }
    }
}
