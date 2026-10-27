/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.task;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Instant;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.queue.QueueStore;
import de.planmarshall.core.queue.QueueTaskRecord;
import de.planmarshall.core.queue.QueueTaskRecord.AttemptEnd;
import de.planmarshall.core.queue.QueueTaskRecord.Continuation;
import de.planmarshall.core.queue.QueueTaskRecord.ContinuationForm;
import de.planmarshall.core.queue.QueueTaskRecord.FallbackReason;
import de.planmarshall.core.queue.QueueTaskRecord.Outcome;
import de.planmarshall.core.queue.QueueTaskRecord.Status;
import de.planmarshall.workflow.hypermedia.AnswerFact;
import de.planmarshall.workflow.hypermedia.Answers;

/**
 * The continuation of a task after its question in the task protocol (Model Work § Continuing after a Question): the
 * hold, the answer and its acknowledgement, the offer of a resumed suspension, and the fallback.
 */
@DisplayName("Task protocol continuation")
class TaskProtocolContinuationTest {

    private static final String TASK = "qt-0000000000000001";

    private static TaskProtocol protocol(QueueStore queue, TestLocks locks) {
        return TaskProtocol.builder(queue, locks).build();
    }

    private static Continuation continuation(ContinuationForm form, Instant at) {
        return form == ContinuationForm.HOLD
                ? new Continuation("Q-001", form, at.plusSeconds(600), null, null, null, null, null, null)
                : new Continuation("Q-001", form, null, "session-1", at, at.plusSeconds(3600), TaskFixtures.DIGEST,
                "c".repeat(40), null);
    }

    private static Answers answer() {
        return Answers.empty().plus(new AnswerFact("Q-001", "TASK-003", "Which contract?", "option_a", "Option A",
                "the note", AnswerFact.Kind.CHOSEN));
    }

    /** A task offered, then acknowledged, so the protocol lets it hold. */
    private static QueueTaskRecord executing(TaskProtocol protocol) {
        var offered = TaskFixtures.task(protocol.wait(TaskFixtures.waitFor(TaskFixtures.GENERATION),
                TaskFixtures.AT));
        return ((CallResult.Accepted) protocol.acknowledge(TaskFixtures.call(offered),
                TaskFixtures.AT.plusSeconds(1))).task();
    }

    @Test
    @DisplayName("a hold keeps the lease, offers only the wait and refuses the links")
    void hold(@TempDir Path base) {
        var locks = new TestLocks();
        var queue = QueueStore.ofProject(base, "project-a", locks);
        queue.create(TaskFixtures.queued(TASK, TaskFixtures.ROLE).build());
        var protocol = protocol(queue, locks);
        var executing = executing(protocol);

        var held = ((CallResult.Accepted) protocol.hold(TaskFixtures.call(executing),
                continuation(ContinuationForm.HOLD, TaskFixtures.AT.plusSeconds(2)), TaskFixtures.AT.plusSeconds(2)))
                .task();
        var refused = protocol.submit(TaskFixtures.call(held), TaskFixtures.AT.plusSeconds(3));
        var wait = protocol.wait(TaskFixtures.waitFor(TaskFixtures.GENERATION), TaskFixtures.AT.plusSeconds(3));

        assertAll(
                () -> assertEquals(Status.AWAITING_ANSWER, held.status()),
                () -> assertEquals(TaskFixtures.GENERATION, held.lease().generationId()),
                () -> assertEquals(1, held.attempts().size()),
                () -> assertEquals(0L, held.spentReOffers()),
                () -> assertInstanceOf(CallResult.Refused.class, refused),
                () -> assertEquals(new WaitAnswer.WaitAgain("awaiting_answer"), wait));
    }

