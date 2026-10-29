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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.profile.WorkerParameters;
import de.planmarshall.core.role.RoleClass;
import de.planmarshall.core.role.RoleForm;
import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.service.JobRunner;
import de.planmarshall.core.store.WorkerState;
import de.planmarshall.runtime.launch.HarnessEnrolment;
import de.planmarshall.runtime.launch.HarnessVerifier;
import de.planmarshall.runtime.worker.WorkerSupervisor.StartOutcome;

/**
 * The supervisor scenarios of the package Done When (PM-WORK-3, Detection and Supervision): every deadline is driven by
 * the virtual clock and asserted at deadline−1, at the deadline and after it, so no scenario sleeps. The consultation,
 * resume and {@code agent_started} scenarios start {@link FixtureHarness} as a real process and read its scripted
 * stream, so what the harness does is observed rather than assumed.
 */
@DisplayName("Supervisor scenarios")
class SupervisorScenarioTest {

    private static final Instant NOW = SupervisorTestFixture.NOW;

    private static final String TASK = "qt-0123456789abcdef";

    private static final int ACK_SECONDS = 10;

    private static final int BOUNDED_WAIT_SECONDS = 30;

    private static final int GRACE_SECONDS = 10;

    private static final int SILENCE_SECONDS = BOUNDED_WAIT_SECONDS + GRACE_SECONDS;

    private static final Duration AWAIT = Duration.ofSeconds(20);

    @TempDir
    Path base;

    @Test
    @DisplayName("the acknowledgement signal fires at its deadline and settles after")
    void acknowledgementAtTheDeadline() throws Exception {
        var fixture = new SupervisorTestFixture(base);
        var detection = fixture.detection(RoleClass.WRITER, parameters(OptionalInt.of(ACK_SECONDS), false));
        detection.started(NOW);
        detection.offered(TASK, NOW);
        var deadline = NOW.plusSeconds(ACK_SECONDS);

        fixture.clock.moveTo(deadline.minusMillis(1));
        var before = fixture.signals.size();
        fixture.clock.moveTo(deadline);
        var at = fixture.signals.size();
        fixture.clock.moveTo(deadline.plusSeconds(1));
        var after = fixture.signals.size();

        assertAll(
                () -> assertEquals(0, before, "nothing fires a millisecond before the deadline"),
                () -> assertEquals(1, at, "the signal fires at the deadline"),
                () -> assertEquals(LossReason.ACK_MISSED, fixture.signals.getFirst().reason()),
                () -> assertEquals(TASK, fixture.signals.getFirst().leasedTask()),
                () -> assertEquals(1, after, "a settled detection fires no second signal"));
    }

    @Test
    @DisplayName("the silence signal fires at its deadline and settles after")
    void silenceAtTheDeadline() throws Exception {
        var fixture = new SupervisorTestFixture(base);
        var detection = fixture.detection(RoleClass.WRITER, parameters(OptionalInt.empty(), false));
        detection.started(NOW);
        detection.firstCall(NOW);
        var deadline = NOW.plusSeconds(SILENCE_SECONDS);

        fixture.clock.moveTo(deadline.minusMillis(1));
        var before = fixture.signals.size();
        fixture.clock.moveTo(deadline);
        var at = fixture.signals.size();
        fixture.clock.moveTo(deadline.plusSeconds(1));
        var after = fixture.signals.size();

        assertAll(
                () -> assertEquals(0, before, "nothing fires a millisecond before the deadline"),
                () -> assertEquals(1, at, "the signal fires at the deadline"),
                () -> assertEquals(LossReason.SILENCE, fixture.signals.getFirst().reason()),
                () -> assertEquals(1, after, "a settled detection fires no second signal"));
    }

    @Test
    @DisplayName("an exit after the first call is a loss at once and settles every deadline")
    void anExitAfterTheFirstCallIsALoss() throws Exception {
        var fixture = new SupervisorTestFixture(base);
        var detection = fixture.detection(RoleClass.WRITER, parameters(OptionalInt.empty(), false));
        detection.started(NOW);
        detection.firstCall(NOW);

        var exited = detection.exit(NOW.plusSeconds(1));

        assertAll(
                () -> assertEquals(LossReason.EXIT, exited.orElseThrow().reason(), "the exit is the loss"),
                () -> assertTrue(detection.due(NOW.plusSeconds(SILENCE_SECONDS)).isEmpty(),
                        "a settled detection fires no deadline"));
    }

