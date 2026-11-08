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

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.runtime.testing.generators.RuntimeGenerators;

/**
 * The start spacing of one harness (PM-WORK-3, Starts of One Harness): the wait for the previous worker's first call,
 * the compiled cap on that wait, the release by a previous exit and the collision the start-failure guard counts.
 * <p>
 * The client id, the role, the project and the failure text are generated in {@code @BeforeEach}; the 30-second
 * spacing bound and the timing tolerances are the contract values the test pins.
 */
@EnableGeneratorController
@DisplayName("Harness start spacing")
class HarnessStartSpacingTest {

    private static final Instant NOW = Instant.parse("2026-10-25T10:00:00Z");

    private String client;

    private String role;

    private String project;

    private String failure;

    @BeforeEach
    void draw() {
        client = RuntimeGenerators.words().next();
        role = RuntimeGenerators.words().next();
        project = RuntimeGenerators.words().next();
        failure = Generators.letterStrings(3, 40).next();
    }

    @Test
    @DisplayName("a second start waits until the previous worker made its first call")
    void aSecondStartWaitsForTheFirstCall() throws Exception {
        var spacing = new HarnessStartSpacing(Duration.ofSeconds(5));
        spacing.awaitTurn(client);
        var released = new CountDownLatch(1);
        var waiting = new Thread(() -> {
            try {
                spacing.awaitTurn(client);
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
            } finally {
                released.countDown();
            }
        });

        waiting.start();
        var releasedEarly = released.await(200, TimeUnit.MILLISECONDS);
        spacing.firstCall(client);
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
        spacing.awaitTurn(client);

        var started = System.nanoTime();
        spacing.awaitTurn(client);
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
        spacing.awaitTurn(client);
        spacing.ended(client);

        var started = System.nanoTime();
        spacing.awaitTurn(client);
        var waited = Duration.ofNanos(System.nanoTime() - started);

        assertTrue(waited.compareTo(Duration.ofMillis(500)) < 0, "the exit releases the next start: " + waited);
    }

    @Test
    @DisplayName("a collision that exits before its first call counts for the guard")
    void aCollisionCountsForTheGuard() throws Exception {
        var spacing = new HarnessStartSpacing(Duration.ofMillis(50));
        var events = new CopyOnWriteArrayList<AuditEvent>();
        var guard = new StartFailureGuard(project, List::of, new SilentProtocol(), events::add,
                InstantSource.fixed(NOW));

        spacing.awaitTurn(client);
        spacing.awaitTurn(client);
        guard.startFailed(role, client, failure);

        assertAll(
                () -> assertEquals(1, events.size(), "the collision is one counted start failure"),
                () -> assertEquals(AuditEvent.WORKER_START_FAILED, events.getFirst().event()),
                () -> assertFalse(guard.holds(role, client), "one collision does not hold the guard"));
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
        public void offTrack(OffTrackReport report) {
            // spacing raises no activity signal
        }

        @Override
        public void closeUnavailable(String role, String harness) {
            // the guard's hold is another step's contract
        }
    }
}
