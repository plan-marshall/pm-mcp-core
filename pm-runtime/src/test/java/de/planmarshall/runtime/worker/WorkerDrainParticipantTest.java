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

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.role.RoleClass;
import de.planmarshall.core.role.RoleForm;
import de.planmarshall.core.service.JobRunner;
import de.planmarshall.runtime.worker.WorkerSupervisor.StartOutcome;

/**
 * The supervisor as a drain participant (PM-WORK-3; Runtime &amp; Interaction Model, Drain): idle warm workers end on
 * {@code endIdle}, a running task continues up to the drain timeout, a worker that outlives it is force-ended with the
 * cause {@code drain} and its state left unwritten until its exit, and {@code awaitQuiet} answers at the deadline.
 */
@DisplayName("Worker drain participant")
class WorkerDrainParticipantTest {

    private static final SupervisorEnvironment.StartGuard OPEN_GUARD = SupervisorEnvironment.StartGuard.open();

    private static final SupervisorEnvironment.EligibilityGate OPEN_GATE =
            SupervisorEnvironment.EligibilityGate.open();

    @TempDir
    Path base;

    private SupervisorTestFixture fixture;

    private SupervisorEnvironment environment;

    private WorkerSupervisor supervisor;

    private WorkerEnding ending;

    @BeforeEach
    void setUp() throws IOException {
        fixture = new SupervisorTestFixture(base);
        environment = fixture.environment(OPEN_GUARD, OPEN_GATE);
        supervisor = new WorkerSupervisor(environment);
        ending = new WorkerEnding(environment, () -> false, () -> {
        });
    }

    @Test
    @DisplayName("an idle warm worker ends on endIdle with the drain cause")
    void idleWarmWorkerEndsOnEndIdle() {
        var generation = started(RoleForm.WARM);
        supervisor.replaced(generation.advanceTo(WorkerLifecycle.WAITING));
        var participant = new WorkerDrainParticipant(supervisor, ending);

        participant.endIdle();

        assertTrue(ending.endRequested(generation.generationId()));
        assertEquals(Optional.of(EndCause.DRAIN), ending.requestedCause(generation.generationId()));
    }

    @Test
    @DisplayName("a running task continues up to the drain timeout")
    void runningTaskContinues() {
        var generation = started(RoleForm.FRESH);
        supervisor.replaced(generation.advanceTo(WorkerLifecycle.WAITING)
                .advanceTo(WorkerLifecycle.OFFERED)
                .advanceTo(WorkerLifecycle.ACKNOWLEDGED)
                .advanceTo(WorkerLifecycle.EXECUTING));
        var participant = new WorkerDrainParticipant(supervisor, ending);

        var quiet = assertDoesNotThrowQuietly(participant, SupervisorTestFixture.NOW.minusSeconds(1));

        assertFalse(quiet);
        assertFalse(ending.endRequested(generation.generationId()), "a running task is not ended early");
        assertTrue(fixture.starter.cancels.isEmpty());
    }

    @Test
    @DisplayName("a worker that outlives the timeout is force-ended with the drain cause and left queued")
    void outlivingWorkerIsForceEnded() {
        var generation = started(RoleForm.FRESH);
        supervisor.replaced(generation.advanceTo(WorkerLifecycle.WAITING));
        var participant = new WorkerDrainParticipant(supervisor, ending);

        var terminated = participant.terminateRemaining();

        assertAllTerminated(generation, terminated);
        assertTrue(fixture.stateWriter.writes.isEmpty(), "the state is written only after the exit");
        assertTrue(fixture.protocol.fences.isEmpty(), "the generation is fenced only after the exit");
    }

    @Test
    @DisplayName("awaitQuiet answers true when nothing runs and false at the deadline")
    void awaitQuietAnswersAtTheDeadline() throws Exception {
        var participant = new WorkerDrainParticipant(supervisor, ending);
        var quiet = participant.awaitQuiet(SupervisorTestFixture.NOW);

        var generation = started(RoleForm.FRESH);
        supervisor.replaced(generation.advanceTo(WorkerLifecycle.WAITING));
        var running = participant.awaitQuiet(SupervisorTestFixture.NOW.minusSeconds(1));

        assertTrue(quiet);
        assertFalse(running);
    }

    @Test
    @DisplayName("stopAdmitting stops the supervisor's starts")
    void stopAdmittingStopsStarts() {
        var participant = new WorkerDrainParticipant(supervisor, ending);
        var role = fixture.role(RoleClass.WRITER, RoleForm.FRESH, 1);
        participant.stopAdmitting();

        var outcomes = supervisor.start(List.of(fixture.freshRequest(role)));

        assertFalse(supervisor.admitting());
        assertTrue(outcomes.isEmpty());
    }

    @Test
    @DisplayName("close releases the supervisor's timers")
    void closeReleasesTheTimers() {
        var timers = new RecordingTimers();
        fixture.timers = timers;
        environment = fixture.environment(OPEN_GUARD, OPEN_GATE);
        supervisor = new WorkerSupervisor(environment);
        var participant = new WorkerDrainParticipant(supervisor, new WorkerEnding(environment, () -> false, () -> {
        }));

        participant.close();

        assertTrue(timers.closed);
    }

    private WorkerGeneration started(RoleForm form) {
        var role = fixture.role(RoleClass.WRITER, form, 1);
        var request = form == RoleForm.FRESH ? fixture.freshRequest(role) : fixture.warmRequest(role, fixture.workdir);
        return assertInstanceOf(StartOutcome.Started.class, supervisor.start(List.of(request)).getFirst())
                .generation();
    }

    private static boolean assertDoesNotThrowQuietly(WorkerDrainParticipant participant, Instant deadline) {
        try {
            return participant.awaitQuiet(deadline);
        } catch (InterruptedException | IOException failure) {
            throw new AssertionError(failure);
        }
    }

    private void assertAllTerminated(WorkerGeneration generation, List<String> terminated) {
        assertEquals(List.of(generation.generationId()), terminated);
        assertEquals(Optional.of(EndCause.DRAIN), ending.requestedCause(generation.generationId()));
        assertEquals(List.of(JobRunner.CancelCause.SUPERVISOR_END), fixture.starter.cancels);
    }

    /** The timers of the test: they record the close and schedule nothing. */
    private static final class RecordingTimers implements WorkerTimers {

        private boolean closed;

        @Override
        public Cancellable schedule(Instant at, Runnable action) {
            return () -> {
            };
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
