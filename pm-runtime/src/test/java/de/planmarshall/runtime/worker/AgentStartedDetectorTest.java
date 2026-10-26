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
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.role.RoleArtifact;
import de.planmarshall.core.role.RoleClass;
import de.planmarshall.core.role.RoleForm;
import de.planmarshall.runtime.launch.HarnessEnrolment;
import de.planmarshall.runtime.launch.HarnessVerifier;

/**
 * The {@code agent_started} detection (PM-WORK-7, A Worker Off Track): the enrolled match by path and digest, the
 * impostor control, the operator binary, the fixed action and threshold, the record the signal writes, and the one end
 * of the worker.
 * <p>
 * The tree is the real one: the test's own process is the root and the child it starts is the member, so the detector
 * reads what the operating system answers for a live process rather than a table the test supplied.
 */
@EnableTestLogger
@DisplayName("agent_started detection")
class AgentStartedDetectorTest {

    /** An executable every Linux and macOS installation carries, so the enrolled match reads a real file. */
    private static final Path SLEEP = Path.of("/bin/sleep").toAbsolutePath();

    private static final String QUEUE_TASK = "queue-task-7";

    private static final int ATTEMPT = 2;

    @TempDir
    Path base;

    private SupervisorTestFixture fixture;

    private Process child;

    @BeforeEach
    void setUp() throws IOException {
        fixture = new SupervisorTestFixture(base);
    }

    @AfterEach
    void tearDown() {
        if (child != null) {
            child.destroyForcibly();
        }
    }

