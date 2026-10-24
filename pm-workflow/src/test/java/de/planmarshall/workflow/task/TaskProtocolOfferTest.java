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
import de.planmarshall.core.queue.QueueTaskRecord.Scope;
import de.planmarshall.core.queue.QueueTaskRecord.ScopeType;
import de.planmarshall.core.queue.QueueTaskRecord.Status;

/**
 * The offer rule of the task protocol (Model Work § Offer): the role, the role's current configuration digest, the one
 * writer of a plan, the pause of a plan, the machine's own queue and the order of the queue.
 */
@SuppressWarnings("java:S5778")
// the refusal cases call one throwing method per case, by design
@DisplayName("Task protocol offers")
class TaskProtocolOfferTest {

    private static TaskProtocol protocol(QueueStore queue, TestLocks locks) {
        return TaskProtocol.builder(queue, locks).build();
    }

    @Test
    @DisplayName("the queued task of the role is offered and leased to the caller's generation")
    void offersAndLeases(@TempDir Path base) {
        var locks = new TestLocks();
        var queue = QueueStore.ofProject(base, "project-a", locks);
        var task = TaskFixtures.queued("qt-0000000000000001", TaskFixtures.ROLE).build();
        queue.create(task);

        var answer = protocol(queue, locks).wait(TaskFixtures.waitFor(TaskFixtures.GENERATION), TaskFixtures.AT);

        var offered = assertInstanceOf(WaitAnswer.Offered.class, answer);
        assertAll(
                () -> assertEquals(task.queueTaskId(), offered.task().queueTaskId()),
                () -> assertEquals(Status.OFFERED, offered.task().status()),
                () -> assertEquals(TaskFixtures.GENERATION,
                        offered.task().lease().generationId()),
                () -> assertEquals(TaskFixtures.DIGEST,
                        offered.task().currentAttempt().orElseThrow().configurationDigest()),
                () -> assertEquals(1, offered.task().attempts().size()));
    }

    @Test
    @DisplayName("a generation that holds an unacknowledged offer receives the same offer again")
    void sameOffer(@TempDir Path base) {
        var locks = new TestLocks();
        var queue = QueueStore.ofProject(base, "project-a", locks);
        queue.create(TaskFixtures.queued("qt-0000000000000001", TaskFixtures.ROLE).build());
        var protocol = protocol(queue, locks);
        protocol.wait(TaskFixtures.waitFor(TaskFixtures.GENERATION), TaskFixtures.AT);

        var answer = protocol.wait(TaskFixtures.waitFor(TaskFixtures.GENERATION), TaskFixtures.AT.plusSeconds(1));

        assertAll(
                () -> assertEquals(1, queue.records().size()),
                () -> assertEquals(1, TaskFixtures.task(answer).attempts().size()),
                () -> assertEquals(Status.OFFERED, TaskFixtures.task(answer).status()));
    }

    @Test
    @DisplayName("a generation with an outdated configuration digest is offered no task and ends")
    void outdatedDigest(@TempDir Path base) {
        var locks = new TestLocks();
        var queue = QueueStore.ofProject(base, "project-a", locks);
        queue.create(TaskFixtures.queued("qt-0000000000000001", TaskFixtures.ROLE).build());
        var protocol = protocol(queue, locks);

        var answer = protocol.wait(new WaitRequest(TaskFixtures.GENERATION, TaskFixtures.ROLE,
                TaskFixtures.OTHER_DIGEST, TaskFixtures.DIGEST, 3), TaskFixtures.AT);

        assertAll(
                () -> assertEquals(new WaitAnswer.End("recycled"), answer),
                () -> assertEquals(Status.QUEUED, queue.records().getFirst().status()));
    }

