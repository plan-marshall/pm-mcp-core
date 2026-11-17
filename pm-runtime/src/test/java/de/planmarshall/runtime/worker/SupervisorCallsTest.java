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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.profile.WorkerParameters;
import de.planmarshall.core.role.EffectiveRole;
import de.planmarshall.core.role.RoleArtifact;
import de.planmarshall.core.role.RoleClass;
import de.planmarshall.core.role.RoleForm;
import de.planmarshall.core.store.WorkerState;

/**
 * The supervisor's inbound calls (PM-WORK-3, PM-WORK-7): what each call of {@link WorkerCalls} records, the answer a
 * wait receives, the transitions the lifecycle table admits, and the generation the calls ignore.
 * <p>
 * The clock is the fixture's virtual clock, so a deadline is asserted by moving the clock and never by waiting. The
 * generation is generated and the queue-task id is a literal of the wire shape the queue uses; the bounded wait, the
 * acknowledgement deadline and the silence grace are the test's own values, because the fixture profile declares no
 * worker block (the production values come from the harness profile).
 */

@EnableGeneratorController
@DisplayName("Supervisor calls")
class SupervisorCallsTest {

    /** The bounded wait of the fixture profile; the silence limit adds the grace to it. */
    private static final int BOUNDED_WAIT_SECONDS = 300;

    private static final int SILENCE_GRACE_SECONDS = 600;

    private static final int IDLE_WAKEUPS = 3;

    private static final String TASK = "qt-0123456789abcdef";

    private static final String DIGEST = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @TempDir
    Path base;

    private SupervisorTestFixture fixture;

    private SupervisorEnvironment environment;

    private WorkerParameters parameters;

    private WorkerEnding ending;

    private WorkerSupervisor supervisor;

    private SupervisorCalls calls;

    @BeforeEach
    void setUp() throws IOException {
        fixture = new SupervisorTestFixture(base);
        environment = fixture.environment(SupervisorEnvironment.StartGuard.open(),
                SupervisorEnvironment.EligibilityGate.open());
        parameters = parameters(IDLE_WAKEUPS);
        ending = ending(environment);
        supervisor = new WorkerSupervisor(environment);
        calls = calls(ending, recycling(ending, null), parameters);
    }

    private void withGuard(SupervisorEnvironment.StartGuard guard) {
        environment = fixture.environment(guard, SupervisorEnvironment.EligibilityGate.open());
        ending = ending(environment);
        supervisor = new WorkerSupervisor(environment);
        calls = calls(ending, recycling(ending, null), parameters);
    }

    private void withBounds(RoleArtifact.@Nullable Recycling bounds, WorkerParameters workerParameters) {
        parameters = workerParameters;
        calls = calls(ending, recycling(ending, bounds), parameters);
    }

    private static WorkerParameters parameters(int idleWakeups) {
        return new WorkerParameters(true, OptionalInt.of(270), false, SILENCE_GRACE_SECONDS, idleWakeups,
                BOUNDED_WAIT_SECONDS, false);
    }

    private SupervisorCalls calls(WorkerEnding end, WorkerRecycling recycling, WorkerParameters workerParameters) {
        return new SupervisorCalls(supervisor, end, recycling, workerParameters, _ -> RoleClass.WRITER);
    }

    private static WorkerEnding ending(SupervisorEnvironment env) {
        return new WorkerEnding(env, () -> false, () -> {
        });
    }

    private WorkerRecycling recycling(WorkerEnding end, RoleArtifact.@Nullable Recycling bounds) {
        return new WorkerRecycling(environment, end, supervisor::generations, _ -> Optional.ofNullable(bounds),
                parameters);
    }

    private WorkerGeneration started(EffectiveRole role) {
        var outcomes = supervisor.start(List.of(fixture.warmRequest(role, fixture.workdir)));
        return ((WorkerSupervisor.StartOutcome.Started) outcomes.getFirst()).generation();
    }

    private List<WorkerGeneration> startedFresh(EffectiveRole role, int count) {
        var requests = IntStream.range(0, count).mapToObj(_ -> fixture.freshRequest(role)).toList();
        return supervisor.start(requests).stream()
                .map(outcome -> (WorkerSupervisor.StartOutcome.Started) outcome)
                .map(WorkerSupervisor.StartOutcome.Started::generation)
                .toList();
    }