    @Test
    @DisplayName("the answer reaches the pending wait and the task's links return only after its acknowledgement")
    void answerAndAcknowledge(@TempDir Path base) {
        var locks = new TestLocks();
        var queue = QueueStore.ofProject(base, "project-a", locks);
        queue.create(TaskFixtures.queued(TASK, TaskFixtures.ROLE).build());
        var protocol = protocol(queue, locks);
        var held = ((CallResult.Accepted) protocol.hold(TaskFixtures.call(executing(protocol)),
                continuation(ContinuationForm.HOLD, TaskFixtures.AT.plusSeconds(2)), TaskFixtures.AT.plusSeconds(2)))
                .task();

        protocol.answer("Q-001", answer(), TaskFixtures.AT.plusSeconds(4));
        var delivered = protocol.wait(TaskFixtures.waitFor(TaskFixtures.GENERATION), TaskFixtures.AT.plusSeconds(5));
        var beforeAck = protocol.submit(TaskFixtures.call(held), TaskFixtures.AT.plusSeconds(6));
        var acknowledged = protocol.acknowledgeAnswer(TaskFixtures.call(held), TaskFixtures.AT.plusSeconds(7));
        var submitted = protocol.submit(TaskFixtures.call(held), TaskFixtures.AT.plusSeconds(8));

        var answered = assertInstanceOf(WaitAnswer.Answered.class, delivered);
        assertAll(
                () -> assertEquals(held.queueTaskId(), answered.task().queueTaskId()),
                () -> assertEquals(answer(), answered.answers()),
                () -> assertInstanceOf(CallResult.Refused.class, beforeAck),
                () -> assertEquals(Status.EXECUTING,
                        assertInstanceOf(CallResult.Accepted.class, acknowledged).task().status()),
                () -> assertEquals(Outcome.SUBMITTED,
                        assertInstanceOf(CallResult.Accepted.class, submitted).task().outcome()));
    }

    @Test
    @DisplayName("a suspended task is offered to a new generation with its answer, and that offer's ack is the answer ack")
    void resume(@TempDir Path base) {
        var locks = new TestLocks();
        var queue = QueueStore.ofProject(base, "project-a", locks);
        queue.create(TaskFixtures.queued(TASK, TaskFixtures.ROLE).build());
        var protocol = protocol(queue, locks);
        var held = ((CallResult.Accepted) protocol.hold(TaskFixtures.call(executing(protocol)),
                continuation(ContinuationForm.HOLD, TaskFixtures.AT.plusSeconds(2)), TaskFixtures.AT.plusSeconds(2)))
                .task();
        var suspended = ((CallResult.Accepted) protocol.suspend(TaskFixtures.call(held),
                continuation(ContinuationForm.RESUME, TaskFixtures.AT.plusSeconds(3)), TaskFixtures.AT.plusSeconds(3)))
                .task();

        protocol.answer("Q-001", answer(), TaskFixtures.AT.plusSeconds(4));
        var resumed = protocol.wait(TaskFixtures.waitFor(TaskFixtures.SUCCESSOR), TaskFixtures.AT.plusSeconds(5));
        var answered = assertInstanceOf(WaitAnswer.Answered.class, resumed);
        var repeated = protocol.wait(TaskFixtures.waitFor(TaskFixtures.SUCCESSOR), TaskFixtures.AT.plusSeconds(6));
        var stray = protocol.wait(TaskFixtures.waitFor(TaskFixtures.THIRD), TaskFixtures.AT.plusSeconds(6));
        var acknowledged = protocol.acknowledgeAnswer(
                new TaskCall(answered.task().queueTaskId(), TaskFixtures.SUCCESSOR), TaskFixtures.AT.plusSeconds(7));

        assertAll(
                () -> assertEquals(Status.SUSPENDED, suspended.status()),
                () -> assertNull(suspended.lease()),
                () -> assertEquals(AttemptEnd.SUSPENDED, suspended.currentAttempt().orElseThrow().end()),
                () -> assertEquals(TaskFixtures.SUCCESSOR, answered.task().lease().generationId()),
                () -> assertEquals(answer(), answered.answers()),
                () -> assertEquals(answer(), assertInstanceOf(WaitAnswer.Answered.class, repeated).answers(),
                        "a retried wait answers with the same facts"),
                () -> assertEquals(new WaitAnswer.WaitAgain("no_task"), stray,
                        "a neighbouring wait keeps the answer of the resumed task"),
                () -> assertEquals(Status.EXECUTING,
                        assertInstanceOf(CallResult.Accepted.class, acknowledged).task().status()),
                () -> assertEquals(2, assertInstanceOf(CallResult.Accepted.class, acknowledged).task()
                        .attempts().size()));
    }