    @Test
    @DisplayName("a held consultation suppresses the silence until the scripted answer closes it")
    void aConsultationSuppressesSilence() throws Exception {
        var fixture = new SupervisorTestFixture(base);
        try (var harness = FixtureHarness.start(base, List.of("--output", "answer: continue"))) {
            var answer = harness.awaitLine(AWAIT);
            var detection = fixture.detection(RoleClass.WRITER, parameters(OptionalInt.empty(), false));
            detection.started(NOW);
            detection.firstCall(NOW);
            detection.waitOpened(NOW);

            fixture.advance(Duration.ofSeconds(SILENCE_SECONDS * 2L));
            var held = fixture.signals.size();
            detection.waitClosed(fixture.clock.instant());
            fixture.advance(Duration.ofSeconds(SILENCE_SECONDS));

            assertAll(
                    () -> assertEquals("answer: continue", answer,
                            "the consultation reads the harness's scripted answer"),
                    () -> assertEquals(0, held, "a held wait is an open call and is never silent"),
                    () -> assertEquals(1, fixture.signals.size(), "the answer re-arms the silence deadline"));
        }
    }

    @Test
    @DisplayName("a resumed generation reads the fixture harness's scripted stream")
    void aResumedGenerationReadsTheScriptedStream() throws Exception {
        var fixture = new SupervisorTestFixture(base);
        try (var harness = FixtureHarness.start(base, List.of("--output", "resume: session-42"))) {
            var supervisor = fixture.supervisor();
            var role = fixture.role(RoleClass.WRITER, RoleForm.FRESH, 1);

            var started = assertInstanceOf(StartOutcome.Started.class,
                    supervisor.start(List.of(fixture.freshRequest(role))).getFirst());
            var resumed = harness.awaitLine(AWAIT);

            assertAll(
                    () -> assertEquals("resume: session-42", resumed, "the resume reads the harness's scripted stream"),
                    () -> assertEquals(1, fixture.starter.starts.size(), "one worker started"),
                    () -> assertEquals(started.generation().generationId(),
                            fixture.starter.starts.getFirst().launch().generationId(),
                            "the recorded start belongs to the generation"),
                    () -> assertEquals(0, fixture.signals.size(), "no loss fired"));
        }
    }

    @Test
    @DisplayName("the fixture harness runs as a real process with its scripted delay, held call and SIGTERM grace")
    void theFixtureHarnessRunsAsARealProcess() throws Exception {
        var startedAt = System.nanoTime();
        var harness = FixtureHarness.start(base, List.of("--first-call-delay-ms", "300", "--output", "ready",
                "--hold", "--term-grace-ms", "1500"));
        try {
            var line = harness.awaitLine(AWAIT);
            var firstLineMillis = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
            harness.terminate();
            var survivedTheRequest = harness.survives(Duration.ofMillis(300));

            assertAll(
                    () -> assertTrue(harness.pid() > 0, "the fixture harness is a real process"),
                    () -> assertEquals("ready", line, "the scripted stream carries the line"),
                    () -> assertTrue(firstLineMillis >= 300, "the first call waits the scripted delay"),
                    () -> assertTrue(survivedTheRequest, "the harness ignores the termination request for its grace"),
                    () -> assertEquals(143, harness.awaitExit(AWAIT), "the harness exits after its grace"));
        } finally {
            harness.close();
        }
    }

    @Test
    @DisplayName("the start-failure guard holds at its count and a changed configuration clears it with the recycle")
    void theStartFailureGuardHoldsAndAChangedConfigurationClearsIt() throws Exception {
        var fixture = new SupervisorTestFixture(base);
        var parameters = parameters(OptionalInt.empty(), false);
        var guard = new StartFailureGuard(SupervisorTestFixture.PROJECT, List::of, fixture.protocol,
                fixture.events::add, fixture.clock);
        var environment = fixture.environment(guard, SupervisorEnvironment.EligibilityGate.open());
        var supervisor = new WorkerSupervisor(environment);
        var role = fixture.role(RoleClass.WRITER, RoleForm.WARM, 1);
        var generation = assertInstanceOf(StartOutcome.Started.class,
                supervisor.start(List.of(fixture.warmRequest(role, base.resolve("warm")))).getFirst()).generation();
        var ending = new WorkerEnding(environment, () -> false, () -> {
        });
        var recycling = new WorkerRecycling(environment, ending, supervisor::generations,
                name -> name.equals(role.artifact().name()) ? Optional.of(role.artifact().recycling()) : Optional.empty(),
                parameters);

        for (var failure = 1; failure <= StartFailureGuard.HOLD_THRESHOLD; failure++) {
            guard.startFailed(role.artifact().name(), SupervisorTestFixture.CLIENT, "the start failed");
        }
        var heldBeforeTheChange = guard.holds(role.artifact().name(), SupervisorTestFixture.CLIENT);
        var cause = recycling.afterTaskClosed(generation, "another-configuration");
        var failedStarts = fixture.events.stream()
                .filter(event -> AuditEvent.WORKER_START_FAILED.equals(event.event()))
                .count();

        assertAll(
                () -> assertEquals(StartFailureGuard.HOLD_THRESHOLD, failedStarts,
                        "every counted failure is one audit record"),
                () -> assertEquals(List.of(role.artifact().name() + "/" + SupervisorTestFixture.CLIENT),
                        fixture.protocol.closedUnavailable, "the hold closes the role's tasks once"),
                () -> assertTrue(heldBeforeTheChange, "the guard holds at its count"),
                () -> assertEquals(Optional.of(RecycleCause.CONFIGURATION_CHANGED), cause,
                        "the changed configuration recycles the warm worker"),
                () -> assertEquals(Optional.of(EndCause.RECYCLE), ending.requestedCause(generation.generationId()),
                        "the recycle runs through the one end path"),
                () -> assertFalse(guard.holds(role.artifact().name(), SupervisorTestFixture.CLIENT),
                        "the changed configuration clears the guard"));
    }

