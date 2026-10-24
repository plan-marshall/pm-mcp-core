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

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.planmarshall.core.queue.QueueStore;
import de.planmarshall.core.queue.QueueTaskRecord;
import de.planmarshall.core.queue.QueueTaskRecord.AttemptEnd;
import de.planmarshall.core.queue.QueueTaskRecord.LostReason;
import de.planmarshall.core.queue.QueueTaskRecord.Outcome;
import de.planmarshall.core.queue.QueueTaskRecord.Status;

/**
 * A task is done exactly once (Model Work § Execute &amp; Submit): the first valid submit closes it, every later call
 * of that generation is {@code stale_entity}, a late call of a replaced generation is {@code generation_fenced}, and a
 * lease survives a restart.
 */
@SuppressWarnings("java:S5778")
// the refusal cases call one throwing method per case, by design
@DisplayName("Task protocol submits")
class TaskProtocolSubmitTest {

    private static TaskProtocol protocol(QueueStore queue, TestLocks locks) {
        return TaskProtocol.builder(queue, locks).build();
    }

    @Test
    @DisplayName("the first submit closes the task and the second is stale")
    void exactlyOnce(@TempDir Path base) {
        var locks = new TestLocks();
        var queue = QueueStore.ofProject(base, "project-a", locks);
        queue.create(TaskFixtures.queued("qt-0000000000000001", TaskFixtures.ROLE).build());
        var protocol = protocol(queue, locks);
        var call = TaskFixtures.call(TaskFixtures.task(
                protocol.wait(TaskFixtures.waitFor(TaskFixtures.GENERATION), TaskFixtures.AT)));

        var acknowledged = assertInstanceOf(CallResult.Accepted.class,
                protocol.acknowledge(call, TaskFixtures.AT.plusSeconds(1)));
        var first = assertInstanceOf(CallResult.Accepted.class,
                protocol.submit(call, TaskFixtures.AT.plusSeconds(2)));
        var second = assertInstanceOf(CallResult.Refused.class,
                protocol.submit(call, TaskFixtures.AT.plusSeconds(3)));

        assertAll(
                () -> assertEquals(Status.EXECUTING, acknowledged.task().status()),
                () -> assertEquals(Status.CLOSED, first.task().status()),
                () -> assertEquals(Outcome.SUBMITTED, first.task().outcome()),
                () -> assertEquals(AttemptEnd.CLOSED, first.task().currentAttempt().orElseThrow().end()),
                () -> assertEquals(TaskRefusal.STALE_ENTITY, second.refusal()));
    }

    @Test
    @DisplayName("a call on an entity the generation never held is a scope mismatch")
    void otherGeneration(@TempDir Path base) {
        var locks = new TestLocks();
        var queue = QueueStore.ofProject(base, "project-a", locks);
        queue.create(TaskFixtures.queued("qt-0000000000000001", TaskFixtures.ROLE).build());
        var protocol = protocol(queue, locks);
        var task = TaskFixtures.task(protocol.wait(TaskFixtures.waitFor(TaskFixtures.GENERATION), TaskFixtures.AT));

        var refused = assertInstanceOf(CallResult.Refused.class,
                protocol.acknowledge(TaskFixtures.call(task, TaskFixtures.SUCCESSOR), TaskFixtures.AT.plusSeconds(1)));

        assertEquals(TaskRefusal.JOB_SCOPE_MISMATCH, refused.refusal());
    }