    @Test
    @DisplayName("a fenced holding generation keeps its continuation, and the successor receives the answer")
    void fenceKeepsTheAnswer(@TempDir Path base) {
        var locks = new TestLocks();
        var queue = QueueStore.ofProject(base, "project-a", locks);
        queue.create(TaskFixtures.queued(TASK, TaskFixtures.ROLE).build());
        var protocol = protocol(queue, locks);
        protocol.hold(TaskFixtures.call(executing(protocol)),
                continuation(ContinuationForm.HOLD, TaskFixtures.AT.plusSeconds(2)), TaskFixtures.AT.plusSeconds(2));
        protocol.answer("Q-001", answer(), TaskFixtures.AT.plusSeconds(3));

        protocol.fence(TaskFixtures.GENERATION, TaskFixtures.AT.plusSeconds(4));
        var successor = protocol.wait(TaskFixtures.waitFor(TaskFixtures.SUCCESSOR), TaskFixtures.AT.plusSeconds(5));

        assertAll(
                () -> assertEquals("Q-001", queue.read(TASK).orElseThrow().continuation().questionId()),
                () -> assertEquals(answer(),
                        assertInstanceOf(WaitAnswer.Answered.class, successor).answers()));
    }

    @Test
    @DisplayName("a fallback closes the task asked, records the reason and drops the stored session")
    void fallbackFromHold(@TempDir Path base) {
        var locks = new TestLocks();
        var queue = QueueStore.ofProject(base, "project-a", locks);
        queue.create(TaskFixtures.queued(TASK, TaskFixtures.ROLE).build());
        var protocol = protocol(queue, locks);
        var held = ((CallResult.Accepted) protocol.hold(TaskFixtures.call(executing(protocol)),
                continuation(ContinuationForm.HOLD, TaskFixtures.AT.plusSeconds(2)), TaskFixtures.AT.plusSeconds(2)))
                .task();

        var closed = ((CallResult.Accepted) protocol.fallback(TaskFixtures.call(held),
                FallbackReason.HOLD_EXPIRED, TaskFixtures.AT.plusSeconds(700))).task();

        assertAll(
                () -> assertEquals(Status.CLOSED, closed.status()),
                () -> assertEquals(Outcome.ASKED, closed.outcome()),
                () -> assertEquals("Q-001", closed.questionId()),
                () -> assertEquals(FallbackReason.HOLD_EXPIRED, closed.continuation().fallbackReason()),
                () -> assertNull(closed.lease()),
                () -> assertEquals(AttemptEnd.CLOSED, closed.currentAttempt().orElseThrow().end()));
    }