    @Test
    @DisplayName("a pause at once forces the end with the operator's cause and spends no re-offer")
    void aPauseAtOnceForcesTheEndWithoutAReoffer() throws Exception {
        var fixture = new SupervisorTestFixture(base);
        var environment = fixture.environment(SupervisorEnvironment.StartGuard.open(),
                SupervisorEnvironment.EligibilityGate.open());
        var supervisor = new WorkerSupervisor(environment);
        var role = fixture.role(RoleClass.WRITER, RoleForm.FRESH, 1);
        var generation = assertInstanceOf(StartOutcome.Started.class,
                supervisor.start(List.of(fixture.freshRequest(role))).getFirst()).generation();
        var ending = new WorkerEnding(environment, () -> false, () -> {
        });

        ending.forceEnd(generation, EndCause.PAUSE_AT_ONCE);

        assertAll(
                () -> assertEquals(Optional.of(EndCause.PAUSE_AT_ONCE),
                        ending.requestedCause(generation.generationId()), "the end is requested at once"),
                () -> assertEquals(List.of(JobRunner.CancelCause.PLAN_PAUSED_NOW), fixture.starter.cancels,
                        "the force reaches the runner as the operator's pause"),
                () -> assertFalse(EndCause.PAUSE_AT_ONCE.spendsReoffer(), "a paused task spends no re-offer"),
                () -> assertEquals(WorkerState.EndReason.WITHDRAWN, EndCause.PAUSE_AT_ONCE.endReason(),
                        "the job record names the withdrawal"));
    }

    @Test
    @DisplayName("a pause at once of a plan with a leased task withdraws it, releases the lease at the fence and re-offers nothing")
    void aPauseAtOnceWithALeasedTask() throws Exception {
        var fixture = new SupervisorTestFixture(base);
        var environment = fixture.environment(SupervisorEnvironment.StartGuard.open(),
                SupervisorEnvironment.EligibilityGate.open());
        var supervisor = new WorkerSupervisor(environment);
        var role = fixture.role(RoleClass.WRITER, RoleForm.FRESH, 1);
        var generation = assertInstanceOf(StartOutcome.Started.class,
                supervisor.start(List.of(fixture.freshRequest(role))).getFirst()).generation();
        var leased = generation.withLeasedTask(TASK);
        var successors = new AtomicInteger();
        var ending = new WorkerEnding(environment, () -> false, successors::incrementAndGet);

        ending.forceEnd(leased, EndCause.PAUSE_AT_ONCE);
        var terminal = ending.exited(leased);

        assertAll(
                () -> assertEquals(TASK, leased.leasedTask(), "the paused generation holds the task's lease"),
                () -> assertEquals(List.of(JobRunner.CancelCause.PLAN_PAUSED_NOW), fixture.starter.cancels,
                        "the leased worker is cancelled with the operator's pause"),
                () -> assertEquals(List.of(generation.generationId()), fixture.protocol.fences,
                        "the fenced generation releases the lease of the held task"),
                () -> assertFalse(EndCause.PAUSE_AT_ONCE.spendsReoffer(),
                        "the paused attempt's re-offer budget is not spent"),
                () -> assertEquals(0, successors.get(), "a paused plan starts no successor"),
                () -> assertEquals(1, fixture.stateWriter.writes.size(), "the terminal state is written once"),
                () -> assertEquals(WorkerState.EndReason.WITHDRAWN,
                        fixture.stateWriter.writes.getFirst().endReason(), "the record names the withdrawal"),
                () -> assertTrue(terminal.terminal(), "the generation ends"));
    }