    @Test
    @DisplayName("a call of an unknown generation is ignored and creates no detection")
    void anUnknownGenerationIsIgnored() {
        calls.callHeartbeat("unknown", fixture.clock.instant());
        var answer = calls.waitOpened("unknown", "", DIGEST);
        fixture.advance(Duration.ofSeconds(BOUNDED_WAIT_SECONDS + SILENCE_GRACE_SECONDS));

        assertAll(
                () -> assertEquals(new WorkerCalls.WaitAnswer.End(), answer, "an unknown generation is told to end"),
                () -> assertTrue(supervisor.generations().isEmpty(), "no generation is created by the call"),
                () -> assertTrue(calls.loss("unknown").isEmpty(), "no detection is created for the call"),
                () -> assertTrue(fixture.fences.isEmpty(), "no generation is fenced by a deadline"),
                () -> assertTrue(fixture.writes.isEmpty(), "no terminal state is written by the call"));
    }

    @Test
    @DisplayName("the first call clears the start-failure guard and settles the generation as waiting")
    void theFirstCallClearsTheGuard() {
        var guard = new StartFailureGuard(fixture.project, List::of, fixture.protocol, fixture.eventSink,
                fixture.clock);
        withGuard(guard);
        var role = fixture.role(RoleClass.WRITER, RoleForm.WARM, 1);
        var generation = started(role);
        for (var ignored = 0; ignored < StartFailureGuard.HOLD_THRESHOLD; ignored++) {
            guard.startFailed(role.artifact().name(), role.harness(), "");
        }

        var held = guard.holds(role.artifact().name(), role.harness());
        calls.callHeartbeat(generation.generationId(), fixture.clock.instant());

        assertAll(
                () -> assertTrue(held, "the guard holds after three counted failures"),
                () -> assertFalse(guard.holds(role.artifact().name(), role.harness()),
                        "the first call clears the held guard"),
                () -> assertEquals(WorkerLifecycle.WAITING,
                        supervisor.generation(generation.generationId()).orElseThrow().lifecycle(),
                        "the first call settles the generation as waiting"),
                () -> assertTrue(calls.detection(generation).firstCallMade(), "the detection records the first call"));
    }

    @Test
    @DisplayName("a wait that names a task is answered with the offer and leases the task")
    void anOfferLeasesTheTask() {
        var generation = started(fixture.role(RoleClass.WRITER, RoleForm.WARM, 1));

        var answer = calls.waitOpened(generation.generationId(), TASK, generation.configurationDigest());
        var leased = supervisor.generation(generation.generationId()).orElseThrow();

        assertAll(
                () -> assertEquals(new WorkerCalls.WaitAnswer.Offered(TASK, generation.configurationDigest()), answer,
                        "the answer offers the task the transport named"),
                () -> assertEquals(TASK, leased.leasedTask(), "the generation holds the offered task"),
                () -> assertEquals(WorkerLifecycle.OFFERED, leased.lifecycle(),
                        "the offer moves the generation to offered"));
    }

    @Test
    @DisplayName("a wait without a task is answered wait again and counts one idle wake-up")
    void aWaitWithoutATaskCountsAnIdleWakeUp() {
        var generation = started(fixture.role(RoleClass.WRITER, RoleForm.WARM, 1));

        var answer = calls.waitOpened(generation.generationId(), "", generation.configurationDigest());
        var idle = supervisor.generation(generation.generationId()).orElseThrow();
        var beforeTheLimit = ending.requestedCause(generation.generationId());
        fixture.advance(Duration.ofSeconds(BOUNDED_WAIT_SECONDS + SILENCE_GRACE_SECONDS));

        assertAll(
                () -> assertEquals(new WorkerCalls.WaitAnswer.WaitAgain(), answer,
                        "a wait with no candidate is answered wait again"),
                () -> assertEquals(1L, idle.idleWakeups(), "the answer counts one idle wake-up"),
                () -> assertEquals(WorkerLifecycle.WAITING, idle.lifecycle(),
                        "the generation waits after the answer"),
                () -> assertTrue(beforeTheLimit.isEmpty(), "an idle wake-up below the bound recycles nothing"),
                () -> assertEquals(Optional.of(EndCause.LOST), ending.requestedCause(generation.generationId()),
                        "the answered wait re-arms the silence limit"));
    }

