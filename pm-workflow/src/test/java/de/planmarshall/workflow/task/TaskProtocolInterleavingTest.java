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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Random;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.planmarshall.core.queue.QueueStore;
import de.planmarshall.core.queue.QueueTaskRecord;
import de.planmarshall.core.queue.QueueTaskRecord.AttemptEnd;
import de.planmarshall.core.queue.QueueTaskRecord.Outcome;

/**
 * Exactly-once submit under random interleavings of worker calls, losses and a fence (Workflow DSL § Verification of
 * the loop-back and the diagrams): whatever order the calls take, the task closes at most once, and a close is always
 * the accepted submission with exactly one {@code closed} attempt end.
 */
@DisplayName("Task protocol interleavings")
class TaskProtocolInterleavingTest {

    private enum Step {
        WAIT, ACK, SUBMIT, WORKER_LOST, WAITED_AGAIN, FENCE
    }

    @ParameterizedTest(name = "seed {0}")
    @ValueSource(ints = {1, 2, 3, 5, 8, 13, 21, 34, 55, 89, 144, 233, 377, 610, 987, 1597, 2584, 4181, 6765, 10946})
    @DisplayName("the task closes at most once, whatever the order of the calls")
    void exactlyOnce(int seed, @TempDir Path base) {
        var locks = new TestLocks();
        var queue = QueueStore.ofProject(base, "project-a", locks);
        queue.create(TaskFixtures.queued("qt-0000000000000001", TaskFixtures.ROLE).build());
        var protocol = TaskProtocol.builder(queue, locks).build();
        var random = new Random(seed);
        var fenced = false;
        var acceptedSubmits = 0;

        for (var turn = 0; turn < 40; turn++) {
            var generation = random.nextBoolean() ? TaskFixtures.GENERATION : TaskFixtures.SUCCESSOR;
            var step = Step.values()[random.nextInt(Step.values().length)];
            var at = TaskFixtures.AT.plusSeconds(turn + 1L);
            var record = queue.records().isEmpty() ? null : queue.records().getFirst();
            var call = record == null ? null : TaskFixtures.call(record, generation);
            switch (step) {
                case WAIT -> {
                    var answer = protocol.wait(TaskFixtures.waitFor(generation), at);
                    if (fenced && TaskFixtures.GENERATION.equals(generation)) {
                        assertInstanceOf(WaitAnswer.End.class, answer, "a fenced generation is offered no task");
                    }
                }
                case ACK -> {
                    if (call != null) {
                        protocol.acknowledge(call, at);
                    }
                }
                case SUBMIT -> {
                    if (call != null && protocol.submit(call, at) instanceof CallResult.Accepted) {
                        acceptedSubmits++;
                    }
                }
                case WORKER_LOST -> {
                    if (call != null) {
                        protocol.report(call, AttemptEnd.WORKER_LOST, 3, at);
                    }
                }
                case WAITED_AGAIN -> {
                    if (call != null) {
                        protocol.report(call, AttemptEnd.WAITED_AGAIN, 3, at);
                    }
                }
                case FENCE -> {
                    if (TaskFixtures.GENERATION.equals(generation)) {
                        protocol.fence(TaskFixtures.GENERATION, at);
                        fenced = true;
                    }
                }
            }
        }

        var records = queue.records();
        var closed = records.stream().filter(QueueTaskRecord::isClosed).toList();
        var closedEnds = records.stream().flatMap(task -> task.attempts().stream())
                .filter(attempt -> attempt.end() == AttemptEnd.CLOSED).count();
        var finalClosed = closed.size();
        var finalSubmits = acceptedSubmits;
        var submitted = closed.stream().filter(task -> task.outcome() == Outcome.SUBMITTED).toList();
        assertAll(
                () -> assertTrue(finalClosed <= 1, "the task closes at most once"),
                () -> assertTrue(finalSubmits <= 1, "at most one accepted submit"),
                () -> assertEquals(finalSubmits, submitted.size(), "an accepted submit is the one submitted close"),
                () -> assertEquals(closedEnds, submitted.size(), "a submitted close has exactly one closed attempt end"),
                () -> assertTrue(closed.stream().allMatch(task -> task.outcome() == Outcome.SUBMITTED
                                || task.outcome() == Outcome.LOST),
                        "the run closes the task submitted or lost, never otherwise"),
                () -> assertTrue(closed.stream().filter(task -> task.outcome() == Outcome.LOST)
                                .allMatch(task -> task.reason() == QueueTaskRecord.LostReason.ATTEMPTS_SPENT),
                        "a lost close is the spent re-offer budget"));
    }
}