    @Test
    @DisplayName("the server falls a suspended continuation back without a lease")
    void fallbackOfSuspended(@TempDir Path base) {
        var locks = new TestLocks();
        var queue = QueueStore.ofProject(base, "project-a", locks);
        queue.create(TaskFixtures.queued(TASK, TaskFixtures.ROLE).build());
        var protocol = protocol(queue, locks);
        var held = ((CallResult.Accepted) protocol.hold(TaskFixtures.call(executing(protocol)),
                continuation(ContinuationForm.HOLD, TaskFixtures.AT.plusSeconds(2)), TaskFixtures.AT.plusSeconds(2)))
                .task();
        protocol.suspend(TaskFixtures.call(held), continuation(ContinuationForm.RESUME, TaskFixtures.AT.plusSeconds(3)),
                TaskFixtures.AT.plusSeconds(3));

        var closed = protocol.fallback(TASK, FallbackReason.SESSION_MISSING, TaskFixtures.AT.plusSeconds(4000));

        assertAll(
                () -> assertTrue(closed.isPresent()),
                () -> assertEquals(Outcome.ASKED, closed.orElseThrow().outcome()),
                () -> assertEquals(FallbackReason.SESSION_MISSING,
                        closed.orElseThrow().continuation().fallbackReason()),
                () -> assertEquals(AttemptEnd.SUSPENDED,
                        closed.orElseThrow().currentAttempt().orElseThrow().end()));
    }

    @Test
    @DisplayName("a suspended task holds no lease, so a pause ends no attempt and a wait offers nothing until its answer")
    void suspendedSurvivesAPause(@TempDir Path base) {
        var locks = new TestLocks();
        var queue = QueueStore.ofProject(base, "project-a", locks);
        queue.create(TaskFixtures.queued(TASK, TaskFixtures.ROLE).build());
        var protocol = protocol(queue, locks);
        var held = ((CallResult.Accepted) protocol.hold(TaskFixtures.call(executing(protocol)),
                continuation(ContinuationForm.HOLD, TaskFixtures.AT.plusSeconds(2)), TaskFixtures.AT.plusSeconds(2)))
                .task();
        protocol.suspend(TaskFixtures.call(held), continuation(ContinuationForm.RESUME, TaskFixtures.AT.plusSeconds(3)),
                TaskFixtures.AT.plusSeconds(3));

        var endedAttempts = protocol.pausePlan(TaskFixtures.PLAN, TaskFixtures.AT.plusSeconds(4));
        var afterPause = queue.read(TASK).orElseThrow();
        var wait = protocol.wait(TaskFixtures.waitFor(TaskFixtures.SUCCESSOR), TaskFixtures.AT.plusSeconds(5));

        assertAll(
                () -> assertEquals(0, endedAttempts, "a suspended task holds no attempt to end"),
                () -> assertEquals(Status.SUSPENDED, afterPause.status()),
                () -> assertNull(afterPause.lease()),
                () -> assertEquals(new WaitAnswer.WaitAgain("no_task"), wait));
    }

    @Test
    @DisplayName("an answer that arrives after the fence is stored and reaches the successor with its facts")
    void answerAfterFence(@TempDir Path base) {
        var locks = new TestLocks();
        var queue = QueueStore.ofProject(base, "project-a", locks);
        queue.create(TaskFixtures.queued(TASK, TaskFixtures.ROLE).build());
        var protocol = protocol(queue, locks);
        protocol.hold(TaskFixtures.call(executing(protocol)),
                continuation(ContinuationForm.HOLD, TaskFixtures.AT.plusSeconds(2)), TaskFixtures.AT.plusSeconds(2));
        protocol.fence(TaskFixtures.GENERATION, TaskFixtures.AT.plusSeconds(3));

        var delivered = protocol.answer("Q-001", answer(), TaskFixtures.AT.plusSeconds(4));
        var successor = protocol.wait(TaskFixtures.waitFor(TaskFixtures.SUCCESSOR), TaskFixtures.AT.plusSeconds(5));

        assertAll(
                () -> assertEquals(TASK, delivered.orElseThrow().queueTaskId(),
                        "a fenced continuation still receives its answer"),
                () -> assertEquals(answer(),
                        assertInstanceOf(WaitAnswer.Answered.class, successor).answers()));
    }