    @Test
    @DisplayName("a late call of a replaced generation is refused generation_fenced")
    void lateCallOfReplacedGeneration(@TempDir Path base) {
        var locks = new TestLocks();
        var queue = QueueStore.ofProject(base, "project-a", locks);
        queue.create(TaskFixtures.queued("qt-0000000000000001", TaskFixtures.ROLE).build());
        var protocol = protocol(queue, locks);
        var offered = TaskFixtures.task(protocol.wait(TaskFixtures.waitFor(TaskFixtures.GENERATION),
                TaskFixtures.AT));
        protocol.acknowledge(TaskFixtures.call(offered), TaskFixtures.AT.plusSeconds(1));

        protocol.fence(TaskFixtures.GENERATION, TaskFixtures.AT.plusSeconds(2));
        var successorOffer = TaskFixtures.task(protocol.wait(TaskFixtures.waitFor(TaskFixtures.SUCCESSOR),
                TaskFixtures.AT.plusSeconds(3)));
        protocol.acknowledge(TaskFixtures.call(successorOffer, TaskFixtures.SUCCESSOR),
                TaskFixtures.AT.plusSeconds(4));
        var successorSubmit = assertInstanceOf(CallResult.Accepted.class,
                protocol.submit(TaskFixtures.call(successorOffer, TaskFixtures.SUCCESSOR),
                        TaskFixtures.AT.plusSeconds(5)));
        var late = assertInstanceOf(CallResult.Refused.class,
                protocol.submit(TaskFixtures.call(offered), TaskFixtures.AT.plusSeconds(6)));

        assertAll(
                () -> assertEquals(Outcome.SUBMITTED, successorSubmit.task().outcome()),
                () -> assertEquals(TaskRefusal.GENERATION_FENCED, late.refusal()));
    }

    @Test
    @DisplayName("a lease survives a restart")
    void leaseSurvivesRestart(@TempDir Path base) {
        var locks = new TestLocks();
        var queue = QueueStore.ofProject(base, "project-a", locks);
        queue.create(TaskFixtures.queued("qt-0000000000000001", TaskFixtures.ROLE).build());
        protocol(queue, locks).wait(TaskFixtures.waitFor(TaskFixtures.GENERATION), TaskFixtures.AT);

        var restarted = protocol(queue, locks);
        var other = restarted.wait(TaskFixtures.waitFor(TaskFixtures.SUCCESSOR), TaskFixtures.AT.plusSeconds(1));
        var same = restarted.wait(TaskFixtures.waitFor(TaskFixtures.GENERATION), TaskFixtures.AT.plusSeconds(2));

        assertAll(
                () -> assertEquals(new WaitAnswer.WaitAgain("no_task"), other),
                () -> assertInstanceOf(WaitAnswer.Offered.class, same),
                () -> assertEquals(1, queue.records().getFirst().attempts().size()));
    }

