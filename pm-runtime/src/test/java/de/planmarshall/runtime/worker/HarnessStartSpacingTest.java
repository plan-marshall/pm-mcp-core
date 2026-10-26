/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.worker;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.service.AuditEvent;

/**
 * The start spacing of one harness (PM-WORK-3, Starts of One Harness): the wait for the previous worker's first call,
 * the compiled cap on that wait, the release by a previous exit and the collision the start-failure guard counts.
 */
@DisplayName("Harness start spacing")
class HarnessStartSpacingTest {

    private static final String CLIENT = "claude";

    private static final String ROLE = "implementer";

    private static final Instant NOW = Instant.parse("2026-10-25T10:00:00Z");

    @Test
    @DisplayName("a second start waits until the previous worker made its first call")
    void aSecondStartWaitsForTheFirstCall() throws Exception {
        var spacing = new HarnessStartSpacing(Duration.ofSeconds(5));
        spacing.awaitTurn(CLIENT);
        var released = new CountDownLatch(1);
        var waiting = new Thread(() -> {
            try {
                spacing.awaitTurn(CLIENT);
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
            } finally {
                released.countDown();
            }
        });

        waiting.start();
        var releasedEarly = released.await(200, TimeUnit.MILLISECONDS);
        spacing.firstCall(CLIENT);
        var releasedAfterTheFirstCall = released.await(2, TimeUnit.SECONDS);
        waiting.join(Duration.ofSeconds(2).toMillis());

        assertAll(
                () -> assertFalse(releasedEarly, "the second start waits for the first call"),
                () -> assertTrue(releasedAfterTheFirstCall, "the first call releases the second start"));
    }

    @Test
    @DisplayName("the wait is capped at the compiled bound")
    void theWaitIsCapped() throws Exception {
        var spacing = new HarnessStartSpacing(Duration.ofMillis(150));
        spacing.awaitTurn(CLIENT);

        var started = System.nanoTime();
        spacing.awaitTurn(CLIENT);
        var waited = Duration.ofNanos(System.nanoTime() - started);

        assertAll(
                () -> assertEquals(HarnessStartSpacing.SPACING_BOUND, Duration.ofSeconds(30),
                        "the compiled bound is the specification's 30 s"),
                () -> assertTrue(waited.compareTo(Duration.ofMillis(100)) >= 0,
                        "the wait runs to its bound: " + waited),
                () -> assertTrue(waited.compareTo(Duration.ofSeconds(5)) < 0,
                        "the wait is capped: " + waited));
    }

    @Test
    @DisplayName("a start after the previous exit proceeds at once")
    void aStartAfterTheExitProceedsAtOnce() throws Exception {
        var spacing = new HarnessStartSpacing(Duration.ofSeconds(5));
        spacing.awaitTurn(CLIENT);
        spacing.ended(CLIENT);

        var started = System.nanoTime();
        spacing.awaitTurn(CLIENT);
        var waited = Duration.ofNanos(System.nanoTime() - started);

        assertTrue(waited.compareTo(Duration.ofMillis(500)) < 0, "the exit releases the next start: " + waited);
    }

    @Test
    @DisplayName("a collision that exits before its first call counts for the guard")
    void aCollisionCountsForTheGuard() throws Exception {
        var spacing = new HarnessStartSpacing(Duration.ofMillis(50));
        var events = new CopyOnWriteArrayList<AuditEvent>();
        var guard = new StartFailureGuard("sample-project", List::of, new SilentProtocol(), events::add,
                InstantSource.fixed(NOW));

        spacing.awaitTurn(CLIENT);
        spacing.awaitTurn(CLIENT);
        guard.startFailed(ROLE, CLIENT, "the harness exited before its first call");

        assertAll(
                () -> assertEquals(1, events.size(), "the collision is one counted start failure"),
                () -> assertEquals(AuditEvent.WORKER_START_FAILED, events.getFirst().event()),
                () -> assertFalse(guard.holds(ROLE, CLIENT), "one collision does not hold the guard"));
    }

    /** The task protocol boundary the collision scenario needs, which closes nothing and fences nothing. */
    private static final class SilentProtocol implements WorkerProtocol {

        @Override
        public void fence(String generationId) {
            // the spacing never fences
        }

        @Override
        public void agentStarted(String generationId, String queueTaskId, int attempt, String executable, Instant at) {
            // detection is another step's contract
        }

        @Override
        public void closeUnavailable(String role, String harness) {
            // the guard's hold is another step's contract
        }
    }
}