    @Test
    @DisplayName("a task of a paused plan is offered to no worker until the plan is started")
    void pausedPlan(@TempDir Path base) {
        var locks = new TestLocks();
        var queue = QueueStore.ofProject(base, "project-a", locks);
        queue.create(TaskFixtures.queued("qt-0000000000000001", TaskFixtures.ROLE).build());
        var paused = new TaskFixtures.Paused();
        paused.pause(TaskFixtures.PLAN);
        var protocol = TaskProtocol.builder(queue, locks).pause(paused).build();

        var whilePaused = protocol.wait(TaskFixtures.waitFor(TaskFixtures.GENERATION), TaskFixtures.AT);
        var queuedWhilePaused = queue.records().getFirst().status();
        paused.start(TaskFixtures.PLAN);
        var afterStart = protocol.wait(TaskFixtures.waitFor(TaskFixtures.GENERATION), TaskFixtures.AT.plusSeconds(1));

        assertAll(
                () -> assertEquals(new WaitAnswer.WaitAgain("no_task"), whilePaused),
                () -> assertEquals(Status.QUEUED, queuedWhilePaused),
                () -> assertInstanceOf(WaitAnswer.Offered.class, afterStart));
    }

    @Test
    @DisplayName("a writer task of a plan is not offered while another writer task of the plan is leased")
    void oneWriterPerPlan(@TempDir Path base) {
        var locks = new TestLocks();
        var queue = QueueStore.ofProject(base, "project-a", locks);
        queue.create(TaskFixtures.queued("qt-0000000000000001", TaskFixtures.ROLE).build());
        queue.create(TaskFixtures.queued("qt-0000000000000002", TaskFixtures.ROLE).build());
        var protocol = protocol(queue, locks);

        var first = protocol.wait(TaskFixtures.waitFor(TaskFixtures.GENERATION), TaskFixtures.AT);
        var second = protocol.wait(TaskFixtures.waitFor(TaskFixtures.SUCCESSOR), TaskFixtures.AT.plusSeconds(1));

        assertAll(
                () -> assertEquals("qt-0000000000000001", TaskFixtures.task(first).queueTaskId()),
                () -> assertEquals(new WaitAnswer.WaitAgain("no_task"), second));
    }

    @Test
    @DisplayName("consultation tasks are offered before work tasks")
    void consultationFirst(@TempDir Path base) {
        var locks = new TestLocks();
        var queue = QueueStore.ofProject(base, "project-a", locks);
        queue.create(TaskFixtures.queued("qt-0000000000000001", TaskFixtures.REVIEWER).build());
        queue.create(TaskFixtures.consultation("qt-0000000000000002", "qt-0000000000000001"));
        var protocol = protocol(queue, locks);

        var answer = protocol.wait(TaskFixtures.waitFor(TaskFixtures.GENERATION, TaskFixtures.REVIEWER),
                TaskFixtures.AT);

        assertAll(
                () -> assertEquals(QueueTaskRecord.Kind.CONSULTATION, TaskFixtures.task(answer).kind()),
                () -> assertEquals("qt-0000000000000002", TaskFixtures.task(answer).queueTaskId()));
    }

    @Test
    @DisplayName("a machine-scope task is queued and leased in the machine's own queue")
    void machineQueue(@TempDir Path base) {
        var locks = new TestLocks();
        var queue = QueueStore.ofMachine(base, locks);
        var task = TaskFixtures.queued("qt-0000000000000001", TaskFixtures.ROLE)
                .scope(new Scope(ScopeType.MACHINE, null))
                .build();
        queue.create(task);
        var protocol = protocol(queue, locks);

        var answer = protocol.wait(TaskFixtures.waitFor(TaskFixtures.GENERATION), TaskFixtures.AT);

        assertAll(
                () -> assertInstanceOf(WaitAnswer.Offered.class, answer),
                () -> assertTrue(base.resolve("machine/queue/qt-0000000000000001.json").toFile().exists()),
                () -> assertEquals(base.resolve("machine/queue").toAbsolutePath().normalize(), queue.directory()));
    }

    @Test
    @DisplayName("a fenced generation is answered end and is offered no task")
    void fenced(@TempDir Path base) {
        var locks = new TestLocks();
        var queue = QueueStore.ofProject(base, "project-a", locks);
        queue.create(TaskFixtures.queued("qt-0000000000000001", TaskFixtures.ROLE).build());
        var protocol = protocol(queue, locks);
        protocol.fence(TaskFixtures.GENERATION, TaskFixtures.AT);

        var answer = protocol.wait(TaskFixtures.waitFor(TaskFixtures.GENERATION), TaskFixtures.AT.plusSeconds(1));

        assertAll(
                () -> assertEquals(new WaitAnswer.End("fenced"), answer),
                () -> assertNull(queue.records().getFirst().lease()));
    }
}
