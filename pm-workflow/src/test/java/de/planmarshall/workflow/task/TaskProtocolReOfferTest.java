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

import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.newCapture;
import static org.easymock.EasyMock.replay;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Optional;


import org.easymock.Capture;
import org.easymock.CaptureType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.planmarshall.core.queue.ActivityEntry;
import de.planmarshall.core.queue.ActivityJournal;
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
@EnableGeneratorController
@SuppressWarnings("java:S5778")
// the refusal cases call one throwing method per case, by design
@DisplayName("Task protocol re-offers")
class TaskProtocolReOfferTest {

    private static final double MEASUREMENT = Generators.integers(1, 10).next();
    private static final double THRESHOLD = Generators.integers(1, 10).next();
    private static final int RE_OFFER_BUDGET = Generators.integers(1, 10).next();
    private static final String EXECUTABLE = "/usr/local/bin/" + Generators.letterStrings(4, 12).next();

    private record Harness(QueueStore queue, TaskProtocol protocol, Capture<SecurityAudit.Event> auditEvents) {
    }

    private static Harness harness(Path base) {
        var locks = new TestLocks();
        var queue = QueueStore.ofProject(base, "project-a", locks);
        queue.create(TaskFixtures.queued("qt-0000000000000001", TaskFixtures.ROLE).build());
        Capture<SecurityAudit.Event> events = newCapture(CaptureType.ALL);
        SecurityAudit audit = createMock(SecurityAudit.class);
        audit.agentStarted(capture(events));
        expectLastCall().anyTimes();
        replay(audit);
        return new Harness(queue, TaskProtocol.builder(queue, locks).audit(audit).build(), events);
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
                () -> assertEquals(Status.OFFERED, task.status(),
                        "the re-offer leaves the task offered again"),
                () -> assertEquals(2, task.attempts().size(),
                        "the waited-again attempt adds a second attempt"),
                () -> assertEquals(AttemptEnd.WAITED_AGAIN, task.attempts().getFirst().end(),
                        "the first attempt ends as waited again"),
                () -> assertTrue(task.attempts().getFirst().counts(),
                        "the waited-again attempt counts against the budget"),
                () -> assertEquals(1, task.spentReOffers(),
                        "one re-offer of the budget is spent"));
    }

    @Test
    @DisplayName("a pause ends the attempt with end paused, releases the lease and spends no re-offer")
    void pauseRequeues(@TempDir Path base) {
        var locks = new TestLocks();
        var queue = QueueStore.ofProject(base, "project-a", locks);
        queue.create(TaskFixtures.queued("qt-0000000000000001", TaskFixtures.ROLE).build());
        var paused = new HashSet<String>();
        var protocol = TaskProtocol.builder(queue, locks).pause(TaskFixtures.pause(paused)).build();
        var offered = TaskFixtures.task(protocol.wait(TaskFixtures.waitFor(TaskFixtures.GENERATION),
                TaskFixtures.AT));
        protocol.acknowledge(TaskFixtures.call(offered), TaskFixtures.AT.plusSeconds(1));

        paused.add(TaskFixtures.PLAN);
        var ended = protocol.pausePlan(TaskFixtures.PLAN, TaskFixtures.AT.plusSeconds(2));
        var whilePaused = protocol.wait(TaskFixtures.waitFor(TaskFixtures.GENERATION), TaskFixtures.AT.plusSeconds(3));
        paused.remove(TaskFixtures.PLAN);
        var afterStart = protocol.wait(TaskFixtures.waitFor(TaskFixtures.GENERATION), TaskFixtures.AT.plusSeconds(4));

        var task = queue.read("qt-0000000000000001").orElseThrow();
        assertAll(
                () -> assertEquals(1, ended,
                        "the pause ends the acknowledged attempt"),
                () -> assertEquals(new WaitAnswer.WaitAgain(WaitAnswer.WaitAgainReason.NO_TASK), whilePaused,
                        "a paused plan is answered no task"),
                () -> assertInstanceOf(WaitAnswer.Offered.class, afterStart,
                        "the started plan is offered the task again"),
                () -> assertEquals(AttemptEnd.PAUSED, task.attempts().getFirst().end(),
                        "the attempt ends with the pause"),
                () -> assertEquals(0L, task.spentReOffers(),
                        "a pause spends none of the re-offer budget"));
    }

    @Test
    @DisplayName("the task is queued again at once, before the plan is started")
    void pauseRequeuesAtOnce(@TempDir Path base) {
        var harness = harness(base);
        acknowledged(harness);

        harness.protocol().pausePlan(TaskFixtures.PLAN, TaskFixtures.AT.plusSeconds(2));

        var task = harness.queue().read("qt-0000000000000001").orElseThrow();
        assertAll(
                () -> assertEquals(Status.QUEUED, task.status(),
                        "the paused task is queued again at once"),
                () -> assertNull(task.lease(),
                        "the pause releases the lease of the task"),
                () -> assertEquals(AttemptEnd.PAUSED, task.currentAttempt().orElseThrow().end(),
                        "the current attempt ends with the pause"));
    }

    @Test
    @DisplayName("an off-track restart records the signal, ends the attempt off_track and re-offers")
    void offTrackRestart(@TempDir Path base) {
        var harness = harness(base);
        var call = acknowledged(harness);

        var reported = assertInstanceOf(CallResult.Accepted.class,
                harness.protocol().offTrack(call,
                        new OffTrackSignal(SignalName.REPEATING, SignalAction.RESTART, MEASUREMENT, THRESHOLD),
                        RE_OFFER_BUDGET, TaskFixtures.AT.plusSeconds(2)),
                "the off-track restart is accepted");

        var task = reported.task();
        assertAll(
                () -> assertEquals(Status.QUEUED, task.status(),
                        "the off-track restart queues the task again"),
                () -> assertEquals(1, task.signals().size(),
                        "the restart records one signal on the task"),
                () -> assertEquals(SignalName.REPEATING, task.signals().getFirst().signal(),
                        "the recorded signal is the reported one"),
                () -> assertEquals(MEASUREMENT, task.signals().getFirst().measurement(),
                        "the signal keeps the reported measurement"),
                () -> assertEquals(AttemptEnd.OFF_TRACK, task.currentAttempt().orElseThrow().end(),
                        "the attempt ends off track"),
                () -> assertEquals(1, task.spentReOffers(),
                        "the restart spends one re-offer"));
    }

    @Test
    @DisplayName("a show records the signal and ends no attempt")
    void offTrackShow(@TempDir Path base) {
        var harness = harness(base);
        var call = acknowledged(harness);

        var reported = assertInstanceOf(CallResult.Accepted.class,
                harness.protocol().offTrack(call,
                        new OffTrackSignal(SignalName.NO_PROGRESS, SignalAction.SHOW, MEASUREMENT, THRESHOLD),
                        RE_OFFER_BUDGET, TaskFixtures.AT.plusSeconds(2)),
                "the show signal is accepted");

        assertAll(
                () -> assertEquals(Status.EXECUTING, reported.task().status(),
                        "a show leaves the task executing"),
                () -> assertEquals(1, reported.task().signals().size(),
                        "the show records one signal on the task"),
                () -> assertNull(reported.task().currentAttempt().orElseThrow().end(),
                        "a show ends no attempt of the task"));
    }

    @Test
    @DisplayName("a second call of a held signal with another action is refused")
    void signalActionConflict(@TempDir Path base) {
        var harness = harness(base);
        var call = acknowledged(harness);
        harness.protocol().offTrack(call,
                new OffTrackSignal(SignalName.NO_PROGRESS, SignalAction.SHOW, MEASUREMENT, THRESHOLD),
                RE_OFFER_BUDGET, TaskFixtures.AT.plusSeconds(2));

        var refused = assertInstanceOf(CallResult.Refused.class,
                harness.protocol().offTrack(call,
                        new OffTrackSignal(SignalName.NO_PROGRESS, SignalAction.RESTART, MEASUREMENT, THRESHOLD),
                        RE_OFFER_BUDGET, TaskFixtures.AT.plusSeconds(3)),
                "the conflicting second call is refused");

        assertAll(
                () -> assertEquals(TaskRefusal.INVALID_LINK_ARGS, refused.refusal(),
                        "the conflicting action is refused as bad link args"),
                () -> assertEquals(SignalAction.SHOW, refused.task().signals().getFirst().action(),
                        "the held signal keeps its first action"),
                () -> assertEquals(QueueTaskRecord.Status.EXECUTING, refused.task().status(),
                        "the refused call leaves the task executing"));
    }

    @Test
    @DisplayName("a signal holds at most once per attempt")
    void signalOnce(@TempDir Path base) {
        var harness = harness(base);
        var call = acknowledged(harness);
        var signal = new OffTrackSignal(SignalName.NO_PROGRESS, SignalAction.NUDGE, MEASUREMENT, THRESHOLD);

        harness.protocol().offTrack(call, signal, RE_OFFER_BUDGET, TaskFixtures.AT.plusSeconds(2));
        var second = assertInstanceOf(CallResult.Accepted.class,
                harness.protocol().offTrack(call, signal, RE_OFFER_BUDGET, TaskFixtures.AT.plusSeconds(3)),
                "the repeated signal is still accepted");

        assertEquals(1, second.task().signals().size(),
                "a signal is held at most once per attempt");
    }

    @Test
    @DisplayName("agent_started records its security audit, restarts the worker and re-offers the task")
    void agentStarted(@TempDir Path base) {
        var harness = harness(base);
        var call = acknowledged(harness);
        var event = new SecurityAudit.Event(EXECUTABLE, TaskFixtures.GENERATION,
                call.queueTaskId(), 1, TaskFixtures.AT.plusSeconds(2));

        var reported = assertInstanceOf(CallResult.Accepted.class,
                harness.protocol().agentStarted(call, event, RE_OFFER_BUDGET),
                "the agent-started call is accepted");

        var task = reported.task();
        assertAll(
                () -> assertEquals(Status.QUEUED, task.status(),
                        "the agent restart queues the task again"),
                () -> assertEquals(SignalName.AGENT_STARTED, task.signals().getFirst().signal(),
                        "the signal is the agent-started detection"),
                () -> assertEquals(SignalAction.RESTART, task.signals().getFirst().action(),
                        "the agent-started signal restarts the worker"),
                () -> assertEquals(1, task.signals().getFirst().measurement(),
                        "the detection records a single measurement"),
                () -> assertEquals(1, task.signals().getFirst().threshold(),
                        "the detection records a threshold of one"),
                () -> assertEquals(TaskFixtures.AT.plusSeconds(2), task.signals().getFirst().at(),
                        "the signal carries the time of the detection"),
                () -> assertEquals(AttemptEnd.OFF_TRACK, task.currentAttempt().orElseThrow().end(),
                        "the agent-started attempt ends off track"),
                () -> assertEquals(1, harness.auditEvents().getValues().size(),
                        "the detection writes one security audit event"),
                () -> assertEquals(EXECUTABLE,
                        harness.auditEvents().getValues().getFirst().executable(),
                        "the audit event names the started executable"));
    }

    @Test
    @DisplayName("a suspension stores the continuation and spends no re-offer")
    void suspend(@TempDir Path base) {
        var harness = harness(base);
        var call = acknowledged(harness);
        harness.protocol().hold(call, TaskFixtures.holdContinuation(), TaskFixtures.AT.plusSeconds(2));
        Continuation continuation = TaskFixtures.continuation();

        var suspended = assertInstanceOf(CallResult.Accepted.class,
                harness.protocol().suspend(call, continuation, TaskFixtures.AT.plusSeconds(2)),
                "the suspension call is accepted");

        assertAll(
                () -> assertEquals(Status.SUSPENDED, suspended.task().status(),
                        "the suspension leaves the task suspended"),
                () -> assertEquals(continuation, suspended.task().continuation(),
                        "the suspension stores the continuation"),
                () -> assertEquals(AttemptEnd.SUSPENDED, suspended.task().currentAttempt().orElseThrow().end(),
                        "the current attempt ends as suspended"),
                () -> assertEquals(0L, suspended.task().spentReOffers(),
                        "a suspension spends no re-offer"),
                () -> assertNull(suspended.task().lease(),
                        "the suspension releases the lease"));
    }

    @Test
    @DisplayName("a pause clears the continuation of a held task")
    void pauseClearsContinuation(@TempDir Path base) {
        var locks = new TestLocks();
        var queue = QueueStore.ofProject(base, "project-a", locks);
        queue.create(TaskFixtures.queued("qt-0000000000000001", TaskFixtures.ROLE).build());
        var protocol = TaskProtocol.builder(queue, locks).build();
        var offered = TaskFixtures.task(protocol.wait(TaskFixtures.waitFor(TaskFixtures.GENERATION),
                TaskFixtures.AT));
        protocol.acknowledge(TaskFixtures.call(offered), TaskFixtures.AT.plusSeconds(1));
        protocol.hold(TaskFixtures.call(offered), TaskFixtures.holdContinuation(), TaskFixtures.AT.plusSeconds(2));
        var held = queue.read("qt-0000000000000001").orElseThrow();

        protocol.pausePlan(TaskFixtures.PLAN, TaskFixtures.AT.plusSeconds(3));

        var after = queue.read("qt-0000000000000001").orElseThrow();
        assertAll(
                () -> assertNotNull(held.continuation(), "the hold stored a continuation to clear"),
                () -> assertEquals(QueueTaskRecord.Status.QUEUED, after.status(),
                        "the pause queues the held task again"),
                () -> assertNull(after.continuation(),
                        "the pause clears the stored continuation"));
    }

    @Test
    @DisplayName("a signal is journalled in the journal of the task's scope")
    void signalJournalPerScope(@TempDir Path base) {
        var locks = new TestLocks();
        var queue = QueueStore.ofProject(base, "project-a", locks);
        queue.create(TaskFixtures.queued("qt-0000000000000001", TaskFixtures.ROLE).build());
        var protocol = TaskProtocol.builder(queue, locks)
                .journal(scope -> Optional.of(ActivityJournal.ofScope(base, "project-a", scope, locks)))
                .build();
        var offered = TaskFixtures.task(protocol.wait(TaskFixtures.waitFor(TaskFixtures.GENERATION),
                TaskFixtures.AT));
        protocol.acknowledge(TaskFixtures.call(offered), TaskFixtures.AT.plusSeconds(1));

        protocol.offTrack(TaskFixtures.call(offered),
                new OffTrackSignal(QueueTaskRecord.SignalName.NO_PROGRESS, SignalAction.SHOW, MEASUREMENT, THRESHOLD),
                RE_OFFER_BUDGET, TaskFixtures.AT.plusSeconds(2));

        var journal = ActivityJournal.ofScope(base, "project-a",
                TaskFixtures.planScope(), locks);
        var entries = journal.entries("qt-0000000000000001");
        assertAll(
                () -> assertEquals(1, entries.size(),
                        "the signal writes a single journal entry"),
                () -> assertEquals(ActivityEntry.Kind.SIGNAL, entries.getFirst().kind(),
                        "the journal entry records a signal"),
                () -> assertTrue(Files.exists(base.resolve(
                                "projects/project-a/plans/plan-a/tasks/activity/qt-0000000000000001.jsonl")),
                        "the journal file of the plan scope exists"));
    }

    @Test
    @DisplayName("an off-track signal without a journal of the scope completes without an entry")
    void signalWithoutJournal(@TempDir Path base) {
        var locks = new TestLocks();
        var queue = QueueStore.ofProject(base, "project-a", locks);
        queue.create(TaskFixtures.queued("qt-0000000000000001", TaskFixtures.ROLE).build());
        var protocol = TaskProtocol.builder(queue, locks).journal(scope -> Optional.empty()).build();
        var offered = TaskFixtures.task(protocol.wait(TaskFixtures.waitFor(TaskFixtures.GENERATION),
                TaskFixtures.AT));
        protocol.acknowledge(TaskFixtures.call(offered), TaskFixtures.AT.plusSeconds(1));

        var result = protocol.offTrack(TaskFixtures.call(offered),
                new OffTrackSignal(QueueTaskRecord.SignalName.NO_PROGRESS, SignalAction.SHOW, MEASUREMENT, THRESHOLD),
                RE_OFFER_BUDGET, TaskFixtures.AT.plusSeconds(2));

        assertAll(
                () -> assertInstanceOf(CallResult.Accepted.class, result, "the accepted signal call completes"),
                () -> assertFalse(Files.exists(base.resolve(
                                "projects/project-a/plans/plan-a/tasks/activity/qt-0000000000000001.jsonl")),
                        "no journal entry exists without a journal of the scope"));
    }

    @Test
    @DisplayName("an agent_started detection is journalled with the action restart")
    void agentStartedJournalled(@TempDir Path base) {
        var locks = new TestLocks();
        var queue = QueueStore.ofProject(base, "project-a", locks);
        queue.create(TaskFixtures.queued("qt-0000000000000001", TaskFixtures.ROLE).build());
        var protocol = TaskProtocol.builder(queue, locks)
                .journal(scope -> Optional.of(ActivityJournal.ofScope(base, "project-a", scope, locks)))
                .build();
        var offered = TaskFixtures.task(protocol.wait(TaskFixtures.waitFor(TaskFixtures.GENERATION),
                TaskFixtures.AT));
        protocol.acknowledge(TaskFixtures.call(offered), TaskFixtures.AT.plusSeconds(1));
        var event = new SecurityAudit.Event(EXECUTABLE, TaskFixtures.GENERATION,
                offered.queueTaskId(), 1, TaskFixtures.AT.plusSeconds(2));

        protocol.agentStarted(TaskFixtures.call(offered), event, RE_OFFER_BUDGET);

        var entries = ActivityJournal.ofScope(base, "project-a", TaskFixtures.planScope(), locks)
                .entries("qt-0000000000000001");
        assertAll(
                () -> assertEquals(1, entries.size(),
                        "the detection writes a single journal entry"),
                () -> assertEquals(ActivityEntry.Kind.SIGNAL, entries.getFirst().kind(),
                        "the journal entry records a signal"),
                () -> assertEquals(SignalName.AGENT_STARTED, entries.getFirst().signal().signal(),
                        "the recorded signal is the agent-started one"),
                () -> assertEquals(SignalAction.RESTART, entries.getFirst().signal().action(),
                        "the recorded action restarts the worker"));
    }

    @Test
    @DisplayName("a task whose next attempt exhausts the budget closes lost attempts_spent")
    void budgetSpent(@TempDir Path base) {
        var harness = harness(base);
        var call = acknowledged(harness);

        var reported = assertInstanceOf(CallResult.Accepted.class,
                harness.protocol().report(call, AttemptEnd.WORKER_LOST, 0, TaskFixtures.AT.plusSeconds(2)),
                "the worker-lost report is accepted");

        assertAll(
                () -> assertEquals(Status.CLOSED, reported.task().status(),
                        "the spent budget closes the task"),
                () -> assertEquals(QueueTaskRecord.Outcome.LOST, reported.task().outcome(),
                        "the closed task is lost"),
                () -> assertEquals(QueueTaskRecord.LostReason.ATTEMPTS_SPENT, reported.task().reason(),
                        "the loss reason is the spent attempt budget"));
    }
}