    @Test
    @DisplayName("a wait without a task at the idle bound recycles and is answered end")
    void aWaitAtTheIdleBoundEndsTheGeneration() {
        withBounds(null, parameters(1));
        var generation = started(fixture.role(RoleClass.WRITER, RoleForm.WARM, 1));

        var answer = calls.waitOpened(generation.generationId(), "", generation.configurationDigest());

        assertAll(
                () -> assertEquals(new WorkerCalls.WaitAnswer.End(), answer,
                        "the bound's idle answer ends the worker"),
                () -> assertEquals(Optional.of(EndCause.RECYCLE), ending.requestedCause(generation.generationId()),
                        "the answer recycles the generation through the end path"));
    }

    @Test
    @DisplayName("a wait of a generation whose end is requested is answered end")
    void aWaitOfAnEndingGenerationIsAnsweredEnd() {
        var generation = started(fixture.role(RoleClass.WRITER, RoleForm.WARM, 1));
        ending.end(generation, EndCause.DRAIN);

        var answer = calls.waitOpened(generation.generationId(), TASK, generation.configurationDigest());

        assertAll(
                () -> assertEquals(new WorkerCalls.WaitAnswer.End(), answer,
                        "an end-requested generation is never offered a task"),
                () -> assertNull(supervisor.generation(generation.generationId()).orElseThrow().leasedTask(),
                        "the refused offer leases nothing"));
    }

    @Test
    @DisplayName("a heartbeat settles the acknowledgement deadline an offer armed")
    void aHeartbeatSettlesTheAcknowledgement() {
        var generation = started(fixture.role(RoleClass.WRITER, RoleForm.WARM, 1));
        calls.waitOpened(generation.generationId(), TASK, generation.configurationDigest());
        fixture.advance(Duration.ofSeconds(1));

        calls.callHeartbeat(generation.generationId(), fixture.clock.instant());
        fixture.advance(Duration.ofSeconds(269));

        assertTrue(ending.requestedCause(generation.generationId()).isEmpty(),
                "an acknowledged offer is not a missed acknowledgement");
    }

    @Test
    @DisplayName("a warm worker's first call arms no acknowledgement deadline")
    void aWarmFirstCallArmsNoAcknowledgement() {
        var generation = started(fixture.role(RoleClass.WRITER, RoleForm.WARM, 1));

        calls.callHeartbeat(generation.generationId(), fixture.clock.instant());
        fixture.advance(Duration.ofSeconds(270));

        assertTrue(ending.requestedCause(generation.generationId()).isEmpty(),
                "a warm worker without an offer owes no acknowledgement");
    }

    @Test
    @DisplayName("an offer no call acknowledges is a missed acknowledgement at its deadline")
    void anUnacknowledgedOfferIsLost() {
        var generation = started(fixture.role(RoleClass.WRITER, RoleForm.WARM, 1));
        calls.waitOpened(generation.generationId(), TASK, generation.configurationDigest());

        fixture.advance(Duration.ofSeconds(270));

        assertAll(
                () -> assertEquals(Optional.of(EndCause.LOST), ending.requestedCause(generation.generationId()),
                        "the missed acknowledgement ends the worker"),
                () -> assertEquals(Optional.of(LossReason.ACK_MISSED),
                        calls.loss(generation.generationId()).map(LossSignal::reason),
                        "the loss names the missed acknowledgement"));
    }

    @Test
    @DisplayName("a call of an ended generation is ignored")
    void aCallOfAnEndedGenerationIsIgnored() {
        var generation = started(fixture.role(RoleClass.WRITER, RoleForm.WARM, 1));
        ending.forceEnd(generation, EndCause.DRAIN);
        supervisor.replaced(ending.exited(generation));

        calls.callHeartbeat(generation.generationId(), fixture.clock.instant());
        var answer = calls.waitOpened(generation.generationId(), TASK, generation.configurationDigest());
        fixture.advance(Duration.ofSeconds(BOUNDED_WAIT_SECONDS + SILENCE_GRACE_SECONDS));

        assertAll(
                () -> assertEquals(new WorkerCalls.WaitAnswer.End(), answer, "an ended generation is told to end"),
                () -> assertNull(supervisor.generation(generation.generationId()).orElseThrow().leasedTask(),
                        "the ended generation leases nothing"),
                () -> assertTrue(ending.requestedCause(generation.generationId()).isEmpty(),
                        "an ended generation is not ended a second time"),
                () -> assertTrue(calls.loss(generation.generationId()).isEmpty(),
                        "no detection is created for the ended generation"));
    }

