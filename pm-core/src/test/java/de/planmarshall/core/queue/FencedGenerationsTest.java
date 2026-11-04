/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.queue;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The generation fence is persisted beside the queue, so a late call of a fenced generation is refused after a
 * restart and a second fence keeps the instant of the first.
 */
@EnableGeneratorController
@SuppressWarnings("java:S5778")
// the refusal cases call one throwing method per case, by design
@DisplayName("Fenced generations")
class FencedGenerationsTest {

    @Test
    @DisplayName("a fence survives a restart and keeps the instant of the first fence")
    void persists(@TempDir Path base) {
        var queue = QueueStore.ofProject(base, "project-a", new TestLocks());
        var fences = new FencedGenerations(queue.directory(), queue.lockKey(), new TestLocks());
        var at = Instant.ofEpochSecond(Generators.longs(1_600_000_000L, 1_900_000_000L).next());
        fences.fence(QueueFixtures.GENERATION, at);

        var reopened = new FencedGenerations(queue.directory(), queue.lockKey(), new TestLocks());
        reopened.fence(QueueFixtures.GENERATION, at.plusSeconds(Generators.longs(1, 3600).next()));

        assertAll(
                () -> assertTrue(reopened.isFenced(QueueFixtures.GENERATION)),
                () -> assertFalse(reopened.isFenced(QueueFixtures.OTHER_GENERATION)),
                () -> assertEquals(1, reopened.fenced().size()),
                () -> assertEquals(at, reopened.entries().getFirst().fencedAt()));
    }

    @Test
    @DisplayName("the fence document is not a queue task record")
    void notATask(@TempDir Path base) {
        var queue = QueueStore.ofProject(base, "project-a", new TestLocks());
        var fences = new FencedGenerations(queue.directory(), queue.lockKey(), new TestLocks());
        fences.fence(QueueFixtures.GENERATION, Instant.EPOCH);

        assertTrue(queue.queueTaskIds().isEmpty());
    }
}
