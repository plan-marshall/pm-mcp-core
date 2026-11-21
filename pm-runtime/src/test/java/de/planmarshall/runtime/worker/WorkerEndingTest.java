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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
import de.planmarshall.runtime.log.RuntimeLogMessages.INFO;
import de.planmarshall.runtime.worker.WorkerSupervisor.StartOutcome;

/**
 * The one end path of a worker generation (PM-WORK-3, Ending a Worker): every cause ends with its own state and one log
 * record, the forced end reaches the process only through the runner's cancel, the fence, token revocation, slot release
 * and terminal state occur in that order, only a lost worker and an off-track restart spend a re-offer, and a task-done
 * end starts a successor when work remains.
 * <p>
 * The loss signal's observed instant is generated; the helper order names, the terminal states and the end reasons are
 * the contracts the production code names.
 */
@EnableGeneratorController
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

        var write = fixture.writes.getFirst();
        assertEquals(1, fixture.writes.size(), "the terminal state is written once");
        assertTrue(terminal.lifecycle().terminal(), "the worker reaches its terminal lifecycle");
        assertEquals(switch (cause) {
            case START_FAILURE -> WorkerLifecycle.START_FAILED;
            case LOST -> WorkerLifecycle.LOST;
            default -> WorkerLifecycle.ENDED;
        }, terminal.lifecycle(), "a failed start reaches its own terminal state");
        assertEquals(cause.terminalState(), write.state(), "the terminal worker state");
        assertEquals(cause.endReason(), write.endReason(), "the written end reason names the cause");
        assertEquals(cause == EndCause.START_FAILURE, write.startFailure(), "only a start failure is flagged");
        LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO,
                INFO.WORKER_GENERATION_ENDED.resolveIdentifierString());
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

        assertTrue(fixture.order.contains("fence"), "the end fences the generation");
        assertTrue(fixture.order.contains("release"), "the end releases the slot");
        assertTrue(fixture.order.contains("write"), "the end writes the terminal state");
        assertEquals(List.of("fence", "release", "write"), fixture.order,
                "the end steps run in fence, release, write order");
        assertEquals(List.of(false), fixture.revokedAtFence, "the fence precedes the token revocation");
        assertTrue(fixture.jobTokens.revoked(generation.generationId()), "the token is revoked");
        assertTrue(fixture.writes.getFirst().slotReleased(), "the slot is released before the write");
    }

    @Test
    @DisplayName("a lost worker is forced through the runner at once")
    void lostWorkerIsForcedThroughTheRunner() {
        var generation = startedFresh();
        var ending = ending(false, () -> {
        });
        var at = SupervisorTestFixture.NOW.plusSeconds(Generators.integers(1, 120).next());
        var signal = new LossSignal(LossReason.SILENCE, generation.generationId(), at, at, true, null);

        ending.end(generation, signal);
        ending.exited(generation);

        var write = fixture.writes.getFirst();
        assertEquals(List.of(JobRunner.CancelCause.SUPERVISOR_END), fixture.cancels,
                "the silence loss cancels with supervisor end");
        assertEquals(WorkerState.State.LOST, write.state(), "the lost worker writes its lost state");
        assertEquals(WorkerState.EndReason.SILENCE, write.endReason(), "the write names the silence");
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

        assertTrue(fixture.cancels.isEmpty(), "the end is requested first, not forced");
        timers.fireDue(SupervisorTestFixture.NOW.plus(TerminationProtocol.GRACE_PERIOD));
        assertEquals(List.of(JobRunner.CancelCause.SUPERVISOR_END), fixture.cancels,
                "the grace expiry forces the end");
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

        assertFalse(EndCause.PAUSE_AT_ONCE.spendsReoffer(), "a pause at once spends no re-offer");
        assertEquals(List.of(JobRunner.CancelCause.PLAN_PAUSED_NOW), fixture.cancels,
                "the pause cancels with the paused-now cause");
    }

    @Test
    @DisplayName("only a lost worker and an off-track restart spend a re-offer")
    void onlyLostAndOffTrackSpendAReoffer() {
        assertAll(
                () -> assertTrue(EndCause.LOST.spendsReoffer(), "a lost worker is offered again"),
                () -> assertTrue(EndCause.OFF_TRACK.spendsReoffer(), "an off-track restart is offered again"),
                () -> assertFalse(EndCause.DRAIN.spendsReoffer(), "a drained task stays queued"),
                () -> assertFalse(EndCause.START_FAILURE.spendsReoffer(), "a start failure spends none"),
                () -> assertFalse(EndCause.PAUSE_AT_ONCE.spendsReoffer(), "a paused task spends none"),
                () -> assertFalse(EndCause.TASK_DONE.spendsReoffer(), "a served task is done"),
                () -> assertFalse(EndCause.RECYCLE.spendsReoffer(), "a recycle serves its task"));
    }

    @Test
    @DisplayName("a task-done end starts a successor when work remains")
    void taskDoneStartsASuccessor() {
        var generation = startedFresh();
        var successors = new AtomicInteger();
        var ending = ending(true, successors::incrementAndGet);

        ending.end(generation, EndCause.TASK_DONE);
        ending.exited(generation);

        assertEquals(1, successors.get(), "a task-done end starts one successor");
    }

    @Test
    @DisplayName("no successor starts without queued work")
    void noSuccessorWithoutQueuedWork() {
        var generation = startedFresh();
        var successors = new AtomicInteger();
        var ending = ending(false, successors::incrementAndGet);

        ending.end(generation, EndCause.TASK_DONE);
        ending.exited(generation);

        assertEquals(0, successors.get(), "no successor starts without queued work");
    }

    @Test
    @DisplayName("the request survives the exit until the terminal generation is registered")
    void theRequestSurvivesTheExitUntilCompleted() {
        var generation = startedFresh();
        var ending = ending(false, () -> {
        });

        ending.end(generation, EndCause.TASK_DONE);
        ending.exited(generation);
        var requestedAfterExit = ending.endRequested(generation.generationId());
        ending.completed(generation.generationId());

        assertTrue(requestedAfterExit, "the end stays requested after the exit");
        assertFalse(ending.endRequested(generation.generationId()), "the completion removes the request");
    }

    @Test
    @DisplayName("a forced end between the exit and the registration sends no cancel and installs nothing")
    void aForcedEndAfterTheExitSendsNoCancel() {
        var timers = new ManualTimers();
        fixture.timers = timers;
        environment = fixture.environment(OPEN_GUARD, OPEN_GATE);
        var generation = startedFresh();
        var ending = ending(false, () -> {
        });
        ending.end(generation, EndCause.TASK_DONE);
        ending.exited(generation);

        ending.forceEnd(generation, EndCause.PAUSE_AT_ONCE);
        ending.end(generation, EndCause.DRAIN);
        timers.fireDue(SupervisorTestFixture.NOW.plus(TerminationProtocol.GRACE_PERIOD));
        var cause = ending.requestedCause(generation.generationId());
        ending.completed(generation.generationId());

        assertTrue(fixture.cancels.isEmpty(), "no cancel reaches a worker that exited");
        assertEquals(Optional.of(EndCause.TASK_DONE), cause, "the claimed end keeps its own cause");
        assertFalse(ending.endRequested(generation.generationId()), "no second request is left behind");
    }

    @Test
    @DisplayName("a retry after a failed successor start logs the end once and starts no second successor")
    void aRetryAfterAFailedSuccessorLogsOnce() {
        var generation = startedFresh();
        var successors = new AtomicInteger();
        var ending = ending(true, () -> {
            successors.incrementAndGet();
            throw new IllegalStateException(Generators.nonBlankStrings().next());
        });
        ending.end(generation, EndCause.TASK_DONE);
        assertThrows(IllegalStateException.class, () -> ending.exited(generation),
                "the failed successor start fails the end");

        var terminal = ending.exited(generation);

        assertTrue(terminal.terminal(), "the retry completes the end");
        assertEquals(1, successors.get(), "the successor start runs once across both");
        LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO,
                INFO.WORKER_GENERATION_ENDED.resolveIdentifierString());
    }

    private WorkerGeneration startedFresh() {
        var role = fixture.role(RoleClass.WRITER, RoleForm.FRESH, 1);
        var supervisor = new WorkerSupervisor(environment);
        return assertInstanceOf(StartOutcome.Started.class,
                supervisor.start(List.of(fixture.freshRequest(role))).getFirst(),
                "the ending test starts a fresh worker").generation();
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