    @Test
    @DisplayName("the first process of the tree that runs an enrolled executable raises the signal once")
    void theEnrolledMatchRaisesTheSignalOnce() throws Exception {
        var environment = environment();
        var generation = generation(environment);
        var ending = ending(environment);
        child = startChild();
        var executable = realPath();
        var detector = new AgentStartedDetector(environment, ProcessExecutables.system(),
                () -> List.of(enrolment(executable, sha256(executable))), ending);

        var matched = detector.detect(generation, ProcessHandle.current().pid(), task());
        var again = detector.detect(generation, ProcessHandle.current().pid(), task());
        var signal = fixture.protocol.startedAgents.getFirst();

        assertAll(
                () -> assertEquals(executable, matched.orElseThrow().path(), "the child's executable matched"),
                () -> assertEquals(ProcessExecutable.DIGEST_PREFIX + sha256(executable),
                        matched.orElseThrow().digest(), "the enrolment's digest is compared with the file's"),
                () -> assertTrue(again.isEmpty(), "one started agent never raises the signal twice"),
                () -> assertEquals(1, fixture.protocol.startedAgents.size(), "exactly one signal"),
                () -> assertEquals(generation.generationId(), signal.generationId()),
                () -> assertEquals(QUEUE_TASK, signal.queueTaskId(), "the signal names the task it holds"),
                () -> assertEquals(ATTEMPT, signal.attempt()),
                () -> assertEquals(executable.toString(), signal.executable()),
                () -> assertEquals(Optional.of(EndCause.OFF_TRACK), ending.requestedCause(generation.generationId()),
                        "the action is restart"),
                () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                        "Agent started in the process tree of worker generation '" + generation.generationId() + "'"),
                () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                        executable + "' (" + ProcessExecutable.DIGEST_PREFIX));
    }

    @Test
    @DisplayName("an impostor of the enrolled name with another digest does not match")
    void anImpostorOfAnotherDigestDoesNotMatch() throws Exception {
        var environment = environment();
        var generation = generation(environment);
        var ending = ending(environment);
        child = startChild();
        var executable = realPath();
        var detector = new AgentStartedDetector(environment, ProcessExecutables.system(),
                () -> List.of(enrolment(executable, "b".repeat(64))), ending);

        var matched = detector.detect(generation, ProcessHandle.current().pid(), task());

        assertAll(
                () -> assertTrue(matched.isEmpty(), "the same path with another digest is not the enrolled file"),
                () -> assertTrue(fixture.protocol.startedAgents.isEmpty(), "no signal"),
                () -> assertTrue(ending.requestedCause(generation.generationId()).isEmpty(),
                        "the worker is not ended"));
    }

    @Test
    @DisplayName("the installed operator binary matches by path and digest")
    void theOperatorBinaryMatches() throws Exception {
        var operator = operatorBinary();
        var environment = environment();
        var generation = generation(environment);
        var ending = ending(environment);
        child = startChild();
        var detector = new AgentStartedDetector(environment,
                reading(child.pid(), new ProcessExecutable(child.pid(), operator, digest(operator))), List::of, ending);

        var matched = detector.detect(generation, ProcessHandle.current().pid(), task());

        assertAll(
                () -> assertEquals(operator, matched.orElseThrow().path(), "the operator binary matched"),
                () -> assertEquals(1, fixture.protocol.startedAgents.size(), "one signal"),
                () -> assertEquals(Optional.of(EndCause.OFF_TRACK), ending.requestedCause(generation.generationId())));
    }

    @Test
    @DisplayName("the operator binary at another digest does not match")
    void theOperatorBinaryOfAnotherDigestDoesNotMatch() throws Exception {
        var operator = operatorBinary();
        var environment = environment();
        var generation = generation(environment);
        var ending = ending(environment);
        child = startChild();
        var detector = new AgentStartedDetector(environment, reading(child.pid(),
                new ProcessExecutable(child.pid(), operator, ProcessExecutable.DIGEST_PREFIX + "c".repeat(64))),
                List::of, ending);

        var matched = detector.detect(generation, ProcessHandle.current().pid(), task());

        assertAll(
                () -> assertTrue(matched.isEmpty(), "the installed file's digest decides the match"),
                () -> assertTrue(fixture.protocol.startedAgents.isEmpty(), "no signal"),
                () -> assertTrue(ending.requestedCause(generation.generationId()).isEmpty(), "no end"));
    }

    @Test
    @DisplayName("a role artifact's own off-track action and threshold change neither value")
    void theActionAndThresholdStayFixed() throws Exception {
        var lowered = Map.of(RoleArtifact.OffTrackSignal.REPEATING,
                new RoleArtifact.OffTrackSetting(1.0, RoleArtifact.OffTrackAction.SHOW),
                RoleArtifact.OffTrackSignal.NO_PROGRESS,
                new RoleArtifact.OffTrackSetting(5.0, RoleArtifact.OffTrackAction.NUDGE));
        var environment = environment();
        var generation = generation(environment, lowered);
        var ending = ending(environment);
        child = startChild();
        var executable = realPath();
        var detector = new AgentStartedDetector(environment, ProcessExecutables.system(),
                () -> List.of(enrolment(executable, sha256(executable))), ending);

        var matched = detector.detect(generation, ProcessHandle.current().pid(), task());

        assertAll(
                () -> assertTrue(matched.isPresent(), "the first match raises the signal"),
                () -> assertEquals(1, fixture.protocol.startedAgents.size(),
                        "the threshold stays 1, whatever the artifact names for its own signals"),
                () -> assertEquals(Optional.of(EndCause.OFF_TRACK), ending.requestedCause(generation.generationId()),
                        "the action stays restart, whatever the artifact names"));
    }

    @Test
    @DisplayName("a tree of ordinary processes raises no signal")
    void anOrdinaryTreeRaisesNoSignal() throws Exception {
        var environment = environment();
        var generation = generation(environment);
        var ending = ending(environment);
        child = startChild();
        var detector = new AgentStartedDetector(environment, ProcessExecutables.system(), List::of, ending);

        var matched = detector.detect(generation, ProcessHandle.current().pid(), task());

        assertAll(
                () -> assertTrue(matched.isEmpty(), "no enrolled executable and no operator binary in the tree"),
                () -> assertTrue(fixture.protocol.startedAgents.isEmpty(), "no signal"),
                () -> assertTrue(ending.requestedCause(generation.generationId()).isEmpty(), "no end"));
    }

    private SupervisorEnvironment environment() {
        return fixture.environment(SupervisorEnvironment.StartGuard.open(),
                SupervisorEnvironment.EligibilityGate.open());
    }

    /** Starts the child the recorded tree holds, so the detector reads a live process. */
    private Process startChild() throws IOException {
        return new ProcessBuilder(realPath().toString(), "30").start();
    }

    /** @return the installed operator binary, without which the detector matches no operator process */
    private Path operatorBinary() throws IOException {
        var operator = Files.createDirectories(base.resolve("runtime").resolve("bin")).resolve("pm-operator");
        Files.writeString(operator, "#!/bin/sh\n");
        return operator;
    }

    /** @return the path the operating system reports for the executable the child runs */
    private Path realPath() throws IOException {
        return SLEEP.toRealPath();
    }

    private static String sha256(Path executable) {
        return HarnessVerifier.digestExecutable(executable).orElseThrow();
    }

    private static String digest(Path file) {
        return ProcessExecutable.DIGEST_PREFIX + HarnessVerifier.digestExecutable(file).orElseThrow();
    }

    /** @return an executable reader that answers one PID with the executable the test names */
    private static ProcessExecutables reading(long pid, ProcessExecutable executable) {
        return asked -> asked == pid ? new ProcessExecutables.Found(executable) : new ProcessExecutables.Absent();
    }

    private static AgentStartedDetector.HeldTask task() {
        return new AgentStartedDetector.HeldTask(QUEUE_TASK, ATTEMPT);
    }

    private static HarnessEnrolment enrolment(Path executable, String sha256) {
        var now = Instant.parse("2026-10-25T10:00:00Z");
        return new HarnessEnrolment(SupervisorTestFixture.CLIENT, executable, sha256, "1.5.0", now, now,
                new HarnessEnrolment.UsageReport(HarnessEnrolment.UsageSource.DECLARED, Map.of()), Map.of(), Map.of());
    }

    private static WorkerEnding ending(SupervisorEnvironment environment) {
        return new WorkerEnding(environment, () -> false, () -> {
        });
    }

    private WorkerGeneration generation(SupervisorEnvironment environment) {
        return generation(environment, Map.of());
    }

    /** @return the generation one start of the fixture's role registered */
    private WorkerGeneration generation(SupervisorEnvironment environment,
            Map<RoleArtifact.OffTrackSignal, RoleArtifact.OffTrackSetting> offTrack) {
        var supervisor = new WorkerSupervisor(environment);
        var role = fixture.role(RoleClass.WRITER, RoleForm.FRESH, 1, offTrack);
        var started = (WorkerSupervisor.StartOutcome.Started) supervisor.start(List.of(fixture.freshRequest(role)))
                .getFirst();
        return started.generation();
    }
}
