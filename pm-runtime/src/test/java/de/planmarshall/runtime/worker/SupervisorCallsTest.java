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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
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
import de.planmarshall.runtime.launch.LaunchRequest;
import de.planmarshall.runtime.testing.generators.RuntimeGenerators;

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

    /** The acknowledgement deadline of the fixture parameters, in seconds. */
    private static final int ACK_DEADLINE_SECONDS = 270;

    private static final String TASK = "qt-0123456789abcdef";

    /** A second task, which no generation of these tests leases. */
    private static final String OTHER_TASK = "qt-fedcba9876543210";

    private static final int TOKEN_BUDGET = 20_000;

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
        return new WorkerParameters(true, ACK_DEADLINE_SECONDS, false, SILENCE_GRACE_SECONDS,
                idleWakeups, BOUNDED_WAIT_SECONDS, false);
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
                fixture.tasks, parameters);
    }

    private WorkerGeneration started(EffectiveRole role) {
        var outcomes = supervisor.start(List.of(fixture.warmRequest(role, fixture.workdir)));
        return ((WorkerSupervisor.StartOutcome.Started) outcomes.getFirst()).generation();
    }

    /** @return a warm generation that went through an offer and a turn and now holds its task */
    private WorkerGeneration holdingGeneration() {
        var generation = started(fixture.role(RoleClass.WRITER, RoleForm.WARM, 1));
        calls.waitOpened(generation.generationId(), TASK, generation.configurationDigest());
        calls.callHeartbeat(generation.generationId(), fixture.clock.instant());
        calls.turnCompleted(generation.generationId(), 1_000L);
        calls.holdOpened(generation.generationId(), TASK, fixture.clock.instant());
        return generation;
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
        var recycled = supervisor.generation(generation.generationId()).orElseThrow();

        assertAll(
                () -> assertEquals(new WorkerCalls.WaitAnswer.End(), answer,
                        "the bound's idle answer ends the worker"),
                () -> assertEquals(Optional.of(EndCause.RECYCLE), ending.requestedCause(generation.generationId()),
                        "the answer recycles the generation through the end path"),
                () -> assertEquals(1L, recycled.idleWakeups(),
                        "the ending generation records the count its answer reached"));
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
        var atTheOfferDeadline = ending.requestedCause(generation.generationId());
        fixture.advance(Duration.ofSeconds(2));

        assertAll(
                () -> assertTrue(atTheOfferDeadline.isEmpty(),
                        "an acknowledged offer is not a missed acknowledgement"),
                () -> assertTrue(ending.requestedCause(generation.generationId()).isEmpty(),
                        "the cleared deadline never fires late"));
    }

    @Test
    @DisplayName("a first call that is a wait clears the guard like any other call")
    void aWaitAsTheFirstCallClearsTheGuard() {
        var guard = new StartFailureGuard(fixture.project, List::of, fixture.protocol, fixture.eventSink,
                fixture.clock);
        withGuard(guard);
        var role = fixture.role(RoleClass.WRITER, RoleForm.WARM, 1);
        var generation = started(role);
        for (var ignored = 0; ignored < StartFailureGuard.HOLD_THRESHOLD; ignored++) {
            guard.startFailed(role.artifact().name(), role.harness(), "");
        }

        calls.waitOpened(generation.generationId(), TASK, generation.configurationDigest());

        assertAll(
                () -> assertFalse(guard.holds(role.artifact().name(), role.harness()),
                        "a first call that is a wait clears the held guard"),
                () -> assertTrue(calls.detection(generation).firstCallMade(),
                        "the wait records the generation's first call"));
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
        ending.completed(generation.generationId());

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
    @DisplayName("a generation of a role with no class has no detection, and the failure names the role")
    void aRoleWithNoClassIsAnIllegalState() {
        var generation = started(fixture.role(RoleClass.WRITER, RoleForm.WARM, 1));
        var unclassified = new SupervisorCalls(supervisor, ending, recycling(ending, null), parameters, _ -> null);

        var failure = assertThrows(IllegalStateException.class, () -> unclassified.detection(generation),
                "a role with no class is an illegal state");

        assertEquals("The role '" + generation.role() + "' has no class", failure.getMessage(),
                "the failure names the role without a class");
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
    @DisplayName("a hold moves the generation to holding, keeps its lease and its slot")
    void aHoldMovesTheGenerationToHolding() {
        var generation = started(fixture.role(RoleClass.WRITER, RoleForm.WARM, 1));
        calls.waitOpened(generation.generationId(), TASK, generation.configurationDigest());
        calls.callHeartbeat(generation.generationId(), fixture.clock.instant());
        calls.turnCompleted(generation.generationId(), 1_000L);

        calls.holdOpened(generation.generationId(), TASK, fixture.clock.instant());
        var holding = supervisor.generation(generation.generationId()).orElseThrow();
        fixture.advance(Duration.ofSeconds(BOUNDED_WAIT_SECONDS + SILENCE_GRACE_SECONDS));

        assertAll(
                () -> assertEquals(WorkerLifecycle.HOLDING, holding.lifecycle(),
                        "the hold moves the generation to holding"),
                () -> assertEquals(TASK, holding.leasedTask(), "a hold keeps the lease of its task"),
                () -> assertEquals(generation.slot(), holding.slot(), "a hold keeps the model slot"),
                () -> assertTrue(ending.requestedCause(generation.generationId()).isEmpty(),
                        "a held wait suppresses the silence signal"));
    }

    @Test
    @DisplayName("an answer delivered to a holding generation leaves the hold")
    void anAnswerDeliveredLeavesTheHold() {
        var generation = holdingGeneration();

        calls.answerDelivered(generation.generationId(), fixture.clock.instant());

        assertEquals(WorkerLifecycle.EXECUTING,
                supervisor.generation(generation.generationId()).orElseThrow().lifecycle(),
                "the answer ends the hold and resumes the execution");
    }

    @Test
    @DisplayName("a hold opened at the budget recycles instead of holding with no recycle")
    void aHoldOpenedAtTheBudgetRecycles() {
        withBounds(new RoleArtifact.Recycling(null, TOKEN_BUDGET, null), parameters(IDLE_WAKEUPS));
        var generation = started(fixture.role(RoleClass.WRITER, RoleForm.WARM, 1));
        calls.waitOpened(generation.generationId(), TASK, generation.configurationDigest());
        calls.callHeartbeat(generation.generationId(), fixture.clock.instant());
        calls.turnCompleted(generation.generationId(), TOKEN_BUDGET);

        calls.holdOpened(generation.generationId(), TASK, fixture.clock.instant());

        assertAll(
                () -> assertEquals(Optional.of(EndCause.RECYCLE),
                        ending.requestedCause(generation.generationId()),
                        "the hold's turn decides the budget and recycles"),
                () -> assertEquals(1, fixture.holdEnds.size(), "the hold of the budget is ended once"));
    }

    @Test
    @DisplayName("a closed hold resumes the execution and re-arms the silence limit")
    void aClosedHoldResumesTheExecution() {
        var generation = started(fixture.role(RoleClass.WRITER, RoleForm.WARM, 1));
        calls.waitOpened(generation.generationId(), TASK, generation.configurationDigest());
        calls.callHeartbeat(generation.generationId(), fixture.clock.instant());
        calls.turnCompleted(generation.generationId(), 1_000L);
        calls.holdOpened(generation.generationId(), TASK, fixture.clock.instant());

        calls.holdClosed(generation.generationId(), TASK, fixture.clock.instant());
        var resumed = supervisor.generation(generation.generationId()).orElseThrow();
        fixture.advance(Duration.ofSeconds(BOUNDED_WAIT_SECONDS + SILENCE_GRACE_SECONDS));

        assertAll(
                () -> assertEquals(WorkerLifecycle.EXECUTING, resumed.lifecycle(),
                        "the close resumes the execution"),
                () -> assertEquals(Optional.of(EndCause.LOST), ending.requestedCause(generation.generationId()),
                        "the close re-arms the silence limit"));
    }

    @Test
    @DisplayName("a hold the transition table cannot admit is refused and leaves the state")
    void aRefusedHoldTransitionIsRefused() {
        var generation = started(fixture.role(RoleClass.WRITER, RoleForm.WARM, 1));

        calls.holdOpened(generation.generationId(), TASK, fixture.clock.instant());
        var refused = supervisor.generation(generation.generationId()).orElseThrow();

        assertAll(
                () -> assertEquals(WorkerLifecycle.STARTING, refused.lifecycle(),
                        "the refused hold leaves the generation starting"),
                () -> assertNull(refused.leasedTask(), "the refused hold leases nothing"));
    }

    @Test
    @DisplayName("a close of a generation that holds nothing is refused")
    void aCloseOfANonHoldingGenerationIsRefused() {
        var generation = started(fixture.role(RoleClass.WRITER, RoleForm.WARM, 1));

        calls.holdClosed(generation.generationId(), TASK, fixture.clock.instant());

        assertEquals(WorkerLifecycle.STARTING,
                supervisor.generation(generation.generationId()).orElseThrow().lifecycle(),
                "the refused close leaves the generation starting");
    }

    @Test
    @DisplayName("a hold that names another task than the lease is refused")
    void aHoldOfAnotherTaskIsRefused() {
        var generation = started(fixture.role(RoleClass.WRITER, RoleForm.WARM, 1));
        calls.waitOpened(generation.generationId(), TASK, generation.configurationDigest());
        calls.callHeartbeat(generation.generationId(), fixture.clock.instant());
        calls.turnCompleted(generation.generationId(), 1_000L);

        calls.holdOpened(generation.generationId(), OTHER_TASK, fixture.clock.instant());
        var refused = supervisor.generation(generation.generationId()).orElseThrow();

        assertAll(
                () -> assertEquals(WorkerLifecycle.EXECUTING, refused.lifecycle(),
                        "the refused hold leaves the execution"),
                () -> assertEquals(TASK, refused.leasedTask(), "the refused hold keeps the lease it had"));
    }

    @Test
    @DisplayName("a held generation that reports its budget ends the hold and is recycled")
    void aHeldTurnAtTheBudgetEndsTheHold() {
        withBounds(new RoleArtifact.Recycling(null, TOKEN_BUDGET, null), parameters(IDLE_WAKEUPS));
        var generation = started(fixture.role(RoleClass.WRITER, RoleForm.WARM, 1));
        calls.waitOpened(generation.generationId(), TASK, generation.configurationDigest());
        calls.callHeartbeat(generation.generationId(), fixture.clock.instant());
        calls.turnCompleted(generation.generationId(), 1_000L);
        calls.holdOpened(generation.generationId(), TASK, fixture.clock.instant());

        calls.turnCompleted(generation.generationId(), TOKEN_BUDGET);

        assertAll(
                () -> assertEquals(1, fixture.holdEnds.size(), "the held turn reports one hold end"),
                () -> assertEquals(TASK, fixture.holdEnds.getFirst().queueTaskId(),
                        "the hold end names the held task"),
                () -> assertEquals(WorkerLifecycle.HOLDING,
                        supervisor.generation(generation.generationId()).orElseThrow().lifecycle(),
                        "the generation holds until the end path runs"),
                () -> assertEquals(Optional.of(EndCause.RECYCLE),
                        ending.requestedCause(generation.generationId()), "the budget recycles the generation"));
    }

    @Test
    @DisplayName("the hold entries of an unknown generation are ignored")
    void theHoldEntriesIgnoreAnUnknownGeneration() {
        calls.holdOpened("unknown", TASK, fixture.clock.instant());
        calls.holdClosed("unknown", TASK, fixture.clock.instant());
        calls.answerDelivered("unknown", fixture.clock.instant());
        // a detection the calls had created would fence at its silence or acknowledgement deadline: past both, nothing
        fixture.advance(Duration.ofSeconds(BOUNDED_WAIT_SECONDS + SILENCE_GRACE_SECONDS));

        assertAll(
                () -> assertTrue(supervisor.generations().isEmpty(), "no generation is created by the call"),
                () -> assertTrue(calls.loss("unknown").isEmpty(), "no detection is created for the call"),
                () -> assertTrue(fixture.fences.isEmpty(), "no generation is fenced by the hold entries"),
                () -> assertTrue(fixture.writes.isEmpty(), "no terminal state is written by the hold entries"));
    }

    @Test
    @DisplayName("an answer's deadline fires the missed acknowledgement at it and not before")
    void anUnacknowledgedAnswerIsLostAtItsDeadline() {
        var generation = holdingGeneration();

        calls.answerDelivered(generation.generationId(), fixture.clock.instant());
        fixture.advance(Duration.ofSeconds(ACK_DEADLINE_SECONDS - 1));
        var beforeTheDeadline = ending.requestedCause(generation.generationId());
        fixture.advance(Duration.ofSeconds(1));

        assertAll(
                () -> assertTrue(beforeTheDeadline.isEmpty(), "the answer's deadline has not fired yet"),
                () -> assertEquals(Optional.of(EndCause.LOST), ending.requestedCause(generation.generationId()),
                        "the missed answer acknowledgement ends the worker"),
                () -> assertEquals(Optional.of(LossReason.ACK_MISSED),
                        calls.loss(generation.generationId()).map(LossSignal::reason),
                        "the loss names the missed acknowledgement"));
    }

    @Test
    @DisplayName("an acknowledged answer clears the deadline before it fires")
    void anAcknowledgedAnswerClearsTheDeadline() {
        var generation = holdingGeneration();

        calls.answerDelivered(generation.generationId(), fixture.clock.instant());
        fixture.advance(Duration.ofSeconds(ACK_DEADLINE_SECONDS - 1));
        calls.callHeartbeat(generation.generationId(), fixture.clock.instant());
        fixture.advance(Duration.ofSeconds(2));

        assertTrue(ending.requestedCause(generation.generationId()).isEmpty(),
                "an acknowledged answer never fires the deadline");
    }

    @Test
    @DisplayName("a held wait that names another task is never offered and keeps the hold closable")
    void aHeldWaitIsNeverOfferedATask() {
        var generation = holdingGeneration();

        var answer = calls.waitOpened(generation.generationId(), OTHER_TASK, generation.configurationDigest());
        calls.holdClosed(generation.generationId(), TASK, fixture.clock.instant());
        var closed = supervisor.generation(generation.generationId()).orElseThrow();

        assertAll(
                () -> assertEquals(new WorkerCalls.WaitAnswer.WaitAgain(), answer,
                        "a held wait is answered wait again"),
                () -> assertEquals(TASK, closed.leasedTask(), "a held wait keeps the lease of the hold"),
                () -> assertEquals(WorkerLifecycle.EXECUTING, closed.lifecycle(),
                        "the hold still closes after the held wait"));
    }

    @Test
    @DisplayName("a held wait of a generation whose end is requested is answered end")
    void aHeldWaitOfAnEndingGenerationIsAnsweredEnd() {
        var generation = holdingGeneration();
        ending.end(generation, EndCause.RECYCLE);

        var answer = calls.waitOpened(generation.generationId(), OTHER_TASK, generation.configurationDigest());

        assertEquals(new WorkerCalls.WaitAnswer.End(), answer,
                "a held wait of an ending generation is told to end");
    }

    @Test
    @DisplayName("a held generation's bounded wait keeps the hold's silence suppression")
    void aHeldBoundedWaitKeepsTheSuppression() {
        var generation = holdingGeneration();

        var answer = calls.waitOpened(generation.generationId(), "", generation.configurationDigest());
        fixture.advance(Duration.ofSeconds(BOUNDED_WAIT_SECONDS + SILENCE_GRACE_SECONDS));

        assertAll(
                () -> assertEquals(new WorkerCalls.WaitAnswer.WaitAgain(), answer,
                        "a held generation's wait is answered wait again"),
                () -> assertEquals(WorkerLifecycle.HOLDING,
                        supervisor.generation(generation.generationId()).orElseThrow().lifecycle(),
                        "the held wait leaves the generation holding"),
                () -> assertTrue(ending.requestedCause(generation.generationId()).isEmpty(),
                        "a held wait suppresses the silence signal"));
    }

    @Test
    @DisplayName("a closed task ends the hold and re-arms the silence limit")
    void aClosedHeldTaskReArmsTheSilenceLimit() {
        var generation = holdingGeneration();

        calls.taskClosed(generation.generationId(), TASK);
        var closed = supervisor.generation(generation.generationId()).orElseThrow();
        fixture.advance(Duration.ofSeconds(BOUNDED_WAIT_SECONDS + SILENCE_GRACE_SECONDS));

        assertAll(
                () -> assertEquals(WorkerLifecycle.WAITING, closed.lifecycle(),
                        "the closed held task moves the generation to waiting"),
                () -> assertEquals(Optional.of(EndCause.LOST), ending.requestedCause(generation.generationId()),
                        "the closed held task re-arms the silence limit"));
    }

    @Test
    @DisplayName("a close that names another task than the lease is refused")
    void aCloseOfAnotherTaskIsRefused() {
        var generation = holdingGeneration();

        calls.holdClosed(generation.generationId(), OTHER_TASK, fixture.clock.instant());
        var refused = supervisor.generation(generation.generationId()).orElseThrow();
        fixture.advance(Duration.ofSeconds(BOUNDED_WAIT_SECONDS + SILENCE_GRACE_SECONDS));

        assertAll(
                () -> assertEquals(WorkerLifecycle.HOLDING, refused.lifecycle(),
                        "the refused close leaves the hold in place"),
                () -> assertTrue(ending.requestedCause(generation.generationId()).isEmpty(),
                        "the refused close keeps the silence suppressed"));
    }

    @Test
    @DisplayName("a close of an executing generation is refused")
    void aCloseOfAnExecutingGenerationIsRefused() {
        var generation = started(fixture.role(RoleClass.WRITER, RoleForm.WARM, 1));
        calls.waitOpened(generation.generationId(), TASK, generation.configurationDigest());
        calls.callHeartbeat(generation.generationId(), fixture.clock.instant());
        calls.turnCompleted(generation.generationId(), 1_000L);

        calls.holdClosed(generation.generationId(), TASK, fixture.clock.instant());

        assertEquals(WorkerLifecycle.EXECUTING,
                supervisor.generation(generation.generationId()).orElseThrow().lifecycle(),
                "a close of an executing generation is refused");
    }

    @Test
    @DisplayName("a first asking turn's hold is accepted from the offer")
    void aFirstTurnHoldIsAccepted() {
        var generation = started(fixture.role(RoleClass.WRITER, RoleForm.WARM, 1));
        calls.waitOpened(generation.generationId(), TASK, generation.configurationDigest());

        calls.holdOpened(generation.generationId(), TASK, fixture.clock.instant());
        var holding = supervisor.generation(generation.generationId()).orElseThrow();

        assertAll(
                () -> assertEquals(WorkerLifecycle.HOLDING, holding.lifecycle(),
                        "the first asking turn's hold is accepted"),
                () -> assertEquals(TASK, holding.leasedTask(), "the first hold keeps the offered task"));
    }

    @Test
    @DisplayName("an answered hold counts the silence limit again when the acknowledgement is informational")
    void anAnsweredHoldIsSilentAgain() {
        withBounds(null, new WorkerParameters(true, ACK_DEADLINE_SECONDS, true,
                SILENCE_GRACE_SECONDS, IDLE_WAKEUPS, BOUNDED_WAIT_SECONDS, false));
        var generation = holdingGeneration();

        calls.answerDelivered(generation.generationId(), fixture.clock.instant());
        fixture.advance(Duration.ofSeconds(BOUNDED_WAIT_SECONDS + SILENCE_GRACE_SECONDS));

        assertAll(
                () -> assertEquals(Optional.of(EndCause.LOST), ending.requestedCause(generation.generationId()),
                        "an answered hold without a call is silent again"),
                () -> assertEquals(Optional.of(LossReason.SILENCE),
                        calls.loss(generation.generationId()).map(LossSignal::reason),
                        "the loss is the silence limit"));
    }

    @Test
    @DisplayName("a first asking turn's hold is the offer's acknowledgement")
    void aFirstTurnHoldAcknowledgesTheOffer() {
        var generation = started(fixture.role(RoleClass.WRITER, RoleForm.WARM, 1));
        calls.waitOpened(generation.generationId(), TASK, generation.configurationDigest());
        calls.holdOpened(generation.generationId(), TASK, fixture.clock.instant());

        fixture.advance(Duration.ofSeconds(ACK_DEADLINE_SECONDS));

        assertTrue(ending.requestedCause(generation.generationId()).isEmpty(),
                "a first turn's hold never misses the offer's acknowledgement");
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
    @DisplayName("a pause leaves the generations of another plan running")
    void aPauseLeavesAnotherPlanRunning() {
        var role = fixture.role(RoleClass.WRITER, RoleForm.FRESH, 2);
        var otherPlan = RuntimeGenerators.planIds().next();
        var requests = List.of(fixture.freshRequest(role), new WorkerStartRequest(role,
                LaunchRequest.WorkdirKind.PLAN_WORKTREE, otherPlan, fixture.workdir, fixture.envelope(), false,
                List.of(), List.of(), "", null));
        var outcomes = supervisor.start(requests);

        calls.planPaused(fixture.plan);

        assertAll(
                () -> assertEquals(Optional.of(EndCause.PAUSE_AT_ONCE),
                        ending.requestedCause(((WorkerSupervisor.StartOutcome.Started) outcomes.get(0)).generation()
                                .generationId()),
                        "the paused plan's generation ends"),
                () -> assertTrue(ending.requestedCause(
                                ((WorkerSupervisor.StartOutcome.Started) outcomes.get(1)).generation().generationId()).isEmpty(),
                        "the other plan's generation keeps running"));
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

    @Test
    @DisplayName("an enrolment of another harness clears no role of this one")
    void anEnrolmentOfAnotherHarnessClearsNothing() {
        var guard = new StartFailureGuard(fixture.project, List::of, fixture.protocol, fixture.eventSink,
                fixture.clock);
        withGuard(guard);
        var role = fixture.role(RoleClass.WRITER, RoleForm.WARM, 1);
        started(role);
        for (var ignored = 0; ignored < StartFailureGuard.HOLD_THRESHOLD; ignored++) {
            guard.startFailed(role.artifact().name(), role.harness(), "");
        }

        calls.harnessEnrolled("other-harness");

        assertTrue(guard.holds(role.artifact().name(), role.harness()),
                "another harness's enrolment clears no role of this one");
    }
}
