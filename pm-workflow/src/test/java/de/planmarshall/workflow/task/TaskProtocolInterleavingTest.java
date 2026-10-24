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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.planmarshall.core.queue.QueueStore;
import de.planmarshall.core.queue.QueueTaskRecord;
import de.planmarshall.core.queue.QueueTaskRecord.AttemptEnd;
import de.planmarshall.core.queue.QueueTaskRecord.Outcome;

/**
 * Exactly-once submit under random interleavings of worker calls, losses and a fence (Workflow DSL § Verification of
 * the loop-back and the diagrams): whatever order the calls take, the task closes at most once with the outcome of its
 * own transition, and at least one run reaches the submission.
 */
@SuppressWarnings("java:S5778")
// the seeded runs are one scenario per case, by design
@DisplayName("Task protocol interleavings")
class TaskProtocolInterleavingTest {

    private enum Step {
        WAIT, ACK, SUBMIT, WORKER_LOST, WAITED_AGAIN, FENCE
    }

    @ParameterizedTest(name = "seed {0}")
    @ValueSource(ints = {2, 5, 89, 377, 610, 1597})
    @DisplayName("a run reaches the submission and closes the task once")
    void aSubmitIsReachable(int seed, @TempDir Path base) {
        var closed = run(seed, base);

        assertAll(
                () -> assertEquals(1, closed.size()),
                () -> assertEquals(Outcome.SUBMITTED, closed.getFirst().outcome()),
                () -> assertEquals(1, closed.getFirst().attempts().stream()
                        .filter(attempt -> attempt.end() == AttemptEnd.CLOSED).count()));
    }

    @Test
    @DisplayName("every seed closes at most once and only through its own transition")
    void atMostOneClose(@TempDir Path base) throws Exception {
        var submittedRuns = 0;
        for (var seed : new int[]{1, 2, 3, 5, 8, 13, 21, 34, 55, 89, 144, 233, 377, 610, 987, 1597, 2584, 4181,
                6765, 10946}) {
            var closed = run(seed, Files.createTempDirectory(base, "seed-" + seed));
            submittedRuns += closed.stream().filter(task -> task.outcome() == Outcome.SUBMITTED).count();
            assertAll(
                    () -> assertTrue(closed.size() <= 1, "the task closes at most once"),
                    () -> assertTrue(closed.stream().allMatch(task -> task.outcome() == Outcome.SUBMITTED
                                    || task.outcome() == Outcome.LOST),
                            "the run closes the task submitted or lost, never otherwise"),
                    () -> assertTrue(closed.stream().filter(task -> task.outcome() == Outcome.LOST)
                                    .allMatch(task -> task.reason() == QueueTaskRecord.LostReason.ATTEMPTS_SPENT),
                            "a lost close is the spent re-offer budget"));
        }
        assertTrue(submittedRuns > 0, "at least one seed reaches the submission");
    }

    private static List<QueueTaskRecord> run(int seed, Path base) {
        var locks = new TestLocks();
        var queue = QueueStore.ofProject(base, "project-a", locks);
        queue.create(TaskFixtures.queued("qt-0000000000000001", TaskFixtures.ROLE).build());
        var protocol = TaskProtocol.builder(queue, locks).build();
        var random = new Random(seed);
        var fenced = false;

        for (var turn = 0; turn < 40; turn++) {
            var generation = random.nextBoolean() ? TaskFixtures.GENERATION : TaskFixtures.SUCCESSOR;
            var step = Step.values()[random.nextInt(Step.values().length)];
            var at = TaskFixtures.AT.plusSeconds(turn + 1L);
            var task = queue.records().isEmpty() ? null : queue.records().getFirst();
            var call = task == null ? null : TaskFixtures.call(task, generation);
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
                    if (call != null) {
                        protocol.submit(call, at);
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
        return queue.records().stream().filter(QueueTaskRecord::isClosed).toList();
    }
}
