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


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.queue.QueueStore;
import de.planmarshall.core.queue.QueueTaskRecord;
import de.planmarshall.core.queue.QueueTaskRecord.AttemptEnd;
import de.planmarshall.core.queue.QueueTaskRecord.Continuation;
import de.planmarshall.core.queue.QueueTaskRecord.SignalAction;
import de.planmarshall.core.queue.QueueTaskRecord.SignalName;
import de.planmarshall.core.queue.QueueTaskRecord.Status;

/**
 * The outcomes that return a task to the queue (Model Work § Re-Offer and § A Worker Off Track): a waited-again
 * attempt, the pause of a plan, an off-track restart, the {@code agent_started} detection, and the suspension that
 * spends no re-offer.
 */
@DisplayName("Task protocol re-offers")
class TaskProtocolReOfferTest {

    private record Harness(QueueStore queue, TaskProtocol protocol, TaskFixtures.RecordingAudit audit) {
    }

    private static Harness harness(Path base) {
        var locks = new TestLocks();
        var queue = QueueStore.ofProject(base, "project-a", locks);
        queue.create(TaskFixtures.queued("qt-0000000000000001", TaskFixtures.ROLE).build());
        var audit = new TaskFixtures.RecordingAudit();
        return new Harness(queue, TaskProtocol.builder(queue, locks).audit(audit).build(), audit);
    }

    private static TaskCall acknowledged(Harness harness) {
        var offered = TaskFixtures.task(harness.protocol().wait(TaskFixtures.waitFor(TaskFixtures.GENERATION),
                TaskFixtures.AT));
        harness.protocol().acknowledge(TaskFixtures.call(offered), TaskFixtures.AT.plusSeconds(1));
        return TaskFixtures.call(offered);
    }

    @Test
    @DisplayName("a wait that arrives while the generation holds an acknowledged task re-offers it as a new attempt")
    void waitedAgain(@TempDir Path base) {
        var harness = harness(base);
        acknowledged(harness);

        var answer = harness.protocol().wait(TaskFixtures.waitFor(TaskFixtures.GENERATION),
                TaskFixtures.AT.plusSeconds(2));

        var task = TaskFixtures.task(answer);
        assertAll(
                () -> assertEquals(Status.OFFERED, task.status()),
                () -> assertEquals(2, task.attempts().size()),
                () -> assertEquals(AttemptEnd.WAITED_AGAIN, task.attempts().getFirst().end()),
                () -> assertTrue(task.attempts().getFirst().counts()),
                () -> assertEquals(1, task.spentReOffers()));
    }

    @Test
    @DisplayName("a pause ends the attempt with end paused, releases the lease and spends no re-offer")
    void pauseRequeues(@TempDir Path base) {
        var locks = new TestLocks();
        var queue = QueueStore.ofProject(base, "project-a", locks);
        queue.create(TaskFixtures.queued("qt-0000000000000001", TaskFixtures.ROLE).build());
        var paused = new TaskFixtures.Paused();
        var protocol = TaskProtocol.builder(queue, locks).pause(paused).build();
        var offered = TaskFixtures.task(protocol.wait(TaskFixtures.waitFor(TaskFixtures.GENERATION),
                TaskFixtures.AT));
        protocol.acknowledge(TaskFixtures.call(offered), TaskFixtures.AT.plusSeconds(1));

        paused.pause(TaskFixtures.PLAN);
        var ended = protocol.pausePlan(TaskFixtures.PLAN, TaskFixtures.AT.plusSeconds(2));
        var whilePaused = protocol.wait(TaskFixtures.waitFor(TaskFixtures.GENERATION), TaskFixtures.AT.plusSeconds(3));
        paused.start(TaskFixtures.PLAN);
        var afterStart = protocol.wait(TaskFixtures.waitFor(TaskFixtures.GENERATION), TaskFixtures.AT.plusSeconds(4));

        var task = queue.read("qt-0000000000000001").orElseThrow();
        assertAll(
                () -> assertEquals(1, ended),
                () -> assertEquals(new WaitAnswer.WaitAgain("no_task"), whilePaused),
                () -> assertInstanceOf(WaitAnswer.Offered.class, afterStart),
                () -> assertEquals(AttemptEnd.PAUSED, task.attempts().getFirst().end()),
                () -> assertEquals(0L, task.spentReOffers()));
    }

    @Test
    @DisplayName("the task is queued again at once, before the plan is started")
    void pauseRequeuesAtOnce(@TempDir Path base) {
        var harness = harness(base);
        acknowledged(harness);

        harness.protocol().pausePlan(TaskFixtures.PLAN, TaskFixtures.AT.plusSeconds(2));

        var task = harness.queue().read("qt-0000000000000001").orElseThrow();
        assertAll(
                () -> assertEquals(Status.QUEUED, task.status()),
                () -> assertNull(task.lease()),
                () -> assertEquals(AttemptEnd.PAUSED, task.currentAttempt().orElseThrow().end()));
    }

