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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import de.planmarshall.core.role.RoleClass;
import de.planmarshall.core.role.RoleForm;
import de.planmarshall.core.service.JobRunner;
import de.planmarshall.core.store.WorkerState;
import de.planmarshall.runtime.job.TerminationProtocol;
import de.planmarshall.runtime.worker.WorkerSupervisor.StartOutcome;

/**
 * The one end path of a worker generation (PM-WORK-3, Ending a Worker): every cause ends with its own state and one log
 * record, the forced end reaches the process only through the runner's cancel, the fence, token revocation, slot release
 * and terminal state occur in that order, a pause at once spends no re-offer, and a task-done end starts a successor
 * when work remains.
 */
@EnableTestLogger
@DisplayName("Worker ending")
class WorkerEndingTest {

    private static final SupervisorEnvironment.StartGuard OPEN_GUARD = SupervisorEnvironment.StartGuard.open();

    private static final SupervisorEnvironment.EligibilityGate OPEN_GATE =
            SupervisorEnvironment.EligibilityGate.open();

    @TempDir
    Path base;

    private SupervisorTestFixture fixture;

    private SupervisorEnvironment environment;

    @BeforeEach
    void setUp() throws IOException {
        fixture = new SupervisorTestFixture(base);
        environment = fixture.environment(OPEN_GUARD, OPEN_GATE);
    }

    @ParameterizedTest
    @EnumSource(EndCause.class)
    @DisplayName("every cause ends the generation with its own state and one record")
    void everyCauseEndsWithItsState(EndCause cause) {
        var generation = startedFresh();
        var ending = ending(false, () -> {
        });

        ending.end(generation, cause);
        var terminal = ending.exited(generation);

        var write = fixture.stateWriter.writes.getFirst();
        assertEquals(1, fixture.stateWriter.writes.size());
        assertTrue(terminal.lifecycle().terminal());
        assertEquals(cause.terminalState(), write.state(), "the terminal worker state");
        assertEquals(cause.endReason(), write.endReason(), "the end reason");
        assertEquals(cause == EndCause.START_FAILURE, write.startFailure(), "only a start failure is flagged");
        LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO,
                "ended with cause '" + cause.wireName() + "'");
    }

    @Test
    @DisplayName("the fence, the token revocation, the slot release and the terminal state occur in that order")
    void endStepsAreOrdered() {
        var generation = startedFresh();
        var ending = ending(false, () -> {
        });

        ending.end(generation, EndCause.TASK_DONE);
        ending.exited(generation);

        assertTrue(fixture.order.contains("fence"), fixture.order::toString);
        assertTrue(fixture.order.contains("release"), fixture.order::toString);
        assertTrue(fixture.order.contains("write"), fixture.order::toString);
        assertEquals(List.of("fence", "release", "write"), fixture.order);
        assertEquals(List.of(false), fixture.protocol.revokedAtFence, "the fence precedes the token revocation");
        assertTrue(fixture.jobTokens.revoked(generation.generationId()), "the token is revoked");
        assertTrue(fixture.stateWriter.writes.getFirst().slotReleased(), "the slot is released before the write");
    }

    @Test
    @DisplayName("a lost worker is forced through the runner at once")
    void lostWorkerIsForcedThroughTheRunner() {
        var generation = startedFresh();
        var ending = ending(false, () -> {
        });
        var at = SupervisorTestFixture.NOW.plusSeconds(40);
        var signal = new LossSignal(LossReason.SILENCE, generation.generationId(), at, at, true, null);

        ending.end(generation, signal);
        ending.exited(generation);

        var write = fixture.stateWriter.writes.getFirst();
        assertEquals(List.of(JobRunner.CancelCause.SUPERVISOR_END), fixture.starter.cancels);
        assertEquals(WorkerState.State.LOST, write.state());
        assertEquals(WorkerState.EndReason.SILENCE, write.endReason());
    }

    @Test
    @DisplayName("an instructed end forces through the runner only when the grace expires")
    void instructedEndWaitsForTheGrace() {
        var timers = new ManualTimers();
        fixture.timers = timers;
        environment = fixture.environment(OPEN_GUARD, OPEN_GATE);
        var generation = startedFresh();
        var ending = ending(false, () -> {
        });

        ending.end(generation, EndCause.DRAIN);

        assertTrue(fixture.starter.cancels.isEmpty(), "the end is requested first, not forced");
        timers.fireDue(SupervisorTestFixture.NOW.plus(TerminationProtocol.GRACE_PERIOD));
        assertEquals(List.of(JobRunner.CancelCause.SUPERVISOR_END), fixture.starter.cancels);
    }

    @Test
    @DisplayName("a pause at once does not spend a re-offer and cancels with the pause cause")
    void pauseAtOnceSpendsNoReoffer() {
        var timers = new ManualTimers();
        fixture.timers = timers;
        environment = fixture.environment(OPEN_GUARD, OPEN_GATE);
        var generation = startedFresh();
        var ending = ending(false, () -> {
        });

        ending.end(generation, EndCause.PAUSE_AT_ONCE);
        timers.fireDue(SupervisorTestFixture.NOW.plus(TerminationProtocol.GRACE_PERIOD));

        assertFalse(EndCause.PAUSE_AT_ONCE.spendsReoffer());
        assertEquals(List.of(JobRunner.CancelCause.PLAN_PAUSED_NOW), fixture.starter.cancels);
    }

    @Test
    @DisplayName("a task-done end starts a successor when work remains")
    void taskDoneStartsASuccessor() {
        var generation = startedFresh();
        var successors = new AtomicInteger();
        var ending = ending(true, successors::incrementAndGet);

        ending.end(generation, EndCause.TASK_DONE);
        ending.exited(generation);

        assertEquals(1, successors.get());
    }

    @Test
    @DisplayName("no successor starts without queued work")
    void noSuccessorWithoutQueuedWork() {
        var generation = startedFresh();
        var successors = new AtomicInteger();
        var ending = ending(false, successors::incrementAndGet);

        ending.end(generation, EndCause.TASK_DONE);
        ending.exited(generation);

        assertEquals(0, successors.get());
    }

    private WorkerGeneration startedFresh() {
        var role = fixture.role(RoleClass.WRITER, RoleForm.FRESH, 1);
        var supervisor = new WorkerSupervisor(environment);
        return assertInstanceOf(StartOutcome.Started.class,
                supervisor.start(List.of(fixture.freshRequest(role))).getFirst()).generation();
    }

    private WorkerEnding ending(boolean workQueued, Runnable successor) {
        return new WorkerEnding(environment, () -> workQueued, successor);
    }

    /** The timers of the test: nothing runs until {@link #fireDue(Instant)} is called. */
    private static final class ManualTimers implements WorkerTimers {

        private final List<Entry> entries = new ArrayList<>();

        @Override
        public Cancellable schedule(Instant at, Runnable action) {
            var entry = new Entry(at, action);
            entries.add(entry);
            return () -> entry.cancelled = true;
        }

        void fireDue(Instant now) {
            for (var entry : new ArrayList<>(entries)) {
                if (!entry.cancelled && !entry.at.isAfter(now)) {
                    entry.cancelled = true;
                    entry.action.run();
                }
            }
        }

        private static final class Entry {

            private final Instant at;

            private final Runnable action;

            private boolean cancelled;

            Entry(Instant at, Runnable action) {
                this.at = at;
                this.action = action;
            }
        }
    }
}