    /**
     * The interleavings of the specification: each step sequence ends with exactly one closed task, and every call
     * after the close is refused.
     */
    static Stream<Arguments> interleavings() {
        return Stream.of(
                Arguments.of("acknowledge then submit", List.of(Step.ACK, Step.SUBMIT), null),
                Arguments.of("submit then submit", List.of(Step.ACK, Step.SUBMIT, Step.SUBMIT),
                        TaskRefusal.STALE_ENTITY),
                Arguments.of("submit then acknowledge", List.of(Step.ACK, Step.SUBMIT, Step.ACK),
                        TaskRefusal.STALE_ENTITY),
                Arguments.of("submit then report", List.of(Step.ACK, Step.SUBMIT, Step.REPORT),
                        TaskRefusal.STALE_ENTITY));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("interleavings")
    @DisplayName("exactly one submit closes the task")
    void exactlyOnceUnderInterleavings(String name, List<Step> steps, TaskRefusal lastRefusal, @TempDir Path base) {
        var locks = new TestLocks();
        var queue = QueueStore.ofProject(base, "project-a", locks);
        queue.create(TaskFixtures.queued("qt-0000000000000001", TaskFixtures.ROLE).build());
        var protocol = protocol(queue, locks);
        var call = TaskFixtures.call(TaskFixtures.task(
                protocol.wait(TaskFixtures.waitFor(TaskFixtures.GENERATION), TaskFixtures.AT)));

        var time = 1;
        CallResult last = null;
        for (var step : steps) {
            time++;
            last = switch (step) {
                case ACK -> protocol.acknowledge(call, TaskFixtures.AT.plusSeconds(time));
                case SUBMIT -> protocol.submit(call, TaskFixtures.AT.plusSeconds(time));
                case REPORT -> protocol.report(call, AttemptEnd.WORKER_LOST, 3, TaskFixtures.AT.plusSeconds(time));
            };
        }

        var records = queue.records().stream().filter(QueueTaskRecord::isClosed).toList();
        var lastResult = last;
        assertAll(
                () -> assertEquals(1, records.size(), name),
                () -> assertEquals(Outcome.SUBMITTED, records.getFirst().outcome(), name),
                () -> assertEquals(1, records.getFirst().attempts().stream()
                        .filter(attempt -> attempt.end() == AttemptEnd.CLOSED).count(), name),
                () -> assertEquals(lastRefusal == null, lastResult instanceof CallResult.Accepted, name));
        if (lastRefusal != null) {
            assertEquals(lastRefusal, ((CallResult.Refused) lastResult).refusal(), name);
        }
    }

    /** One step of an interleaving. */
    enum Step {
        ACK, SUBMIT, REPORT
    }

    @Test
    @DisplayName("a submit before the acknowledgement is refused")
    void submitWithoutAcknowledge(@TempDir Path base) {
        var locks = new TestLocks();
        var queue = QueueStore.ofProject(base, "project-a", locks);
        queue.create(TaskFixtures.queued("qt-0000000000000001", TaskFixtures.ROLE).build());
        var protocol = protocol(queue, locks);
        var call = TaskFixtures.call(TaskFixtures.task(
                protocol.wait(TaskFixtures.waitFor(TaskFixtures.GENERATION), TaskFixtures.AT)));

        var refused = assertInstanceOf(CallResult.Refused.class,
                protocol.submit(call, TaskFixtures.AT.plusSeconds(1)));

        assertAll(
                () -> assertEquals(TaskRefusal.LINK_NOT_AVAILABLE, refused.refusal()),
                () -> assertEquals(Status.OFFERED, queue.records().getFirst().status()));
    }

    @Test
    @DisplayName("a generation that never held a closed task is refused job_scope_mismatch")
    void otherGenerationAfterClose(@TempDir Path base) {
        var locks = new TestLocks();
        var queue = QueueStore.ofProject(base, "project-a", locks);
        queue.create(TaskFixtures.queued("qt-0000000000000001", TaskFixtures.ROLE).build());
        var protocol = protocol(queue, locks);
        var call = TaskFixtures.call(TaskFixtures.task(protocol.wait(TaskFixtures.waitFor(TaskFixtures.GENERATION),
                TaskFixtures.AT)));
        protocol.acknowledge(call, TaskFixtures.AT.plusSeconds(1));
        protocol.submit(call, TaskFixtures.AT.plusSeconds(2));

        var refused = assertInstanceOf(CallResult.Refused.class,
                protocol.submit(new TaskCall(call.queueTaskId(), TaskFixtures.SUCCESSOR),
                        TaskFixtures.AT.plusSeconds(3)));

        assertEquals(TaskRefusal.JOB_SCOPE_MISMATCH, refused.refusal());
    }

    @Test
    @DisplayName("a task whose budget is spent closes lost attempts_spent")
    void lostAttemptsSpent(@TempDir Path base) {
        var locks = new TestLocks();
        var queue = QueueStore.ofProject(base, "project-a", locks);
        queue.create(TaskFixtures.queued("qt-0000000000000001", TaskFixtures.ROLE).build());
        var protocol = protocol(queue, locks);
        var call = TaskFixtures.call(TaskFixtures.task(
                protocol.wait(new WaitRequest(TaskFixtures.GENERATION, TaskFixtures.ROLE, TaskFixtures.DIGEST,
                        TaskFixtures.DIGEST, 0), TaskFixtures.AT)));
        protocol.acknowledge(call, TaskFixtures.AT.plusSeconds(1));

        var reported = assertInstanceOf(CallResult.Accepted.class,
                protocol.report(call, AttemptEnd.WORKER_LOST, 0, TaskFixtures.AT.plusSeconds(2)));

        assertAll(
                () -> assertEquals(Status.CLOSED, reported.task().status()),
                () -> assertEquals(Outcome.LOST, reported.task().outcome()),
                () -> assertEquals(LostReason.ATTEMPTS_SPENT, reported.task().reason()));
    }
}