    @Test
    @DisplayName("an off-track restart records the signal, ends the attempt off_track and re-offers")
    void offTrackRestart(@TempDir Path base) {
        var harness = harness(base);
        var call = acknowledged(harness);

        var reported = assertInstanceOf(CallResult.Accepted.class,
                harness.protocol().offTrack(call,
                        new OffTrackSignal(SignalName.REPEATING, SignalAction.RESTART, 4, 3), 3,
                        TaskFixtures.AT.plusSeconds(2)));

        var task = reported.task();
        assertAll(
                () -> assertEquals(Status.QUEUED, task.status()),
                () -> assertEquals(1, task.signals().size()),
                () -> assertEquals(SignalName.REPEATING, task.signals().getFirst().signal()),
                () -> assertEquals(4, task.signals().getFirst().measurement()),
                () -> assertEquals(AttemptEnd.OFF_TRACK, task.currentAttempt().orElseThrow().end()),
                () -> assertEquals(1, task.spentReOffers()));
    }

    @Test
    @DisplayName("a show records the signal and ends no attempt")
    void offTrackShow(@TempDir Path base) {
        var harness = harness(base);
        var call = acknowledged(harness);

        var reported = assertInstanceOf(CallResult.Accepted.class,
                harness.protocol().offTrack(call, new OffTrackSignal(SignalName.NO_PROGRESS, SignalAction.SHOW, 7, 5),
                        3, TaskFixtures.AT.plusSeconds(2)));

        assertAll(
                () -> assertEquals(Status.EXECUTING, reported.task().status()),
                () -> assertEquals(1, reported.task().signals().size()),
                () -> assertNull(reported.task().currentAttempt().orElseThrow().end()));
    }

    @Test
    @DisplayName("a signal holds at most once per attempt")
    void signalOnce(@TempDir Path base) {
        var harness = harness(base);
        var call = acknowledged(harness);
        var signal = new OffTrackSignal(SignalName.NO_PROGRESS, SignalAction.NUDGE, 7, 5);

        harness.protocol().offTrack(call, signal, 3, TaskFixtures.AT.plusSeconds(2));
        var second = assertInstanceOf(CallResult.Accepted.class,
                harness.protocol().offTrack(call, signal, 3, TaskFixtures.AT.plusSeconds(3)));

        assertEquals(1, second.task().signals().size());
    }

    @Test
    @DisplayName("agent_started records its security audit, restarts the worker and re-offers the task")
    void agentStarted(@TempDir Path base) {
        var harness = harness(base);
        var call = acknowledged(harness);
        var event = new SecurityAudit.Event("/usr/local/bin/opencode", TaskFixtures.GENERATION,
                call.queueTaskId(), 1, TaskFixtures.AT.plusSeconds(2));

        var reported = assertInstanceOf(CallResult.Accepted.class, harness.protocol().agentStarted(call, event, 3));

        var task = reported.task();
        assertAll(
                () -> assertEquals(Status.QUEUED, task.status()),
                () -> assertEquals(SignalName.AGENT_STARTED, task.signals().getFirst().signal()),
                () -> assertEquals(SignalAction.RESTART, task.signals().getFirst().action()),
                () -> assertEquals(1, task.signals().getFirst().measurement()),
                () -> assertEquals(1, task.signals().getFirst().threshold()),
                () -> assertEquals(TaskFixtures.AT.plusSeconds(2), task.signals().getFirst().at()),
                () -> assertEquals(AttemptEnd.OFF_TRACK, task.currentAttempt().orElseThrow().end()),
                () -> assertEquals(1, harness.audit().events().size()),
                () -> assertEquals("/usr/local/bin/opencode",
                        harness.audit().events().getFirst().executable()));
    }

    @Test
    @DisplayName("a suspension stores the continuation and spends no re-offer")
    void suspend(@TempDir Path base) {
        var harness = harness(base);
        var call = acknowledged(harness);
        Continuation continuation = TaskFixtures.continuation();

        var suspended = assertInstanceOf(CallResult.Accepted.class,
                harness.protocol().suspend(call, continuation, TaskFixtures.AT.plusSeconds(2)));

        assertAll(
                () -> assertEquals(Status.SUSPENDED, suspended.task().status()),
                () -> assertEquals(continuation, suspended.task().continuation()),
                () -> assertEquals(AttemptEnd.SUSPENDED, suspended.task().currentAttempt().orElseThrow().end()),
                () -> assertEquals(0L, suspended.task().spentReOffers()),
                () -> assertNull(suspended.task().lease()));
    }

    @Test
    @DisplayName("a task whose next attempt exhausts the budget closes lost attempts_spent")
    void budgetSpent(@TempDir Path base) {
        var harness = harness(base);
        var call = acknowledged(harness);

        var reported = assertInstanceOf(CallResult.Accepted.class,
                harness.protocol().report(call, AttemptEnd.WORKER_LOST, 1, TaskFixtures.AT.plusSeconds(2)));

        assertAll(
                () -> assertEquals(Status.CLOSED, reported.task().status()),
                () -> assertEquals(QueueTaskRecord.Outcome.LOST, reported.task().outcome()),
                () -> assertEquals(QueueTaskRecord.LostReason.ATTEMPTS_SPENT, reported.task().reason()));
    }
}
