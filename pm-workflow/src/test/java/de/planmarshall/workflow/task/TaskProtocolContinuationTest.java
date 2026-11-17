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
import java.util.Locale;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
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
@EnableGeneratorController
@DisplayName("Task protocol continuation")
class TaskProtocolContinuationTest {

    // The task and question ids are the wire format; the answer facts and the session id are generated.
    private static final String TASK = "qt-0000000000000001";
    private static final String ANSWER_TASK = "TASK-" + Generators.integers(100, 999).next();
    private static final String QUESTION = Generators.letterStrings(8, 24).next();
    private static final String OPTION_ID =
            "option_" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);
    private static final String OPTION_LABEL = Generators.letterStrings(3, 10).next();
    private static final String NOTE = Generators.letterStrings(8, 24).next();
    private static final String SESSION = "session-" + Generators.letterStrings(3, 10).next();

    private static TaskProtocol protocol(QueueStore queue, TestLocks locks) {
        return TaskProtocol.builder(queue, locks).build();
    }

    private static Continuation continuation(ContinuationForm form, Instant at) {
        return form == ContinuationForm.HOLD
                ? new Continuation("Q-001", form, at.plusSeconds(600), null, null, null, null, null, null)
                : new Continuation("Q-001", form, null, SESSION, at, at.plusSeconds(3600), TaskFixtures.DIGEST,
                "c".repeat(40), null);
    }

    private static Answers answer() {
        return Answers.empty().plus(new AnswerFact("Q-001", ANSWER_TASK, QUESTION, OPTION_ID, OPTION_LABEL,
                NOTE, AnswerFact.Kind.CHOSEN));
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
                () -> assertEquals(Status.AWAITING_ANSWER, held.status(),
                        "the hold leaves the task awaiting its answer"),
                () -> assertEquals(TaskFixtures.GENERATION, held.lease().generationId(),
                        "the hold keeps the lease of its generation"),
                () -> assertEquals(1, held.attempts().size(),
                        "the hold adds no second attempt"),
                () -> assertEquals(0L, held.spentReOffers(),
                        "a hold spends none of the re-offer budget"),
                () -> assertInstanceOf(CallResult.Refused.class, refused,
                        "a held task refuses its links"),
                () -> assertEquals(new WaitAnswer.WaitAgain(WaitAnswer.WaitAgainReason.AWAITING_ANSWER), wait,
                        "a held task waits for its answer"));
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

        var answered = assertInstanceOf(WaitAnswer.Answered.class, delivered,
                "the answer reaches the pending wait");
        assertAll(
                () -> assertEquals(held.queueTaskId(), answered.task().queueTaskId(),
                        "the answered wait names the held task"),
                () -> assertEquals(answer(), answered.answers(),
                        "the wait delivers the recorded answer facts"),
                () -> assertInstanceOf(CallResult.Refused.class, beforeAck,
                        "the links stay refused before the answer ack"),
                () -> assertEquals(Status.EXECUTING,
                        assertInstanceOf(CallResult.Accepted.class, acknowledged, "the answer ack is accepted")
                                .task().status(),
                        "acknowledging the answer leaves the task executing"),
                () -> assertEquals(Outcome.SUBMITTED,
                        assertInstanceOf(CallResult.Accepted.class, submitted, "the submit is accepted")
                                .task().outcome(),
                        "the submitted task carries the submitted outcome"));
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
        var answered = assertInstanceOf(WaitAnswer.Answered.class, resumed,
                "the resumed suspension is offered with its answer");
        var repeated = protocol.wait(TaskFixtures.waitFor(TaskFixtures.SUCCESSOR), TaskFixtures.AT.plusSeconds(6));
        var stray = protocol.wait(TaskFixtures.waitFor(TaskFixtures.THIRD), TaskFixtures.AT.plusSeconds(6));
        var acknowledged = protocol.acknowledgeAnswer(
                new TaskCall(answered.task().queueTaskId(), TaskFixtures.SUCCESSOR), TaskFixtures.AT.plusSeconds(7));

        assertAll(
                () -> assertEquals(Status.SUSPENDED, suspended.status(),
                        "the suspension leaves the task suspended"),
                () -> assertNull(suspended.lease(),
                        "the suspended task holds no lease"),
                () -> assertEquals(AttemptEnd.SUSPENDED, suspended.currentAttempt().orElseThrow().end(),
                        "the current attempt ends as suspended"),
                () -> assertEquals(TaskFixtures.SUCCESSOR, answered.task().lease().generationId(),
                        "the successor holds the new generation"),
                () -> assertEquals(answer(), answered.answers(),
                        "the resumed offer carries the answer facts"),
                () -> assertEquals(answer(),
                        assertInstanceOf(WaitAnswer.Answered.class, repeated, "the retried wait is answered")
                                .answers(),
                        "a retried wait answers with the same facts"),
                () -> assertEquals(new WaitAnswer.WaitAgain(WaitAnswer.WaitAgainReason.NO_TASK), stray,
                        "a neighbouring wait keeps the answer of the resumed task"),
                () -> assertEquals(Status.EXECUTING,
                        assertInstanceOf(CallResult.Accepted.class, acknowledged, "the resumed ack is accepted")
                                .task().status(),
                        "acknowledging the resumed offer leaves it executing"),
                () -> assertEquals(2,
                        assertInstanceOf(CallResult.Accepted.class, acknowledged, "the acknowledged call is accepted")
                                .task()
                                .attempts().size(),
                        "the acknowledged offer counts two attempts"));
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
                () -> assertEquals("Q-001", queue.read(TASK).orElseThrow().continuation().questionId(),
                        "the fence keeps the continuation question"),
                () -> assertEquals(answer(),
                        assertInstanceOf(WaitAnswer.Answered.class, successor, "the successor wait is answered")
                                .answers(),
                        "the successor receives the stored answer"));
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
                () -> assertEquals(Status.CLOSED, closed.status(),
                        "the fallback closes the asked task"),
                () -> assertEquals(Outcome.ASKED, closed.outcome(),
                        "the closed task carries the asked outcome"),
                () -> assertEquals("Q-001", closed.questionId(),
                        "the closed task keeps its question id"),
                () -> assertEquals(FallbackReason.HOLD_EXPIRED, closed.continuation().fallbackReason(),
                        "the fallback reason is the hold expiry"),
                () -> assertNull(closed.lease(),
                        "the fallback releases the lease"),
                () -> assertEquals(AttemptEnd.CLOSED, closed.currentAttempt().orElseThrow().end(),
                        "the fallback ends the attempt as closed"));
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
                () -> assertTrue(closed.isPresent(),
                        "the server falls back the suspended task"),
                () -> assertEquals(Outcome.ASKED, closed.orElseThrow().outcome(),
                        "the fallen-back task carries the asked outcome"),
                () -> assertEquals(FallbackReason.SESSION_MISSING,
                        closed.orElseThrow().continuation().fallbackReason(),
                        "the fallback reason is the missing session"),
                () -> assertEquals(AttemptEnd.SUSPENDED,
                        closed.orElseThrow().currentAttempt().orElseThrow().end(),
                        "the attempt ends as suspended"));
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
                () -> assertEquals(Status.SUSPENDED, afterPause.status(),
                        "the pause leaves the suspended task suspended"),
                () -> assertNull(afterPause.lease(),
                        "the suspended task still holds no lease"),
                () -> assertEquals(new WaitAnswer.WaitAgain(WaitAnswer.WaitAgainReason.NO_TASK), wait,
                        "a suspended task is answered no task"));
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
                        assertInstanceOf(WaitAnswer.Answered.class, successor, "the fenced wait is answered")
                                .answers(),
                        "the successor receives the answer after the fence"));
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
                () -> assertTrue(closed.isPresent(),
                        "the server falls back the expired hold"),
                () -> assertEquals(Outcome.ASKED, closed.orElseThrow().outcome(),
                        "the fallen-back hold carries the asked outcome"),
                () -> assertEquals(FallbackReason.HOLD_EXPIRED,
                        closed.orElseThrow().continuation().fallbackReason(),
                        "the hold fallback reason is the hold expiry"),
                () -> assertNull(closed.orElseThrow().lease(),
                        "the fallback of the hold releases the lease"));
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
                () -> assertEquals(Status.AWAITING_ANSWER, heldA.status(),
                        "the first held task awaits its answer"),
                () -> assertEquals(Status.AWAITING_ANSWER, heldB.status(),
                        "the second held task awaits its answer"),
                () -> assertTrue(ambiguous.isEmpty(), "a bare question id names no scope"),
                () -> assertEquals("qt-0000000000000002", scoped.orElseThrow().queueTaskId(),
                        "the scoped answer reaches the plan-b task"));
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

        assertTrue(protocol.answer("Q-999", answer(), TaskFixtures.AT).isEmpty(),
                "an answer to an unknown question reaches no task");
    }
}