    @Test
    @DisplayName("a continuing worker's missing answer acknowledgement is a loss at its deadline, and an acknowledged one is none")
    void theMissingAnswerAcknowledgementOfAContinuingWorker() throws Exception {
        var fixture = new SupervisorTestFixture(base);
        var parameters = parameters(OptionalInt.of(ACK_SECONDS), false);
        var answeredAt = NOW.plusSeconds(2);
        var silent = fixture.detection(RoleClass.WRITER, parameters);
        silent.started(NOW);
        silent.offered(TASK, NOW);
        silent.acknowledged(NOW.plusSeconds(1));
        silent.waitOpened(answeredAt);
        silent.waitClosed(answeredAt);
        silent.answerDelivered(answeredAt);
        var acknowledging = fixture.detection(RoleClass.WRITER, parameters);
        acknowledging.started(NOW);
        acknowledging.offered(TASK, NOW);
        acknowledging.acknowledged(NOW.plusSeconds(1));
        acknowledging.waitOpened(answeredAt);
        acknowledging.waitClosed(answeredAt);
        acknowledging.answerDelivered(answeredAt);
        acknowledging.acknowledged(answeredAt.plusSeconds(1));
        var deadline = answeredAt.plusSeconds(ACK_SECONDS);

        fixture.clock.moveTo(deadline.minusMillis(1));
        var before = fixture.signals.size();
        fixture.clock.moveTo(deadline);
        var at = fixture.signals.size();

        assertAll(
                () -> assertEquals(0, before, "nothing fires a millisecond before the answer's deadline"),
                () -> assertEquals(1, at, "one continuing worker missed its acknowledgement"),
                () -> assertEquals(LossReason.ACK_MISSED, fixture.signals.getFirst().reason(),
                        "the loss is the missing acknowledgement"),
                () -> assertEquals(TASK, fixture.signals.getFirst().leasedTask(),
                        "the loss names the task the worker holds"),
                () -> assertTrue(acknowledging.due(deadline.plusSeconds(1)).isEmpty(),
                        "a worker that acknowledges the answer has no acknowledgement loss"));
    }

    @Test
    @DisplayName("a consulting wait_again is no idle wake-up, while an idle warm worker recycles at its bound")
    void aConsultingWaitAgainIsNoIdleWakeUp() throws Exception {
        var fixture = new SupervisorTestFixture(base);
        var parameters = parameters(OptionalInt.empty(), false);
        var environment = fixture.environment(SupervisorEnvironment.StartGuard.open(),
                SupervisorEnvironment.EligibilityGate.open());
        var supervisor = new WorkerSupervisor(environment);
        var role = fixture.role(RoleClass.WRITER, RoleForm.WARM, 1);
        var ending = new WorkerEnding(environment, () -> false, () -> {
        });
        var recycling = new WorkerRecycling(environment, ending, supervisor::generations,
                name -> Optional.of(role.artifact().recycling()), parameters);
        var started = assertInstanceOf(StartOutcome.Started.class,
                supervisor.start(List.of(fixture.warmRequest(role, base.resolve("warm")))).getFirst()).generation();
        var consulting = started.withLeasedTask(TASK);
        var idle = started.withLeasedTask(null).withIdleWakeups(parameters.idleWakeups() - 1);

        var held = recycling.waitAgain(consulting);
        var recycled = recycling.waitAgain(idle);

        assertAll(
                () -> assertEquals(consulting.idleWakeups(), held.idleWakeups(), "a held task is not idle"),
                () -> assertTrue(held.recycleCause().isEmpty(), "a consulting worker is never recycled as idle"),
                () -> assertEquals(parameters.idleWakeups(), recycled.idleWakeups(),
                        "the idle wake-up bound counts the answer"),
                () -> assertEquals(Optional.of(RecycleCause.IDLE), recycled.recycleCause(),
                        "the idle warm worker recycles"),
                () -> assertEquals(Optional.of(EndCause.RECYCLE), ending.requestedCause(idle.generationId())));
    }