    @Test
    @DisplayName("a completed turn records the context size and lets the generation execute")
    void aCompletedTurnRecordsTheContext() {
        var generation = started(fixture.role(RoleClass.WRITER, RoleForm.WARM, 1));
        calls.waitOpened(generation.generationId(), TASK, generation.configurationDigest());

        calls.turnCompleted(generation.generationId(), 42_000L);
        var served = supervisor.generation(generation.generationId()).orElseThrow();

        assertAll(
                () -> assertEquals(WorkerState.ContextTokens.measured(42_000L), served.contextTokens(),
                        "the turn's context size is recorded on the generation"),
                () -> assertEquals(WorkerLifecycle.EXECUTING, served.lifecycle(),
                        "an acknowledged offer executes after its turn"));
    }

    @Test
    @DisplayName("a closed task is served, releases the lease and waits again")
    void aClosedTaskIsServedAndWaitsAgain() {
        var generation = started(fixture.role(RoleClass.WRITER, RoleForm.WARM, 1));
        calls.waitOpened(generation.generationId(), TASK, generation.configurationDigest());

        calls.taskClosed(generation.generationId(), TASK);
        var closed = supervisor.generation(generation.generationId()).orElseThrow();

        assertAll(
                () -> assertEquals(List.of(TASK), closed.servedTasks(), "the closed task is served on the generation"),
                () -> assertNull(closed.leasedTask(), "the closed task releases the lease"),
                () -> assertEquals(WorkerLifecycle.WAITING, closed.lifecycle(), "the generation waits after its task"));
    }

    @Test
    @DisplayName("a closed task at the tasks-per-generation bound recycles the generation")
    void aClosedTaskAtTheBoundRecycles() {
        withBounds(new RoleArtifact.Recycling(null, 20_000, 1), parameters(IDLE_WAKEUPS));
        var generation = started(fixture.role(RoleClass.WRITER, RoleForm.WARM, 1));
        calls.waitOpened(generation.generationId(), TASK, generation.configurationDigest());

        calls.taskClosed(generation.generationId(), TASK);

        assertAll(
                () -> assertEquals(Optional.of(EndCause.RECYCLE), ending.requestedCause(generation.generationId()),
                        "the bound's closed task recycles the generation"),
                () -> assertNull(supervisor.generation(generation.generationId()).orElseThrow().leasedTask(),
                        "the recycling generation holds no task"));
    }

    @Test
    @DisplayName("a pause at once ends every generation of the plan by force")
    void aPauseEndsEveryGenerationOfThePlan() {
        var generations = startedFresh(fixture.role(RoleClass.WRITER, RoleForm.FRESH, 2), 2);

        calls.planPaused(fixture.plan);

        assertAll(
                () -> assertEquals(2, generations.size(), "the fixture started two generations of the plan"),
                () -> assertEquals(Optional.of(EndCause.PAUSE_AT_ONCE),
                        ending.requestedCause(generations.get(0).generationId()), "the first ends at once"),
                () -> assertEquals(Optional.of(EndCause.PAUSE_AT_ONCE),
                        ending.requestedCause(generations.get(1).generationId()), "the second ends at once"));
    }

    @Test
    @DisplayName("an enrolment clears the guard of every role on the harness")
    void anEnrolmentClearsTheGuardOfTheHarness() {
        var guard = new StartFailureGuard(fixture.project, List::of, fixture.protocol, fixture.eventSink,
                fixture.clock);
        withGuard(guard);
        var role = fixture.role(RoleClass.WRITER, RoleForm.WARM, 1);
        var generation = started(role);
        for (var ignored = 0; ignored < StartFailureGuard.HOLD_THRESHOLD; ignored++) {
            guard.startFailed(role.artifact().name(), role.harness(), "");
        }

        calls.harnessEnrolled(SupervisorTestFixture.CLIENT);

        assertAll(
                () -> assertFalse(guard.holds(role.artifact().name(), role.harness()),
                        "the enrolment clears the role's guard"),
                () -> assertTrue(supervisor.generation(generation.generationId()).isPresent(),
                        "the running generation stays registered"));
    }
}