    @Test
    @DisplayName("the server falls back a hold whose limit passed, without a lease")
    void fallbackOfHold(@TempDir Path base) {
        var locks = new TestLocks();
        var queue = QueueStore.ofProject(base, "project-a", locks);
        queue.create(TaskFixtures.queued(TASK, TaskFixtures.ROLE).build());
        var protocol = protocol(queue, locks);
        protocol.hold(TaskFixtures.call(executing(protocol)),
                continuation(ContinuationForm.HOLD, TaskFixtures.AT.plusSeconds(2)), TaskFixtures.AT.plusSeconds(2));

        var closed = protocol.fallback(TASK, FallbackReason.HOLD_EXPIRED, TaskFixtures.AT.plusSeconds(700));

        assertAll(
                () -> assertTrue(closed.isPresent()),
                () -> assertEquals(Outcome.ASKED, closed.orElseThrow().outcome()),
                () -> assertEquals(FallbackReason.HOLD_EXPIRED,
                        closed.orElseThrow().continuation().fallbackReason()),
                () -> assertNull(closed.orElseThrow().lease()));
    }

    @Test
    @DisplayName("an answer without a named scope is delivered to nobody while two scopes hold the same question")
    void answerAmbiguity(@TempDir Path base) {
        var locks = new TestLocks();
        var queue = QueueStore.ofProject(base, "project-a", locks);
        var planA = new QueueTaskRecord.Scope(QueueTaskRecord.ScopeType.PLAN, "plan-a");
        var planB = new QueueTaskRecord.Scope(QueueTaskRecord.ScopeType.PLAN, "plan-b");
        queue.create(TaskFixtures.queued(TASK, TaskFixtures.ROLE).scope(planA).build());
        queue.create(TaskFixtures.queued("qt-0000000000000002", TaskFixtures.ROLE).scope(planB).build());
        var protocol = protocol(queue, locks);
        var heldA = ((CallResult.Accepted) protocol.hold(TaskFixtures.call(executing(protocol)),
                continuation(ContinuationForm.HOLD, TaskFixtures.AT.plusSeconds(2)), TaskFixtures.AT.plusSeconds(2)))
                .task();
        var heldB = ((CallResult.Accepted) protocol.hold(
                TaskFixtures.call(executing(protocol, TaskFixtures.SUCCESSOR), TaskFixtures.SUCCESSOR),
                continuation(ContinuationForm.HOLD, TaskFixtures.AT.plusSeconds(2)), TaskFixtures.AT.plusSeconds(2)))
                .task();

        var ambiguous = protocol.answer("Q-001", answer(), TaskFixtures.AT.plusSeconds(4));
        var scoped = protocol.answer(planB, "Q-001", answer(), TaskFixtures.AT.plusSeconds(4));

        assertAll(
                () -> assertEquals(Status.AWAITING_ANSWER, heldA.status()),
                () -> assertEquals(Status.AWAITING_ANSWER, heldB.status()),
                () -> assertTrue(ambiguous.isEmpty(), "a bare question id names no scope"),
                () -> assertEquals("qt-0000000000000002", scoped.orElseThrow().queueTaskId()));
    }

    /** A task offered to, and acknowledged by, the named generation. */
    private static QueueTaskRecord executing(TaskProtocol protocol, String generation) {
        var offered = TaskFixtures.task(protocol.wait(TaskFixtures.waitFor(generation), TaskFixtures.AT));
        return ((CallResult.Accepted) protocol.acknowledge(TaskFixtures.call(offered, generation),
                TaskFixtures.AT.plusSeconds(1))).task();
    }

    @Test
    @DisplayName("an answer to a question no open task holds is delivered to nobody")
    void answerToUnknownQuestion(@TempDir Path base) {
        var locks = new TestLocks();
        var queue = QueueStore.ofProject(base, "project-a", locks);
        queue.create(TaskFixtures.queued(TASK, TaskFixtures.ROLE).build());
        var protocol = protocol(queue, locks);

        assertTrue(protocol.answer("Q-999", answer(), TaskFixtures.AT).isEmpty());
    }
}