    @Test
    @DisplayName("an enrolled executable in the worker's tree raises agent_started, restarts the worker and names it")
    void anEnrolledExecutableRaisesAgentStarted() throws Exception {
        var fixture = new SupervisorTestFixture(base);
        var environment = fixture.environment(SupervisorEnvironment.StartGuard.open(),
                SupervisorEnvironment.EligibilityGate.open());
        var executable = realExecutable();
        try (var harness = FixtureHarness.start(base, List.of("--output", "ready", "--child",
                     executable.toString(), "--hold"))) {
            assertEquals("ready", harness.awaitLine(AWAIT));
            var childPid = Long.parseLong(harness.awaitLine(AWAIT).substring(FixtureHarness.CHILD_MARKER.length()));
            var ending = new WorkerEnding(environment, () -> false, () -> {
            });
            var generation = generation(fixture, environment);
            var detector = new AgentStartedDetector(environment, ProcessExecutables.system(),
                    () -> List.of(enrolment(executable, sha256(executable))), ending);

            var matched = detector.detect(generation, harness.pid(), task());

            assertAll(
                    () -> assertEquals(executable, matched.orElseThrow().path(),
                            "the first process of the tree that runs the enrolled file matches"),
                    () -> assertEquals(1, fixture.protocol.startedAgents.size(), "exactly one signal"),
                    () -> assertEquals(executable.toString(),
                            fixture.protocol.startedAgents.getFirst().executable(),
                            "the signal names the executable of the recorded tree"),
                    () -> assertEquals(TASK, fixture.protocol.startedAgents.getFirst().queueTaskId(),
                            "the signal names the task whose attempt is re-offered"),
                    () -> assertEquals(Optional.of(EndCause.OFF_TRACK), ending.requestedCause(generation.generationId()),
                            "the fixed action is restart"),
                    () -> assertTrue(EndCause.OFF_TRACK.spendsReoffer(), "the restart spends one re-offer"));
            ProcessHandle.of(childPid).ifPresent(ProcessHandle::destroyForcibly);
        }
    }

    @Test
    @DisplayName("an impostor of the enrolled executable does not match")
    void anImpostorBinaryDoesNotMatch() throws Exception {
        var fixture = new SupervisorTestFixture(base);
        var environment = fixture.environment(SupervisorEnvironment.StartGuard.open(),
                SupervisorEnvironment.EligibilityGate.open());
        var executable = realExecutable();
        try (var harness = FixtureHarness.start(base, List.of("--output", "ready", "--child",
                     executable.toString(), "--hold"))) {
            assertEquals("ready", harness.awaitLine(AWAIT));
            var childPid = Long.parseLong(harness.awaitLine(AWAIT).substring(FixtureHarness.CHILD_MARKER.length()));
            var ending = new WorkerEnding(environment, () -> false, () -> {
            });
            var generation = generation(fixture, environment);
            var detector = new AgentStartedDetector(environment, ProcessExecutables.system(),
                    () -> List.of(enrolment(executable, "b".repeat(64))), ending);

            var matched = detector.detect(generation, harness.pid(), task());

            assertAll(
                    () -> assertTrue(matched.isEmpty(), "the same path with another digest is not the enrolled file"),
                    () -> assertTrue(fixture.protocol.startedAgents.isEmpty(), "no signal"),
                    () -> assertTrue(ending.requestedCause(generation.generationId()).isEmpty(),
                            "the worker is not ended"));
            ProcessHandle.of(childPid).ifPresent(ProcessHandle::destroyForcibly);
        }
    }

    private WorkerGeneration generation(SupervisorTestFixture fixture, SupervisorEnvironment environment) {
        var supervisor = new WorkerSupervisor(environment);
        var role = fixture.role(RoleClass.WRITER, RoleForm.FRESH, 1);
        return assertInstanceOf(StartOutcome.Started.class,
                supervisor.start(List.of(fixture.freshRequest(role))).getFirst()).generation();
    }

    /** @return an executable the installation carries, the file an enrolment names in the scenario */
    private static Path realExecutable() throws IOException {
        return Path.of("/bin/sleep").toRealPath();
    }

    private static AgentStartedDetector.HeldTask task() {
        return new AgentStartedDetector.HeldTask(TASK, 1);
    }

    private static HarnessEnrolment enrolment(Path executable, String sha256) {
        return new HarnessEnrolment(SupervisorTestFixture.CLIENT, executable, sha256, "1.5.0", NOW, NOW,
                new HarnessEnrolment.UsageReport(HarnessEnrolment.UsageSource.DECLARED, Map.of()), Map.of(), Map.of());
    }

    private static String sha256(Path executable) {
        return HarnessVerifier.digestExecutable(executable).orElseThrow();
    }

    private static WorkerParameters parameters(OptionalInt ackDeadline, boolean informational) {
        return new WorkerParameters(true, ackDeadline, informational, GRACE_SECONDS, 3, BOUNDED_WAIT_SECONDS, true);
    }
}
